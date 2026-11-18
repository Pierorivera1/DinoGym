package server

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// The sync suite runs over HTTP on the full handler, with the bearer check and
// body validation. sync_test.go runs it on the in-memory fake, and
// sync_integration_test.go on the real store against the emulator.

// syncEnv is a handler on one store with two signed-in users.
type syncEnv struct {
	t          *testing.T
	h          http.Handler
	alice, bob string // Authorization header values
}

func newSyncEnv(t *testing.T, st Store, log *slog.Logger) *syncEnv {
	t.Helper()
	if log == nil {
		log = slog.New(slog.NewTextHandler(io.Discard, nil))
	}
	// A fresh auth service gives fresh user ids, so suites can share a table.
	a := testAuth(t)
	return &syncEnv{
		t:     t,
		h:     New(log, st, a).Handler(),
		alice: bearerFor(t, a, "good-id-token"),
		bob:   bearerFor(t, a, "other-id-token"),
	}
}

func (e *syncEnv) pushRaw(user, body string) (*httptest.ResponseRecorder, api.Error) {
	e.t.Helper()
	return serveWith(e.t, e.h, http.MethodPost, "/sync/push", body, user)
}

func (e *syncEnv) push(user string, cs api.ChangeSet) []api.PushResult {
	e.t.Helper()
	body, err := json.Marshal(cs)
	if err != nil {
		e.t.Fatal(err)
	}
	rec, _ := e.pushRaw(user, string(body))
	if rec.Code != http.StatusOK {
		e.t.Fatalf("push: %d %s", rec.Code, rec.Body)
	}
	var res api.PushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		e.t.Fatal(err)
	}
	return res.Results
}

func (e *syncEnv) pull(user string, since int64, limit int) api.PullResponse {
	e.t.Helper()
	path := fmt.Sprintf("/sync/pull?since=%d", since)
	if limit > 0 {
		path += fmt.Sprintf("&limit=%d", limit)
	}
	rec, _ := serveWith(e.t, e.h, http.MethodGet, path, "", user)
	if rec.Code != http.StatusOK {
		e.t.Fatalf("pull: %d %s", rec.Code, rec.Body)
	}
	var res api.PullResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		e.t.Fatal(err)
	}
	return res
}

// pullAll pages from since until hasMore is false, the way the app will.
func (e *syncEnv) pullAll(user string, since int64, limit int) (rows []any, cursor int64, pages int) {
	e.t.Helper()
	cursor = since
	for more := true; more; pages++ {
		if pages > 100 {
			e.t.Fatal("pull never ended")
		}
		res := e.pull(user, cursor, limit)
		if res.Cursor < cursor {
			e.t.Fatalf("cursor went back from %d to %d", cursor, res.Cursor)
		}
		rows = append(rows, flatten(res.Changes)...)
		cursor, more = res.Cursor, res.HasMore
	}
	return rows, cursor, pages
}

// flatten lists every row of a change set, in the order of syncTables.
func flatten(cs api.ChangeSet) []any {
	var out []any
	for _, t := range syncTables {
		out = append(out, t.rows(&cs)...)
	}
	return out
}

// uid returns a UUID-shaped id for n.
func uid(n int) string { return fmt.Sprintf("00000000-0000-4000-8000-%012d", n) }

func ptr[T any](v T) *T { return &v }

