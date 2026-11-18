package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// Limits from the contract (decision 62).
const (
	MaxPushRows      = 500
	DefaultPullLimit = 500
	MaxPullLimit     = 1000
)

// SyncStore keeps the synced rows. *store.Store is the real one; tests use a
// fake with the same rules.
type SyncStore interface {
	PutRows(ctx context.Context, userID string, rows []store.Row) ([]store.Result, error)
	Since(ctx context.Context, userID string, after int64, limit int32) (changes []store.Change, cursor int64, more bool, err error)
}

// syncTable maps one of the contract's ChangeSet lists to the store's rows.
type syncTable struct {
	name api.SyncTable
	kind string // sort key prefix (decision 52)
	key  string // the JSON field that identifies a row: "id", or "key" for preferences
	// rows returns the table's pushed rows; add appends one pulled row, as JSON.
	rows func(*api.ChangeSet) []any
	add  func(*api.ChangeSet, []byte) error
}

func table[T any](name api.SyncTable, kind, key string, list func(*api.ChangeSet) **[]T) syncTable {
	return syncTable{
		name: name, kind: kind, key: key,
		rows: func(cs *api.ChangeSet) []any {
			p := *list(cs)
			if p == nil {
				return nil
			}
			out := make([]any, len(*p))
			for i := range *p {
				out[i] = (*p)[i]
			}
			return out
		},
		add: func(cs *api.ChangeSet, data []byte) error {
			var row T
			if err := json.Unmarshal(data, &row); err != nil {
				return err
			}
			p := list(cs)
			if *p == nil {
				*p = &[]T{}
			}
			**p = append(**p, row)
			return nil
		},
	}
}

// syncTables lists every table of the contract's ChangeSet, in the order a
// push is applied and its results are returned.
var syncTables = []syncTable{
	table(api.SyncTableProgram, "PROGRAM", "id", func(c *api.ChangeSet) **[]api.Program { return &c.Programs }),
	table(api.SyncTableCycleStep, "CYCLESTEP", "id", func(c *api.ChangeSet) **[]api.CycleStep { return &c.CycleSteps }),
	table(api.SyncTableRoutine, "ROUTINE", "id", func(c *api.ChangeSet) **[]api.Routine { return &c.Routines }),
	table(api.SyncTableExercise, "EXERCISE", "id", func(c *api.ChangeSet) **[]api.Exercise { return &c.Exercises }),
	table(api.SyncTableRoutineExercise, "ROUTINEEXERCISE", "id", func(c *api.ChangeSet) **[]api.RoutineExercise { return &c.RoutineExercises }),
	table(api.SyncTableSession, "SESSION", "id", func(c *api.ChangeSet) **[]api.WorkoutSession { return &c.Sessions }),
	table(api.SyncTableSetEntry, "SET", "id", func(c *api.ChangeSet) **[]api.SetEntry { return &c.SetEntries }),
	table(api.SyncTableWeighIn, "WEIGHIN", "id", func(c *api.ChangeSet) **[]api.WeighIn { return &c.WeighIns }),
	table(api.SyncTablePreference, "PREF", "key", func(c *api.ChangeSet) **[]api.Preference { return &c.Preferences }),
}

var tablesByKind = func() map[string]syncTable {
	m := make(map[string]syncTable, len(syncTables))
	for _, t := range syncTables {
		m[t.kind] = t
	}
	return m
}()

func userFrom(ctx context.Context) (string, error) {
	id, ok := auth.UserID(ctx)
	if !ok {
		// The bearer check puts it there; reaching this is a wiring bug.
		return "", errors.New("no user in the request context")
	}
	return id, nil
}

// PushChanges stores the pushed rows under the signed-in user. The body was
// already checked against the contract's ChangeSet schema (validate.go).
func (s *Server) PushChanges(ctx context.Context, req api.PushChangesRequestObject) (api.PushChangesResponseObject, error) {
	userID, err := userFrom(ctx)
	if err != nil {
		return nil, err
	}
	var cs api.ChangeSet
	if req.Body != nil {
		cs = *req.Body
	}

	total := 0
	for _, t := range syncTables {
		total += len(t.rows(&cs))
	}
	if total > MaxPushRows {
		return api.PushChanges400JSONResponse{BadRequestJSONResponse: api.BadRequestJSONResponse{
			Code:    "too_many_rows",
			Message: fmt.Sprintf("%d rows in one push; the most is %d", total, MaxPushRows),
		}}, nil
	}

	rows := make([]store.Row, 0, total)
	results := make([]api.PushResult, 0, total)
	for _, t := range syncTables {
		for _, v := range t.rows(&cs) {
			row, err := toRow(t, v)
			if err != nil {
				return nil, fmt.Errorf("%s row: %w", t.name, err)
			}
			rows = append(rows, row)
			results = append(results, api.PushResult{Table: t.name, Id: row.ID})
		}
	}
	if len(rows) == 0 {
		return api.PushChanges200JSONResponse{Results: results}, nil
	}

	stored, err := s.store.PutRows(ctx, userID, rows)
	if err != nil {
		return nil, fmt.Errorf("push: %w", err)
	}
	counts := map[store.Status]int{}
	for i, r := range stored {
		counts[r.Status]++
		results[i].Status = api.PushResultStatus(r.Status)
		switch r.Status {
		case store.Accepted:
			results[i].ChangeNumber = &r.ChangeNumber
		case store.Rejected:
			results[i].Reason = &r.Reason
		}
	}
	if touchesReminders(rows, stored) {
		s.reconcile(ctx, userID)
	}
	// Counts only: row contents never go to the logs.
	s.log.DebugContext(ctx, "sync push", "rows", len(rows),
		"accepted", counts[store.Accepted], "stale", counts[store.Stale], "rejected", counts[store.Rejected])
	return api.PushChanges200JSONResponse{Results: results}, nil
}

