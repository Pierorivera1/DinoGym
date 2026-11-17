# Backend

The DinoGym API in Go. One binary serves HTTP on the laptop and runs as the Lambda function behind API Gateway (decision 63).

| Path | What |
|---|---|
| `cmd/api` | The binary: config, telemetry, then a local server or the Lambda loop |
| `internal/api` | Generated from [../api/openapi.yaml](../api/openapi.yaml). Do not edit `api.gen.go`; run `go generate ./...` |
| `internal/server` | The API's handlers (`StrictServerInterface`) |
| `internal/lambdahttp` | API Gateway HTTP API events ↔ `net/http` |
| `internal/telemetry` | OpenTelemetry traces, metrics and logs (decision 64) |
| `internal/config` | Settings from environment variables |

## Run it

```bash
cd backend
DINOGYM_TELEMETRY=console go run ./cmd/api   # http://127.0.0.1:8080, spans printed to stdout
curl -s 127.0.0.1:8080/health
```

Once the API calls AWS, run `source ../infra/emulator/env.sh` first so the SDK goes to the AWS Emulator ([../docs/WORKFLOW.md](../docs/WORKFLOW.md)).

| Variable | Default | Meaning |
|---|---|---|
| `DINOGYM_ENV` | `local` | `local`, `staging` or `prod` |
| `DINOGYM_ADDR` | `127.0.0.1:8080` | Local listen address (ignored on Lambda) |
| `DINOGYM_LOG_LEVEL` | `DEBUG`, `WARN` in prod | Lowest log level |
| `DINOGYM_TELEMETRY` | `otlp` if `OTEL_EXPORTER_OTLP_ENDPOINT` is set, else `none` | `otlp`, `console` or `none` |

## Test it

Only the packages you changed, two at a time at most (CLAUDE.md):

```bash
go test -p 2 ./internal/server/
```

## Build the Lambda binary

```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -X main.version=$(git rev-parse --short HEAD)" -o bootstrap ./cmd/api
```