// fullChangeSet has rows in every table, with every optional field both set
// and null, deletions, an empty string, decimals and non-ASCII text.
func fullChangeSet(at int64) api.ChangeSet {
	return api.ChangeSet{
		Programs: &[]api.Program{
			{Id: uid(1), Name: "Push Pull Legs", Type: api.ProgramTypeGYM, CurrentStep: 2, IsActive: true, UpdatedAt: at},
			{Id: uid(2), Name: "", Type: api.ProgramTypeCALISTHENICS, CurrentStep: 0, IsActive: false, UpdatedAt: at + 1, DeletedAt: ptr(at + 1)},
		},
		CycleSteps: &[]api.CycleStep{
			{Id: uid(3), ProgramId: uid(1), Position: 0, RoutineId: ptr(uid(4)), UpdatedAt: at},
			{Id: uid(5), ProgramId: uid(1), Position: 1, RoutineId: nil, UpdatedAt: at},
		},
		Routines: &[]api.Routine{
			{Id: uid(4), Name: "Día de pierna", UpdatedAt: at},
		},
		Exercises: &[]api.Exercise{
			{Id: uid(6), Name: "Pull-up", IconKey: "pullup", Kind: api.ExerciseKindTEMPLATE, Template: ptr("3s + {n} pulls + 3s"), WeightEnabled: false, Archived: true, UpdatedAt: at},
			{Id: uid(7), Name: "Squat", IconKey: "squat", Kind: api.ExerciseKindREPS, WeightEnabled: true, Archived: false, UpdatedAt: at},
			{Id: uid(15), Name: "Plank", IconKey: "plank", Kind: api.ExerciseKindSHOWONLY, UpdatedAt: at},
		},
		RoutineExercises: &[]api.RoutineExercise{
			{Id: uid(8), RoutineId: uid(4), ExerciseId: uid(7), Position: 0, Sets: 3, RepMin: 6, RepMax: 10, RestSeconds: 90, UpdatedAt: at},
		},
		Sessions: &[]api.WorkoutSession{
			{Id: uid(9), RoutineId: uid(4), ProgramId: uid(1), StartedAt: at - 3_600_000, EndedAt: ptr(at), CurrentPosition: 1, UpdatedAt: at},
			{Id: uid(10), RoutineId: uid(4), ProgramId: uid(1), StartedAt: at, CurrentPosition: 0, UpdatedAt: at},
		},
		SetEntries: &[]api.SetEntry{
			{Id: uid(11), SessionId: uid(9), ExerciseId: uid(7), SetIndex: 1, Reps: 8, WeightKg: ptr(72.5), Note: ptr(`felt "easy" 💪`), UpdatedAt: at},
			{Id: uid(12), SessionId: uid(9), ExerciseId: uid(7), SetIndex: 2, Reps: 0, UpdatedAt: at},
			{Id: uid(13), SessionId: uid(9), ExerciseId: uid(7), SetIndex: 3, Reps: 5, WeightKg: ptr(102.25), Note: ptr(""), UpdatedAt: at, DeletedAt: ptr(at)},
		},
		WeighIns: &[]api.WeighIn{
			{Id: uid(14), MeasuredAt: at - 1000, WeightKg: 72.4, UpdatedAt: at},
			{Id: uid(16), MeasuredAt: at - 2000, WeightKg: 73, UpdatedAt: at},
		},
		Preferences: &[]api.Preference{
			{Key: "goal_weight_kg", Value: "70", UpdatedAt: at},
			{Key: "rest#timer", Value: "", UpdatedAt: at, DeletedAt: ptr(at)},
		},
	}
}

func countRows(cs api.ChangeSet) int { return len(flatten(cs)) }

func weighIns(n, from int, at int64) api.ChangeSet {
	rows := make([]api.WeighIn, n)
	for i := range rows {
		rows[i] = api.WeighIn{Id: uid(from + i), MeasuredAt: at, WeightKg: 70, UpdatedAt: at}
	}
	return api.ChangeSet{WeighIns: &rows}
}

func wantStatuses(t *testing.T, got []api.PushResult, want ...api.PushResultStatus) {
	t.Helper()
	if len(got) != len(want) {
		t.Fatalf("%d results, want %d: %+v", len(got), len(want), got)
	}
	for i := range want {
		if got[i].Status != want[i] {
			t.Fatalf("result %d is %+v, want %s", i, got[i], want[i])
		}
		if (got[i].Status == api.Accepted) != (got[i].ChangeNumber != nil) {
			t.Fatalf("result %d: changeNumber only and always when accepted: %+v", i, got[i])
		}
	}
}

