package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"maps"
	"net/http"
	"net/http/httptest"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth/authtest"
)

func decodeSession(t *testing.T, rec *httptest.ResponseRecorder) api.Session {
	t.Helper()
	if rec.Code != http.StatusOK {
		t.Fatalf("got %d %s, want 200", rec.Code, rec.Body)
	}
	var s api.Session
	if err := json.Unmarshal(rec.Body.Bytes(), &s); err != nil {
		t.Fatal(err)
	}
	if s.UserId == "" || s.AccessToken == "" || s.RefreshToken == "" || s.AccessTokenExpiresAt == 0 {
		t.Fatalf("incomplete session %+v", s)
	}
	return s
}

func TestSignInAndRefresh(t *testing.T) {
	var logs bytes.Buffer
	a := testAuth(t)
	h := New(slog.New(slog.NewTextHandler(&logs, &slog.HandlerOptions{Level: slog.LevelDebug})), newFakeStore(), a).Handler()
	post := func(path, body string) (*httptest.ResponseRecorder, api.Error) {
		return serveWith(t, h, http.MethodPost, path, body, "")
	}

	rec, _ := post("/auth/google", `{"idToken":"good-id-token"}`)
	s1 := decodeSession(t, rec)
	if until := time.Until(time.UnixMilli(s1.AccessTokenExpiresAt)); until < 14*time.Minute || until > 15*time.Minute {
		t.Fatalf("access token expires in %v, want 15 minutes", until)
	}

	rec, _ = post("/auth/refresh", fmt.Sprintf(`{"refreshToken":%q}`, s1.RefreshToken))
	s2 := decodeSession(t, rec)
	if s2.UserId != s1.UserId || s2.RefreshToken == s1.RefreshToken {
		t.Fatalf("refresh gave %+v after %+v", s2, s1)
	}

	// The new access token opens the protected routes.
	rec, _ = serveWith(t, h, http.MethodGet, "/sync/pull?since=0", "", "Bearer "+s2.AccessToken)
	if rec.Code != http.StatusOK {
		t.Fatalf("pull with a fresh token: %d", rec.Code)
	}

	// Reusing the first refresh token revokes the family, including s2's token.
	if rec, e := post("/auth/refresh", fmt.Sprintf(`{"refreshToken":%q}`, s1.RefreshToken)); rec.Code != http.StatusUnauthorized || e.Code != "invalid_token" {
		t.Fatalf("reuse: %d %q", rec.Code, e.Code)
	}
	if rec, e := post("/auth/refresh", fmt.Sprintf(`{"refreshToken":%q}`, s2.RefreshToken)); rec.Code != http.StatusUnauthorized || e.Code != "invalid_token" {
		t.Fatalf("after reuse: %d %q", rec.Code, e.Code)
	}

	for _, secret := range []string{s1.AccessToken, s1.RefreshToken, s2.AccessToken, s2.RefreshToken, "good-id-token"} {
		if strings.Contains(logs.String(), secret) {
			t.Fatal("a token was logged")
		}
	}
}

func TestAuthErrors(t *testing.T) {
	cases := []struct {
		name, path, body string
		status           int
		code             string
	}{
		{"forged Google token", "/auth/google", `{"idToken":"forged"}`, http.StatusUnauthorized, "invalid_token"},
		{"no idToken", "/auth/google", `{}`, http.StatusBadRequest, "bad_request"},
		{"empty idToken", "/auth/google", `{"idToken":""}`, http.StatusBadRequest, "bad_request"},
		{"bad json", "/auth/google", `{`, http.StatusBadRequest, "bad_request"},
		{"unknown refresh token", "/auth/refresh", `{"refreshToken":"nope"}`, http.StatusUnauthorized, "invalid_token"},
		{"no refreshToken", "/auth/refresh", `{}`, http.StatusBadRequest, "bad_request"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			rec, e := serve(t, http.MethodPost, c.path, c.body)
			if rec.Code != c.status || e.Code != c.code {
				t.Fatalf("got %d %q, want %d %q", rec.Code, e.Code, c.status, c.code)
			}
		})
	}
}

type unreachableGoogle struct{}

func (unreachableGoogle) VerifyGoogle(context.Context, string) (string, error) {
	return "", errors.New("google discovery: connection refused")
}

