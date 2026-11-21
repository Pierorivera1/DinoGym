package secrets

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
	"github.com/aws/aws-sdk-go-v2/service/ssm/types"
)

type fakeSSM struct {
	values map[string]string
	calls  [][]string
	err    error
}

func (f *fakeSSM) GetParameters(_ context.Context, in *ssm.GetParametersInput, _ ...func(*ssm.Options)) (*ssm.GetParametersOutput, error) {
	f.calls = append(f.calls, in.Names)
	if f.err != nil {
		return nil, f.err
	}
	if !aws.ToBool(in.WithDecryption) {
		return nil, errors.New("not decrypted")
	}
	out := &ssm.GetParametersOutput{}
	for _, n := range in.Names {
		if v, ok := f.values[n]; ok {
			out.Parameters = append(out.Parameters, types.Parameter{Name: aws.String(n), Value: aws.String(v)})
		} else {
			out.InvalidParameters = append(out.InvalidParameters, n)
		}
	}
	return out, nil
}

func TestReadsAllInOneCall(t *testing.T) {
	f := &fakeSSM{values: map[string]string{"/a": "1", "/b": "2"}}
	got, err := Read(context.Background(), f, "/a", "", "/b")
	if err != nil {
		t.Fatal(err)
	}
	if got["/a"] != "1" || got["/b"] != "2" || len(f.calls) != 1 || len(f.calls[0]) != 2 {
		t.Fatalf("got %v, calls %v", got, f.calls)
	}
}

func TestNothingToReadSkipsSSM(t *testing.T) {
	f := &fakeSSM{}
	got, err := Read(context.Background(), f, "", "")
	if err != nil || len(got) != 0 || len(f.calls) != 0 {
		t.Fatalf("got %v, %v, calls %v", got, err, f.calls)
	}
}

func TestMissingParameterIsNamed(t *testing.T) {
	f := &fakeSSM{values: map[string]string{"/a": "secret-value"}}
	_, err := Read(context.Background(), f, "/a", "/dinogym-prod/grafana-token")
	if err == nil || !strings.Contains(err.Error(), "/dinogym-prod/grafana-token") || strings.Contains(err.Error(), "secret-value") {
		t.Fatalf("err = %v", err)
	}
}

func TestCallErrorIsWrapped(t *testing.T) {
	boom := errors.New("AccessDenied")
	_, err := Read(context.Background(), &fakeSSM{err: boom}, "/a")
	if !errors.Is(err, boom) {
		t.Fatalf("err = %v", err)
	}
}
