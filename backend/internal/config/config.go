// Package config reads the API's settings from environment variables.
package config

import (
	"fmt"
	"log/slog"
	"strings"
)

// Config holds every setting the API reads at start.
type Config struct {
	// Env is "local", "staging" or "prod". It tags telemetry and picks defaults.
	Env string
	// Addr is where the local HTTP server listens. Not used on Lambda.
	Addr string
	// LogLevel is the lowest level written. Defaults to DEBUG except in prod (decision 22).
	LogLevel slog.Level
	// Telemetry picks the exporter and how Grafana Cloud is reached (decisions 64 and 118).
	Telemetry Telemetry
	// Table is the DynamoDB table (decision 52).
	Table string
	// GoogleClientID is the Web OAuth client ID that Google ID tokens must be
	// issued to (decision 80). Not secret.
	GoogleClientID string
	// SessionKeyParam is the SSM parameter holding the session signing key
	// (decision 79). Set by Terraform in staging and prod.
	SessionKeyParam string
	// SessionKey is the session signing key itself, for the laptop. Secret:
	// never log a Config.
	SessionKey string
	// ShareBaseURL is where shared links live, https://<distribution>.cloudfront.net
	// (decision 100), set by Terraform. Empty: POST /shares answers no url.
	ShareBaseURL string
	// Reminder schedules (decision 105): the environment's schedule group, the
	// reminder Lambda the schedules invoke and the role they invoke it with.
	// All three or none; with none, settings sync but no schedule is made.
	ScheduleGroup       string
	ReminderFunctionARN string
	SchedulerRoleARN    string
}

// Load reads the settings through getenv, normally os.Getenv.
//
//	DINOGYM_ENV        local (default), staging, prod
//	DINOGYM_ADDR       default 127.0.0.1:8080
//	DINOGYM_LOG_LEVEL  DEBUG, INFO, WARN, ERROR; default WARN in prod, DEBUG elsewhere
//	DINOGYM_TELEMETRY  otlp, console, none; default otlp when OTEL_EXPORTER_OTLP_ENDPOINT
//	                   is set, otherwise none
//	DINOGYM_OTLP_INSTANCE_ID, DINOGYM_OTLP_TOKEN_PARAM
//	                   Grafana Cloud's instance id and the SSM parameter with its
//	                   token; both or none (decision 118)
//	DINOGYM_TABLE      DynamoDB table name, default dinogym
//	DINOGYM_GOOGLE_CLIENT_ID   Web OAuth client ID; required in staging and prod
//	DINOGYM_SESSION_KEY_PARAM  SSM parameter with the session signing key
//	DINOGYM_SESSION_KEY        the signing key itself (laptop only)
//	DINOGYM_SHARE_BASE_URL     base of shared links, no trailing slash
//	DINOGYM_SCHEDULE_GROUP, DINOGYM_REMINDER_FUNCTION_ARN, DINOGYM_SCHEDULER_ROLE_ARN
//	                           reminder schedules; all three or none
//
// Staging and prod need one of the two session key settings, not both. Locally
// with neither, cmd/api makes a random key at start.
func Load(getenv func(string) string) (Config, error) {
	c := Config{
		Env:   orDefault(getenv("DINOGYM_ENV"), "local"),
		Addr:  orDefault(getenv("DINOGYM_ADDR"), "127.0.0.1:8080"),
		Table: orDefault(getenv("DINOGYM_TABLE"), "dinogym"),

		GoogleClientID:  getenv("DINOGYM_GOOGLE_CLIENT_ID"),
		SessionKeyParam: getenv("DINOGYM_SESSION_KEY_PARAM"),
		SessionKey:      getenv("DINOGYM_SESSION_KEY"),
		ShareBaseURL:    strings.TrimRight(getenv("DINOGYM_SHARE_BASE_URL"), "/"),

		ScheduleGroup:       getenv("DINOGYM_SCHEDULE_GROUP"),
		ReminderFunctionARN: getenv("DINOGYM_REMINDER_FUNCTION_ARN"),
		SchedulerRoleARN:    getenv("DINOGYM_SCHEDULER_ROLE_ARN"),
	}
	if set := countSet(c.ScheduleGroup, c.ReminderFunctionARN, c.SchedulerRoleARN); set != 0 && set != 3 {
		return Config{}, fmt.Errorf("set all of DINOGYM_SCHEDULE_GROUP, DINOGYM_REMINDER_FUNCTION_ARN and DINOGYM_SCHEDULER_ROLE_ARN, or none")
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

	var err error
	if c.Telemetry, err = LoadTelemetry(getenv); err != nil {
		return Config{}, err
	}

	if c.SessionKeyParam != "" && c.SessionKey != "" {
		return Config{}, fmt.Errorf("set DINOGYM_SESSION_KEY_PARAM or DINOGYM_SESSION_KEY, not both")
	}
	if c.Env != "local" {
		if c.GoogleClientID == "" {
			return Config{}, fmt.Errorf("DINOGYM_GOOGLE_CLIENT_ID is required in %s", c.Env)
		}
		if c.SessionKeyParam == "" && c.SessionKey == "" {
			return Config{}, fmt.Errorf("DINOGYM_SESSION_KEY_PARAM is required in %s", c.Env)
		}
	}
	return c, nil
}

// Telemetry is how the process exports traces, metrics and logs.
type Telemetry struct {
	// Mode is "otlp", "console" or "none" (decision 64).
	Mode string
	// InstanceID is the Grafana Cloud stack's OTLP user, sent with the token
	// as HTTP Basic auth. Not secret.
	InstanceID string
	// TokenParam is the SSM SecureString parameter holding the Grafana Cloud
	// token (decision 69). Empty: the exporters send no credentials.
	TokenParam string
}

// LoadTelemetry reads the telemetry settings. The API and the reminder
// function share them.
func LoadTelemetry(getenv func(string) string) (Telemetry, error) {
	t := Telemetry{
		Mode:       "none",
		InstanceID: getenv("DINOGYM_OTLP_INSTANCE_ID"),
		TokenParam: getenv("DINOGYM_OTLP_TOKEN_PARAM"),
	}
	endpoint := getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if endpoint != "" {
		t.Mode = "otlp"
	}
	if v := getenv("DINOGYM_TELEMETRY"); v != "" {
		t.Mode = v
	}
	switch t.Mode {
	case "otlp", "console", "none":
	default:
		return Telemetry{}, fmt.Errorf("DINOGYM_TELEMETRY=%q: want otlp, console or none", t.Mode)
	}
	if countSet(t.InstanceID, t.TokenParam) == 1 {
		return Telemetry{}, fmt.Errorf("set both DINOGYM_OTLP_INSTANCE_ID and DINOGYM_OTLP_TOKEN_PARAM, or neither")
	}
	if t.Mode == "otlp" && endpoint == "" {
		return Telemetry{}, fmt.Errorf("DINOGYM_TELEMETRY=otlp needs OTEL_EXPORTER_OTLP_ENDPOINT")
	}
	return t, nil
}

func countSet(vs ...string) int {
	n := 0
	for _, v := range vs {
		if v != "" {
			n++
		}
	}
	return n
}

func orDefault(v, def string) string {
	if v == "" {
		return def
	}
	return v
}
