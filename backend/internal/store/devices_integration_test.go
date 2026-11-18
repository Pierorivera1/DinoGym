//go:build integration

package store

import (
	"context"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

func TestDevicesOnTheEmulator(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	now := time.Now()
	const user = "u1"

	if err := s.PutDevice(ctx, user, "install-a", "token-1", now); err != nil {
		t.Fatal(err)
	}
	if err := s.PutDevice(ctx, user, "install-a", "token-2", now); err != nil {
		t.Fatal(err)
	}
	if err := s.PutDevice(ctx, user, "install-b", "token-3", now.Add(-DeviceTTL-time.Hour)); err != nil {
		t.Fatal(err)
	}
	if err := s.PutDevice(ctx, "u2", "install-c", "token-4", now); err != nil {
		t.Fatal(err)
	}

	got, err := s.Devices(ctx, user, now)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || got[0] != (Device{InstallationID: "install-a", Token: "token-2"}) {
		t.Fatalf("devices %+v, want only install-a with its newest token (install-b is past its TTL)", got)
	}

	for range 2 {
		if err := s.DeleteDevice(ctx, user, "install-a"); err != nil {
			t.Fatal(err)
		}
	}
	if got, _ := s.Devices(ctx, user, now); len(got) != 0 {
		t.Fatalf("devices %+v after delete", got)
	}

	// Devices never reach a pull.
	changes, _, _, err := s.Since(ctx, "u2", 0, 100)
	if err != nil || len(changes) != 0 {
		t.Fatalf("pull returned %d rows, %v; device items must stay out of sync", len(changes), err)
	}
}

func TestReminderSettingsReadsSyncedPreferences(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	at := time.Now().Add(-time.Minute).UnixMilli()
	pref := func(key, value string) Row {
		return Row{Kind: "PREF", ID: key, UpdatedAt: at, Fields: map[string]types.AttributeValue{"value": &types.AttributeValueMemberS{Value: value}}}
	}
	if _, err := s.PutRows(ctx, "u1", []Row{
		pref("reminder_workout_day", "true"),
		pref("reminder_time", "07:30"),
		deleted(pref("reminder_weekly", "true"), at),
		pref("body_weight_goal_kg", "70"),
	}); err != nil {
		t.Fatal(err)
	}
	got, err := s.ReminderSettings(ctx, "u1")
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got["reminder_workout_day"] != "true" || got["reminder_time"] != "07:30" {
		t.Fatalf("settings %v, want the two live reminder rows only", got)
	}
}
