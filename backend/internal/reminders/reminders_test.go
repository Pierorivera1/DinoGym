package reminders

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/service/scheduler"
	"github.com/aws/aws-sdk-go-v2/service/scheduler/types"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/codes"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	"go.opentelemetry.io/otel/trace"
	"golang.org/x/oauth2"

	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

var quiet = slog.New(slog.NewTextHandler(io.Discard, nil))

const user = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f"

func TestParseSettings(t *testing.T) {
	on := map[string]string{KeyWorkoutDay: "true", KeyWeekly: "true", KeyTime: "07:05", KeyTimeZone: "America/Lima"}
	s, err := ParseSettings(on)
	if err != nil || !s.WorkoutDay || !s.Weekly {
		t.Fatalf("got %+v, %v", s, err)
	}
	if got := s.cron(WorkoutDay); got != "cron(5 7 * * ? *)" {
		t.Errorf("workout day cron %q", got)
	}
	if got := s.cron(WeeklySummary); got != "cron(0 20 ? * SUN *)" {
		t.Errorf("weekly cron %q", got)
	}

	if s, err := ParseSettings(map[string]string{}); err != nil || s.On(WorkoutDay) || s.On(WeeklySummary) {
		t.Errorf("no rows: %+v, %v; want everything off", s, err)
	}
	if s, _ := ParseSettings(map[string]string{KeyWorkoutDay: "true", KeyTimeZone: "UTC"}); s.Time != DefaultTime {
		t.Errorf("missing time: %q, want %q", s.Time, DefaultTime)
	}
	for name, prefs := range map[string]map[string]string{
		"bad time":     {KeyWorkoutDay: "true", KeyTime: "24:00", KeyTimeZone: "UTC"},
		"time no zero": {KeyWorkoutDay: "true", KeyTime: "7:00", KeyTimeZone: "UTC"},
		"no zone":      {KeyWeekly: "true"},
		"unknown zone": {KeyWeekly: "true", KeyTimeZone: "Mars/Olympus"},
	} {
		s, err := ParseSettings(prefs)
		if err == nil || s.On(WorkoutDay) || s.On(WeeklySummary) {
			t.Errorf("%s: %+v, %v; want an error and everything off", name, s, err)
		}
	}
}

// fakeScheduler keeps schedules by group/name, with Scheduler's not-found errors.
type fakeScheduler struct {
	mu        sync.Mutex
	schedules map[string]*scheduler.CreateScheduleInput
	creates   int
	failWith  error
}

func newFakeScheduler() *fakeScheduler {
	return &fakeScheduler{schedules: map[string]*scheduler.CreateScheduleInput{}}
}

func (f *fakeScheduler) CreateSchedule(_ context.Context, in *scheduler.CreateScheduleInput, _ ...func(*scheduler.Options)) (*scheduler.CreateScheduleOutput, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failWith != nil {
		return nil, f.failWith
	}
	k := *in.GroupName + "/" + *in.Name
	if _, ok := f.schedules[k]; ok {
		return nil, &types.ConflictException{}
	}
	f.creates++
	f.schedules[k] = in
	return &scheduler.CreateScheduleOutput{}, nil
}

func (f *fakeScheduler) UpdateSchedule(_ context.Context, in *scheduler.UpdateScheduleInput, _ ...func(*scheduler.Options)) (*scheduler.UpdateScheduleOutput, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failWith != nil {
		return nil, f.failWith
	}
	k := *in.GroupName + "/" + *in.Name
	if _, ok := f.schedules[k]; !ok {
		return nil, &types.ResourceNotFoundException{}
	}
	f.schedules[k] = &scheduler.CreateScheduleInput{
		Name: in.Name, GroupName: in.GroupName, ScheduleExpression: in.ScheduleExpression,
		ScheduleExpressionTimezone: in.ScheduleExpressionTimezone, Target: in.Target,
	}
	return &scheduler.UpdateScheduleOutput{}, nil
}

func (f *fakeScheduler) DeleteSchedule(_ context.Context, in *scheduler.DeleteScheduleInput, _ ...func(*scheduler.Options)) (*scheduler.DeleteScheduleOutput, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	k := *in.GroupName + "/" + *in.Name
	if _, ok := f.schedules[k]; !ok {
		return nil, &types.ResourceNotFoundException{}
	}
	delete(f.schedules, k)
	return &scheduler.DeleteScheduleOutput{}, nil
}

type mapSettings map[string]string

func (m mapSettings) ReminderSettings(context.Context, string) (map[string]string, error) {
	return m, nil
}

var target = Target{Group: "dinogym-test", FunctionARN: "arn:aws:lambda:us-east-1:000000000000:function:r", RoleARN: "arn:aws:iam::000000000000:role/s"}

