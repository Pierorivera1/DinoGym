package auth

import "time"

// SetNow moves the clock of s and its Tokens, for tests.
func SetNow(s *Service, now func() time.Time) {
	s.now = now
	s.tokens.now = now
}

// Hash exposes the refresh token hash to tests.
var Hash = hashRefreshToken
