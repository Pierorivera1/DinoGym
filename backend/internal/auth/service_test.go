package auth_test

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth/authtest"
)

type clock struct{ t time.Time }

func (c *clock) now() time.Time { return c.t }

func newService(t *testing.T) (*auth.Service, *authtest.Store, *clock) {
	t.Helper()
	tokens, err := auth.NewTokens([]byte(strings.Repeat("k", 32)), "local")
	if err != nil {
		t.Fatal(err)
	}
	st := authtest.NewStore()
	svc := auth.NewService(tokens, authtest.Google{"good-id-token": "google-sub-1"}, st)
	c := &clock{t: time.Unix(1_800_000_000, 0)}
	auth.SetNow(svc, c.now)
	return svc, st, c
}

func TestSignInCreatesTheUserOnce(t *testing.T) {
	svc, st, _ := newService(t)
	ctx := context.Background()

	first, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}
	second, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}
	if first.UserID == "" || first.UserID != second.UserID {
		t.Fatalf("user ids %q and %q: want the same, non-empty", first.UserID, second.UserID)
	}
	if first.UserID == "google-sub-1" {
		t.Fatal("the user id is the Google sub")
	}
	if len(st.Families) != 2 {
		t.Fatalf("%d families, want one per sign-in", len(st.Families))
	}
	if id, err := svc.VerifyAccess(first.AccessToken); err != nil || id != first.UserID {
		t.Fatalf("access token: %q, %v", id, err)
	}
	if _, ok := st.Tokens[first.RefreshToken]; ok {
		t.Fatal("the raw refresh token is stored")
	}
	if _, ok := st.Tokens[auth.Hash(first.RefreshToken)]; !ok {
		t.Fatal("the refresh token's hash is not stored")
	}
}

func TestSignInRefusesABadGoogleToken(t *testing.T) {
	svc, st, _ := newService(t)
	if _, err := svc.SignInWithGoogle(context.Background(), "forged"); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("got %v", err)
	}
	if len(st.Accounts) != 0 || len(st.Tokens) != 0 {
		t.Fatal("something was stored")
	}
}

func TestRefreshRotatesAndWorksOnce(t *testing.T) {
	svc, _, c := newService(t)
	ctx := context.Background()
	s1, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}

	c.t = c.t.Add(time.Hour)
	s2, err := svc.Refresh(ctx, s1.RefreshToken)
	if err != nil {
		t.Fatal(err)
	}
	if s2.UserID != s1.UserID || s2.RefreshToken == s1.RefreshToken || s2.AccessToken == s1.AccessToken {
		t.Fatalf("refresh gave %+v after %+v", s2, s1)
	}
	if !s2.AccessTokenExpiresAt.Equal(c.t.Add(auth.AccessTokenTTL)) {
		t.Fatalf("access token expires %v", s2.AccessTokenExpiresAt)
	}
	s3, err := svc.Refresh(ctx, s2.RefreshToken)
	if err != nil {
		t.Fatalf("second rotation: %v", err)
	}
	if s3.UserID != s1.UserID {
		t.Fatal("the user changed")
	}
}

func TestReusingARefreshTokenRevokesTheFamily(t *testing.T) {
	svc, st, _ := newService(t)
	ctx := context.Background()
	s1, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}
	s2, err := svc.Refresh(ctx, s1.RefreshToken)
	if err != nil {
		t.Fatal(err)
	}
	// Another sign-in (a second phone) is a separate family.
	other, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}

	if _, err := svc.Refresh(ctx, s1.RefreshToken); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("reuse: got %v", err)
	}
	if _, err := svc.Refresh(ctx, s2.RefreshToken); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("the newest token of a revoked family still works: %v", err)
	}
	revoked := 0
	for _, f := range st.Families {
		if f.Revoked {
			revoked++
		}
	}
	if revoked != 1 {
		t.Fatalf("%d families revoked, want 1", revoked)
	}
	if _, err := svc.Refresh(ctx, other.RefreshToken); err != nil {
		t.Fatalf("the other family was hit: %v", err)
	}
}

func TestRefreshRefusesExpiredAndUnknownTokens(t *testing.T) {
	svc, _, c := newService(t)
	ctx := context.Background()
	s1, err := svc.SignInWithGoogle(ctx, "good-id-token")
	if err != nil {
		t.Fatal(err)
	}

	if _, err := svc.Refresh(ctx, "never-issued"); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("unknown: got %v", err)
	}
	c.t = c.t.Add(auth.RefreshTokenTTL)
	if _, err := svc.Refresh(ctx, s1.RefreshToken); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("expired: got %v", err)
	}
}

func TestUserIDContext(t *testing.T) {
	if _, ok := auth.UserID(context.Background()); ok {
		t.Fatal("an empty context has a user")
	}
	if id, ok := auth.UserID(auth.WithUserID(context.Background(), "u1")); !ok || id != "u1" {
		t.Fatalf("got %q %v", id, ok)
	}
}
