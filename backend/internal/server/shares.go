package server

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// Limits for shares (decision 97). The contract's schema already caps every
// list and string; these cover what a schema cannot say.
const (
	// MaxShareBytes caps a snapshot's JSON, far below DynamoDB's 400 KB item.
	// A five-routine program of eight exercises each is about 8 KB.
	MaxShareBytes = 64 << 10
	// SharesPerDay is how many shares one user may publish per UTC day.
	SharesPerDay = 20
	// shareCacheControl lets CloudFront and browsers keep a share an hour. A
	// share never changes, so the only cost is a deleted one living on that
	// long, and there is no delete yet.
	shareCacheControl = "public, max-age=3600"
)

// ShareStore keeps published shares. *store.Store is the real one.
type ShareStore interface {
	PutShare(ctx context.Context, sh store.Share, perDay int) error
	GetShare(ctx context.Context, id string) (store.Share, error)
}

var _ ShareStore = (*store.Store)(nil)

var shareIDPattern = regexp.MustCompile(`^[A-Za-z0-9_-]{22}$`)

// newShareID returns 128 random bits as 22 URL-safe characters: unguessable,
// so a link is the only way to find a share.
func newShareID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

// CreateShare publishes the signed-in user's snapshot. The body was checked
// against the contract's ShareSnapshot schema (validate.go); checkSnapshot
// adds the rules between fields.
func (s *Server) CreateShare(ctx context.Context, req api.CreateShareRequestObject) (api.CreateShareResponseObject, error) {
	userID, err := userFrom(ctx)
	if err != nil {
		return nil, err
	}
	if req.Body == nil {
		return api.CreateShare400JSONResponse{BadRequestJSONResponse: badRequest("a snapshot is required")}, nil
	}
	if msg := checkSnapshot(req.Body); msg != "" {
		return api.CreateShare400JSONResponse{BadRequestJSONResponse: badRequest(msg)}, nil
	}
	// Re-encoded from the typed body, so fields the contract does not know
	// are never stored.
	snap, err := json.Marshal(req.Body)
	if err != nil {
		return nil, fmt.Errorf("encode snapshot: %w", err)
	}
	if len(snap) > MaxShareBytes {
		return api.CreateShare413JSONResponse{Code: "share_too_large", Message: fmt.Sprintf("the snapshot is %d bytes; the most is %d", len(snap), MaxShareBytes)}, nil
	}
	id, err := newShareID()
	if err != nil {
		return nil, fmt.Errorf("share id: %w", err)
	}
	err = s.store.PutShare(ctx, store.Share{ID: id, OwnerID: userID, CreatedAt: s.now().UnixMilli(), Snapshot: snap}, SharesPerDay)
	if errors.Is(err, store.ErrShareLimit) {
		return api.CreateShare429JSONResponse{Code: "too_many_shares", Message: fmt.Sprintf("at most %d shares a day", SharesPerDay)}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("create share: %w", err)
	}
	// No ids or names in the logs: the kind and size are enough.
	s.log.DebugContext(ctx, "share created", "kind", req.Body.Kind, "routines", len(req.Body.Routines), "bytes", len(snap))
	return api.CreateShare201JSONResponse{Id: id}, nil
}

// checkSnapshot returns what is wrong with a snapshot beyond its schema, or
// "" when it is fine.
func checkSnapshot(sn *api.ShareSnapshot) string {
	switch sn.Kind {
	case api.ShareSnapshotKindRoutine:
		if sn.Program != nil {
			return "a routine share has no program"
		}
		if len(sn.Routines) != 1 {
			return "a routine share has exactly one routine"
		}
	case api.ShareSnapshotKindProgram:
		if sn.Program == nil {
			return "a program share needs its program"
		}
		used := make([]bool, len(sn.Routines))
		for i, step := range sn.Program.Cycle {
			if step.Routine == nil {
				continue
			}
			if int(*step.Routine) >= len(sn.Routines) {
				return fmt.Sprintf("/program/cycle/%d/routine: there is no routine %d", i, *step.Routine)
			}
			used[*step.Routine] = true
		}
		for i, u := range used {
			if !u {
				return fmt.Sprintf("/routines/%d: not used by the cycle", i)
			}
		}
	default:
		return "kind must be routine or program"
	}
	for i, r := range sn.Routines {
		for j, e := range r.Exercises {
			if e.RepMin > e.RepMax {
				return fmt.Sprintf("/routines/%d/exercises/%d: repMin is above repMax", i, j)
			}
		}
	}
	return ""
}

// GetShare returns a share to anyone with its id. An id that cannot exist
// gets the same 404 as one that does not, without a store read.
func (s *Server) GetShare(ctx context.Context, req api.GetShareRequestObject) (api.GetShareResponseObject, error) {
	notFound := api.GetShare404JSONResponse{Code: "not_found", Message: "no such share"}
	if !shareIDPattern.MatchString(req.ShareId) {
		return notFound, nil
	}
	sh, err := s.store.GetShare(ctx, req.ShareId)
	if errors.Is(err, store.ErrShareNotFound) {
		return notFound, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get share: %w", err)
	}
	var snap api.ShareSnapshot
	if err := json.Unmarshal(sh.Snapshot, &snap); err != nil {
		return nil, fmt.Errorf("decode share snapshot: %w", err)
	}
	cc := shareCacheControl
	return api.GetShare200JSONResponse{
		Body:    api.Share{Id: sh.ID, CreatedAt: sh.CreatedAt, Snapshot: snap},
		Headers: api.GetShare200ResponseHeaders{CacheControl: &cc},
	}, nil
}
