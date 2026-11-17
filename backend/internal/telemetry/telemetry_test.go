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
)

// Flush must deliver traces, metrics and logs to the OTLP endpoint right away:
// on Lambda nothing runs in the background between invocations.
func TestOTLPFlushSendsAllThreeSignals(t *testing.T) {
	var mu sync.Mutex
	got := map[string]bool{}
	collector := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		got[r.URL.Path] = true
		mu.Unlock()
		w.WriteHeader(http.StatusOK)
	}))
	defer collector.Close()
	t.Setenv("OTEL_EXPORTER_OTLP_ENDPOINT", collector.URL)

	ctx := context.Background()
	var stdout bytes.Buffer
	tel, log, err := Setup(ctx, "otlp", "local", "test", slog.LevelInfo, &stdout)
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
	for _, path := range []string{"/v1/traces", "/v1/metrics", "/v1/logs"} {
		if !got[path] {
			t.Errorf("nothing sent to %s; got %v", path, got)
		}
	}
	if !strings.Contains(stdout.String(), `"msg":"hello"`) || strings.Contains(stdout.String(), "below the level") {
		t.Errorf("stdout JSON log: %s", stdout.String())
	}
}

func TestConsolePrintsSpans(t *testing.T) {
	var stdout bytes.Buffer
	tel, _, err := Setup(context.Background(), "console", "local", "test", slog.LevelInfo, &stdout)
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
	if _, _, err := Setup(context.Background(), "zipkin", "local", "test", slog.LevelInfo, &bytes.Buffer{}); err == nil {
		t.Fatal("want an error")
	}
}