func runSyncSuite(t *testing.T, newStore func(t *testing.T) Store) {
	// An hour ago: well inside the clock rule, and growing from there.
	at := time.Now().Add(-time.Hour).UnixMilli()

	t.Run("every table round-trips", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		sent := fullChangeSet(at)
		res := e.push(e.alice, sent)
		if len(res) != countRows(sent) {
			t.Fatalf("%d results for %d rows", len(res), countRows(sent))
		}
		for i, row := range flatten(sent) {
			if res[i].Status != api.Accepted || res[i].ChangeNumber == nil {
				t.Fatalf("row %d (%T) not accepted: %+v", i, row, res[i])
			}
		}
		// Results come back in table order with the row's table and id.
		if res[0].Table != api.SyncTableProgram || res[0].Id != uid(1) || res[len(res)-1].Table != api.SyncTablePreference || res[len(res)-1].Id != "rest#timer" {
			t.Fatalf("results out of order: first %+v, last %+v", res[0], res[len(res)-1])
		}

		got := e.pull(e.alice, 0, 0)
		if got.HasMore {
			t.Fatal("hasMore on a page under the default limit")
		}
		if !reflect.DeepEqual(got.Changes, sent) {
			a, _ := json.Marshal(sent)
			b, _ := json.Marshal(got.Changes)
			t.Fatalf("pulled rows differ from pushed\n sent %s\n got  %s", a, b)
		}
		if want := *res[len(res)-1].ChangeNumber; got.Cursor != want {
			t.Fatalf("cursor %d, want the last change number %d", got.Cursor, want)
		}
	})

	t.Run("newest edit wins and a deletion wins a tie", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		row := func(updatedAt int64, kg float64, deleted bool) api.ChangeSet {
			w := api.WeighIn{Id: uid(1), MeasuredAt: at, WeightKg: kg, UpdatedAt: updatedAt}
			if deleted {
				w.DeletedAt = ptr(updatedAt)
			}
			return api.ChangeSet{WeighIns: &[]api.WeighIn{w}}
		}
		steps := []struct {
			name string
			cs   api.ChangeSet
			want api.PushResultStatus
		}{
			{"new row", row(at, 70, false), api.Accepted},
			{"older edit", row(at-1, 99, false), api.Stale},
			{"newer edit", row(at+10, 71, false), api.Accepted},
			{"edit at the same time", row(at+10, 50, false), api.Stale},
			{"deletion at the same time", row(at+10, 71, true), api.Accepted},
			{"deletion again", row(at+10, 71, true), api.Stale},
			{"edit after the deletion", row(at+20, 72, false), api.Accepted},
		}
		for _, s := range steps {
			if got := e.push(e.alice, s.cs); got[0].Status != s.want {
				t.Fatalf("%s: %+v, want %s", s.name, got[0], s.want)
			}
		}
		got := e.pull(e.alice, 0, 0)
		if !reflect.DeepEqual(got.Changes, row(at+20, 72, false)) {
			t.Fatalf("stored %+v, want the edit after the deletion", *got.Changes.WeighIns)
		}
	})

	t.Run("pushing the same rows again changes nothing", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		sent := fullChangeSet(at)
		e.push(e.alice, sent)
		first := e.pull(e.alice, 0, 0)

		again := e.push(e.alice, sent)
		for i, r := range again {
			if r.Status != api.Stale {
				t.Fatalf("re-pushed row %d: %+v, want stale", i, r)
			}
		}
		if after := e.pull(e.alice, first.Cursor, 0); countRows(after.Changes) != 0 || after.Cursor != first.Cursor {
			t.Fatalf("a re-push produced %d new rows, cursor %d → %d", countRows(after.Changes), first.Cursor, after.Cursor)
		}
		if all := e.pull(e.alice, 0, 0); !reflect.DeepEqual(all.Changes, sent) {
			t.Fatal("a re-push changed the stored rows")
		}
	})

	t.Run("a future updatedAt is refused and not stored", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		future := time.Now().Add(store.MaxClockSkew + time.Minute).UnixMilli()
		near := time.Now().Add(store.MaxClockSkew - time.Minute).UnixMilli()
		res := e.push(e.alice, api.ChangeSet{WeighIns: &[]api.WeighIn{
			{Id: uid(1), MeasuredAt: at, WeightKg: 70, UpdatedAt: future},
			{Id: uid(2), MeasuredAt: at, WeightKg: 70, UpdatedAt: near},
		}})
		wantStatuses(t, res, api.Rejected, api.Accepted)
		if res[0].Reason == nil || *res[0].Reason != "updated_at_in_future" {
			t.Fatalf("reason %v, want updated_at_in_future", res[0].Reason)
		}
		got := e.pull(e.alice, 0, 0)
		if got.Changes.WeighIns == nil || len(*got.Changes.WeighIns) != 1 || (*got.Changes.WeighIns)[0].Id != uid(2) {
			t.Fatalf("stored %+v, want only the row within the skew", got.Changes)
		}
	})

	t.Run("users never see each other's rows", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		e.push(e.alice, fullChangeSet(at))
		if got := e.pull(e.bob, 0, 0); countRows(got.Changes) != 0 || got.HasMore || got.Cursor != 0 {
			t.Fatalf("bob pulled %d of alice's rows, cursor %d", countRows(got.Changes), got.Cursor)
		}
		// The same ids under bob are bob's own rows: older ones are accepted,
		// not stale against alice's, and alice's rows do not change.
		older := fullChangeSet(at - 1000)
		for _, r := range e.push(e.bob, older) {
			if r.Status != api.Accepted {
				t.Fatalf("bob's row %+v, want accepted", r)
			}
		}
		if got := e.pull(e.alice, 0, 0); !reflect.DeepEqual(got.Changes, fullChangeSet(at)) {
			t.Fatal("bob's push changed alice's rows")
		}
		if got := e.pull(e.bob, 0, 0); !reflect.DeepEqual(got.Changes, older) {
			t.Fatal("bob does not get his own rows back")
		}
	})

	t.Run("a first sign-in uploads a few thousand rows in batches and pages them back", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		const total = 1200 // three pushes: 500, 500, 200
		for from := 0; from < total; from += MaxPushRows {
			n := min(MaxPushRows, total-from)
			res := e.push(e.alice, weighIns(n, from, at))
			for _, r := range res {
				if r.Status != api.Accepted {
					t.Fatalf("batch at %d: %+v", from, r)
				}
			}
		}

		rows, cursor, pages := e.pullAll(e.alice, 0, MaxPullLimit)
		if len(rows) != total || pages != 2 {
			t.Fatalf("pulled %d rows in %d pages, want %d in 2", len(rows), pages, total)
		}
		seen := map[string]bool{}
		for _, r := range rows {
			seen[r.(api.WeighIn).Id] = true
		}
		if len(seen) != total {
			t.Fatalf("%d distinct rows, want %d", len(seen), total)
		}
		// The default limit pages too, and a pull from the end is empty.
		if rows, _, pages := e.pullAll(e.alice, 0, 0); len(rows) != total || pages != 3 {
			t.Fatalf("default limit: %d rows in %d pages, want %d in 3", len(rows), pages, total)
		}
		if end := e.pull(e.alice, cursor, 0); countRows(end.Changes) != 0 || end.HasMore || end.Cursor != cursor {
			t.Fatalf("after the end: %d rows, cursor %d, more %v", countRows(end.Changes), end.Cursor, end.HasMore)
		}
	})

	t.Run("requests outside the contract are refused", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		tooMany := weighIns(MaxPushRows, 0, at)
		*tooMany.WeighIns = append(*tooMany.WeighIns, api.WeighIn{Id: uid(9999), WeightKg: 1, UpdatedAt: at})
		tooManyBody, _ := json.Marshal(tooMany)

		w := func(fields string) string { return `{"weighIns":[{` + fields + `}]}` }
		const okID = `"id":"00000000-0000-4000-8000-000000000001"`
		pushes := []struct {
			name, body, code string
		}{
			{"501 rows", string(tooManyBody), "too_many_rows"},
			{"empty body", ``, "bad_request"},
			{"not an object", `[]`, "bad_request"},
			{"short id", w(`"id":"abc","measuredAt":1,"weightKg":70,"updatedAt":1`), "bad_request"},
			{"missing updatedAt", w(okID + `,"measuredAt":1,"weightKg":70`), "bad_request"},
			{"missing weightKg", w(okID + `,"measuredAt":1,"updatedAt":1`), "bad_request"},
			{"weightKg as text", w(okID + `,"measuredAt":1,"weightKg":"70","updatedAt":1`), "bad_request"},
			{"fractional updatedAt", w(okID + `,"measuredAt":1,"weightKg":70,"updatedAt":1.5`), "bad_request"},
			{"unknown program type", `{"programs":[{` + okID + `,"name":"x","type":"YOGA","currentStep":0,"isActive":true,"updatedAt":1}]}`, "bad_request"},
			{"set index 0", `{"setEntries":[{` + okID + `,"sessionId":"00000000-0000-4000-8000-000000000002","exerciseId":"00000000-0000-4000-8000-000000000003","setIndex":0,"reps":1,"updatedAt":1}]}`, "bad_request"},
			{"empty preference key", `{"preferences":[{"key":"","value":"x","updatedAt":1}]}`, "bad_request"},
		}
		for _, p := range pushes {
			t.Run(p.name, func(t *testing.T) {
				rec, eb := e.pushRaw(e.alice, p.body)
				if rec.Code != http.StatusBadRequest || eb.Code != p.code {
					t.Fatalf("got %d %q (%s), want 400 %q", rec.Code, eb.Code, eb.Message, p.code)
				}
			})
		}
		if got := e.pull(e.alice, 0, 0); countRows(got.Changes) != 0 {
			t.Fatalf("a refused push stored %d rows", countRows(got.Changes))
		}

		for _, q := range []string{"since=-1", "since=0&limit=0", "since=0&limit=1001", "since=x", "limit=5"} {
			t.Run("pull "+q, func(t *testing.T) {
				rec, eb := serveWith(t, e.h, http.MethodGet, "/sync/pull?"+q, "", e.alice)
				if rec.Code != http.StatusBadRequest || eb.Code != "bad_request" {
					t.Fatalf("got %d %q, want 400 bad_request", rec.Code, eb.Code)
				}
			})
		}
	})

	t.Run("an empty push answers no results", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		for _, body := range []string{`{}`, `{"weighIns":[]}`} {
			rec, _ := e.pushRaw(e.alice, body)
			if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != `{"results":[]}` {
				t.Fatalf("%s: got %d %s", body, rec.Code, rec.Body)
			}
		}
	})
}
