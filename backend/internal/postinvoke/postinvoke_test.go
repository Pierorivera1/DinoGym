package postinvoke

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// fakeAPI is the Extensions API: register, then a /next that hands out an
// INVOKE each time the test sends on invoke.
type fakeAPI struct {
	invoke chan string // event bodies to return from /next
	mu     sync.Mutex
	log    []string // what happened, in order
}

func (f *fakeAPI) record(s string) {
	f.mu.Lock()
	f.log = append(f.log, s)
	f.mu.Unlock()
}

func (f *fakeAPI) events() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.log...)
}

func (f *fakeAPI) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/2020-01-01/extension/register":
		body, _ := io.ReadAll(r.Body)
		if r.Header.Get("Lambda-Extension-Name") != Name || strings.Contains(string(body), "SHUTDOWN") {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		w.Header().Set("Lambda-Extension-Identifier", "ext-1")
		f.record("register")
	case "/2020-01-01/extension/event/next":
		if r.Header.Get("Lambda-Extension-Identifier") != "ext-1" {
			w.WriteHeader(http.StatusForbidden)
			return
		}
		f.record("next")
		select {
		case ev, ok := <-f.invoke:
			if !ok {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			io.WriteString(w, ev)
		case <-r.Context().Done():
		}
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

func start(t *testing.T, work func(context.Context)) (*fakeAPI, *Runner) {
	t.Helper()
	f := &fakeAPI{invoke: make(chan string)}
	srv := httptest.NewServer(f)
	t.Cleanup(srv.Close)
	r, err := Start(context.Background(), srv.Client(), strings.TrimPrefix(srv.URL, "http://"), time.Second, work)
	if err != nil {
		t.Fatal(err)
	}
	return f, r
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	for i := 0; i < 200; i++ {
		if cond() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatal("timed out")
}

// The work runs only after the handler is done, and the extension asks for
// the next event only after the work: that is what keeps Lambda from freezing
// the process mid-flush while the client already has its response.
func TestWorkRunsAfterDoneAndBeforeNext(t *testing.T) {
	var f *fakeAPI
	worked := make(chan time.Duration, 2)
	f, r := start(t, func(ctx context.Context) {
		f.record("work")
		dl, _ := ctx.Deadline()
		worked <- time.Until(dl)
	})
	waitFor(t, func() bool { return len(f.events()) == 2 }) // register, next

	f.invoke <- `{"eventType":"INVOKE","deadlineMs":` + itoa(time.Now().Add(10*time.Second).UnixMilli()) + `,"requestId":"r1"}`
	time.Sleep(20 * time.Millisecond)
	if got := f.events(); len(got) != 2 {
		t.Fatalf("work ran before Done: %v", got)
	}
	r.Done(context.Background())
	if left := <-worked; left <= 0 || left > time.Second {
		t.Fatalf("work deadline %v from now, want within the 1s limit", left)
	}
	waitFor(t, func() bool { return len(f.events()) == 4 })
	want := []string{"register", "next", "work", "next"}
	for i, e := range f.events() {
		if e != want[i] {
			t.Fatalf("got %v, want %v", f.events(), want)
		}
	}

	// A second invocation whose deadline is closer than the limit.
	f.invoke <- `{"eventType":"INVOKE","deadlineMs":` + itoa(time.Now().Add(200*time.Millisecond).UnixMilli()) + `,"requestId":"r2"}`
	r.Done(context.Background())
	if left := <-worked; left > 250*time.Millisecond {
		t.Fatalf("work deadline %v from now, want the invocation's", left)
	}
}

func TestDoneRunsWorkInlineOnceTheLoopStops(t *testing.T) {
	ran := make(chan struct{}, 1)
	f, r := start(t, func(context.Context) { ran <- struct{}{} })
	waitFor(t, func() bool { return len(f.events()) == 2 })
	close(f.invoke) // /next fails: the loop ends
	waitFor(t, func() bool { return !r.running.Load() })
	r.Done(context.Background())
	select {
	case <-ran:
	default:
		t.Fatal("work did not run inline")
	}
}

func TestRegisterFailureIsAnError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()
	if _, err := Start(context.Background(), srv.Client(), strings.TrimPrefix(srv.URL, "http://"), time.Second, func(context.Context) {}); err == nil {
		t.Fatal("want an error")
	}
}

func itoa(n int64) string { return strconv.FormatInt(n, 10) }
