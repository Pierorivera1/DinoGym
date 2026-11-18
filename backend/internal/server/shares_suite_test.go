package server

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"reflect"
	"strings"
	"testing"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
)

// The shares suite: publish with a session, read without one, the rules
// between fields, the size and daily caps. shares_test.go runs it on the
// in-memory fake, shares_integration_test.go on the real store against the
// emulator.

func sharedExercise(name string) api.SharedExercise {
	return api.SharedExercise{Name: name, IconKey: "LEVER", Kind: api.SharedExerciseKindREPS, WeightEnabled: false}
}

func routineSnapshot() api.ShareSnapshot {
	return api.ShareSnapshot{
		Version: 1,
		Kind:    api.ShareSnapshotKindRoutine,
		Routines: []api.SharedRoutine{{
			Name: "PULL",
			Exercises: []api.SharedRoutineExercise{
				{Exercise: api.SharedExercise{Name: "Muscle-up", IconKey: "LEVER", Kind: api.SharedExerciseKindTEMPLATE, Template: ptr("3s + {n} pulls + 3s"), WeightEnabled: false}, Sets: 2, RepMin: 3, RepMax: 5, RestSeconds: 150},
				{Exercise: api.SharedExercise{Name: "Curl", IconKey: "CURL", Kind: api.SharedExerciseKindREPS, WeightEnabled: true}, Sets: 3, RepMin: 10, RepMax: 10, RestSeconds: 90},
			},
		}},
	}
}

func programSnapshot() api.ShareSnapshot {
	return api.ShareSnapshot{
		Version: 1,
		Kind:    api.ShareSnapshotKindProgram,
		Program: &api.SharedProgram{
			Name: "Upper/Lower",
			Type: api.SharedProgramTypeGYM,
			// Upper, Lower, rest, Upper, Lower, rest.
			Cycle: []api.SharedCycleStep{{Routine: ptr[int32](0)}, {Routine: ptr[int32](1)}, {}, {Routine: ptr[int32](0)}, {Routine: ptr[int32](1)}, {}},
		},
		Routines: []api.SharedRoutine{
			{Name: "Upper", Exercises: []api.SharedRoutineExercise{{Exercise: sharedExercise("Bench press"), Sets: 3, RepMin: 6, RepMax: 8, RestSeconds: 150}}},
			{Name: "Lower", Exercises: []api.SharedRoutineExercise{{Exercise: sharedExercise("Squat"), Sets: 3, RepMin: 5, RepMax: 8, RestSeconds: 180}}},
		},
	}
}

func (e *syncEnv) createShare(user string, body any) (*http.Response, api.ShareCreated, api.Error) {
	e.t.Helper()
	b, err := json.Marshal(body)
	if err != nil {
		e.t.Fatal(err)
	}
	rec, apiErr := serveWith(e.t, e.h, http.MethodPost, "/shares", string(b), user)
	var created api.ShareCreated
	if rec.Code == http.StatusCreated {
		if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
			e.t.Fatal(err)
		}
	}
	return rec.Result(), created, apiErr
}

func (e *syncEnv) mustShare(user string, sn api.ShareSnapshot) string {
	e.t.Helper()
	res, created, apiErr := e.createShare(user, sn)
	if res.StatusCode != http.StatusCreated {
		e.t.Fatalf("create share: %d %+v", res.StatusCode, apiErr)
	}
	return created.Id
}

// getShare reads a share with no Authorization header, as the web page does.
func (e *syncEnv) getShare(id string) (int, http.Header, map[string]json.RawMessage, api.Share) {
	e.t.Helper()
	rec, _ := serveWith(e.t, e.h, http.MethodGet, "/shares/"+id, "", "")
	var raw map[string]json.RawMessage
	var sh api.Share
	if rec.Code == http.StatusOK {
		if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
			e.t.Fatal(err)
		}
		if err := json.Unmarshal(rec.Body.Bytes(), &sh); err != nil {
			e.t.Fatal(err)
		}
	}
	return rec.Code, rec.Header(), raw, sh
}

