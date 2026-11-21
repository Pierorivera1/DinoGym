// Package secrets reads SecureString parameters from SSM at cold start
// (decisions 54 and 69). The owner creates them; Terraform only grants access.
package secrets

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
)

// Client is the part of the SSM client Read uses.
type Client interface {
	GetParameters(context.Context, *ssm.GetParametersInput, ...func(*ssm.Options)) (*ssm.GetParametersOutput, error)
}

// Read fetches the named parameters, decrypted, in one call, and returns
// their values by name. Empty names are skipped; with none left, SSM is not
// called. A missing parameter is an error that names it. Values are never
// put in an error.
func Read(ctx context.Context, c Client, names ...string) (map[string]string, error) {
	var want []string
	for _, n := range names {
		if n != "" {
			want = append(want, n)
		}
	}
	got := make(map[string]string, len(want))
	if len(want) == 0 {
		return got, nil
	}
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	out, err := c.GetParameters(ctx, &ssm.GetParametersInput{Names: want, WithDecryption: aws.Bool(true)})
	if err != nil {
		return nil, fmt.Errorf("read %s from SSM: %w", strings.Join(want, ", "), err)
	}
	for _, p := range out.Parameters {
		if p.Name != nil && p.Value != nil {
			got[*p.Name] = *p.Value
		}
	}
	var missing []string
	for _, n := range want {
		if _, ok := got[n]; !ok {
			missing = append(missing, n)
		}
	}
	if len(missing) > 0 {
		return nil, fmt.Errorf("SSM parameters not found: %s", strings.Join(missing, ", "))
	}
	return got, nil
}
