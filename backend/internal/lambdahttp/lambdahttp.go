// Package lambdahttp runs an http.Handler on AWS Lambda behind an API Gateway
// HTTP API (payload format 2.0), so the same handler serves the laptop and
// Lambda (decision 63).
package lambdahttp

import (
	"bytes"
	"context"
	"encoding/base64"
	"fmt"
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/aws/aws-lambda-go/events"
)

// Handler turns h into a Lambda handler. after, if not nil, runs once the
// response is ready and before it is returned: either the telemetry flush
// itself, or the signal that lets postinvoke flush once the response is sent.
func Handler(h http.Handler, after func(context.Context)) func(context.Context, events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	return func(ctx context.Context, e events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
		if after != nil {
			defer after(ctx)
		}
		r, err := toRequest(ctx, e)
		if err != nil {
			// API Gateway answers 502 for a handler error, which is right for an
			// event it should never have sent.
			return events.APIGatewayV2HTTPResponse{}, err
		}
		w := newResponse()
		h.ServeHTTP(w, r)
		return w.toEvent(), nil
	}
}

func toRequest(ctx context.Context, e events.APIGatewayV2HTTPRequest) (*http.Request, error) {
	body := []byte(e.Body)
	if e.IsBase64Encoded {
		var err error
		if body, err = base64.StdEncoding.DecodeString(e.Body); err != nil {
			return nil, fmt.Errorf("decode base64 body: %w", err)
		}
	}

	url := e.RawPath
	if url == "" {
		url = "/"
	}
	if e.RawQueryString != "" {
		url += "?" + e.RawQueryString
	}
	r, err := http.NewRequestWithContext(ctx, e.RequestContext.HTTP.Method, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("build request: %w", err)
	}

	// Payload 2.0 joins repeated headers with commas, which HTTP allows for
	// every header except Cookie; cookies arrive in their own list.
	for k, v := range e.Headers {
		r.Header.Set(k, v)
	}
	if len(e.Cookies) > 0 {
		r.Header.Set("Cookie", strings.Join(e.Cookies, "; "))
	}
	r.Host = e.RequestContext.DomainName
	r.RemoteAddr = e.RequestContext.HTTP.SourceIP
	r.RequestURI = url
	return r, nil
}

// response collects what the handler writes.
type response struct {
	header http.Header
	body   bytes.Buffer
	status int
}

func newResponse() *response { return &response{header: http.Header{}} }

func (w *response) Header() http.Header { return w.header }

func (w *response) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	return w.body.Write(b)
}

func (w *response) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
	}
}

func (w *response) toEvent() events.APIGatewayV2HTTPResponse {
	out := events.APIGatewayV2HTTPResponse{
		StatusCode: w.status,
		Headers:    map[string]string{},
		Cookies:    w.header.Values("Set-Cookie"),
	}
	if out.StatusCode == 0 {
		out.StatusCode = http.StatusOK
	}
	for k, vs := range w.header {
		if k != "Set-Cookie" {
			out.Headers[k] = strings.Join(vs, ",")
		}
	}
	// JSON and other text pass through as is; anything else is base64 so API
	// Gateway returns the exact bytes.
	if b := w.body.Bytes(); utf8.Valid(b) {
		out.Body = string(b)
	} else {
		out.Body = base64.StdEncoding.EncodeToString(b)
		out.IsBase64Encoded = true
	}
	return out
}
