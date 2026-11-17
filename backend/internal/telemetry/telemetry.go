// Package telemetry sets up OpenTelemetry traces, metrics and logs (decision 9).
//
// Three modes (config.Config.Telemetry):
//   - "otlp": send everything over OTLP/HTTP. The exporters read the standard
//     OTEL_EXPORTER_OTLP_* variables (endpoint, headers), which is how Grafana
//     Cloud is reached.
//   - "console": print spans to stdout, for trying the API on the laptop.
//   - "none": no exporters; the API still runs its instrumentation at almost no cost.
package telemetry

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"

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

// Setup creates the providers for mode, installs them as the OpenTelemetry
// globals and returns a logger that writes JSON to stdout (for CloudWatch,
// decision 22) and, in "otlp" mode, to the OpenTelemetry log pipeline too.
func Setup(ctx context.Context, mode, env, version string, level slog.Level, stdout io.Writer) (*Telemetry, *slog.Logger, error) {
	res, err := resource.Merge(resource.Default(), resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceName(ServiceName),
		semconv.ServiceVersion(version),
		semconv.DeploymentEnvironmentNameKey.String(env),
	))
	if err != nil {
		return nil, nil, fmt.Errorf("telemetry resource: %w", err)
	}

	t := &Telemetry{}
	traceOpts := []sdktrace.TracerProviderOption{sdktrace.WithResource(res)}
	meterOpts := []sdkmetric.Option{sdkmetric.WithResource(res)}

	switch mode {
	case "otlp":
		spans, err := otlptracehttp.New(ctx)
		if err != nil {
			return nil, nil, fmt.Errorf("trace exporter: %w", err)
		}
		metrics, err := otlpmetrichttp.New(ctx)
		if err != nil {
			return nil, nil, fmt.Errorf("metric exporter: %w", err)
		}
		logs, err := otlploghttp.New(ctx)
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

// Flush sends everything buffered. On Lambda it runs after every invocation,
// because the runtime may freeze the process before a background export.
func (t *Telemetry) Flush(ctx context.Context) error {
	errs := []error{t.tracer.ForceFlush(ctx), t.meter.ForceFlush(ctx)}
	if t.logger != nil {
		errs = append(errs, t.logger.ForceFlush(ctx))
	}
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
