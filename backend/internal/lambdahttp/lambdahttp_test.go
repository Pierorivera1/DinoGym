package lambdahttp

import (
	"context"
	"encoding/base64"
	"io"
	"net/http"
	"testing"

	"github.com/aws/aws-lambda-go/events"
)

func event(method, path, query, body string) events.APIGatewayV2HTTPRequest {
	e := events.APIGatewayV2HTTPRequest{
		RawPath:        path,
		RawQueryString: query,
		Body:           body,
		Headers:        map[string]string{"content-type": "application/json", "x-multi": "a,b"},
		Cookies:        []string{"a=1", "b=2"},
	}
	e.RequestContext.HTTP.Method = method
	e.RequestContext.HTTP.SourceIP = "203.0.113.7"
	e.RequestContext.DomainName = "abc.execute-api.us-east-1.amazonaws.com"
	return e
}

func TestRequestReachesTheHandlerIntact(t *testing.T) {
	var got *http.Request
	var gotBody string
	h := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
	})

	e := event(http.MethodPost, "/sync/push", "x=1&y=2", base64.StdEncoding.EncodeToString([]byte(`{"a":1}`)))
	e.IsBase64Encoded = true
	if _, err := Handler(h, nil)(context.Background(), e); err != nil {
		t.Fatal(err)
	}

	checks := map[string][2]string{
		"method": {got.Method, http.MethodPost},
		"path":   {got.URL.Path, "/sync/push"},
		"query":  {got.URL.Query().Get("y"), "2"},
		"header": {got.Header.Get("Content-Type"), "application/json"},
		"multi":  {got.Header.Get("X-Multi"), "a,b"},
		"cookie": {got.Header.Get("Cookie"), "a=1; b=2"},
		"host":   {got.Host, "abc.execute-api.us-east-1.amazonaws.com"},
		"remote": {got.RemoteAddr, "203.0.113.7"},
		"body":   {gotBody, `{"a":1}`},
	}
	for name, c := range checks {
		if c[0] != c[1] {
			t.Errorf("%s = %q, want %q", name, c[0], c[1])
		}
	}
}

func TestResponseBecomesTheEvent(t *testing.T) {
	h := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Add("Set-Cookie", "s=1")
		w.Header().Add("Set-Cookie", "t=2")
		w.WriteHeader(http.StatusTeapot)
		_, _ = w.Write([]byte(`{"ok":true}`))
	})

	flushed := false
	out, err := Handler(h, func(context.Context) { flushed = true })(context.Background(), event(http.MethodGet, "/", "", ""))
	if err != nil {
		t.Fatal(err)
	}
	if out.StatusCode != http.StatusTeapot || out.Body != `{"ok":true}` || out.IsBase64Encoded {
		t.Fatalf("got %+v", out)
	}
	if out.Headers["Content-Type"] != "application/json" {
		t.Fatalf("headers %v", out.Headers)
	}
	if len(out.Cookies) != 2 || out.Cookies[1] != "t=2" {
		t.Fatalf("cookies %v", out.Cookies)
	}
	if !flushed {
		t.Fatal("after did not run")
	}
}

func TestBinaryBodyIsBase64AndEmptyIs200(t *testing.T) {
	h := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/bin" {
			_, _ = w.Write([]byte{0xff, 0xfe})
		}
	})
	run := Handler(h, nil)

	out, _ := run(context.Background(), event(http.MethodGet, "/bin", "", ""))
	if !out.IsBase64Encoded || out.Body != base64.StdEncoding.EncodeToString([]byte{0xff, 0xfe}) {
		t.Fatalf("binary body: %+v", out)
	}
	out, _ = run(context.Background(), event(http.MethodGet, "/empty", "", ""))
	if out.StatusCode != http.StatusOK || out.Body != "" {
		t.Fatalf("empty response: %+v", out)
	}
}

func TestBadBase64IsAnError(t *testing.T) {
	e := event(http.MethodPost, "/", "", "not base64!")
	e.IsBase64Encoded = true
	if _, err := Handler(http.NotFoundHandler(), nil)(context.Background(), e); err == nil {
		t.Fatal("want an error")
	}
}
