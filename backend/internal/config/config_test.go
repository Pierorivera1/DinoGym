package config

import (
	"log/slog"
	"testing"
)

func env(m map[string]string) func(string) string {
	return func(k string) string { return m[k] }
}

func TestDefaults(t *testing.T) {
	c, err := Load(env(nil))
	if err != nil {
		t.Fatal(err)
	}
	want := Config{Env: "local", Addr: "127.0.0.1:8080", LogLevel: slog.LevelDebug, Telemetry: "none"}
	if c != want {
		t.Fatalf("got %+v, want %+v", c, want)
	}
}

func TestProdIsQuietAndOTLPFollowsTheEndpoint(t *testing.T) {
	c, err := Load(env(map[string]string{
		"DINOGYM_ENV":                 "prod",
		"OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp.example",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if c.LogLevel != slog.LevelWarn || c.Telemetry != "otlp" {
		t.Fatalf("got %+v", c)
	}
}

func TestOverrides(t *testing.T) {
	c, err := Load(env(map[string]string{
		"DINOGYM_LOG_LEVEL": "info",
		"DINOGYM_TELEMETRY": "console",
		"DINOGYM_ADDR":      ":9000",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if c.LogLevel != slog.LevelInfo || c.Telemetry != "console" || c.Addr != ":9000" {
		t.Fatalf("got %+v", c)
	}
}

func TestRejectsBadValues(t *testing.T) {
	for _, m := range []map[string]string{
		{"DINOGYM_ENV": "dev"},
		{"DINOGYM_LOG_LEVEL": "loud"},
		{"DINOGYM_TELEMETRY": "grpc"},
	} {
		if _, err := Load(env(m)); err == nil {
			t.Errorf("Load(%v) accepted a bad value", m)
		}
	}
}
