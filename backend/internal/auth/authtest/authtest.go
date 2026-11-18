// Package authtest has in-memory stand-ins for the auth package's Store and
// Google verifier, for unit tests that must not reach DynamoDB or Google. The
// real conditional writes are tested against the emulator in
// internal/store (build tag integration).
package authtest

import (
	"context"
	"sync"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
)

// Store keeps accounts, families and refresh tokens in maps, with the same
// rules as store.Store.
type Store struct {
	mu       sync.Mutex
	Accounts map[string]string // Google sub → user id
	Families map[string]*Family
	Tokens   map[string]*Token // hash → token
}

// Family is a session family.
type Family struct {
	ExpiresAt time.Time
	Revoked   bool
}

// Token is a stored refresh token.
type Token struct {
	auth.RefreshRecord
	UsedAt *time.Time
}

// NewStore returns an empty Store.
func NewStore() *Store {
	return &Store{Accounts: map[string]string{}, Families: map[string]*Family{}, Tokens: map[string]*Token{}}
}

var _ auth.Store = (*Store)(nil)

func (s *Store) GoogleUser(_ context.Context, sub, newUserID string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if id, ok := s.Accounts[sub]; ok {
		return id, nil
	}
	s.Accounts[sub] = newUserID
	return newUserID, nil
}

func (s *Store) PutFamily(_ context.Context, id string, expiresAt time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.Families[id] = &Family{ExpiresAt: expiresAt}
	return nil
}

func (s *Store) RenewFamily(_ context.Context, id string, expiresAt time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	f, ok := s.Families[id]
	if !ok || f.Revoked {
		return auth.ErrRevoked
	}
	f.ExpiresAt = expiresAt
	return nil
}

func (s *Store) RevokeFamily(_ context.Context, id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if f, ok := s.Families[id]; ok {
		f.Revoked = true
	} else {
		s.Families[id] = &Family{Revoked: true}
	}
	return nil
}

func (s *Store) PutRefreshToken(_ context.Context, r auth.RefreshRecord) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.Tokens[r.Hash] = &Token{RefreshRecord: r}
	return nil
}

func (s *Store) UseRefreshToken(_ context.Context, hash string, at time.Time) (auth.RefreshRecord, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.Tokens[hash]
	if !ok {
		return auth.RefreshRecord{}, auth.ErrNotFound
	}
	if t.UsedAt != nil {
		return t.RefreshRecord, auth.ErrAlreadyUsed
	}
	t.UsedAt = &at
	return t.RefreshRecord, nil
}

// Google accepts the ID tokens in its map, returning their sub, and refuses
// every other one.
type Google map[string]string

func (g Google) VerifyGoogle(_ context.Context, idToken string) (string, error) {
	if sub, ok := g[idToken]; ok {
		return sub, nil
	}
	return "", auth.ErrInvalidToken
}
