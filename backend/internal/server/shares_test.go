package server

import "testing"

func TestShares(t *testing.T) {
	runSharesSuite(t, func(*testing.T) Store { return newFakeStore() })
}
