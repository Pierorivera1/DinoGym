package auth

import (
	"context"
	"fmt"
	"sync"

	"github.com/coreos/go-oidc/v3/oidc"
)

// GoogleIssuer is the issuer of Google ID tokens. go-oidc also accepts the
// scheme-less "accounts.google.com" that some Google tokens carry.
const GoogleIssuer = "https://accounts.google.com"

// GoogleVerifier checks a Google ID token and returns its sub claim. The
// interface lets handler tests run without Google.
type GoogleVerifier interface {
	VerifyGoogle(ctx context.Context, idToken string) (sub string, err error)
}

// Google verifies ID tokens with go-oidc: signature against Google's published
// keys (cached by the library), issuer, audience (the Web OAuth client ID) and
// expiry (decision 80).
type Google struct {
	clientID string

	mu       sync.Mutex
	verifier *oidc.IDTokenVerifier
}

// NewGoogle returns a verifier for tokens issued to clientID. Nothing is
// fetched until the first sign-in.
func NewGoogle(clientID string) *Google {
	return &Google{clientID: clientID}
}

// VerifyGoogle returns the token's sub. A token that fails any check gives
// ErrInvalidToken. Failing to fetch the discovery document gives another error
// (a 500, which the app retries); go-oidc does not tell a failed key fetch
// apart from a bad signature, so that case is ErrInvalidToken too.
func (g *Google) VerifyGoogle(ctx context.Context, idToken string) (string, error) {
	v, err := g.get(ctx)
	if err != nil {
		return "", err
	}
	tok, err := v.Verify(ctx, idToken)
	if err != nil {
		return "", ErrInvalidToken
	}
	if tok.Subject == "" {
		return "", ErrInvalidToken
	}
	return tok.Subject, nil
}

// get reads Google's discovery document once per process (the first sign-in
// after a cold start). A failure is not cached, so the next request retries.
func (g *Google) get(ctx context.Context) (*oidc.IDTokenVerifier, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.verifier != nil {
		return g.verifier, nil
	}
	if g.clientID == "" {
		return nil, fmt.Errorf("no Google client ID configured (DINOGYM_GOOGLE_CLIENT_ID)")
	}
	// The request's context bounds only this fetch: the verifier fetches and
	// caches Google's keys with a background context of its own.
	p, err := oidc.NewProvider(ctx, GoogleIssuer)
	if err != nil {
		return nil, fmt.Errorf("google discovery: %w", err)
	}
	g.verifier = p.Verifier(&oidc.Config{ClientID: g.clientID})
	return g.verifier, nil
}
