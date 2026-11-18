//go:build integration

package reminders

import (
	"context"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/iam"
	"github.com/aws/aws-sdk-go-v2/service/scheduler"

	"github.com/Pierorivera1/DinoGym/backend/internal/awstest"
)

// TestReconcileOnTheEmulator runs the reconciler against the emulator's
// Scheduler: it proves the API calls and their fields are accepted (time
// zone, retry policy, group), not that the schedule invokes a Lambda, which
// the emulator cannot run for Go (decision 14).
func TestReconcileOnTheEmulator(t *testing.T) {
	cfg := awstest.Config(t)
	ctx := context.Background()

	roles := iam.NewFromConfig(cfg)
	roleName := awstest.Name("dinogym-scheduler")
	role, err := roles.CreateRole(ctx, &iam.CreateRoleInput{
		RoleName: &roleName,
		AssumeRolePolicyDocument: aws.String(`{"Version":"2012-10-17","Statement":[{"Effect":"Allow",` +
			`"Principal":{"Service":"scheduler.amazonaws.com"},"Action":"sts:AssumeRole"}]}`),
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _, _ = roles.DeleteRole(context.Background(), &iam.DeleteRoleInput{RoleName: &roleName}) })

	sched := scheduler.NewFromConfig(cfg)
	group := awstest.Name("dinogym-test")
	if _, err := sched.CreateScheduleGroup(ctx, &scheduler.CreateScheduleGroupInput{Name: &group}); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = sched.DeleteScheduleGroup(context.Background(), &scheduler.DeleteScheduleGroupInput{Name: &group})
	})

	prefs := mapSettings{KeyWorkoutDay: "true", KeyWeekly: "true", KeyTime: "06:45", KeyTimeZone: "America/Lima"}
	r := NewReconciler(quiet, prefs, sched, Target{
		Group:       group,
		FunctionARN: "arn:aws:lambda:us-east-1:000000000000:function:dinogym-test-reminders",
		RoleARN:     *role.Role.Arn,
	})

	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatalf("create: %v", err)
	}
	got, err := sched.GetSchedule(ctx, &scheduler.GetScheduleInput{Name: aws.String(user + "-day"), GroupName: &group})
	if err != nil {
		t.Fatal(err)
	}
	if *got.ScheduleExpression != "cron(45 6 * * ? *)" || *got.ScheduleExpressionTimezone != "America/Lima" {
		t.Fatalf("day schedule %s in %s", *got.ScheduleExpression, *got.ScheduleExpressionTimezone)
	}

	prefs[KeyTime] = "18:00"
	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatalf("update: %v", err)
	}
	got, err = sched.GetSchedule(ctx, &scheduler.GetScheduleInput{Name: aws.String(user + "-day"), GroupName: &group})
	if err != nil || *got.ScheduleExpression != "cron(0 18 * * ? *)" {
		t.Fatalf("after update: %v %v", got, err)
	}

	prefs[KeyWorkoutDay], prefs[KeyWeekly] = "false", "false"
	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if _, err := sched.GetSchedule(ctx, &scheduler.GetScheduleInput{Name: aws.String(user + "-week"), GroupName: &group}); err == nil {
		t.Fatal("weekly schedule still exists after turning it off")
	}
}
