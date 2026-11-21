// Package telemetry sets up OpenTelemetry traces, metrics and logs (decision 9).
//
// Three modes (config.Config.Telemetry):
//   - "otlp": send everything over OTLP/HTTP. The exporters read the standard
//     OTEL_EXPORTER_OTLP_* variables (endpoint, headers); Options.Headers adds
//     Grafana Cloud's credentials, read from SSM at start (decision 118).
//   - "console": print spans to stdout, for trying the API on the laptop.
//   - "none": no exporters; the API still runs its instrumentation at almost no cost.
package telemetry

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"sync"

	"go.opentelemetry.io/contrib/bridges/otelslog"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/exporters/stdout/stdouttrace"
	"go.opentelemetry.io/otel/log/global"
	"go.opentelemetry.io/otel/propagation"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.43.0"
)

// ServiceName names the API in every trace, metric and log.
const ServiceName = "dinogym-api"

// Telemetry owns the providers so they can be flushed and shut down.
type Telemetry struct {
	tracer *sdktrace.TracerProvider
	meter  *sdkmetric.MeterProvider
	logger *sdklog.LoggerProvider // nil unless mode is "otlp"
}

// Options configures Setup.
type Options struct {
	Mode    string // "otlp", "console" or "none"
	Env     string // tags every signal as deployment.environment.name
	Version string
	Level   slog.Level
	Stdout  io.Writer
	// Headers are sent with every OTLP request. When set, they replace
	// OTEL_EXPORTER_OTLP_HEADERS rather than add to it. See BasicAuth.
	Headers map[string]string
}

// BasicAuth is the header Grafana Cloud's OTLP gateway expects: the stack's
// instance id and a token with write scopes, as HTTP Basic credentials.
func BasicAuth(instanceID, token string) map[string]string {
	return map[string]string{
		"Authorization": "Basic " + base64.StdEncoding.EncodeToString([]byte(instanceID+":"+token)),
	}
}

// Setup creates the providers for o.Mode, installs them as the OpenTelemetry
// globals and returns a logger that writes JSON to stdout (for CloudWatch,
// decision 22) and, in "otlp" mode, to the OpenTelemetry log pipeline too.
func Setup(ctx context.Context, o Options) (*Telemetry, *slog.Logger, error) {
	mode, level, stdout := o.Mode, o.Level, o.Stdout
	res, err := newResource(o.Env, o.Version)
	if err != nil {
		return nil, nil, fmt.Errorf("telemetry resource: %w", err)
	}

	t := &Telemetry{}
	traceOpts := []sdktrace.TracerProviderOption{sdktrace.WithResource(res)}
	meterOpts := []sdkmetric.Option{sdkmetric.WithResource(res), sdkmetric.WithView(dropBodySizes)}

	switch mode {
	case "otlp":
		// WithHeaders replaces what the exporters read from the environment,
		// so it is only passed when there is something to send.
		var traceH []otlptracehttp.Option
		var metricH []otlpmetrichttp.Option
		var logH []otlploghttp.Option
		if len(o.Headers) > 0 {
			traceH = append(traceH, otlptracehttp.WithHeaders(o.Headers))
			metricH = append(metricH, otlpmetrichttp.WithHeaders(o.Headers))
			logH = append(logH, otlploghttp.WithHeaders(o.Headers))
		}
		spans, err := otlptracehttp.New(ctx, traceH...)
		if err != nil {
			return nil, nil, fmt.Errorf("trace exporter: %w", err)
		}
		metrics, err := otlpmetrichttp.New(ctx, metricH...)
		if err != nil {
			return nil, nil, fmt.Errorf("metric exporter: %w", err)
		}
		logs, err := otlploghttp.New(ctx, logH...)
		if err != nil {
			return nil, nil, fmt.Errorf("log exporter: %w", err)
		}
		traceOpts = append(traceOpts, sdktrace.WithBatcher(spans))
		meterOpts = append(meterOpts, sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metrics)))
		t.logger = sdklog.NewLoggerProvider(sdklog.WithResource(res), sdklog.WithProcessor(sdklog.NewBatchProcessor(logs)))
	case "console":
		spans, err := stdouttrace.New(stdouttrace.WithWriter(stdout))
		if err != nil {
			return nil, nil, fmt.Errorf("console exporter: %w", err)
		}
		// Synchronous, so a span is printed when its request ends.
		traceOpts = append(traceOpts, sdktrace.WithSyncer(spans))
	case "none":
	default:
		return nil, nil, fmt.Errorf("unknown telemetry mode %q", mode)
	}

	t.tracer = sdktrace.NewTracerProvider(traceOpts...)
	t.meter = sdkmetric.NewMeterProvider(meterOpts...)
	otel.SetTracerProvider(t.tracer)
	otel.SetMeterProvider(t.meter)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	var handler slog.Handler = slog.NewJSONHandler(stdout, &slog.HandlerOptions{Level: level})
	if t.logger != nil {
		global.SetLoggerProvider(t.logger)
		handler = slog.NewMultiHandler(handler, levelFilter{
			Handler: otelslog.NewHandler(ServiceName, otelslog.WithLoggerProvider(t.logger)),
			min:     level,
		})
	}
	return t, slog.New(handler), nil
}

