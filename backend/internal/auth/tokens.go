// Package auth signs users in with Google and keeps their DinoGym sessions
// (decisions 10, 79 and 80):
//
//   - An access token is a JWT (HS256) that lives 15 minutes and is checked
//     without any database read.
//   - A refresh token is 32 random bytes. Only its SHA-256 hash is stored, it
//     works once, and presenting a used one revokes its whole family.
//   - A Google ID token is checked against Google's keys, issuer and the Web
//     OAuth client ID, and its sub is mapped to a DinoGym user id.
package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	// AccessTokenTTL is how long an access token works.
	AccessTokenTTL = 15 * time.Minute
	// RefreshTokenTTL is how long a refresh token works if it is not used.
	RefreshTokenTTL = 60 * 24 * time.Hour
	// MinKeyBytes is the shortest signing key accepted: HS256 needs at least
	// as many key bytes as the hash has.
	MinKeyBytes = 32
	// Audience is the aud claim of every access token.
	Audience = "dinogym-api"
)

// ErrInvalidToken is returned for any token that must not be accepted:
// malformed, badly signed, expired, unknown, used or revoked. The API answers
// it with 401 invalid_token and never says which case it was.
var ErrInvalidToken = errors.New("invalid token")

// Tokens issues and checks access tokens.
type Tokens struct {
	key    []byte
	issuer string
	now    func() time.Time
	parser *jwt.Parser
}

// NewTokens returns Tokens signing with key for environment env (local,
// staging or prod). Keys shorter than MinKeyBytes are refused.
func NewTokens(key []byte, env string) (*Tokens, error) {
	if len(key) < MinKeyBytes {
		return nil, fmt.Errorf("session signing key is %d bytes, want at least %d", len(key), MinKeyBytes)
	}
	t := &Tokens{key: key, issuer: "dinogym-" + env, now: time.Now}
	t.parser = jwt.NewParser(
		// Only HS256: a token claiming "none" or an RSA algorithm is refused
		// before its signature is looked at.
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(t.issuer),
		jwt.WithAudience(Audience),
		jwt.WithExpirationRequired(),
		jwt.WithIssuedAt(),
		jwt.WithTimeFunc(func() time.Time { return t.now() }),
	)
	return t, nil
}

// Issue returns a signed access token for userID and when it expires.
func (t *Tokens) Issue(userID string) (string, time.Time, error) {
	now := t.now()
	exp := now.Add(AccessTokenTTL)
	tok := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.RegisteredClaims{
		Issuer:    t.issuer,
		Audience:  jwt.ClaimStrings{Audience},
		Subject:   userID,
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(exp),
	})
	s, err := tok.SignedString(t.key)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("sign access token: %w", err)
	}
	return s, exp, nil
}

// Verify checks an access token and returns its user id. Every failure is
// ErrInvalidToken.
func (t *Tokens) Verify(token string) (string, error) {
	var claims jwt.RegisteredClaims
	_, err := t.parser.ParseWithClaims(token, &claims, func(*jwt.Token) (any, error) { return t.key, nil })
	if err != nil || claims.Subject == "" {
		return "", ErrInvalidToken
	}
	return claims.Subject, nil
}

// newRefreshToken returns a new refresh token and the hash that is stored.
func newRefreshToken() (raw, hash string, err error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", "", fmt.Errorf("random refresh token: %w", err)
	}
	raw = base64.RawURLEncoding.EncodeToString(b)
	return raw, hashRefreshToken(raw), nil
}

// hashRefreshToken is what the table keys a refresh token by. A plain SHA-256
// is enough: the token is 256 random bits, so there is nothing to brute force.
func hashRefreshToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}
