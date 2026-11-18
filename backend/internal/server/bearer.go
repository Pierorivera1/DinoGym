package server

import (
	"fmt"
	"net/http"
	"slices"
	"strings"

	"github.com/getkin/kin-openapi/openapi3"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
)

// bearerRouter is the router the generated code registers its routes on. It
// wraps every route whose operation the contract secures with bearerAuth in a
// check of the access token, so the check runs before query parsing, body
// decoding and the handler. Which routes need it comes from the contract
// itself (protectedRoutes), not from a list kept by hand.
type bearerRouter struct {
	*http.ServeMux
	protected map[string]bool // "POST /sync/push" → true
	verify    func(token string) (userID string, err error)
	wrapped   map[string]bool
}

func (b *bearerRouter) HandleFunc(pattern string, h func(http.ResponseWriter, *http.Request)) {
	if !b.protected[pattern] {
		b.ServeMux.HandleFunc(pattern, h)
		return
	}
	if b.wrapped == nil {
		b.wrapped = map[string]bool{}
	}
	b.wrapped[pattern] = true
	b.ServeMux.HandleFunc(pattern, func(w http.ResponseWriter, r *http.Request) {
		userID, err := b.verify(bearerToken(r))
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid_token", "the token is missing, invalid or expired")
			return
		}
		// The sync handlers read it with auth.UserID(ctx).
		h(w, r.WithContext(auth.WithUserID(r.Context(), userID)))
	})
}

// mustCoverAll panics if a secured operation in the contract was never
// registered under the pattern expected, which would leave it open.
func (b *bearerRouter) mustCoverAll() {
	for p := range b.protected {
		if !b.wrapped[p] {
			panic(fmt.Sprintf("bearer check: secured route %q was not registered", p))
		}
	}
}

func bearerToken(r *http.Request) string {
	scheme, token, ok := strings.Cut(r.Header.Get("Authorization"), " ")
	if !ok || !strings.EqualFold(scheme, "Bearer") {
		return ""
	}
	return strings.TrimSpace(token)
}

// protectedRoutes reads the embedded contract and returns the ServeMux pattern
// ("METHOD /path") of every operation that requires bearerAuth: its own
// security list, or the document's when it has none.
func protectedRoutes() map[string]bool {
	spec, err := api.GetSwagger()
	if err != nil {
		panic(fmt.Sprintf("embedded contract: %v", err))
	}
	out := map[string]bool{}
	for path, item := range spec.Paths.Map() {
		for method, op := range item.Operations() {
			sec := spec.Security
			if op.Security != nil {
				sec = *op.Security
			}
			if slices.ContainsFunc(sec, func(req openapi3.SecurityRequirement) bool { _, ok := req["bearerAuth"]; return ok }) {
				out[strings.ToUpper(method)+" "+path] = true
			}
		}
	}
	return out
}
