//go:build integration

// Package awstest connects integration tests to the local AWS Emulator
// (decision 59). The tests build only with the "integration" tag:
//
//	source ../infra/emulator/env.sh
//	go test -p 2 -tags integration ./internal/store/ ./internal/awstest/
//
// They prove that our AWS API usage is correct on the emulator, not that it is
// reliable, secure or fast on AWS (docs/WORKFLOW.md).
package awstest

import (
	"context"
	"net/url"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
)

// Config returns an AWS config for the emulator. It fails the test unless the
// shell points at a local endpoint, so the tests can never reach real AWS.
func Config(t testing.TB) aws.Config {
	t.Helper()
	endpoint := os.Getenv("AWS_ENDPOINT_URL")
	u, err := url.Parse(endpoint)
	if endpoint == "" || err != nil || (u.Hostname() != "127.0.0.1" && u.Hostname() != "localhost") {
		t.Fatalf("AWS_ENDPOINT_URL=%q is not the local emulator; run infra/emulator/start.sh and source infra/emulator/env.sh", endpoint)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	cfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		t.Fatalf("load AWS config: %v", err)
	}
	return cfg
}

// Name returns a resource name unique to this run, so tests never share state
// with an earlier run on the same emulator.
func Name(prefix string) string {
	return strings.ToLower(prefix + "-" + strconv.FormatInt(time.Now().UnixNano(), 36))
}
