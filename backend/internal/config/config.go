// Package config reads the API's settings from environment variables.
package config

import (
	"fmt"
	"log/slog"
)

// Config holds every setting the API reads at start.
type Config struct {
	// Env is "local", "staging" or "prod". It tags telemetry and picks defaults.
	Env string
	// Addr is where the local HTTP server listens. Not used on Lambda.
	Addr string
	// LogLevel is the lowest level written. Defaults to DEBUG except in prod (decision 22).
	LogLevel slog.Level
	// Telemetry is "otlp", "console" or "none" (decision 63).
	Telemetry string
}

// Load reads the settings through getenv, normally os.Getenv.
//
//	DINOGYM_ENV        local (default), staging, prod
//	DINOGYM_ADDR       default 127.0.0.1:8080
//	DINOGYM_LOG_LEVEL  DEBUG, INFO, WARN, ERROR; default WARN in prod, DEBUG elsewhere
//	DINOGYM_TELEMETRY  otlp, console, none; default otlp when OTEL_EXPORTER_OTLP_ENDPOINT
//	                   is set, otherwise none
func Load(getenv func(string) string) (Config, error) {
	c := Config{
		Env:  orDefault(getenv("DINOGYM_ENV"), "local"),
		Addr: orDefault(getenv("DINOGYM_ADDR"), "127.0.0.1:8080"),
	}
	switch c.Env {
	case "local", "staging", "prod":
	default:
		return Config{}, fmt.Errorf("DINOGYM_ENV=%q: want local, staging or prod", c.Env)
	}

	c.LogLevel = slog.LevelDebug
	if c.Env == "prod" {
		c.LogLevel = slog.LevelWarn
	}
	if v := getenv("DINOGYM_LOG_LEVEL"); v != "" {
		if err := c.LogLevel.UnmarshalText([]byte(v)); err != nil {
			return Config{}, fmt.Errorf("DINOGYM_LOG_LEVEL=%q: %w", v, err)
		}
	}

	c.Telemetry = "none"
	if getenv("OTEL_EXPORTER_OTLP_ENDPOINT") != "" {
		c.Telemetry = "otlp"
	}
	if v := getenv("DINOGYM_TELEMETRY"); v != "" {
		c.Telemetry = v
	}
	switch c.Telemetry {
	case "otlp", "console", "none":
	default:
		return Config{}, fmt.Errorf("DINOGYM_TELEMETRY=%q: want otlp, console or none", c.Telemetry)
	}
	return c, nil
}

func orDefault(v, def string) string {
	if v == "" {
		return def
	}
	return v
}
