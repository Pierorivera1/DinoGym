package server

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"go.opentelemetry.io/otel"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth/authtest"
)

// testAuth is a session service on in-memory fakes: Google accepts only
// "good-id-token" (sub "google-sub-1") and "other-id-token" (sub
// "google-sub-2"). Each service gives its users new ids.
func testAuth(t *testing.T) *auth.Service {
	t.Helper()
	tokens, err := auth.NewTokens([]byte(strings.Repeat("k", auth.MinKeyBytes)), "local")
	if err != nil {
		t.Fatal(err)
	}
	google := authtest.Google{"good-id-token": "google-sub-1", "other-id-token": "google-sub-2"}
	return auth.NewService(tokens, google, authtest.NewStore())
}

// bearer returns an Authorization header value for the first test user.
func bearer(t *testing.T, a *auth.Service) string {
	t.Helper()
	return bearerFor(t, a, "good-id-token")
}

// bearerFor signs in with a Google ID token and returns an Authorization
// header value for that user.
func bearerFor(t *testing.T, a *auth.Service, idToken string) string {
	t.Helper()
	s, err := a.SignInWithGoogle(context.Background(), idToken)
	if err != nil {
		t.Fatal(err)
	}
	return "Bearer " + s.AccessToken
}

func serve(t *testing.T, method, path, body string) (*httptest.ResponseRecorder, api.Error) {
	t.Helper()
	a := testAuth(t)
	return serveWith(t, New(slog.New(slog.NewTextHandler(io.Discard, nil)), newFakeStore(), a).Handler(), method, path, body, bearer(t, a))
}

func serveWith(t *testing.T, h http.Handler, method, path, body, authorization string) (*httptest.ResponseRecorder, api.Error) {
	t.Helper()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if authorization != "" {
		req.Header.Set("Authorization", authorization)
	}
	h.ServeHTTP(rec, req)

	var e api.Error
	if rec.Code >= 400 {
		if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
			t.Fatalf("%s %s: error Content-Type %q", method, path, ct)
		}
		if err := json.Unmarshal(rec.Body.Bytes(), &e); err != nil || e.Code == "" {
			t.Fatalf("%s %s: error body %q is not the contract's Error", method, path, rec.Body)
		}
	}
	return rec, e
}

func TestHealth(t *testing.T) {
	rec, _ := serve(t, http.MethodGet, "/health", "")
	if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != `{"status":"ok"}` {
		t.Fatalf("got %d %s", rec.Code, rec.Body)
	}
}

func TestErrorsUseTheContractShape(t *testing.T) {
	big := `{"programs":[` + strings.Repeat(" ", MaxBodyBytes) + `]}`
	cases := []struct {
		name, method, path, body string
		status                   int
		code                     string
	}{
		{"missing since", http.MethodGet, "/sync/pull", "", http.StatusBadRequest, "bad_request"},
		{"bad json", http.MethodPost, "/sync/push", `{`, http.StatusBadRequest, "bad_request"},
		{"body over the cap", http.MethodPost, "/sync/push", big, http.StatusRequestEntityTooLarge, "body_too_large"},
		{"unknown path", http.MethodGet, "/nope", "", http.StatusNotFound, "not_found"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			rec, e := serve(t, c.method, c.path, c.body)
			if rec.Code != c.status || e.Code != c.code {
				t.Fatalf("got %d %q, want %d %q", rec.Code, e.Code, c.status, c.code)
			}
		})
	}
}

func TestEveryRequestIsTracedByRoute(t *testing.T) {
	spans := tracetest.NewSpanRecorder()
	prev := otel.GetTracerProvider()
	otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(spans)))
	t.Cleanup(func() { otel.SetTracerProvider(prev) })

	serve(t, http.MethodGet, "/sync/pull?since=7", "")

	ended := spans.Ended()
	if len(ended) != 1 {
		t.Fatalf("got %d spans, want 1", len(ended))
	}
	if name := ended[0].Name(); name != "GET /sync/pull" {
		t.Fatalf("span name %q, want the route, not the URL", name)
	}
}
