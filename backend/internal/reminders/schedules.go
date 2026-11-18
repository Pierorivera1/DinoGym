package reminders

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/scheduler"
	"github.com/aws/aws-sdk-go-v2/service/scheduler/types"
)

// SchedulerAPI is the part of the EventBridge Scheduler client the
// reconciler uses. *scheduler.Client is the real one.
type SchedulerAPI interface {
	CreateSchedule(context.Context, *scheduler.CreateScheduleInput, ...func(*scheduler.Options)) (*scheduler.CreateScheduleOutput, error)
	UpdateSchedule(context.Context, *scheduler.UpdateScheduleInput, ...func(*scheduler.Options)) (*scheduler.UpdateScheduleOutput, error)
	DeleteSchedule(context.Context, *scheduler.DeleteScheduleInput, ...func(*scheduler.Options)) (*scheduler.DeleteScheduleOutput, error)
}

var _ SchedulerAPI = (*scheduler.Client)(nil)

// SettingsStore reads the stored reminder preferences. *store.Store is the real one.
type SettingsStore interface {
	ReminderSettings(ctx context.Context, userID string) (map[string]string, error)
}

// Target is where the schedules deliver: the reminder Lambda, invoked with
// RoleARN, a role Scheduler may assume that may only invoke it.
type Target struct {
	Group       string
	FunctionARN string
	RoleARN     string
}

// Event is what a schedule sends the reminder Lambda.
type Event struct {
	UserID string `json:"userId"`
	Kind   Kind   `json:"kind"`
}

// Reconciler creates, updates and deletes a user's schedules to match the
// settings stored for them (decision 105).
type Reconciler struct {
	log    *slog.Logger
	store  SettingsStore
	sched  SchedulerAPI
	target Target
}

// NewReconciler returns a Reconciler for one environment's schedule group.
func NewReconciler(log *slog.Logger, st SettingsStore, sched SchedulerAPI, t Target) *Reconciler {
	return &Reconciler{log: log, store: st, sched: sched, target: t}
}

// Reconcile reads the user's stored settings and puts or deletes each of
// their schedules. It reads the stored rows rather than trusting a pushed
// one, so calling it twice, or in either order after two pushes, ends in the
// same state.
func (r *Reconciler) Reconcile(ctx context.Context, userID string) error {
	prefs, err := r.store.ReminderSettings(ctx, userID)
	if err != nil {
		return err
	}
	set, err := ParseSettings(prefs)
	if err != nil {
		// Logged without the user id; the reason names only the key.
		r.log.WarnContext(ctx, "reminder settings unusable; removing schedules", "reason", err.Error())
	}
	var errs []error
	for _, k := range Kinds {
		if set.On(k) {
			errs = append(errs, r.put(ctx, userID, k, set))
		} else {
			errs = append(errs, r.remove(ctx, userID, k))
		}
	}
	return errors.Join(errs...)
}

// put updates the schedule, or creates it when it does not exist yet. Update
// first, because after the first time almost every call is an update.
func (r *Reconciler) put(ctx context.Context, userID string, k Kind, s Settings) error {
	input, err := json.Marshal(Event{UserID: userID, Kind: k})
	if err != nil {
		return err
	}
	name := scheduleName(userID, k)
	target := &types.Target{
		Arn:     &r.target.FunctionARN,
		RoleArn: &r.target.RoleARN,
		Input:   aws.String(string(input)),
		// A missed reminder is not worth more: two tries within an hour.
		RetryPolicy: &types.RetryPolicy{
			MaximumRetryAttempts:     aws.Int32(2),
			MaximumEventAgeInSeconds: aws.Int32(3600),
		},
	}
	window := &types.FlexibleTimeWindow{Mode: types.FlexibleTimeWindowModeOff}
	_, err = r.sched.UpdateSchedule(ctx, &scheduler.UpdateScheduleInput{
		Name:                       &name,
		GroupName:                  &r.target.Group,
		ScheduleExpression:         aws.String(s.cron(k)),
		ScheduleExpressionTimezone: &s.TimeZone,
		FlexibleTimeWindow:         window,
		Target:                     target,
		State:                      types.ScheduleStateEnabled,
	})
	var missing *types.ResourceNotFoundException
	if !errors.As(err, &missing) {
		if err != nil {
			return fmt.Errorf("update schedule %s: %w", k, err)
		}
		return nil
	}
	_, err = r.sched.CreateSchedule(ctx, &scheduler.CreateScheduleInput{
		Name:                       &name,
		GroupName:                  &r.target.Group,
		ScheduleExpression:         aws.String(s.cron(k)),
		ScheduleExpressionTimezone: &s.TimeZone,
		FlexibleTimeWindow:         window,
		Target:                     target,
		State:                      types.ScheduleStateEnabled,
	})
	if err != nil {
		return fmt.Errorf("create schedule %s: %w", k, err)
	}
	return nil
}

func (r *Reconciler) remove(ctx context.Context, userID string, k Kind) error {
	_, err := r.sched.DeleteSchedule(ctx, &scheduler.DeleteScheduleInput{
		Name:      aws.String(scheduleName(userID, k)),
		GroupName: &r.target.Group,
	})
	var missing *types.ResourceNotFoundException
	if err != nil && !errors.As(err, &missing) {
		return fmt.Errorf("delete schedule %s: %w", k, err)
	}
	return nil
}
