#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

JAVA_IMAGES=(
  api-gateway
  discovery-server
  config-server
  identity-service
  user-service
  product-service
  order-service
  inventory-service
  payment-service
  shipping-service
  notification-service
  chat-service
  storage-service
  news-service
  audit-service
)

EXTRA_IMAGES=(
  "promotion-service|promotion-service|Dockerfile"
  "agent-service|python-services/agent-service|Dockerfile"
  "mcp-server|python-services/mcp-server|Dockerfile"
  "recommendation-service|python-services/recommendation-service|Dockerfile"
)

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

check_docker_daemon() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker CLI not found. Please install Docker before running this script." >&2
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    cat >&2 <<'ERROR'
Docker daemon is not reachable from this shell.

Please start Docker, then verify with:
  docker info

Common fixes:
  - Linux: sudo systemctl start docker
  - Linux permission issue: add your user to the docker group, then log out/in
  - WSL/Docker Desktop: start Docker Desktop and enable WSL integration for this distro
  - Custom daemon/context: export DOCKER_HOST=... or switch with docker context use <context>
ERROR
    exit 1
  fi
}

configure_docker_builder() {
  if [ -n "${DOCKER_BUILDKIT:-}" ]; then
    echo "Using DOCKER_BUILDKIT=$DOCKER_BUILDKIT"
    return
  fi

  if docker buildx version >/dev/null 2>&1; then
    export DOCKER_BUILDKIT=1
    echo "Using Docker BuildKit"
  else
    export DOCKER_BUILDKIT=0
    echo "Docker buildx is not available; falling back to legacy docker build"
  fi
}

build_and_push() {
  local image_name="$1"
  local context="$2"
  local dockerfile="$3"
  local dockerfile_path="$context/$dockerfile"
  local image_ref="$IMAGE_NAMESPACE/$image_name:$IMAGE_TAG"

  if [ ! -f "$dockerfile_path" ]; then
    echo "Dockerfile not found: $dockerfile_path" >&2
    exit 1
  fi

  CURRENT_INDEX=$((CURRENT_INDEX + 1))
  echo "[$CURRENT_INDEX/$TOTAL_IMAGES] Building $image_ref"
  docker build --pull -f "$dockerfile_path" -t "$image_ref" "$context"

  echo "[$CURRENT_INDEX/$TOTAL_IMAGES] Pushing $image_ref"
  docker push "$image_ref"
  BUILT_IMAGES=$((BUILT_IMAGES + 1))
}

usage() {
  cat <<'USAGE'
Usage:
  DOCKERHUB_USERNAME=<user> DOCKERHUB_TOKEN=<token> _scripts/build-push-dockerhub.sh

Optional environment variables:
  DOCKERHUB_NAMESPACE  Docker Hub namespace. Defaults to DOCKERHUB_USERNAME.
  DOCKER_BUILD_PUSH_SKIP_LOGIN=true  Skip docker login.
  DOCKER_BUILDKIT       Override builder mode. Auto-detected when omitted.
USAGE
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

require_env DOCKERHUB_USERNAME
require_env DOCKERHUB_TOKEN
check_docker_daemon
configure_docker_builder

IMAGE_NAMESPACE="${DOCKERHUB_NAMESPACE:-$DOCKERHUB_USERNAME}"
IMAGE_TAG="$(git rev-parse --short main)"
TOTAL_IMAGES=$((${#JAVA_IMAGES[@]} + ${#EXTRA_IMAGES[@]}))
CURRENT_INDEX=0
BUILT_IMAGES=0

if [ "${DOCKER_BUILD_PUSH_SKIP_LOGIN:-false}" != "true" ]; then
  echo "Logging in to Docker Hub as $DOCKERHUB_USERNAME"
  echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
fi

echo "Docker Hub namespace: $IMAGE_NAMESPACE"
echo "Image tag from current main commit: $IMAGE_TAG"
echo "Images to build and push: $TOTAL_IMAGES"

for service_name in "${JAVA_IMAGES[@]}"; do
  build_and_push "$service_name" "." "$service_name/Dockerfile"
done

for item in "${EXTRA_IMAGES[@]}"; do
  IFS="|" read -r image_name context dockerfile <<< "$item"
  build_and_push "$image_name" "$context" "$dockerfile"
done

echo "Done. Built and pushed $BUILT_IMAGES/$TOTAL_IMAGES images with short hash: $IMAGE_TAG"
