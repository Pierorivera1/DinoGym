#!/usr/bin/env bash
# The "Pull request" stage of docs/WORKFLOW.md, in one script that CI
# (.github/workflows/infra.yml) and the laptop both run:
#   infra/emulator/start.sh      # once, if the emulator is not up
#   infra/emulator/check.sh
#
# 1. Terraform format, and `validate` of the staging and prod roots (no AWS calls).
# 2. The Go integration tests against the emulator.
# 3. The staging stack on the emulator: apply, a second plan that must report
#    no changes, the shared-link checks against the CloudFront distribution
#    the emulator serves, then destroy.
#
# It sources env.sh itself, so the calling shell is never pointed at the
# emulator, and it refuses to start when the emulator is not healthy.
# Needs Go, Terraform 1.17.2, zip and curl.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PORT=9297

step() { printf '\n== %s\n' "$*"; }

# Drops real credentials and points every AWS call at the emulator.
# shellcheck source=env.sh
source "$ROOT/infra/emulator/env.sh"

if ! curl -sf --cacert "$AWS_CA_BUNDLE" "https://127.0.0.1:$PORT/__admin/health" >/dev/null 2>&1; then
  echo "The AWS Emulator is not running on port $PORT. Start it with: infra/emulator/start.sh" >&2
  exit 1
fi

# The three roots use the same AWS provider: download it once, not three times.
export TF_PLUGIN_CACHE_DIR="${TF_PLUGIN_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/dinogym/terraform-plugins}"
mkdir -p "$TF_PLUGIN_CACHE_DIR"
export TF_IN_AUTOMATION=1

step "terraform fmt"
terraform fmt -check -recursive "$ROOT/infra"

# -backend=false: validate needs no state bucket and no AWS credentials.
for env in staging prod; do
  step "terraform validate envs/$env"
  terraform -chdir="$ROOT/infra/envs/$env" init -backend=false -input=false >/dev/null
  terraform -chdir="$ROOT/infra/envs/$env" validate
done

step "Go integration tests"
# -p 2: CPU limit from CLAUDE.md. -count=1: never reuse a cached pass, the emulator is the thing under test.
(cd "$ROOT/backend" && go test -p 2 -count=1 -tags integration ./internal/store/ ./internal/server/ ./internal/awstest/)

step "Build the Lambda zip"
"$ROOT/infra/scripts/build-lambda.sh"

TF=(terraform -chdir="$ROOT/infra/envs/emulator")

# Whatever fails after apply starts, destroy what was created, so a rerun on
# the laptop starts clean. The emulator would forget it on stop anyway.
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "Failed; destroying what was created..." >&2
    "${TF[@]}" destroy -auto-approve -input=false >/dev/null || true
  fi
}

step "terraform apply on the emulator"
"${TF[@]}" init -input=false >/dev/null
trap cleanup EXIT
"${TF[@]}" apply -auto-approve -input=false

# Exit code 0: no changes. 2: changes, meaning the provider and the emulator
# disagree about what was created. 1: error. Only 0 passes.
step "terraform plan must report no changes"
"${TF[@]}" plan -detailed-exitcode -input=false

# The emulator serves the distribution on a port inside its container, so
# these requests run there. They check our configuration (the /r/ rewrite,
# S3 through origin access control, the API behaviour, HTTPS redirect and
# headers), not CloudFront itself (docs/WORKFLOW.md).
step "shared link checks on the emulator's CloudFront"
web="$("${TF[@]}" output -raw web_url)"
http_url="http://${web#https://}"
req() { docker exec aws-emulator curl -sk -o /dev/null -D - "$@" | tr -d '\r'; }
expect() { # expect <description> <headers> <grep -E pattern>
  if ! grep -qiE "$3" <<<"$2"; then
    printf 'FAIL: %s\n%s\n' "$1" "$2" >&2
    exit 1
  fi
  echo "ok: $1"
}
id=AAAAAAAAAAAAAAAAAAAAAA
expect "/r/<id> is the page" "$(req "$web/r/$id")" '^content-type: text/html'
expect "the page names the debug app" \
  "$(docker exec aws-emulator curl -sk "$web/r/$id" | grep -o 'data-package="[^"]*"')" 'data-package="io\.github\.pierorivera1\.dinogym\.debug"'
expect "the page has a CSP" "$(req "$web/r/$id")" "^content-security-policy: default-src 'none'"
expect "/web/r.js is served" "$(req "$web/web/r.js")" '^HTTP/[0-9.]+ 200'
expect "assetlinks.json is JSON" "$(req "$web/.well-known/assetlinks.json")" '^content-type: application/json'
expect "/shares/* goes to API Gateway" "$(req "$web/shares/$id")" '^x-amzn-requestid:'
expect "publishing cannot go through CloudFront" "$(req -X POST "$web/shares/$id")" '^HTTP/[0-9.]+ 403'
expect "other paths are not public" "$(req "$web/web/missing")" '^HTTP/[0-9.]+ 403'
expect "plain HTTP redirects to HTTPS" "$(req "$http_url/r/$id")" '^HTTP/[0-9.]+ 301'

step "terraform destroy"
"${TF[@]}" destroy -auto-approve -input=false
trap - EXIT

step "All checks passed"
