#!/usr/bin/env bash
# Build the Go Lambdas (arm64, decision 63) and zip each for Terraform:
# backend/bin/api.zip (cmd/api) and backend/bin/reminders.zip (cmd/reminders,
# decision 107), each holding one file named bootstrap.
# The zips are reproducible (fixed timestamp), so an unchanged build leaves the
# Lambdas' source_code_hash, and therefore `terraform plan`, unchanged.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/backend/bin"
mkdir -p "$OUT"

cd "$ROOT/backend"
VERSION="$(git rev-parse --short HEAD)"
for cmd in api reminders; do
  dir="$OUT/$cmd"
  mkdir -p "$dir"
  # -p 2: CPU limit from CLAUDE.md.
  GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -p 2 -trimpath -buildvcs=false \
    -ldflags "-s -w -X main.version=$VERSION" \
    -o "$dir/bootstrap" "./cmd/$cmd"
  touch -d @0 "$dir/bootstrap"
  rm -f "$OUT/$cmd.zip"
  (cd "$dir" && zip -q -X "$OUT/$cmd.zip" bootstrap)
  echo "$OUT/$cmd.zip ($(du -h "$OUT/$cmd.zip" | cut -f1))"
done
