#!/usr/bin/env bash
# Start the pinned AWS Emulator in Docker, in the background (decision 57).
# - Port 9297 on 127.0.0.1 only, never the whole network.
# - 2 GB memory cap, no swap (docs/WORKFLOW.md, local resource limits).
# - Telemetry off. Stops itself after 1 hour without an API call.
# State is in memory: stopping the container deletes every emulated resource.
set -euo pipefail

VERSION=1.0.491
IMAGE="dinogym/aws-emulator:$VERSION"
PORT=9297
NAME=aws-emulator
HERE="$(cd "$(dirname "$0")" && pwd)"

if docker ps --format '{{.Names}}' | grep -qx "$NAME"; then
  echo "Already running. Stop it with: docker stop $NAME"
  exit 0
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Building $IMAGE (downloads about 340 MB once)..."
  docker build -t "$IMAGE" "$HERE"
fi

# Must exist before Docker mounts it, or Docker creates it owned by root.
mkdir -p "$HOME/.aws/emulator"

docker run -d --rm --name "$NAME" \
  --user "$(id -u):$(id -g)" \
  --memory=2g --memory-swap=2g \
  -p "127.0.0.1:$PORT:$PORT" \
  -e HOME="$HOME" -v "$HOME/.aws/emulator:$HOME/.aws/emulator" \
  "$IMAGE" -p "$PORT" --telemetry off --idle-timeout 3600 >/dev/null

for _ in $(seq 30); do
  if curl -sf --cacert "$HOME/.aws/emulator/$PORT/ca.pem" "https://127.0.0.1:$PORT/__admin/health" >/dev/null 2>&1; then
    echo "AWS Emulator $VERSION is up on https://127.0.0.1:$PORT"
    echo "Point this shell at it:  source infra/emulator/env.sh"
    echo "Logs:                    docker logs -f $NAME"
    echo "Stop (deletes all data): docker stop $NAME"
    exit 0
  fi
  sleep 1
done
echo "The emulator did not become healthy in 30 s. Logs:" >&2
docker logs "$NAME" >&2 || true
exit 1
