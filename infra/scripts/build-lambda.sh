#!/usr/bin/env bash
# Build the Go API for Lambda (arm64, decision 63) and zip it for Terraform:
# backend/bin/api.zip, holding one file named bootstrap.
# The zip is reproducible (fixed timestamp), so an unchanged build leaves the
# Lambda's source_code_hash, and therefore `terraform plan`, unchanged.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/backend/bin"
mkdir -p "$OUT"

cd "$ROOT/backend"
# -p 2: CPU limit from CLAUDE.md.
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -p 2 -trimpath -buildvcs=false \
  -ldflags "-s -w -X main.version=$(git rev-parse --short HEAD)" \
  -o "$OUT/bootstrap" ./cmd/api

touch -d @0 "$OUT/bootstrap"
rm -f "$OUT/api.zip"
(cd "$OUT" && zip -q -X api.zip bootstrap)
echo "$OUT/api.zip ($(du -h "$OUT/api.zip" | cut -f1))"
