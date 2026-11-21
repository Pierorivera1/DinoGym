//go:build integration

package secrets

// SSM on the AWS Emulator (decision 59): a SecureString written the way the
// owner writes it (infra/README.md) is read back decrypted, and a missing one
// is reported. Proves the API usage, not IAM or KMS on real AWS.

import (
	"context"
	"strings"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
	ssmtypes "github.com/aws/aws-sdk-go-v2/service/ssm/types"

	"github.com/Pierorivera1/DinoGym/backend/internal/awstest"
)

func TestReadOnEmulator(t *testing.T) {
	ctx := context.Background()
	client := ssm.NewFromConfig(awstest.Config(t))
	prefix := "/" + awstest.Name("dinogym-test")
	key, token := prefix+"/session-signing-key", prefix+"/grafana-token"
	for name, value := range map[string]string{key: "signing-key", token: "glc_token"} {
		if _, err := client.PutParameter(ctx, &ssm.PutParameterInput{
			Name: aws.String(name), Value: aws.String(value), Type: ssmtypes.ParameterTypeSecureString,
		}); err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			_, _ = client.DeleteParameter(context.Background(), &ssm.DeleteParameterInput{Name: aws.String(name)})
		})
	}

	got, err := Read(ctx, client, key, token)
	if err != nil {
		t.Fatal(err)
	}
	if got[key] != "signing-key" || got[token] != "glc_token" {
		t.Fatalf("got %v", got)
	}

	missing := prefix + "/missing"
	if _, err := Read(ctx, client, key, missing); err == nil || !strings.Contains(err.Error(), missing) {
		t.Fatalf("err = %v", err)
	}
}
