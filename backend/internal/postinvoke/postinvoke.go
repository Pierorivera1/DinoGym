// Package postinvoke runs work after a Lambda invocation's response has gone
// back to the caller (decision 119).
//
// It registers the process as an internal Lambda extension. Lambda returns the
// function's response to the client as soon as the runtime posts it, but keeps
// the execution environment running until every extension asks for its next
// event. So the extension's loop waits for the handler to finish, runs the
// work (the telemetry flush), and only then asks for the next event. The
// client no longer waits for the flush; the billed duration still includes it.
package postinvoke

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
)

// Name identifies the extension to Lambda.
const Name = "dinogym-postinvoke"

// Runner hands finished invocations to the extension loop.
type Runner struct {
	work    func(context.Context)
	limit   time.Duration
	done    chan struct{}
	running atomic.Bool
}

// Start registers with the Extensions API at runtimeAPI (AWS_LAMBDA_RUNTIME_API)
// and starts the loop. It must run during init, before lambda.Start. work gets
// at most limit, and never more than the invocation has left.
func Start(ctx context.Context, client *http.Client, runtimeAPI string, limit time.Duration, work func(context.Context)) (*Runner, error) {
	base := "http://" + runtimeAPI + "/2020-01-01/extension"
	// Internal extensions may register only for INVOKE, not SHUTDOWN.
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base+"/register", strings.NewReader(`{"events":["INVOKE"]}`))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Lambda-Extension-Name", Name)
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("register extension: %w", err)
	}
	resp.Body.Close()
	id := resp.Header.Get("Lambda-Extension-Identifier")
	if resp.StatusCode != http.StatusOK || id == "" {
		return nil, fmt.Errorf("register extension: status %d", resp.StatusCode)
	}

	r := &Runner{work: work, limit: limit, done: make(chan struct{}, 1)}
	r.running.Store(true)
	go r.loop(client, base+"/event/next", id)
	return r, nil
}

// Done tells the loop that the handler has finished. Call it once per
// invocation, before the handler returns. If the loop has stopped, the work
// runs here instead, so nothing is left unsent.
func (r *Runner) Done(ctx context.Context) {
	if !r.running.Load() {
		ctx, cancel := context.WithTimeout(ctx, r.limit)
		defer cancel()
		r.work(ctx)
		return
	}
	select {
	case r.done <- struct{}{}:
	default: // already signalled for this invocation
	}
}

type event struct {
	EventType  string `json:"eventType"`
	DeadlineMs int64  `json:"deadlineMs"`
}

func (r *Runner) loop(client *http.Client, next, id string) {
	defer r.running.Store(false)
	for {
		// No timeout: the call blocks while the environment is frozen.
		req, err := http.NewRequest(http.MethodGet, next, nil)
		if err != nil {
			return
		}
		req.Header.Set("Lambda-Extension-Identifier", id)
		resp, err := client.Do(req)
		if err != nil {
			return
		}
		var ev event
		err = json.NewDecoder(resp.Body).Decode(&ev)
		resp.Body.Close()
		if err != nil || resp.StatusCode != http.StatusOK || ev.EventType != "INVOKE" {
			return
		}

		<-r.done
		deadline := time.Now().Add(r.limit)
		if ev.DeadlineMs > 0 {
			deadline = earliest(deadline, time.UnixMilli(ev.DeadlineMs))
		}
		ctx, cancel := context.WithDeadline(context.Background(), deadline)
		r.work(ctx)
		cancel()
	}
}

func earliest(a, b time.Time) time.Time {
	if a.Before(b) {
		return a
	}
	return b
}