// newResource describes this process in every signal.
func newResource(env, version string) (*resource.Resource, error) {
	return resource.Merge(resource.Default(), resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceName(ServiceName),
		semconv.ServiceVersion(version),
		semconv.DeploymentEnvironmentNameKey.String(env),
		// Each Lambda instance keeps its own cumulative counters. Without an
		// id of its own, two warm instances would write the same metric
		// series and their totals would overwrite each other (decision 118).
		semconv.ServiceInstanceID(rand.Text()),
	))
}

// dropBodySizes drops otelhttp's request and response size histograms. Each
// is another set of buckets per route and status, and nothing here reads them;
// the free tier counts every series (decision 118).
func dropBodySizes(i sdkmetric.Instrument) (sdkmetric.Stream, bool) {
	switch i.Name {
	case "http.server.request.body.size", "http.server.response.body.size":
		return sdkmetric.Stream{Aggregation: sdkmetric.AggregationDrop{}}, true
	}
	return sdkmetric.Stream{}, false
}

// Flush sends everything buffered. On Lambda it runs after every invocation,
// because the runtime may freeze the process before a background export.
//
// The three exports run at the same time, so a flush takes one round trip to
// the collector, not three (decision 119).
func (t *Telemetry) Flush(ctx context.Context) error {
	flushes := []func(context.Context) error{t.tracer.ForceFlush, t.meter.ForceFlush}
	if t.logger != nil {
		flushes = append(flushes, t.logger.ForceFlush)
	}
	errs := make([]error, len(flushes))
	var wg sync.WaitGroup
	for i, flush := range flushes {
		wg.Go(func() { errs[i] = flush(ctx) })
	}
	wg.Wait()
	return errors.Join(errs...)
}

// Shutdown flushes and stops the providers.
func (t *Telemetry) Shutdown(ctx context.Context) error {
	errs := []error{t.tracer.Shutdown(ctx), t.meter.Shutdown(ctx)}
	if t.logger != nil {
		errs = append(errs, t.logger.Shutdown(ctx))
	}
	return errors.Join(errs...)
}

// levelFilter applies the configured level to the OpenTelemetry log handler,
// which otherwise accepts every level.
type levelFilter struct {
	slog.Handler
	min slog.Level
}

func (f levelFilter) Enabled(ctx context.Context, l slog.Level) bool {
	return l >= f.min && f.Handler.Enabled(ctx, l)
}

func (f levelFilter) WithAttrs(as []slog.Attr) slog.Handler {
	return levelFilter{f.Handler.WithAttrs(as), f.min}
}

func (f levelFilter) WithGroup(name string) slog.Handler {
	return levelFilter{f.Handler.WithGroup(name), f.min}
}
