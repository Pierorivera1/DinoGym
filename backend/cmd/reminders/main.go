// Command reminders is the reminder Lambda (task 24, decision 107). Each
// user's EventBridge schedules invoke it with {"userId", "kind"}; it sends
// that reminder as an FCM data message to every phone the user registered.
//
// It is its own binary so it runs under its own role: it may read the table
// and reach Google, while the API, which faces the internet, may do neither
// of the second.
//
// Settings (environment):
//
//	DINOGYM_ENV              staging or prod
//	DINOGYM_TABLE            DynamoDB table
//	DINOGYM_FCM_PROJECT      Firebase project id, such as dinogym-3870b
//	DINOGYM_GCP_AUDIENCE     the workload identity provider, //iam.googleapis.com/projects/...
//	DINOGYM_FCM_SERVICE_ACCOUNT  the service account to impersonate
//	DINOGYM_LOG_LEVEL, DINOGYM_TELEMETRY  as for the API
package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"go.opentelemetry.io/contrib/instrumentation/github.com/aws/aws-sdk-go-v2/otelaws"

	"github.com/Pierorivera1/DinoGym/backend/internal/reminders"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
	"github.com/Pierorivera1/DinoGym/backend/internal/telemetry"
)

// version is set at build time, as for the API.
var version = "dev"

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "dinogym-reminders:", err)
		os.Exit(1)
	}
}

func run() error {
	env := os.Getenv("DINOGYM_ENV")
	required := map[string]string{}
	for _, k := range []string{"DINOGYM_ENV", "DINOGYM_TABLE", "DINOGYM_FCM_PROJECT", "DINOGYM_GCP_AUDIENCE", "DINOGYM_FCM_SERVICE_ACCOUNT"} {
		v := os.Getenv(k)
		if v == "" {
			return fmt.Errorf("%s is required", k)
		}
		required[k] = v
	}
	level := slog.LevelInfo
	if v := os.Getenv("DINOGYM_LOG_LEVEL"); v != "" {
		if err := level.UnmarshalText([]byte(v)); err != nil {
			return fmt.Errorf("DINOGYM_LOG_LEVEL=%q: %w", v, err)
		}
	}
	mode := os.Getenv("DINOGYM_TELEMETRY")
	if mode == "" {
		mode = "none"
	}

	ctx := context.Background()
	tel, log, err := telemetry.Setup(ctx, mode, env, version, level, os.Stdout)
	if err != nil {
		return err
	}
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		return fmt.Errorf("load AWS config: %w", err)
	}
	otelaws.AppendMiddlewares(&awsCfg.APIOptions)

	ts, err := reminders.Federation{
		Audience:       required["DINOGYM_GCP_AUDIENCE"],
		ServiceAccount: required["DINOGYM_FCM_SERVICE_ACCOUNT"],
	}.TokenSource(ctx, awsCfg)
	if err != nil {
		return err
	}
	st := store.New(dynamodb.NewFromConfig(awsCfg), required["DINOGYM_TABLE"])
	firer := reminders.NewFirer(log, st, reminders.NewFCM(required["DINOGYM_FCM_PROJECT"], ts))

	lambda.Start(func(ctx context.Context, ev reminders.Event) error {
		defer func() {
			if err := tel.Flush(ctx); err != nil {
				log.WarnContext(ctx, "telemetry flush failed", "error", err)
			}
		}()
		return firer.Fire(ctx, ev)
	})
	return nil
}