func TestReconcileCreatesUpdatesAndDeletes(t *testing.T) {
	ctx := context.Background()
	sched := newFakeScheduler()
	prefs := mapSettings{KeyWorkoutDay: "true", KeyWeekly: "true", KeyTime: "08:30", KeyTimeZone: "America/Lima"}
	r := NewReconciler(quiet, prefs, sched, target)

	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatal(err)
	}
	day := sched.schedules["dinogym-test/"+user+"-day"]
	week := sched.schedules["dinogym-test/"+user+"-week"]
	if day == nil || week == nil {
		t.Fatalf("schedules %v, want day and week", sched.schedules)
	}
	if *day.ScheduleExpression != "cron(30 8 * * ? *)" || *day.ScheduleExpressionTimezone != "America/Lima" {
		t.Errorf("day schedule %s in %s", *day.ScheduleExpression, *day.ScheduleExpressionTimezone)
	}
	var ev Event
	if err := json.Unmarshal([]byte(*day.Target.Input), &ev); err != nil || ev != (Event{UserID: user, Kind: WorkoutDay}) {
		t.Errorf("day target input %s", *day.Target.Input)
	}
	if *day.Target.Arn != target.FunctionARN || *day.Target.RoleArn != target.RoleARN {
		t.Errorf("day target %s via %s", *day.Target.Arn, *day.Target.RoleArn)
	}

	// Same settings again: updates, no new creates.
	prefs[KeyTime] = "19:00"
	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatal(err)
	}
	if sched.creates != 2 {
		t.Errorf("%d creates after a second reconcile, want 2", sched.creates)
	}
	if got := *sched.schedules["dinogym-test/"+user+"-day"].ScheduleExpression; got != "cron(0 19 * * ? *)" {
		t.Errorf("updated day schedule %s", got)
	}

	// Turning one off deletes it; turning off twice is fine.
	prefs[KeyWeekly] = "false"
	for range 2 {
		if err := r.Reconcile(ctx, user); err != nil {
			t.Fatal(err)
		}
	}
	if _, ok := sched.schedules["dinogym-test/"+user+"-week"]; ok {
		t.Error("weekly schedule still there after turning it off")
	}

	// Unusable settings remove everything.
	prefs[KeyTimeZone] = "Nowhere/Land"
	if err := r.Reconcile(ctx, user); err != nil {
		t.Fatal(err)
	}
	if len(sched.schedules) != 0 {
		t.Errorf("schedules %v left with a bad zone", sched.schedules)
	}
}

func TestReconcileReportsSchedulerErrors(t *testing.T) {
	sched := newFakeScheduler()
	sched.failWith = errors.New("throttled")
	r := NewReconciler(quiet, mapSettings{KeyWorkoutDay: "true", KeyTimeZone: "UTC"}, sched, target)
	if err := r.Reconcile(context.Background(), user); err == nil || !strings.Contains(err.Error(), "throttled") {
		t.Fatalf("err %v, want the scheduler's", err)
	}
}

