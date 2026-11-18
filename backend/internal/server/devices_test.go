package server

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
)

// recordingReminders records the users it was asked to reconcile.
type recordingReminders struct {
	mu    sync.Mutex
	users []string
	err   error
}

func (r *recordingReminders) Reconcile(_ context.Context, userID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.users = append(r.users, userID)
	return r.err
}

func (r *recordingReminders) calls() int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return len(r.users)
}

const installID = "0b9a4c3e-1f2d-4e5a-8b7c-6d5e4f3a2b1c"

func devicesEnv(t *testing.T) (http.Handler, *fakeStore, *recordingReminders, string) {
	t.Helper()
	st := newFakeStore()
	rem := &recordingReminders{}
	a := testAuth(t)
	h := New(slog.New(slog.NewTextHandler(io.Discard, nil)), st, a).WithReminders(rem).Handler()
	return h, st, rem, bearer(t, a)
}

func TestRegisterAndUnregisterDevice(t *testing.T) {
	h, st, rem, user := devicesEnv(t)

	rec, _ := serveWith(t, h, http.MethodPut, "/devices/"+installID, `{"token":"fcm-token-1"}`, user)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("PUT: %d %s", rec.Code, rec.Body)
	}
	rec, _ = serveWith(t, h, http.MethodPut, "/devices/"+installID, `{"token":"fcm-token-2"}`, user)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("second PUT: %d", rec.Code)
	}
	var tokens []string
	for _, devs := range st.devices {
		for _, tok := range devs {
			tokens = append(tokens, tok)
		}
	}
	if len(tokens) != 1 || tokens[0] != "fcm-token-2" {
		t.Fatalf("stored tokens %v, want only the newest", tokens)
	}
	if rem.calls() != 2 {
		t.Fatalf("reconciled %d times, want once per registration", rem.calls())
	}

	for range 2 { // deleting twice is fine
		rec, _ = serveWith(t, h, http.MethodDelete, "/devices/"+installID, "", user)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("DELETE: %d", rec.Code)
		}
	}
	for _, devs := range st.devices {
		if len(devs) != 0 {
			t.Fatalf("device still stored after DELETE: %v", devs)
		}
	}
}

func TestDeviceRequestErrors(t *testing.T) {
	h, _, _, user := devicesEnv(t)
	cases := []struct {
		method, path, body, auth string
		want                     int
	}{
		{http.MethodPut, "/devices/" + installID, `{"token":"t"}`, "", http.StatusUnauthorized},
		{http.MethodDelete, "/devices/" + installID, "", "", http.StatusUnauthorized},
		{http.MethodPut, "/devices/not-a-uuid", `{"token":"t"}`, user, http.StatusBadRequest},
		{http.MethodPut, "/devices/" + installID, `{"token":""}`, user, http.StatusBadRequest},
		{http.MethodPut, "/devices/" + installID, `{}`, user, http.StatusBadRequest},
	}
	for _, c := range cases {
		rec, _ := serveWith(t, h, c.method, c.path, c.body, c.auth)
		if rec.Code != c.want {
			t.Errorf("%s %s %s: %d, want %d", c.method, c.path, c.body, rec.Code, c.want)
		}
	}
}

func TestAFailedReconcileDoesNotFailTheRequest(t *testing.T) {
	h, _, rem, user := devicesEnv(t)
	rem.err = context.DeadlineExceeded
	rec, _ := serveWith(t, h, http.MethodPut, "/devices/"+installID, `{"token":"t"}`, user)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("PUT with a failing reconcile: %d, want 204", rec.Code)
	}
}

func TestPushReconcilesOnlyForAcceptedReminderSettings(t *testing.T) {
	h, _, rem, user := devicesEnv(t)
	e := &syncEnv{t: t, h: h, alice: user}
	at := time.Now().Add(-time.Minute).UnixMilli()
	pref := func(key string, updatedAt int64) api.ChangeSet {
		return api.ChangeSet{Preferences: &[]api.Preference{{Key: key, Value: "true", UpdatedAt: updatedAt}}}
	}

	e.push(user, pref("body_weight_goal_kg", at))
	if rem.calls() != 0 {
		t.Fatal("a push without reminder settings reconciled")
	}
	e.push(user, pref("reminder_workout_day", at))
	if rem.calls() != 1 {
		t.Fatalf("reconciled %d times after a reminder setting, want 1", rem.calls())
	}
	e.push(user, pref("reminder_workout_day", at)) // stale: same updatedAt
	if rem.calls() != 1 {
		t.Fatal("a stale reminder row reconciled again")
	}
}
