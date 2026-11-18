#!/usr/bin/env bash
# The "Pull request" stage of docs/WORKFLOW.md, in one script that CI
# (.github/workflows/infra.yml) and the laptop both run:
#   infra/emulator/start.sh      # once, if the emulator is not up
#   infra/emulator/check.sh
#
# 1. Terraform format, and `validate` of the staging and prod roots (no AWS calls).
# 2. The Go integration tests against the emulator.
# 3. The staging stack on the emulator: apply, a second plan that must report
#    no changes, then destroy.
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

step "terraform destroy"
"${TF[@]}" destroy -auto-approve -input=false
trap - EXIT

step "All checks passed"