// fcmServer answers like FCM's messages:send: 200 for "good", UNREGISTERED
// for "gone", 503 for anything else. It records what it received.
func fcmServer(t *testing.T) (*httptest.Server, *[]fcmRequest) {
	t.Helper()
	var mu sync.Mutex
	var got []fcmRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/projects/dinogym-test/messages:send" || r.Header.Get("Authorization") != "Bearer test-token" {
			http.Error(w, "bad request "+r.URL.Path, http.StatusBadRequest)
			return
		}
		var req fcmRequest
		_ = json.NewDecoder(r.Body).Decode(&req)
		mu.Lock()
		got = append(got, req)
		mu.Unlock()
		switch req.Message.Token {
		case "good":
			_, _ = w.Write([]byte(`{"name":"projects/dinogym-test/messages/1"}`))
		case "gone":
			w.WriteHeader(http.StatusNotFound)
			_, _ = w.Write([]byte(`{"error":{"code":404,"status":"NOT_FOUND","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}`))
		default:
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"error":{"code":503,"status":"UNAVAILABLE"}}`))
		}
	}))
	t.Cleanup(srv.Close)
	return srv, &got
}

func testFCM(base string) *FCM {
	return newFCM(base, "dinogym-test", oauth2.StaticTokenSource(&oauth2.Token{AccessToken: "test-token"}))
}

func TestFCMSend(t *testing.T) {
	srv, got := fcmServer(t)
	f := testFCM(srv.URL)
	ctx := context.Background()

	if err := f.Send(ctx, "good", WorkoutDay); err != nil {
		t.Fatal(err)
	}
	m := (*got)[0].Message
	if m.Data["kind"] != "workout_day" || len(m.Data) != 1 || m.Android.Priority != "high" || m.Android.TTL != "3600s" {
		t.Errorf("message %+v", m)
	}
	if err := f.Send(ctx, "gone", WorkoutDay); !errors.Is(err, ErrUnregistered) {
		t.Errorf("gone token: %v, want ErrUnregistered", err)
	}
	err := f.Send(ctx, "down", WorkoutDay)
	if err == nil || errors.Is(err, ErrUnregistered) || strings.Contains(err.Error(), "down") {
		t.Errorf("outage: %v, want an error that does not name the token", err)
	}
}

// fakeFireStore holds one user's settings and devices.
type fakeFireStore struct {
	mapSettings
	devices []store.Device
	deleted []string
}

func (f *fakeFireStore) Devices(context.Context, string, time.Time) ([]store.Device, error) {
	return f.devices, nil
}

func (f *fakeFireStore) DeleteDevice(_ context.Context, _ string, id string) error {
	f.deleted = append(f.deleted, id)
	return nil
}

func TestFire(t *testing.T) {
	srv, got := fcmServer(t)
	ctx := context.Background()
	st := &fakeFireStore{
		mapSettings: mapSettings{KeyWorkoutDay: "true", KeyTimeZone: "UTC"},
		devices:     []store.Device{{InstallationID: "a", Token: "good"}, {InstallationID: "b", Token: "gone"}, {InstallationID: "c", Token: "down"}},
	}
	f := NewFirer(quiet, st, testFCM(srv.URL))

	if err := f.Fire(ctx, Event{UserID: user, Kind: WorkoutDay}); err != nil {
		t.Fatalf("one phone reached: %v, want no error (a retry would send it twice)", err)
	}
	if len(*got) != 3 || len(st.deleted) != 1 || st.deleted[0] != "b" {
		t.Fatalf("sent %d, deleted %v; want 3 sent and only the unregistered one deleted", len(*got), st.deleted)
	}

	// Turned off since the schedule was made: nothing is sent.
	*got = nil
	if err := f.Fire(ctx, Event{UserID: user, Kind: WeeklySummary}); err != nil || len(*got) != 0 {
		t.Fatalf("weekly while off: %v, sent %d", err, len(*got))
	}

	// No phone reached for a retryable reason: an error, so Scheduler retries.
	st.devices = []store.Device{{InstallationID: "c", Token: "down"}}
	if err := f.Fire(ctx, Event{UserID: user, Kind: WorkoutDay}); err == nil {
		t.Fatal("outage on every phone: no error")
	}

	if err := f.Fire(ctx, Event{UserID: user, Kind: "other"}); err == nil {
		t.Fatal("unknown kind accepted")
	}
}

func TestHandleMarksFailures(t *testing.T) {
	rec := tracetest.NewSpanRecorder()
	prev := otel.GetTracerProvider()
	otel.SetTracerProvider(sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(rec)))
	t.Cleanup(func() { otel.SetTracerProvider(prev) })

	srv, _ := fcmServer(t)
	st := &fakeFireStore{
		mapSettings: mapSettings{KeyWorkoutDay: "true", KeyTimeZone: "UTC"},
		devices:     []store.Device{{InstallationID: "a", Token: "good"}},
	}
	var logs strings.Builder
	f := NewFirer(slog.New(slog.NewTextHandler(&logs, nil)), st, testFCM(srv.URL))
	ctx := context.Background()

	if err := f.Handle(ctx, Event{UserID: user, Kind: WorkoutDay}); err != nil {
		t.Fatal(err)
	}
	st.devices = []store.Device{{InstallationID: "c", Token: "down"}}
	if err := f.Handle(ctx, Event{UserID: user, Kind: WorkoutDay}); err == nil {
		t.Fatal("outage on every phone: no error")
	}

	spans := rec.Ended()
	if len(spans) != 2 {
		t.Fatalf("%d spans, want 2", len(spans))
	}
	for i, want := range []codes.Code{codes.Unset, codes.Error} {
		s := spans[i]
		if s.Name() != "reminder fire" || s.SpanKind() != trace.SpanKindServer || s.Status().Code != want {
			t.Errorf("span %d: %q kind %v status %v, want server span with status %v", i, s.Name(), s.SpanKind(), s.Status().Code, want)
		}
	}
	if n := strings.Count(logs.String(), "level=ERROR msg=\"reminder failed\""); n != 1 {
		t.Errorf("%d ERROR logs, want 1:\n%s", n, logs.String())
	}
	if strings.Contains(logs.String(), user) {
		t.Error("the log names the user")
	}
}
