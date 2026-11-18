package reminders

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// FireStore is what the reminder Lambda reads and cleans up. *store.Store is
// the real one.
type FireStore interface {
	SettingsStore
	Devices(ctx context.Context, userID string, now time.Time) ([]store.Device, error)
	DeleteDevice(ctx context.Context, userID, installationID string) error
}

// Sender delivers one data message. *FCM is the real one.
type Sender interface {
	Send(ctx context.Context, token string, k Kind) error
}

// Firer handles a schedule firing.
type Firer struct {
	log   *slog.Logger
	store FireStore
	send  Sender
	now   func() time.Time
}

// NewFirer returns a Firer.
func NewFirer(log *slog.Logger, st FireStore, s Sender) *Firer {
	return &Firer{log: log, store: st, send: s, now: time.Now}
}

// Fire sends ev's reminder to every phone of the user, if the setting is
// still on (a schedule left behind by a failed reconcile sends nothing).
// Tokens FCM no longer knows are deleted. It returns an error only when no
// phone could be reached for a reason worth retrying, so Scheduler's retry
// does not send twice to phones that got it.
func (f *Firer) Fire(ctx context.Context, ev Event) error {
	if ev.UserID == "" || (ev.Kind != WorkoutDay && ev.Kind != WeeklySummary) {
		return fmt.Errorf("bad event: kind %q", ev.Kind)
	}
	prefs, err := f.store.ReminderSettings(ctx, ev.UserID)
	if err != nil {
		return err
	}
	set, err := ParseSettings(prefs)
	if err != nil || !set.On(ev.Kind) {
		f.log.InfoContext(ctx, "reminder skipped: turned off", "kind", ev.Kind)
		return nil
	}
	devices, err := f.store.Devices(ctx, ev.UserID, f.now())
	if err != nil {
		return err
	}
	sent, gone := 0, 0
	var failed []error
	for _, d := range devices {
		err := f.send.Send(ctx, d.Token, ev.Kind)
		switch {
		case err == nil:
			sent++
		case errors.Is(err, ErrUnregistered):
			gone++
			if err := f.store.DeleteDevice(ctx, ev.UserID, d.InstallationID); err != nil {
				failed = append(failed, err)
			}
		default:
			failed = append(failed, err)
		}
	}
	// Counts only: no user ids or tokens in the logs.
	f.log.InfoContext(ctx, "reminder sent", "kind", ev.Kind, "devices", len(devices), "sent", sent, "unregistered", gone, "failed", len(failed))
	if sent == 0 && len(failed) > 0 {
		return errors.Join(failed...)
	}
	return nil
}