func TestGoogleOutageIsAServerError(t *testing.T) {
	tokens, err := auth.NewTokens([]byte(strings.Repeat("k", auth.MinKeyBytes)), "local")
	if err != nil {
		t.Fatal(err)
	}
	a := auth.NewService(tokens, unreachableGoogle{}, authtest.NewStore())
	h := New(slog.New(slog.DiscardHandler), newFakeStore(), a).Handler()
	rec, e := serveWith(t, h, http.MethodPost, "/auth/google", `{"idToken":"x"}`, "")
	if rec.Code != http.StatusInternalServerError || e.Code != "internal" {
		t.Fatalf("got %d %q, want 500 internal (the app retries)", rec.Code, e.Code)
	}
}

func TestProtectedRoutesComeFromTheContract(t *testing.T) {
	got := slices.Sorted(maps.Keys(protectedRoutes()))
	// GET /shares/{shareId} is public on purpose: the web page reads it.
	want := []string{"DELETE /devices/{installationId}", "GET /sync/pull", "POST /shares", "POST /sync/push", "PUT /devices/{installationId}"}
	if !slices.Equal(got, want) {
		t.Fatalf("protected routes %v, want %v", got, want)
	}
}

func TestBearerCheck(t *testing.T) {
	a := testAuth(t)
	h := New(slog.New(slog.DiscardHandler), newFakeStore(), a).Handler()
	good := bearer(t, a)

	other, err := auth.NewTokens([]byte(strings.Repeat("x", auth.MinKeyBytes)), "local")
	if err != nil {
		t.Fatal(err)
	}
	forged, _, err := other.Issue("someone")
	if err != nil {
		t.Fatal(err)
	}

	routes := []struct{ method, path, body string }{
		{http.MethodGet, "/sync/pull?since=0", ""},
		{http.MethodPost, "/sync/push", `{}`},
		// The check runs before parsing: no 400 leaks to a caller without a token.
		{http.MethodGet, "/sync/pull", ""},
		{http.MethodPost, "/sync/push", `{`},
	}
	for _, r := range routes {
		for name, header := range map[string]string{
			"missing":      "",
			"not bearer":   "Basic " + strings.TrimPrefix(good, "Bearer "),
			"empty bearer": "Bearer ",
			"garbage":      "Bearer abc.def.ghi",
			"other key":    "Bearer " + forged,
		} {
			t.Run(r.method+" "+r.path+" "+name, func(t *testing.T) {
				rec, e := serveWith(t, h, r.method, r.path, r.body, header)
				if rec.Code != http.StatusUnauthorized || e.Code != "invalid_token" {
					t.Fatalf("got %d %q, want 401 invalid_token", rec.Code, e.Code)
				}
			})
		}
	}

	// Public routes need no token.
	for _, p := range []string{"/health"} {
		if rec, _ := serveWith(t, h, http.MethodGet, p, "", ""); rec.Code != http.StatusOK {
			t.Fatalf("%s without a token: %d", p, rec.Code)
		}
	}
	// A lower-case scheme is accepted (RFC 9110: the scheme is case-insensitive).
	if rec, _ := serveWith(t, h, http.MethodGet, "/sync/pull?since=0", "", "bearer "+strings.TrimPrefix(good, "Bearer ")); rec.Code != http.StatusOK {
		t.Fatalf("lower-case scheme: %d", rec.Code)
	}
}

func TestBearerCheckPutsTheUserInTheContext(t *testing.T) {
	a := testAuth(t)
	s, err := a.SignInWithGoogle(context.Background(), "good-id-token")
	if err != nil {
		t.Fatal(err)
	}
	router := &bearerRouter{ServeMux: http.NewServeMux(), protected: map[string]bool{"GET /me": true}, verify: a.VerifyAccess}
	router.HandleFunc("GET /me", func(w http.ResponseWriter, r *http.Request) {
		id, _ := auth.UserID(r.Context())
		_, _ = w.Write([]byte(id))
	})
	router.mustCoverAll()

	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/me", nil)
	req.Header.Set("Authorization", "Bearer "+s.AccessToken)
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK || rec.Body.String() != s.UserID {
		t.Fatalf("got %d %q, want the user id %q", rec.Code, rec.Body, s.UserID)
	}
}

func TestBearerCheckRefusesToLeaveARouteOpen(t *testing.T) {
	router := &bearerRouter{ServeMux: http.NewServeMux(), protected: map[string]bool{"GET /secret": true}}
	defer func() {
		if recover() == nil {
			t.Fatal("a secured route that was never registered did not panic")
		}
	}()
	router.mustCoverAll()
}
