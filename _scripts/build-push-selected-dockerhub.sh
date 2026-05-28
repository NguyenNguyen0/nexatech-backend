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

SELECTED_SERVICES=()
OUTPUT_FILE="${OUTPUT_FILE:-$SCRIPT_DIR/last-selected-build.env}"
DOCKER_BUILD_PARALLELISM="${DOCKER_BUILD_PARALLELISM:-2}"

usage() {
  cat <<'USAGE'
Usage:
  DOCKERHUB_USERNAME=<user> DOCKERHUB_TOKEN=<token> _scripts/build-push-selected-dockerhub.sh <service> [service...]
  DOCKERHUB_USERNAME=<user> DOCKERHUB_TOKEN=<token> _scripts/build-push-selected-dockerhub.sh --services "order-service payment-service"

Examples:
  _scripts/build-push-selected-dockerhub.sh order-service payment-service
  _scripts/build-push-selected-dockerhub.sh --services "api-gateway recommendation-service"
  _scripts/build-push-selected-dockerhub.sh --list

Optional environment variables:
  DOCKERHUB_NAMESPACE          Docker Hub namespace. Defaults to DOCKERHUB_USERNAME.
  DOCKER_BUILD_PUSH_SKIP_LOGIN=true  Skip docker login.
  DOCKER_BUILDKIT             Override builder mode. Auto-detected when omitted.
  DOCKER_BUILD_PARALLELISM    Number of images to build/push in parallel. Defaults to 2.
  OUTPUT_FILE                 Where to write IMAGE_TAG and DEPLOY_SERVICES.

Output:
  Writes a small env file containing IMAGE_TAG and DEPLOY_SERVICES.
  Pass it to infra with:
    scripts/redeploy-images.sh "$IMAGE_TAG" --services "$DEPLOY_SERVICES"
USAGE
}

all_services() {
  printf '%s\n' "${JAVA_IMAGES[@]}"
  for item in "${EXTRA_IMAGES[@]}"; do
    IFS="|" read -r image_name _context _dockerfile <<< "$item"
    printf '%s\n' "$image_name"
  done
}

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

service_exists() {
  local service="$1"
  local known_service

  while IFS= read -r known_service; do
    if [ "$known_service" = "$service" ]; then
      return 0
    fi
  done < <(all_services)

  return 1
}

service_build_config() {
  local service="$1"
  local java_service item image_name context dockerfile

  for java_service in "${JAVA_IMAGES[@]}"; do
    if [ "$java_service" = "$service" ]; then
      printf '%s|%s|%s\n' "$service" "." "$service/Dockerfile"
      return 0
    fi
  done

  for item in "${EXTRA_IMAGES[@]}"; do
    IFS="|" read -r image_name context dockerfile <<< "$item"
    if [ "$image_name" = "$service" ]; then
      printf '%s|%s|%s\n' "$image_name" "$context" "$dockerfile"
      return 0
    fi
  done

  return 1
}

add_selected_service() {
  local service="$1"

  if ! service_exists "$service"; then
    echo "Unknown service: $service" >&2
    echo "Run with --list to see supported services." >&2
    exit 1
  fi

  case " ${SELECTED_SERVICES[*]} " in
    *" $service "*) ;;
    *) SELECTED_SERVICES+=("$service") ;;
  esac
}

build_and_push() {
  local index="$1"
  local image_name="$2"
  local context="$3"
  local dockerfile="$4"
  local dockerfile_path="$context/$dockerfile"
  local image_ref="$IMAGE_NAMESPACE/$image_name:$IMAGE_TAG"

  if [ ! -f "$dockerfile_path" ]; then
    echo "Dockerfile not found: $dockerfile_path" >&2
    exit 1
  fi

  echo "[$index/$TOTAL_IMAGES] Building $image_ref"
  docker build --pull -f "$dockerfile_path" -t "$image_ref" "$context"

  echo "[$index/$TOTAL_IMAGES] Pushing $image_ref"
  docker push "$image_ref"
  printf '%s\n' "$image_ref" > "$SUCCESS_DIR/$index.success"
}

