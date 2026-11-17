// Command api is the DinoGym backend. The same binary runs as a local HTTP
// server on the laptop and as the Lambda function behind API Gateway
// (decision 63): it checks for the Lambda runtime at start.
//
// Local run, against the AWS Emulator once the API uses AWS (WORKFLOW.md):
//
//	source infra/emulator/env.sh
//	DINOGYM_TELEMETRY=console go run ./cmd/api
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/aws/aws-lambda-go/lambda"

	"github.com/Pierorivera1/DinoGym/backend/internal/config"
	"github.com/Pierorivera1/DinoGym/backend/internal/lambdahttp"
	"github.com/Pierorivera1/DinoGym/backend/internal/server"
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
	handler := server.New(log).Handler()

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
