package reminders

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google/externalaccount"
)

// ErrUnregistered means FCM no longer knows the token: the app was
// uninstalled or its token replaced. The device should be deleted.
var ErrUnregistered = errors.New("fcm token is no longer registered")

const fcmScope = "https://www.googleapis.com/auth/firebase.messaging"

// FCM sends data messages through the HTTP v1 API (decision 107). No
// Firebase SDK: one authenticated POST is all a data message needs.
type FCM struct {
	client   *http.Client
	endpoint string // https://fcm.googleapis.com/v1/projects/<project>/messages:send
}

// NewFCM returns a sender for the Firebase project, authenticating with ts.
func NewFCM(project string, ts oauth2.TokenSource) *FCM {
	return newFCM("https://fcm.googleapis.com", project, ts)
}

func newFCM(base, project string, ts oauth2.TokenSource) *FCM {
	c := oauth2.NewClient(context.Background(), ts)
	c.Timeout = 10 * time.Second
	return &FCM{client: c, endpoint: base + "/v1/projects/" + url.PathEscape(project) + "/messages:send"}
}

type fcmRequest struct {
	Message fcmMessage `json:"message"`
}

type fcmMessage struct {
	Token   string            `json:"token"`
	Data    map[string]string `json:"data"`
	Android fcmAndroid        `json:"android"`
}

type fcmAndroid struct {
	// High priority reaches a phone in Doze at the chosen minute; the app
	// shows a notification for it, which Android expects of high priority.
	Priority string `json:"priority"`
	// A reminder an hour late is no longer worth showing.
	TTL string `json:"ttl"`
}

type fcmError struct {
	Error struct {
		Status  string `json:"status"`
		Details []struct {
			Type      string `json:"@type"`
			ErrorCode string `json:"errorCode"`
		} `json:"details"`
	} `json:"error"`
}

// Send delivers one data message, {"kind": k}, to one token. It returns
// ErrUnregistered when FCM says the token is gone.
func (f *FCM) Send(ctx context.Context, token string, k Kind) error {
	body, err := json.Marshal(fcmRequest{Message: fcmMessage{
		Token:   token,
		Data:    map[string]string{"kind": string(k)},
		Android: fcmAndroid{Priority: "high", TTL: "3600s"},
	}})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, f.endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := f.client.Do(req)
	if err != nil {
		return fmt.Errorf("fcm send: %w", err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode == http.StatusOK {
		return nil
	}
	var e fcmError
	_ = json.Unmarshal(data, &e)
	if resp.StatusCode == http.StatusNotFound || e.Error.Status == "NOT_FOUND" {
		return ErrUnregistered
	}
	for _, d := range e.Error.Details {
		if d.ErrorCode == "UNREGISTERED" {
			return ErrUnregistered
		}
	}
	// The body names the error, never the token.
	return fmt.Errorf("fcm send: %s %s", resp.Status, e.Error.Status)
}

// Federation is how the reminder Lambda gets a Google token (decision 55):
// its own AWS credentials are exchanged at Google's STS for a token of a
// service account that may only send FCM messages.
type Federation struct {
	// Audience is //iam.googleapis.com/projects/<number>/locations/global/
	// workloadIdentityPools/<pool>/providers/<provider>.
	Audience string
	// ServiceAccount is the email of the service account to impersonate.
	ServiceAccount string
}

// TokenSource returns a cached Google token source backed by awsCfg's
// credentials. Nothing secret is configured: the AWS credentials are the
// Lambda's own short-lived ones.
func (fed Federation) TokenSource(ctx context.Context, awsCfg aws.Config) (oauth2.TokenSource, error) {
	ts, err := externalaccount.NewTokenSource(ctx, externalaccount.Config{
		Audience:                       fed.Audience,
		SubjectTokenType:               "urn:ietf:params:aws:token-type:aws4_request",
		TokenURL:                       "https://sts.googleapis.com/v1/token",
		ServiceAccountImpersonationURL: "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/" + fed.ServiceAccount + ":generateAccessToken",
		Scopes:                         []string{fcmScope},
		AwsSecurityCredentialsSupplier: awsSupplier{cfg: awsCfg},
	})
	if err != nil {
		return nil, fmt.Errorf("google federation: %w", err)
	}
	return oauth2.ReuseTokenSource(nil, ts), nil
}

// awsSupplier hands the AWS SDK's credentials to Google's library. The SDK
// caches and refreshes them, as the library asks a supplier to do.
type awsSupplier struct{ cfg aws.Config }

func (s awsSupplier) AwsRegion(context.Context, externalaccount.SupplierOptions) (string, error) {
	if s.cfg.Region == "" {
		return "", errors.New("no AWS region")
	}
	return s.cfg.Region, nil
}

func (s awsSupplier) AwsSecurityCredentials(ctx context.Context, _ externalaccount.SupplierOptions) (*externalaccount.AwsSecurityCredentials, error) {
	c, err := s.cfg.Credentials.Retrieve(ctx)
	if err != nil {
		return nil, err
	}
	return &externalaccount.AwsSecurityCredentials{AccessKeyID: c.AccessKeyID, SecretAccessKey: c.SecretAccessKey, SessionToken: c.SessionToken}, nil
}
