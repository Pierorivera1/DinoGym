// Package server implements the API contract (api/openapi.yaml) on top of the
// interfaces generated in internal/api.
package server

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
)

// MaxBodyBytes caps a request body. A full push of 500 rows is well under it.
const MaxBodyBytes = 1 << 20

// ErrNotImplemented marks an endpoint whose task has not been built yet
// (sign-in in task 20, sync in task 21). It is answered with 501.
var ErrNotImplemented = errors.New("not implemented")

// Server answers the API's operations.
type Server struct {
	log *slog.Logger
}

var _ api.StrictServerInterface = (*Server)(nil)

// New returns a Server that logs to log.
func New(log *slog.Logger) *Server {
	return &Server{log: log}
}

func (s *Server) GetHealth(context.Context, api.GetHealthRequestObject) (api.GetHealthResponseObject, error) {
	return api.GetHealth200JSONResponse{Status: api.Ok}, nil
}

func (s *Server) SignInWithGoogle(context.Context, api.SignInWithGoogleRequestObject) (api.SignInWithGoogleResponseObject, error) {
	return nil, ErrNotImplemented
}

func (s *Server) RefreshSession(context.Context, api.RefreshSessionRequestObject) (api.RefreshSessionResponseObject, error) {
	return nil, ErrNotImplemented
}

func (s *Server) PushChanges(context.Context, api.PushChangesRequestObject) (api.PushChangesResponseObject, error) {
	return nil, ErrNotImplemented
}

func (s *Server) PullChanges(context.Context, api.PullChangesRequestObject) (api.PullChangesResponseObject, error) {
	return nil, ErrNotImplemented
}

// Handler returns the API as an http.Handler: routing and request parsing from
// the generated code, errors in the contract's Error shape, a body size cap, and
// an OpenTelemetry span and metrics for every request.
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
	h := api.HandlerWithOptions(strict, api.StdHTTPServerOptions{
		BaseRouter: mux,
		ErrorHandlerFunc: func(w http.ResponseWriter, r *http.Request, err error) {
			writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		},
	})
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
	if errors.Is(err, ErrNotImplemented) {
		writeError(w, http.StatusNotImplemented, "not_implemented", "this endpoint is not built yet")
		return
	}
	s.log.ErrorContext(r.Context(), "request failed", "method", r.Method, "path", r.URL.Path, "error", err)
	writeError(w, http.StatusInternalServerError, "internal", "internal error")
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(api.Error{Code: code, Message: message})
}
