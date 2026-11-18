package auth

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

// RefreshRecord is a stored refresh token. Hash is the SHA-256 of the token;
// the token itself is never stored.
type RefreshRecord struct {
	Hash      string
	UserID    string
	FamilyID  string
	ExpiresAt time.Time
}

// Errors the Store returns.
var (
	// ErrNotFound: no such refresh token (never issued, or deleted by TTL).
	ErrNotFound = errors.New("not found")
	// ErrAlreadyUsed: the refresh token was used before.
	ErrAlreadyUsed = errors.New("refresh token already used")
	// ErrRevoked: the token's family was revoked, or no longer exists.
	ErrRevoked = errors.New("session family revoked")
)

// Store is the DynamoDB side of sessions (store.Store implements it).
type Store interface {
	// GoogleUser returns the user id for a Google sub, creating the account
	// with newUserID if there is none yet. It is safe under races: the loser
	// of two concurrent creations reads the winner's id.
	GoogleUser(ctx context.Context, sub, newUserID string) (string, error)
	// PutFamily creates a session family that lives until expiresAt.
	PutFamily(ctx context.Context, familyID string, expiresAt time.Time) error
	// RenewFamily extends a family to expiresAt, or returns ErrRevoked if the
	// family is revoked or gone.
	RenewFamily(ctx context.Context, familyID string, expiresAt time.Time) error
	// RevokeFamily marks a family revoked; its tokens stop working.
	RevokeFamily(ctx context.Context, familyID string) error
	// PutRefreshToken stores a new refresh token.
	PutRefreshToken(ctx context.Context, r RefreshRecord) error
	// UseRefreshToken marks the token with hash used at the given time, once.
	// It returns ErrNotFound for an unknown token, and the record with
	// ErrAlreadyUsed for one that was used before.
	UseRefreshToken(ctx context.Context, hash string, at time.Time) (RefreshRecord, error)
}

// Session is what sign-in and refresh return.
type Session struct {
	UserID               string
	AccessToken          string
	AccessTokenExpiresAt time.Time
	RefreshToken         string
}

// Service signs users in and rotates their sessions.
type Service struct {
	tokens *Tokens
	google GoogleVerifier
	store  Store
	now    func() time.Time
}

// NewService returns a Service.
func NewService(tokens *Tokens, google GoogleVerifier, store Store) *Service {
	return &Service{tokens: tokens, google: google, store: store, now: time.Now}
}

// VerifyAccess checks an access token and returns its user id.
func (s *Service) VerifyAccess(token string) (string, error) {
	return s.tokens.Verify(token)
}

// SignInWithGoogle verifies a Google ID token, finds or creates the user, and
// starts a new session family.
func (s *Service) SignInWithGoogle(ctx context.Context, idToken string) (Session, error) {
	sub, err := s.google.VerifyGoogle(ctx, idToken)
	if err != nil {
		return Session{}, err
	}
	userID, err := s.store.GoogleUser(ctx, sub, uuid.NewString())
	if err != nil {
		return Session{}, err
	}
	family := uuid.NewString()
	if err := s.store.PutFamily(ctx, family, s.now().Add(RefreshTokenTTL)); err != nil {
		return Session{}, err
	}
	return s.issue(ctx, userID, family)
}

// Refresh exchanges a refresh token for a new session in the same family. The
// token works once: presenting it again revokes the family, so a stolen token
// and the real one cannot both keep a session alive (decision 79).
func (s *Service) Refresh(ctx context.Context, refreshToken string) (Session, error) {
	now := s.now()
	rec, err := s.store.UseRefreshToken(ctx, hashRefreshToken(refreshToken), now)
	switch {
	case errors.Is(err, ErrNotFound):
		return Session{}, ErrInvalidToken
	case errors.Is(err, ErrAlreadyUsed):
		if err := s.store.RevokeFamily(ctx, rec.FamilyID); err != nil {
			return Session{}, err
		}
		return Session{}, ErrInvalidToken
	case err != nil:
		return Session{}, err
	}
	// The TTL deletes expired items only within a day or two, so expiry is
	// checked here too.
	if !now.Before(rec.ExpiresAt) {
		return Session{}, ErrInvalidToken
	}
	// One call checks the family is not revoked and extends it to the new
	// token's lifetime.
	if err := s.store.RenewFamily(ctx, rec.FamilyID, now.Add(RefreshTokenTTL)); err != nil {
		if errors.Is(err, ErrRevoked) {
			return Session{}, ErrInvalidToken
		}
		return Session{}, err
	}
	return s.issue(ctx, rec.UserID, rec.FamilyID)
}

func (s *Service) issue(ctx context.Context, userID, family string) (Session, error) {
	raw, hash, err := newRefreshToken()
	if err != nil {
		return Session{}, err
	}
	if err := s.store.PutRefreshToken(ctx, RefreshRecord{
		Hash:      hash,
		UserID:    userID,
		FamilyID:  family,
		ExpiresAt: s.now().Add(RefreshTokenTTL),
	}); err != nil {
		return Session{}, err
	}
	access, exp, err := s.tokens.Issue(userID)
	if err != nil {
		return Session{}, fmt.Errorf("issue session: %w", err)
	}
	return Session{UserID: userID, AccessToken: access, AccessTokenExpiresAt: exp, RefreshToken: raw}, nil
}

type userKey struct{}

// WithUserID returns ctx carrying the signed-in user's id.
func WithUserID(ctx context.Context, userID string) context.Context {
	return context.WithValue(ctx, userKey{}, userID)
}

// UserID returns the signed-in user's id, set by the API's bearer check.
func UserID(ctx context.Context) (string, bool) {
	id, ok := ctx.Value(userKey{}).(string)
	return id, ok && id != ""
}
