package auth

import (
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

var testKey = []byte(strings.Repeat("k", MinKeyBytes))

func newTestTokens(t *testing.T, env string) *Tokens {
	t.Helper()
	tok, err := NewTokens(testKey, env)
	if err != nil {
		t.Fatal(err)
	}
	return tok
}

func TestAccessTokenRoundTrip(t *testing.T) {
	tok := newTestTokens(t, "staging")
	start := time.Unix(1_800_000_000, 0)
	tok.now = func() time.Time { return start }

	s, exp, err := tok.Issue("user-1")
	if err != nil {
		t.Fatal(err)
	}
	if !exp.Equal(start.Add(AccessTokenTTL)) {
		t.Fatalf("expires %v, want 15 minutes after %v", exp, start)
	}
	if id, err := tok.Verify(s); err != nil || id != "user-1" {
		t.Fatalf("Verify = %q, %v", id, err)
	}

	tok.now = func() time.Time { return start.Add(AccessTokenTTL + time.Second) }
	if _, err := tok.Verify(s); !errors.Is(err, ErrInvalidToken) {
		t.Fatalf("expired token: got %v", err)
	}
}

func TestShortKeyIsRefused(t *testing.T) {
	if _, err := NewTokens(make([]byte, MinKeyBytes-1), "prod"); err == nil {
		t.Fatal("accepted a 31-byte key")
	}
}

func TestVerifyRefuses(t *testing.T) {
	tok := newTestTokens(t, "prod")
	now := time.Now()
	claims := func() jwt.RegisteredClaims {
		return jwt.RegisteredClaims{
			Issuer:    "dinogym-prod",
			Audience:  jwt.ClaimStrings{Audience},
			Subject:   "user-1",
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(time.Minute)),
		}
	}
	sign := func(m jwt.SigningMethod, key any, c jwt.RegisteredClaims) string {
		s, err := jwt.NewWithClaims(m, c).SignedString(key)
		if err != nil {
			t.Fatal(err)
		}
		return s
	}

	if id, err := tok.Verify(sign(jwt.SigningMethodHS256, testKey, claims())); err != nil || id != "user-1" {
		t.Fatalf("a well-formed token was refused: %v", err)
	}

	wrongAud, wrongIss, noExp, noSub, future := claims(), claims(), claims(), claims(), claims()
	wrongAud.Audience = jwt.ClaimStrings{"someone-else"}
	wrongIss.Issuer = "dinogym-staging"
	noExp.ExpiresAt = nil
	noSub.Subject = ""
	future.IssuedAt = jwt.NewNumericDate(now.Add(time.Hour))
	future.ExpiresAt = jwt.NewNumericDate(now.Add(2 * time.Hour))

	fromStaging, _, err := newTestTokens(t, "staging").Issue("user-1")
	if err != nil {
		t.Fatal(err)
	}

	cases := map[string]string{
		"empty":             "",
		"garbage":           "not.a.jwt",
		"HS512":             sign(jwt.SigningMethodHS512, testKey, claims()),
		"alg none":          sign(jwt.SigningMethodNone, jwt.UnsafeAllowNoneSignatureType, claims()),
		"other key":         sign(jwt.SigningMethodHS256, []byte(strings.Repeat("x", 32)), claims()),
		"wrong audience":    sign(jwt.SigningMethodHS256, testKey, wrongAud),
		"wrong issuer":      sign(jwt.SigningMethodHS256, testKey, wrongIss),
		"other environment": fromStaging,
		"no expiry":         sign(jwt.SigningMethodHS256, testKey, noExp),
		"no subject":        sign(jwt.SigningMethodHS256, testKey, noSub),
		"issued in future":  sign(jwt.SigningMethodHS256, testKey, future),
	}
	for name, s := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := tok.Verify(s); !errors.Is(err, ErrInvalidToken) {
				t.Fatalf("accepted, err %v", err)
			}
		})
	}
}

func TestRefreshTokensAreRandomAndHashed(t *testing.T) {
	a, ha, err := newRefreshToken()
	if err != nil {
		t.Fatal(err)
	}
	b, _, err := newRefreshToken()
	if err != nil {
		t.Fatal(err)
	}
	if a == b || len(a) != 43 {
		t.Fatalf("tokens %q %q: want distinct 43-character base64url strings", a, b)
	}
	if ha == a || ha != hashRefreshToken(a) || len(ha) != 64 {
		t.Fatalf("hash %q of %q", ha, a)
	}
}
