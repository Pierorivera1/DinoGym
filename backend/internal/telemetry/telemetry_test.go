package telemetry

import (
	"bytes"
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"go.opentelemetry.io/otel"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	semconv "go.opentelemetry.io/otel/semconv/v1.43.0"
)

// Flush must deliver traces, metrics and logs to the OTLP endpoint right away:
// on Lambda nothing runs in the background between invocations.
func TestOTLPFlushSendsAllThreeSignals(t *testing.T) {
	var mu sync.Mutex
	got := map[string]bool{}
	auth := map[string]string{}
	collector := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		got[r.URL.Path] = true
		auth[r.URL.Path] = r.Header.Get("Authorization")
		mu.Unlock()
		w.WriteHeader(http.StatusOK)
	}))
	defer collector.Close()
	t.Setenv("OTEL_EXPORTER_OTLP_ENDPOINT", collector.URL)

	ctx := context.Background()
	var stdout bytes.Buffer
	tel, log, err := Setup(ctx, Options{Mode: "otlp", Env: "local", Version: "test", Level: slog.LevelInfo, Stdout: &stdout, Headers: BasicAuth("123456", "tok")})
	if err != nil {
		t.Fatal(err)
	}
	defer tel.Shutdown(ctx)

	ctx, span := otel.Tracer("test").Start(ctx, "work")
	counter, _ := otel.Meter("test").Int64Counter("things")
	counter.Add(ctx, 1)
	log.InfoContext(ctx, "hello")
	log.DebugContext(ctx, "below the level")
	span.End()

	if err := tel.Flush(ctx); err != nil {
		t.Fatal(err)
	}
	mu.Lock()
	defer mu.Unlock()
	// base64("123456:tok"), the Basic credentials Grafana Cloud expects.
	const wantAuth = "Basic MTIzNDU2OnRvaw=="
	for _, path := range []string{"/v1/traces", "/v1/metrics", "/v1/logs"} {
		if !got[path] {
			t.Errorf("nothing sent to %s; got %v", path, got)
		}
		if auth[path] != wantAuth {
			t.Errorf("%s Authorization = %q, want %q", path, auth[path], wantAuth)
		}
	}
	if !strings.Contains(stdout.String(), `"msg":"hello"`) || strings.Contains(stdout.String(), "below the level") {
		t.Errorf("stdout JSON log: %s", stdout.String())
	}
}

func TestConsolePrintsSpans(t *testing.T) {
	var stdout bytes.Buffer
	tel, _, err := Setup(context.Background(), Options{Mode: "console", Env: "local", Version: "test", Level: slog.LevelInfo, Stdout: &stdout})
	if err != nil {
		t.Fatal(err)
	}
	defer tel.Shutdown(context.Background())

	_, span := otel.Tracer("test").Start(context.Background(), "visible-span")
	span.End()
	if !strings.Contains(stdout.String(), "visible-span") {
		t.Fatalf("span not printed: %s", stdout.String())
	}
}

func TestUnknownModeFails(t *testing.T) {
	if _, _, err := Setup(context.Background(), Options{Mode: "zipkin", Stdout: &bytes.Buffer{}}); err == nil {
		t.Fatal("want an error")
	}
}

// Without Options.Headers, the exporters keep OTEL_EXPORTER_OTLP_HEADERS.
func TestEnvironmentHeadersKeptWithoutOptionHeaders(t *testing.T) {
	var mu sync.Mutex
	var auth string
	collector := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		auth = r.Header.Get("Authorization")
		mu.Unlock()
		w.WriteHeader(http.StatusOK)
	}))
	defer collector.Close()
	t.Setenv("OTEL_EXPORTER_OTLP_ENDPOINT", collector.URL)
	t.Setenv("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Bearer from-env")

	ctx := context.Background()
	tel, _, err := Setup(ctx, Options{Mode: "otlp", Stdout: &bytes.Buffer{}})
	if err != nil {
		t.Fatal(err)
	}
	defer tel.Shutdown(ctx)
	_, span := otel.Tracer("test").Start(ctx, "work")
	span.End()
	if err := tel.Flush(ctx); err != nil {
		t.Fatal(err)
	}
	mu.Lock()
	defer mu.Unlock()
	if auth != "Bearer from-env" {
		t.Fatalf("Authorization = %q", auth)
	}
}

// Every process names itself, so concurrent Lambda instances write separate
// metric series (decision 118), and the body size histograms are dropped.
func TestInstanceIDAndDroppedHistograms(t *testing.T) {
	for _, name := range []string{"http.server.request.body.size", "http.server.response.body.size"} {
		if _, ok := dropBodySizes(sdkmetric.Instrument{Name: name}); !ok {
			t.Errorf("%s is kept", name)
		}
	}
	if _, ok := dropBodySizes(sdkmetric.Instrument{Name: "http.server.request.duration"}); ok {
		t.Error("request duration is dropped")
	}

	id := func() string {
		res, err := newResource(ServiceName, "staging", "test")
		if err != nil {
			t.Fatal(err)
		}
		v, _ := res.Set().Value(semconv.ServiceInstanceIDKey)
		return v.AsString()
	}
	a, b := id(), id()
	if a == "" || a == b {
		t.Fatalf("instance ids %q and %q", a, b)
	}

	// The reminder function reports as its own service (dashboards and alerts
	// tell the two apart by service.name).
	res, err := newResource("dinogym-reminders", "prod", "test")
	if err != nil {
		t.Fatal(err)
	}
	if v, _ := res.Set().Value(semconv.ServiceNameKey); v.AsString() != "dinogym-reminders" {
		t.Fatalf("service.name %q", v.AsString())
	}
}
