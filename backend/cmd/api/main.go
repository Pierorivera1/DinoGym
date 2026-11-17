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
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"go.opentelemetry.io/contrib/instrumentation/github.com/aws/aws-sdk-go-v2/otelaws"

	"github.com/Pierorivera1/DinoGym/backend/internal/config"
	"github.com/Pierorivera1/DinoGym/backend/internal/lambdahttp"
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
	tel, log, err := telemetry.Setup(ctx, cfg.Telemetry, cfg.Env, version, cfg.LogLevel, os.Stdout)
	if err != nil {
		return err
	}
	st, err := newStore(ctx, cfg, log)
	if err != nil {
		return err
	}
	handler := server.New(log, st).Handler()

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
	log.Info("listening", "addr", cfg.Addr, "env", cfg.Env, "telemetry", cfg.Telemetry, "version", version)

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
func newStore(ctx context.Context, cfg config.Config, log *slog.Logger) (*store.Store, error) {
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, fmt.Errorf("load AWS config: %w", err)
	}
	otelaws.AppendMiddlewares(&awsCfg.APIOptions)
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
