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
	want := Config{Env: "local", Addr: "127.0.0.1:8080", LogLevel: slog.LevelDebug, Telemetry: Telemetry{Mode: "none"}, Table: "dinogym"}
	if c != want {
		t.Fatalf("got %+v, want %+v", c, want)
	}
}

func TestProdIsQuietAndOTLPFollowsTheEndpoint(t *testing.T) {
	c, err := Load(env(map[string]string{
		"DINOGYM_ENV":                 "prod",
		"OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp.example",
		"DINOGYM_GOOGLE_CLIENT_ID":    "web.apps.googleusercontent.com",
		"DINOGYM_SESSION_KEY_PARAM":   "/dinogym-prod/session-signing-key",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if c.LogLevel != slog.LevelWarn || c.Telemetry.Mode != "otlp" {
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
	if c.LogLevel != slog.LevelInfo || c.Telemetry.Mode != "console" || c.Addr != ":9000" {
		t.Fatalf("got %+v", c)
	}
}

func TestRejectsBadValues(t *testing.T) {
	for _, m := range []map[string]string{
		{"DINOGYM_ENV": "dev"},
		{"DINOGYM_LOG_LEVEL": "loud"},
		{"DINOGYM_TELEMETRY": "grpc"},
		{"DINOGYM_TELEMETRY": "otlp"},
		{"OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp.example", "DINOGYM_OTLP_INSTANCE_ID": "123"},
		{"OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp.example", "DINOGYM_OTLP_TOKEN_PARAM": "/p"},
		{"DINOGYM_SESSION_KEY": "k", "DINOGYM_SESSION_KEY_PARAM": "/p"},
		{"DINOGYM_ENV": "staging", "DINOGYM_SESSION_KEY_PARAM": "/p"},
		{"DINOGYM_ENV": "staging", "DINOGYM_GOOGLE_CLIENT_ID": "id"},
		{"DINOGYM_SCHEDULE_GROUP": "g", "DINOGYM_REMINDER_FUNCTION_ARN": "arn"},
	} {
		if _, err := Load(env(m)); err == nil {
			t.Errorf("Load(%v) accepted a bad value", m)
		}
	}
}

func TestShareBaseURLLosesItsTrailingSlash(t *testing.T) {
	c, err := Load(func(k string) string {
		if k == "DINOGYM_SHARE_BASE_URL" {
			return "https://d123.cloudfront.net/"
		}
		return ""
	})
	if err != nil {
		t.Fatal(err)
	}
	if c.ShareBaseURL != "https://d123.cloudfront.net" {
		t.Fatalf("ShareBaseURL %q", c.ShareBaseURL)
	}
}

func TestGrafanaCloudSettings(t *testing.T) {
	tel, err := LoadTelemetry(env(map[string]string{
		"OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp-gateway-prod-us-east-0.grafana.net/otlp",
		"DINOGYM_OTLP_INSTANCE_ID":    "123456",
		"DINOGYM_OTLP_TOKEN_PARAM":    "/dinogym-staging/grafana-token",
	}))
	if err != nil {
		t.Fatal(err)
	}
	want := Telemetry{Mode: "otlp", InstanceID: "123456", TokenParam: "/dinogym-staging/grafana-token"}
	if tel != want {
		t.Fatalf("got %+v, want %+v", tel, want)
	}
}
