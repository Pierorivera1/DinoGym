// Package server implements the API contract (api/openapi.yaml) on top of the
// interfaces generated in internal/api.
package server

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// MaxBodyBytes caps a request body. A full push of 500 rows is well under it.
const MaxBodyBytes = 1 << 20

// Server answers the API's operations.
type Server struct {
	log   *slog.Logger
	store SyncStore
	auth  *auth.Service
}

var (
	_ api.StrictServerInterface = (*Server)(nil)
	_ SyncStore                 = (*store.Store)(nil)
)

// New returns a Server that logs to log, keeps synced rows in st and signs
// users in with a.
func New(log *slog.Logger, st SyncStore, a *auth.Service) *Server {
	return &Server{log: log, store: st, auth: a}
}

func (s *Server) GetHealth(context.Context, api.GetHealthRequestObject) (api.GetHealthResponseObject, error) {
	return api.GetHealth200JSONResponse{Status: api.Ok}, nil
}

func (s *Server) SignInWithGoogle(ctx context.Context, req api.SignInWithGoogleRequestObject) (api.SignInWithGoogleResponseObject, error) {
	if req.Body.IdToken == "" {
		return api.SignInWithGoogle400JSONResponse{BadRequestJSONResponse: badRequest("idToken is required")}, nil
	}
	sess, err := s.auth.SignInWithGoogle(ctx, req.Body.IdToken)
	if errors.Is(err, auth.ErrInvalidToken) {
		return api.SignInWithGoogle401JSONResponse{UnauthorizedJSONResponse: invalidToken()}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("sign in: %w", err)
	}
	return api.SignInWithGoogle200JSONResponse(toSession(sess)), nil
}

func (s *Server) RefreshSession(ctx context.Context, req api.RefreshSessionRequestObject) (api.RefreshSessionResponseObject, error) {
	if req.Body.RefreshToken == "" {
		return api.RefreshSession400JSONResponse{BadRequestJSONResponse: badRequest("refreshToken is required")}, nil
	}
	sess, err := s.auth.Refresh(ctx, req.Body.RefreshToken)
	if errors.Is(err, auth.ErrInvalidToken) {
		return api.RefreshSession401JSONResponse{UnauthorizedJSONResponse: invalidToken()}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("refresh session: %w", err)
	}
	return api.RefreshSession200JSONResponse(toSession(sess)), nil
}

func toSession(s auth.Session) api.Session {
	return api.Session{
		UserId:               s.UserID,
		AccessToken:          s.AccessToken,
		AccessTokenExpiresAt: s.AccessTokenExpiresAt.UnixMilli(),
		RefreshToken:         s.RefreshToken,
	}
}

func badRequest(msg string) api.BadRequestJSONResponse {
	return api.BadRequestJSONResponse{Code: "bad_request", Message: msg}
}

func invalidToken() api.UnauthorizedJSONResponse {
	return api.UnauthorizedJSONResponse{Code: "invalid_token", Message: "the token is missing, invalid or expired"}
}

// Handler returns the API as an http.Handler: routing and request parsing from
// the generated code, the bearer check and body validation from the contract,
// errors in the contract's Error shape, a body size cap, and an OpenTelemetry
// span and metrics for every request.
func (s *Server) Handler() http.Handler {
	strict := api.NewStrictHandlerWithOptions(s, nil, api.StrictHTTPServerOptions{
		RequestErrorHandlerFunc: func(w http.ResponseWriter, r *http.Request, err error) {
			var tooBig *http.MaxBytesError
			if errors.As(err, &tooBig) {
				writeError(w, http.StatusRequestEntityTooLarge, "body_too_large", "request body is over 1 MiB")
				return
			}
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		},
		ResponseErrorHandlerFunc: s.responseError,
	})

	mux := http.NewServeMux()
	router := &bearerRouter{ServeMux: mux, protected: protectedRoutes(), verify: s.auth.VerifyAccess}
	h := api.HandlerWithOptions(strict, api.StdHTTPServerOptions{
		BaseRouter: &validatingRouter{next: router, schemas: requestBodySchemas()},
		ErrorHandlerFunc: func(w http.ResponseWriter, r *http.Request, err error) {
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		},
	})
	router.mustCoverAll()
	// Paths the contract does not have get a JSON 404 instead of Go's text one.
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		writeError(w, http.StatusNotFound, "not_found", "no such path")
	})

	limited := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, MaxBodyBytes)
		h.ServeHTTP(w, r)
	})
	// Span names use the matched route ("GET /sync/pull"), never the raw URL,
	// so ids in paths or query strings do not create one name per request.
	return otelhttp.NewHandler(limited, "http.request",
		otelhttp.WithSpanNameFormatter(func(_ string, r *http.Request) string {
			if r.Pattern != "" {
				return r.Pattern
			}
			return r.Method + " unmatched"
		}),
	)
}

func (s *Server) responseError(w http.ResponseWriter, r *http.Request, err error) {
	s.log.ErrorContext(r.Context(), "request failed", "method", r.Method, "path", r.URL.Path, "error", err)
	writeError(w, http.StatusInternalServerError, "internal", "internal error")
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(api.Error{Code: code, Message: message})
}
