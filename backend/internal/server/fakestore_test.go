package server

import (
	"context"
	"slices"
	"sync"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// fakeStore is an in-memory SyncStore with decision 51's rules, the same as
// the conditional write in store.PutRows. The same suite runs on the real
// store against the emulator (sync_integration_test.go), which keeps the two
// honest. It has no settle window: its writes are visible at once.
type fakeStore struct {
	mu   sync.Mutex
	seq  map[string]int64
	rows map[string]map[string]store.Change // user → KIND#id → row
	now  func() time.Time
}

func newFakeStore() *fakeStore {
	return &fakeStore{seq: map[string]int64{}, rows: map[string]map[string]store.Change{}, now: time.Now}
}

func (f *fakeStore) PutRows(_ context.Context, userID string, rows []store.Row) ([]store.Result, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.rows[userID] == nil {
		f.rows[userID] = map[string]store.Change{}
	}
	limit := f.now().Add(store.MaxClockSkew).UnixMilli()
	out := make([]store.Result, len(rows))
	for i, r := range rows {
		if r.UpdatedAt > limit {
			out[i] = store.Result{Status: store.Rejected, Reason: store.ReasonUpdatedAtInFuture}
			continue
		}
		f.seq[userID]++
		key := r.Kind + "#" + r.ID
		old, exists := f.rows[userID][key]
		wins := !exists || old.UpdatedAt < r.UpdatedAt ||
			(old.UpdatedAt == r.UpdatedAt && old.DeletedAt == nil && r.DeletedAt != nil)
		if !wins {
			out[i] = store.Result{Status: store.Stale}
			continue
		}
		f.rows[userID][key] = store.Change{Row: r, ChangeNumber: f.seq[userID], AcceptedAt: f.now().UnixMilli()}
		out[i] = store.Result{Status: store.Accepted, ChangeNumber: f.seq[userID]}
	}
	return out, nil
}

func (f *fakeStore) Since(_ context.Context, userID string, after int64, limit int32) ([]store.Change, int64, bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	var all []store.Change
	for _, c := range f.rows[userID] {
		if c.ChangeNumber > after {
			all = append(all, c)
		}
	}
	slices.SortFunc(all, func(a, b store.Change) int { return int(a.ChangeNumber - b.ChangeNumber) })
	more := len(all) > int(limit)
	if more {
		all = all[:limit]
	}
	cursor := after
	if len(all) > 0 {
		cursor = all[len(all)-1].ChangeNumber
	}
	return all, cursor, more, nil
}
