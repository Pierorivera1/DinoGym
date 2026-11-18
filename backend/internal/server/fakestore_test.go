package server

import (
	"context"
	"errors"
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
	// shares by id, and how many each owner published per UTC day.
	shares      map[string]store.Share
	shareCounts map[string]int
	// devices by user, then installation id → token.
	devices map[string]map[string]string
	now     func() time.Time
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

// PutShare and GetShare keep shares like store.PutShare and store.GetShare:
// a per-owner count for each UTC day, and no overwrites.
func (f *fakeStore) PutShare(_ context.Context, sh store.Share, perDay int) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.shares == nil {
		f.shares, f.shareCounts = map[string]store.Share{}, map[string]int{}
	}
	day := sh.OwnerID + "#" + time.UnixMilli(sh.CreatedAt).UTC().Format(time.DateOnly)
	if f.shareCounts[day] >= perDay {
		return store.ErrShareLimit
	}
	f.shareCounts[day]++
	if _, ok := f.shares[sh.ID]; ok {
		return errors.New("share exists")
	}
	f.shares[sh.ID] = sh
	return nil
}

func (f *fakeStore) GetShare(_ context.Context, id string) (store.Share, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	sh, ok := f.shares[id]
	if !ok {
		return store.Share{}, store.ErrShareNotFound
	}
	return sh, nil
}

func (f *fakeStore) PutDevice(_ context.Context, userID, installationID, token string, _ time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.devices == nil {
		f.devices = map[string]map[string]string{}
	}
	if f.devices[userID] == nil {
		f.devices[userID] = map[string]string{}
	}
	f.devices[userID][installationID] = token
	return nil
}

func (f *fakeStore) DeleteDevice(_ context.Context, userID, installationID string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	delete(f.devices[userID], installationID)
	return nil
}
