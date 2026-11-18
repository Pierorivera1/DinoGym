# Backend

The DinoGym API in Go. One binary serves HTTP on the laptop and runs as the Lambda function behind API Gateway (decision 63).

| Path | What |
|---|---|
| `cmd/api` | The binary: config, telemetry, then a local server or the Lambda loop |
| `internal/api` | Generated from [../api/openapi.yaml](../api/openapi.yaml). Do not edit `api.gen.go`; run `go generate ./...` |
| `internal/server` | The API's handlers (`StrictServerInterface`) and the bearer check on secured routes |
| `internal/auth` | Google ID token check, access tokens (JWT) and refresh token rotation (decisions 79–80); `authtest` holds in-memory fakes for tests |
| `internal/lambdahttp` | API Gateway HTTP API events ↔ `net/http` |
| `internal/store` | DynamoDB: the sync rules of decision 51 on the table of decisions 52 and 65; accounts and refresh tokens in `sessions.go` |
| `internal/telemetry` | OpenTelemetry traces, metrics and logs (decision 64) |
| `internal/config` | Settings from environment variables |
| `internal/awstest` | Connects integration tests to the AWS Emulator; holds the SQS, S3 and Scheduler checks |

## Run it

```bash
infra/emulator/start.sh                      # from the repo root; skip if it is already up
source infra/emulator/env.sh                 # this shell's AWS calls now go to the emulator
cd backend
DINOGYM_TELEMETRY=console go run ./cmd/api   # http://127.0.0.1:8080, spans printed to stdout
```

At start it creates the DynamoDB table on the emulator. The server keeps the terminal busy, so call it from a second one:

```bash
curl -s 127.0.0.1:8080/health
```

Without `env.sh` it still starts, with a warning, and storage calls fail ([../docs/WORKFLOW.md](../docs/WORKFLOW.md)).

| Variable | Default | Meaning |
|---|---|---|
| `DINOGYM_ENV` | `local` | `local`, `staging` or `prod` |
| `DINOGYM_ADDR` | `127.0.0.1:8080` | Local listen address (ignored on Lambda) |
| `DINOGYM_LOG_LEVEL` | `DEBUG`, `WARN` in prod | Lowest log level |
| `DINOGYM_TELEMETRY` | `otlp` if `OTEL_EXPORTER_OTLP_ENDPOINT` is set, else `none` | `otlp`, `console` or `none` |
| `DINOGYM_TABLE` | `dinogym` | DynamoDB table |
| `DINOGYM_GOOGLE_CLIENT_ID` | none; required in staging and prod | Web OAuth client ID that Google ID tokens must be issued to |
| `DINOGYM_SESSION_KEY_PARAM` | none | SSM parameter holding the session signing key (set by Terraform) |
| `DINOGYM_SESSION_KEY` | a random key in `local` | The signing key itself, at least 32 bytes; laptop only. Never set both this and `_PARAM` |

### Sign in locally

`/auth/google` needs a real Google ID token issued to the Web client ID, so set it before starting the server:

```bash
export DINOGYM_GOOGLE_CLIENT_ID=854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com
export DINOGYM_SESSION_KEY="$(openssl rand -base64 48)"   # optional: keeps sessions valid across restarts
```

With an ID token from the app's sign-in for that client:

```bash
curl -s -X POST 127.0.0.1:8080/auth/google -H 'Content-Type: application/json' -d '{"idToken":"<id token>"}'
curl -s -X POST 127.0.0.1:8080/auth/refresh -H 'Content-Type: application/json' -d '{"refreshToken":"<refresh token>"}'
curl -s '127.0.0.1:8080/sync/pull?since=0' -H 'Authorization: Bearer <access token>'
```

A refresh token works once; sending a used one again revokes that session (decision 79). Without a valid access token, `/sync/*` answers 401 `invalid_token`. The unit tests cover all of this without Google (`go test -p 2 ./internal/auth/... ./internal/server/`).

## Test it

Only the packages you changed, two at a time at most (CLAUDE.md):

```bash
go test -p 2 ./internal/server/
```

The integration tests (build tag `integration`) need the emulator and refuse any other endpoint. They create their own resources and delete them; the whole set takes about 20 seconds:

```bash
source ../infra/emulator/env.sh
go test -p 2 -tags integration ./internal/store/ ./internal/awstest/
```

## Build the Lambda binary

```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -X main.version=$(git rev-parse --short HEAD)" -o bootstrap ./cmd/api
```
