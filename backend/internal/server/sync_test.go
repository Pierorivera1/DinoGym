package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

func TestSyncOnTheFake(t *testing.T) {
	runSyncSuite(t, func(*testing.T) Store { return newFakeStore() })
}

func TestSyncNeverLogsRowContents(t *testing.T) {
	var logs bytes.Buffer
	log := slog.New(slog.NewTextHandler(&logs, &slog.HandlerOptions{Level: slog.LevelDebug}))
	e := newSyncEnv(t, newFakeStore(), log)
	at := time.Now().Add(-time.Hour).UnixMilli()
	e.push(e.alice, fullChangeSet(at))
	e.pull(e.alice, 0, 0)

	if !strings.Contains(logs.String(), "sync push") {
		t.Fatal("no push log line; the check below would prove nothing")
	}
	for _, secret := range []string{"Día de pierna", "easy", "goal_weight_kg", "72.5", uid(11)} {
		if strings.Contains(logs.String(), secret) {
			t.Fatalf("row content %q was logged", secret)
		}
	}
}

func TestValidationErrorsNameTheField(t *testing.T) {
	e := newSyncEnv(t, newFakeStore(), nil)
	cases := map[string]string{
		`{"weighIns":[{"id":"abc","measuredAt":1,"weightKg":70,"updatedAt":1}]}`:                                        "/weighIns/0/id: ",
		`{"weighIns":[{"id":"00000000-0000-4000-8000-000000000001","measuredAt":1,"weightKg":70}]}`:                     "updatedAt",
		`{"preferences":[{"key":"a","value":"b","updatedAt":1},{"key":"b","value":"c","updatedAt":1,"deletedAt":"x"}]}`: "/preferences/1/deletedAt: ",
	}
	for body, want := range cases {
		_, eb := e.pushRaw(e.alice, body)
		if !strings.Contains(eb.Message, want) || strings.Contains(eb.Message, "Schema:") || len(eb.Message) > 200 {
			t.Errorf("message %q, want it to name %q without a schema dump", eb.Message, want)
		}
	}
}

type failingStore struct{}

func (failingStore) PutRows(context.Context, string, []store.Row) ([]store.Result, error) {
	return nil, errors.New("dynamodb: throttled")
}

func (failingStore) Since(context.Context, string, int64, int32) ([]store.Change, int64, bool, error) {
	return nil, 0, false, errors.New("dynamodb: throttled")
}

func (failingStore) PutShare(context.Context, store.Share, int) error {
	return errors.New("dynamodb: throttled")
}

func (failingStore) GetShare(context.Context, string) (store.Share, error) {
	return store.Share{}, errors.New("dynamodb: throttled")
}

func (failingStore) PutDevice(context.Context, string, string, string, time.Time) error {
	return errors.New("dynamodb: throttled")
}

func (failingStore) DeleteDevice(context.Context, string, string) error {
	return errors.New("dynamodb: throttled")
}

func TestStoreFailureIsAServerError(t *testing.T) {
	e := newSyncEnv(t, failingStore{}, slog.New(slog.DiscardHandler))
	body, _ := json.Marshal(weighIns(1, 0, 1000))
	if rec, eb := e.pushRaw(e.alice, string(body)); rec.Code != http.StatusInternalServerError || eb.Code != "internal" {
		t.Fatalf("push: %d %q, want 500 internal (the app retries the batch)", rec.Code, eb.Code)
	}
	if rec, eb := serveWith(t, e.h, http.MethodGet, "/sync/pull?since=0", "", e.alice); rec.Code != http.StatusInternalServerError || eb.Code != "internal" {
		t.Fatalf("pull: %d %q, want 500 internal", rec.Code, eb.Code)
	}
	body, _ = json.Marshal(routineSnapshot())
	if rec, eb := serveWith(t, e.h, http.MethodPost, "/shares", string(body), e.alice); rec.Code != http.StatusInternalServerError || eb.Code != "internal" {
		t.Fatalf("create share: %d %q, want 500 internal", rec.Code, eb.Code)
	}
	if rec, eb := serveWith(t, e.h, http.MethodGet, "/shares/AAAAAAAAAAAAAAAAAAAAAA", "", ""); rec.Code != http.StatusInternalServerError || eb.Code != "internal" {
		t.Fatalf("get share: %d %q, want 500 internal", rec.Code, eb.Code)
	}
}

// A kind this binary does not know (a newer version wrote it) is skipped, and
// the cursor still moves past it.
func TestPullSkipsUnknownKinds(t *testing.T) {
	f := newFakeStore()
	e := newSyncEnv(t, f, slog.New(slog.DiscardHandler))
	e.push(e.alice, weighIns(1, 0, 1000))
	var userID string
	for id := range f.rows {
		userID = id
	}
	if _, err := f.PutRows(context.Background(), userID, []store.Row{{Kind: "FUTURETABLE", ID: "x", UpdatedAt: 1000}}); err != nil {
		t.Fatal(err)
	}
	got := e.pull(e.alice, 0, 0)
	if countRows(got.Changes) != 1 || got.Cursor != 2 {
		t.Fatalf("got %d rows, cursor %d; want the weigh-in only, cursor 2", countRows(got.Changes), got.Cursor)
	}
}

func TestEverySyncTableIsMapped(t *testing.T) {
	// A new table in the contract must get a kind here, or its rows would be
	// dropped silently: every SyncTable value and every ChangeSet list is used.
	kinds := map[string]bool{}
	names := map[api.SyncTable]bool{}
	for _, tb := range syncTables {
		if !tb.name.Valid() || kinds[tb.kind] || names[tb.name] {
			t.Fatalf("table %+v is invalid or listed twice", tb.name)
		}
		kinds[tb.kind], names[tb.name] = true, true
	}
	full := fullChangeSet(1)
	for _, tb := range syncTables {
		if len(tb.rows(&full)) == 0 {
			t.Fatalf("%s: no rows in the full change set", tb.name)
		}
	}
	b, _ := json.Marshal(full)
	var lists map[string]any
	_ = json.Unmarshal(b, &lists)
	if len(lists) != len(syncTables) {
		t.Fatalf("ChangeSet has %d lists, syncTables %d", len(lists), len(syncTables))
	}
}