func runSharesSuite(t *testing.T, newStore func(t *testing.T) Store) {
	t.Run("a routine round-trips and anyone can read it", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		sent := routineSnapshot()
		id := e.mustShare(e.alice, sent)
		if !shareIDPattern.MatchString(id) {
			t.Fatalf("id %q is not 22 URL-safe characters", id)
		}
		// newSyncEnv has no share base URL, so no link comes back.
		if _, created, _ := e.createShare(e.alice, sent); created.Url != nil {
			t.Fatalf("url %q without a share base URL", *created.Url)
		}
		code, h, raw, got := e.getShare(id)
		if code != http.StatusOK {
			t.Fatalf("get: %d", code)
		}
		if !reflect.DeepEqual(got.Snapshot, sent) {
			t.Fatalf("snapshot changed:\n got %+v\nwant %+v", got.Snapshot, sent)
		}
		if got.Id != id || got.CreatedAt == 0 {
			t.Fatalf("id %q createdAt %d", got.Id, got.CreatedAt)
		}
		if cc := h.Get("Cache-Control"); cc != "public, max-age=3600" {
			t.Fatalf("Cache-Control %q", cc)
		}
		// Nothing about the owner is public.
		for k := range raw {
			if k != "id" && k != "createdAt" && k != "snapshot" {
				t.Fatalf("unexpected field %q in a public share", k)
			}
		}
	})

	t.Run("a program keeps its cycle and rest days", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		sent := programSnapshot()
		_, _, _, got := e.getShare(e.mustShare(e.bob, sent))
		if !reflect.DeepEqual(got.Snapshot, sent) {
			t.Fatalf("snapshot changed:\n got %+v\nwant %+v", got.Snapshot, sent)
		}
	})

	t.Run("fields the contract does not know are not stored", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		b, _ := json.Marshal(routineSnapshot())
		body := `{"ownerEmail":"someone@example.com",` + string(b[1:])
		var withExtra map[string]any
		if err := json.Unmarshal([]byte(body), &withExtra); err != nil {
			t.Fatal(err)
		}
		res, created, apiErr := e.createShare(e.alice, withExtra)
		if res.StatusCode != http.StatusCreated {
			t.Fatalf("create: %d %+v", res.StatusCode, apiErr)
		}
		rec, _ := serveWith(t, e.h, http.MethodGet, "/shares/"+created.Id, "", "")
		if strings.Contains(rec.Body.String(), "ownerEmail") {
			t.Fatalf("unknown field came back: %s", rec.Body)
		}
	})

	t.Run("publishing needs a session", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		res, _, apiErr := e.createShare("", routineSnapshot())
		if res.StatusCode != http.StatusUnauthorized || apiErr.Code != "invalid_token" {
			t.Fatalf("%d %+v", res.StatusCode, apiErr)
		}
	})

	t.Run("unknown and malformed ids are 404", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		for _, id := range []string{"AAAAAAAAAAAAAAAAAAAAAA", "short", "AAAAAAAAAAAAAAAAAAAA.A"} {
			rec, apiErr := serveWith(t, e.h, http.MethodGet, "/shares/"+id, "", "")
			if rec.Code != http.StatusNotFound || apiErr.Code != "not_found" {
				t.Fatalf("%s: %d %+v", id, rec.Code, apiErr)
			}
		}
	})

	t.Run("snapshots that break the rules are refused", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		cases := map[string]func(*api.ShareSnapshot){
			"routine with a program":  func(s *api.ShareSnapshot) { s.Program = programSnapshot().Program },
			"routine with two":        func(s *api.ShareSnapshot) { s.Routines = append(s.Routines, s.Routines[0]) },
			"program without program": func(s *api.ShareSnapshot) { s.Kind = api.ShareSnapshotKindProgram },
			"repMin above repMax":     func(s *api.ShareSnapshot) { s.Routines[0].Exercises[0].RepMin = 9 },
			"zero sets (schema)":      func(s *api.ShareSnapshot) { s.Routines[0].Exercises[0].Sets = 0 },
			"newer version (schema)":  func(s *api.ShareSnapshot) { s.Version = 2 },
			"bad icon key (schema)":   func(s *api.ShareSnapshot) { s.Routines[0].Exercises[0].Exercise.IconKey = "../x" },
		}
		for name, edit := range cases {
			sn := routineSnapshot()
			edit(&sn)
			res, _, apiErr := e.createShare(e.alice, sn)
			if res.StatusCode != http.StatusBadRequest {
				t.Errorf("%s: %d %+v", name, res.StatusCode, apiErr)
			}
		}
		programCases := map[string]func(*api.ShareSnapshot){
			"cycle points past the routines": func(s *api.ShareSnapshot) { s.Program.Cycle[0].Routine = ptr[int32](2) },
			"routine the cycle never uses":   func(s *api.ShareSnapshot) { s.Program.Cycle = s.Program.Cycle[:1] },
		}
		for name, edit := range programCases {
			sn := programSnapshot()
			edit(&sn)
			res, _, apiErr := e.createShare(e.alice, sn)
			if res.StatusCode != http.StatusBadRequest {
				t.Errorf("%s: %d %+v", name, res.StatusCode, apiErr)
			}
		}
	})

	t.Run("a snapshot over 64 KiB is refused", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		ex := api.SharedExercise{Name: strings.Repeat("x", 120), IconKey: "LEVER", Kind: api.SharedExerciseKindTEMPLATE, Template: ptr(strings.Repeat("y", 200))}
		sn := api.ShareSnapshot{Version: 1, Kind: api.ShareSnapshotKindProgram, Program: &api.SharedProgram{Name: "Big", Type: api.SharedProgramTypeGYM}}
		for i := range 14 {
			r := api.SharedRoutine{Name: strings.Repeat("r", 120)}
			for range 30 {
				r.Exercises = append(r.Exercises, api.SharedRoutineExercise{Exercise: ex, Sets: 1, RepMin: 1, RepMax: 1})
			}
			sn.Routines = append(sn.Routines, r)
			sn.Program.Cycle = append(sn.Program.Cycle, api.SharedCycleStep{Routine: ptr(int32(i))})
		}
		res, _, apiErr := e.createShare(e.alice, sn)
		if res.StatusCode != http.StatusRequestEntityTooLarge || apiErr.Code != "share_too_large" {
			t.Fatalf("%d %+v", res.StatusCode, apiErr)
		}
	})

	t.Run("each user may publish 20 a day", func(t *testing.T) {
		e := newSyncEnv(t, newStore(t), nil)
		for range SharesPerDay {
			e.mustShare(e.alice, routineSnapshot())
		}
		res, _, apiErr := e.createShare(e.alice, routineSnapshot())
		if res.StatusCode != http.StatusTooManyRequests || apiErr.Code != "too_many_shares" {
			t.Fatalf("share %d: %d %+v", SharesPerDay+1, res.StatusCode, apiErr)
		}
		// The cap is per user.
		e.mustShare(e.bob, routineSnapshot())
	})

	t.Run("the link uses the share base URL", func(t *testing.T) {
		a := testAuth(t)
		h := New(slog.New(slog.DiscardHandler), newStore(t), a).WithShareBaseURL("https://d123.cloudfront.net").Handler()
		e := &syncEnv{t: t, h: h, alice: bearerFor(t, a, "good-id-token")}
		_, created, _ := e.createShare(e.alice, routineSnapshot())
		if created.Url == nil || *created.Url != "https://d123.cloudfront.net/r/"+created.Id {
			t.Fatalf("url %v for id %q", created.Url, created.Id)
		}
	})
}
