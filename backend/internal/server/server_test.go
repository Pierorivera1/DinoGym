package server

import (
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
)

func serve(t *testing.T, method, path, body string) (*httptest.ResponseRecorder, api.Error) {
	t.Helper()
	h := New(slog.New(slog.NewTextHandler(io.Discard, nil))).Handler()
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
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
		{"not built yet", http.MethodGet, "/sync/pull?since=0", "", http.StatusNotImplemented, "not_implemented"},
		{"push not built yet", http.MethodPost, "/sync/push", `{}`, http.StatusNotImplemented, "not_implemented"},
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