// PullChanges returns the signed-in user's rows after req.Params.Since.
func (s *Server) PullChanges(ctx context.Context, req api.PullChangesRequestObject) (api.PullChangesResponseObject, error) {
	userID, err := userFrom(ctx)
	if err != nil {
		return nil, err
	}
	limit := int32(DefaultPullLimit)
	if req.Params.Limit != nil {
		limit = *req.Params.Limit
	}
	if limit < 1 || limit > MaxPullLimit {
		return api.PullChanges400JSONResponse{BadRequestJSONResponse: badRequest(fmt.Sprintf("limit must be 1 to %d", MaxPullLimit))}, nil
	}
	if req.Params.Since < 0 {
		return api.PullChanges400JSONResponse{BadRequestJSONResponse: badRequest("since must be 0 or more")}, nil
	}

	changes, cursor, more, err := s.store.Since(ctx, userID, req.Params.Since, limit)
	if err != nil {
		return nil, fmt.Errorf("pull: %w", err)
	}
	var cs api.ChangeSet
	for _, c := range changes {
		t, ok := tablesByKind[c.Kind]
		if !ok {
			// A kind this binary does not know, from a newer version: skip it.
			s.log.WarnContext(ctx, "sync pull: unknown row kind", "kind", c.Kind)
			continue
		}
		data, err := fromChange(t, c)
		if err != nil {
			return nil, fmt.Errorf("%s row %d: %w", t.name, c.ChangeNumber, err)
		}
		if err := t.add(&cs, data); err != nil {
			return nil, fmt.Errorf("%s row %d: %w", t.name, c.ChangeNumber, err)
		}
	}
	s.log.DebugContext(ctx, "sync pull", "rows", len(changes), "since", req.Params.Since, "cursor", cursor, "hasMore", more)
	return api.PullChanges200JSONResponse{Changes: cs, Cursor: cursor, HasMore: more}, nil
}

// toRow turns a contract row into a store row: its key field becomes the ID,
// updatedAt and deletedAt go to their own fields, and every other JSON field
// becomes an attribute of the same name. Going through JSON keeps the mapping
// the same for every table, with the names Room and the contract use.
func toRow(t syncTable, v any) (store.Row, error) {
	b, err := json.Marshal(v)
	if err != nil {
		return store.Row{}, err
	}
	dec := json.NewDecoder(bytes.NewReader(b))
	dec.UseNumber() // keeps int64 exact and decimals as written
	var m map[string]any
	if err := dec.Decode(&m); err != nil {
		return store.Row{}, err
	}

	row := store.Row{Kind: t.kind, Fields: map[string]types.AttributeValue{}}
	row.ID, _ = m[t.key].(string)
	delete(m, t.key)
	if row.UpdatedAt, err = int64Field(m, "updatedAt"); err != nil {
		return store.Row{}, err
	}
	delete(m, "updatedAt")
	if _, ok := m["deletedAt"]; ok {
		d, err := int64Field(m, "deletedAt")
		if err != nil {
			return store.Row{}, err
		}
		row.DeletedAt = &d
		delete(m, "deletedAt")
	}
	for k, v := range m {
		switch v := v.(type) {
		case string:
			row.Fields[k] = &types.AttributeValueMemberS{Value: v}
		case bool:
			row.Fields[k] = &types.AttributeValueMemberBOOL{Value: v}
		case json.Number:
			row.Fields[k] = &types.AttributeValueMemberN{Value: v.String()}
		case nil:
			// A null optional field is not stored; it reads back as null.
		default:
			return store.Row{}, fmt.Errorf("field %q has an unsupported type %T", k, v)
		}
	}
	return row, nil
}

func int64Field(m map[string]any, name string) (int64, error) {
	n, ok := m[name].(json.Number)
	if !ok {
		return 0, fmt.Errorf("field %q is not a number", name)
	}
	return n.Int64()
}

// fromChange is toRow in reverse: the stored row as the contract's JSON.
func fromChange(t syncTable, c store.Change) ([]byte, error) {
	m := make(map[string]any, len(c.Fields)+3)
	for k, av := range c.Fields {
		switch v := av.(type) {
		case *types.AttributeValueMemberS:
			m[k] = v.Value
		case *types.AttributeValueMemberBOOL:
			m[k] = v.Value
		case *types.AttributeValueMemberN:
			m[k] = json.Number(v.Value)
		case *types.AttributeValueMemberNULL:
			m[k] = nil
		default:
			return nil, fmt.Errorf("attribute %q has an unsupported type %T", k, av)
		}
	}
	m[t.key] = c.ID
	m["updatedAt"] = c.UpdatedAt
	if c.DeletedAt != nil {
		m["deletedAt"] = *c.DeletedAt
	}
	return json.Marshal(m)
}
