// Command api is the DinoGym backend. The same binary runs as a local HTTP
// server on the laptop and as the Lambda function behind API Gateway
// (decision 63): it checks for the Lambda runtime at start.
//
// Local run, against the AWS Emulator (WORKFLOW.md). With AWS_ENDPOINT_URL on
// this machine, the binary creates its DynamoDB table there at start:
//
//	infra/emulator/start.sh && source infra/emulator/env.sh
//	DINOGYM_TELEMETRY=console go run ./cmd/api
package main

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/scheduler"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
	"go.opentelemetry.io/contrib/instrumentation/github.com/aws/aws-sdk-go-v2/otelaws"

	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/config"
	"github.com/Pierorivera1/DinoGym/backend/internal/lambdahttp"
	"github.com/Pierorivera1/DinoGym/backend/internal/reminders"
	"github.com/Pierorivera1/DinoGym/backend/internal/secrets"
	"github.com/Pierorivera1/DinoGym/backend/internal/server"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
	"github.com/Pierorivera1/DinoGym/backend/internal/telemetry"
)

// version is set at build time: go build -ldflags "-X main.version=<git sha>".
var version = "dev"

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "dinogym-api:", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load(os.Getenv)
	if err != nil {
		return err
	}

	ctx := context.Background()
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		return fmt.Errorf("load AWS config: %w", err)
	}
	// The secrets come first, in one SSM call: telemetry needs the Grafana
	// token before it starts (decision 118).
	secret, err := secrets.Read(ctx, ssm.NewFromConfig(awsCfg), cfg.SessionKeyParam, cfg.Telemetry.TokenParam)
	if err != nil {
		return err
	}
	tel, log, err := telemetry.Setup(ctx, telemetry.Options{
		Mode:    cfg.Telemetry.Mode,
		Env:     cfg.Env,
		Version: version,
		Level:   cfg.LogLevel,
		Stdout:  os.Stdout,
		Headers: otlpHeaders(cfg.Telemetry, secret),
	})
	if err != nil {
		return err
	}
	otelaws.AppendMiddlewares(&awsCfg.APIOptions)

	st, err := newStore(ctx, cfg, awsCfg, log)
	if err != nil {
		return err
	}
	key, err := sessionKey(cfg, secret, log)
	if err != nil {
		return err
	}
	tokens, err := auth.NewTokens(key, cfg.Env)
	if err != nil {
		return err
	}
	if cfg.GoogleClientID == "" {
		log.Warn("DINOGYM_GOOGLE_CLIENT_ID is not set; sign-in with Google will fail")
	}
	svc := auth.NewService(tokens, auth.NewGoogle(cfg.GoogleClientID), st)
	srv := server.New(log, st, svc).WithShareBaseURL(cfg.ShareBaseURL)
	if cfg.ScheduleGroup != "" {
		srv.WithReminders(reminders.NewReconciler(log, st, scheduler.NewFromConfig(awsCfg), reminders.Target{
			Group:       cfg.ScheduleGroup,
			FunctionARN: cfg.ReminderFunctionARN,
			RoleARN:     cfg.SchedulerRoleARN,
		}))
	}
	handler := srv.Handler()

	// Lambda sets AWS_LAMBDA_RUNTIME_API for the runtime client.
	if os.Getenv("AWS_LAMBDA_RUNTIME_API") != "" {
		flush := func(ctx context.Context) {
			if err := tel.Flush(ctx); err != nil {
				log.WarnContext(ctx, "telemetry flush failed", "error", err)
			}
		}
		lambda.Start(lambdahttp.Handler(handler, flush))
		return nil // not reached: lambda.Start exits the process
	}
	return serveLocal(cfg, log, tel, handler)
}

func serveLocal(cfg config.Config, log *slog.Logger, tel *telemetry.Telemetry, h http.Handler) error {
	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           h,
		ReadHeaderTimeout: 5 * time.Second,
	}

	stop, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	errc := make(chan error, 1)
	go func() { errc <- srv.ListenAndServe() }()
	log.Info("listening", "addr", cfg.Addr, "env", cfg.Env, "telemetry", cfg.Telemetry.Mode, "version", version)

	select {
	case err := <-errc:
		return err
	case <-stop.Done():
	}

	ctx, cancelShutdown := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancelShutdown()
	err := srv.Shutdown(ctx)
	if err := tel.Shutdown(ctx); err != nil {
		log.Warn("telemetry shutdown failed", "error", err)
	}
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

// newStore connects to DynamoDB. Every AWS call gets a span (otelaws). On the
// laptop pointed at the emulator, it also creates the table; staging and prod
// get it from Terraform (task 17).
func newStore(ctx context.Context, cfg config.Config, awsCfg aws.Config, log *slog.Logger) (*store.Store, error) {
	st := store.New(dynamodb.NewFromConfig(awsCfg), cfg.Table)

	if cfg.Env != "local" {
		return st, nil
	}
	endpoint := os.Getenv("AWS_ENDPOINT_URL")
	if u, err := url.Parse(endpoint); endpoint == "" || err != nil || (u.Hostname() != "127.0.0.1" && u.Hostname() != "localhost") {
		log.Warn("no local AWS endpoint; storage calls will fail. Run: source infra/emulator/env.sh", "AWS_ENDPOINT_URL", endpoint)
		return st, nil
	}
	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	if err := st.CreateTable(ctx); err != nil {
		return nil, err
	}
	log.Info("table ready on the emulator", "table", cfg.Table, "endpoint", endpoint)
	return st, nil
}

// otlpHeaders returns Grafana Cloud's credentials when a token parameter is
// configured, otherwise nil (the exporters then use OTEL_EXPORTER_OTLP_HEADERS).
func otlpHeaders(t config.Telemetry, secret map[string]string) map[string]string {
	if t.TokenParam == "" {
		return nil
	}
	return telemetry.BasicAuth(t.InstanceID, secret[t.TokenParam])
}

// sessionKey returns the key that signs access tokens (decision 79): read from
// SSM at start in staging and prod, from DINOGYM_SESSION_KEY on the laptop,
// or, on the laptop with neither, made up at start (sessions then end on
// restart). The value is never logged.
func sessionKey(cfg config.Config, secret map[string]string, log *slog.Logger) ([]byte, error) {
	switch {
	case cfg.SessionKeyParam != "":
		return []byte(secret[cfg.SessionKeyParam]), nil
	case cfg.SessionKey != "":
		return []byte(cfg.SessionKey), nil
	case cfg.Env == "local":
		b := make([]byte, 48)
		if _, err := rand.Read(b); err != nil {
			return nil, err
		}
		log.Warn("no session signing key set; using a random one, so sessions end when the server stops")
		return []byte(base64.StdEncoding.EncodeToString(b)), nil
	}
	return nil, fmt.Errorf("no session signing key configured")
}