wait_for_build_slot() {
  while [ "$(jobs -pr | wc -l)" -ge "$DOCKER_BUILD_PARALLELISM" ]; do
    if ! wait -n; then
      FAILED_JOBS=$((FAILED_JOBS + 1))
    fi
  done
}

wait_for_all_builds() {
  while [ "$(jobs -pr | wc -l)" -gt 0 ]; do
    if ! wait -n; then
      FAILED_JOBS=$((FAILED_JOBS + 1))
    fi
  done
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --list)
      all_services
      exit 0
      ;;
    --services)
      if [ -z "${2:-}" ]; then
        echo "--services requires a space-separated service list." >&2
        exit 1
      fi
      for service in $2; do
        add_selected_service "$service"
      done
      shift 2
      ;;
    --output)
      OUTPUT_FILE="${2:-}"
      if [ -z "$OUTPUT_FILE" ]; then
        echo "--output requires a file path." >&2
        exit 1
      fi
      shift 2
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      add_selected_service "$1"
      shift
      ;;
  esac
done

if [ "${#SELECTED_SERVICES[@]}" -eq 0 ]; then
  echo "No service selected." >&2
  usage >&2
  exit 1
fi

if ! [[ "$DOCKER_BUILD_PARALLELISM" =~ ^[1-9][0-9]*$ ]]; then
  echo "DOCKER_BUILD_PARALLELISM must be a positive integer." >&2
  exit 1
fi

require_env DOCKERHUB_USERNAME
require_env DOCKERHUB_TOKEN
check_docker_daemon
configure_docker_builder

IMAGE_NAMESPACE="${DOCKERHUB_NAMESPACE:-$DOCKERHUB_USERNAME}"
IMAGE_TAG="$(git rev-parse --short main)"
TOTAL_IMAGES="${#SELECTED_SERVICES[@]}"
FAILED_JOBS=0
CURRENT_INDEX=0
SUCCESS_DIR="$(mktemp -d)"
trap 'rm -rf "$SUCCESS_DIR"' EXIT

if [ "${DOCKER_BUILD_PUSH_SKIP_LOGIN:-false}" != "true" ]; then
  echo "Logging in to Docker Hub as $DOCKERHUB_USERNAME"
  echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
fi

echo "Docker Hub namespace: $IMAGE_NAMESPACE"
echo "Image tag from current main commit: $IMAGE_TAG"
echo "Selected services: ${SELECTED_SERVICES[*]}"
echo "Parallel build/push jobs: $DOCKER_BUILD_PARALLELISM"

for service in "${SELECTED_SERVICES[@]}"; do
  IFS="|" read -r image_name context dockerfile <<< "$(service_build_config "$service")"
  CURRENT_INDEX=$((CURRENT_INDEX + 1))
  wait_for_build_slot
  build_and_push "$CURRENT_INDEX" "$image_name" "$context" "$dockerfile" &
done

wait_for_all_builds
BUILT_IMAGES="$(find "$SUCCESS_DIR" -name '*.success' -type f | wc -l)"

if [ "$FAILED_JOBS" -gt 0 ]; then
  echo "Failed. Built and pushed $BUILT_IMAGES/$TOTAL_IMAGES selected images with short hash: $IMAGE_TAG"
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"
{
  printf 'IMAGE_TAG=%q\n' "$IMAGE_TAG"
  printf 'DEPLOY_SERVICES=%q\n' "${SELECTED_SERVICES[*]}"
} > "$OUTPUT_FILE"

echo "Done. Built and pushed $BUILT_IMAGES/$TOTAL_IMAGES selected images."
echo "Output file: $OUTPUT_FILE"
echo "IMAGE_TAG=$IMAGE_TAG"
echo "DEPLOY_SERVICES=${SELECTED_SERVICES[*]}"
echo "Infra command:"
echo "  scripts/redeploy-images.sh \"$IMAGE_TAG\" --services \"${SELECTED_SERVICES[*]}\""
