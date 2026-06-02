#!/usr/bin/env sh

set -eu

PRODUCT_BASE_URL="http://localhost:8090/api/v1/products"
INVENTORY_BASE_URL="http://localhost:8085/api/v1/inventory"
QUANTITY=10
PAGE_SIZE=100
BEARER_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJUd20tZjVKSWotWVVVdnlnNXFSWGFzVUJKV0Y1ZWhZejc3NGdCTjdQMTNFIn0.eyJleHAiOjE3ODAzNDEyMzUsImlhdCI6MTc4MDM0MDkzNSwianRpIjoib25ydHJvOmM4ZTllYjZmLTJmZjQtOTA0YS0wN2U4LWM0YzYxOGE5ZTBhMiIsImlzcyI6Imh0dHA6Ly8xMC4yMC4xLjE5NTo5MDAwL3JlYWxtcy9uZXhhdGVjaCIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiIzYmUyZWE1ZS1iOGMzLTRhMDAtOTI5ZC0xMGQ0M2YxYjlhZTYiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJuZXhhdGVjaC1pZCIsInNpZCI6IldYUi0tTk9laldHQlpsZmtzaURwSFVZXyIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiKiJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiTk9USUZJQ0FUSU9OX1dSSVRFIiwiUFJPRFVDVF9DUkVBVEUiLCJDQVRFR09SWV9SRUFEIiwiVVNFUl9DUkVBVEUiLCJCUkFORF9XUklURSIsIkNBVEVHT1JZX1dSSVRFIiwiTkVXU19SRUFEIiwiSU5WRU5UT1JZX1dSSVRFIiwiU1RPUkFHRV9XUklURSIsIm9mZmxpbmVfYWNjZXNzIiwiQVVESVRfUkVBRCIsIk5FV1NfSU1BR0VfV1JJVEUiLCJDVVNUT01FUiIsIk5FV1NfV1JJVEUiLCJOT1RJRklDQVRJT05fUkVBRCIsInVtYV9hdXRob3JpemF0aW9uIiwiVVNFUl9SRUFEIiwiZGVmYXVsdC1yb2xlcy1uZXhhdGVjaCIsIlJPTEVfUkVBRCIsIlVTRVJfRU5BQkxFIiwiUFJPRFVDVF9SRUFEIiwiU1RPUkFHRV9SRUFEIiwiVVNFUl9ERUxFVEUiLCJBVURJVF9FWFBPUlQiLCJVU0VSX1VQREFURSIsIlBST0RVQ1RfREVMRVRFIiwiUk9MRV9XUklURSIsIlBFUk1JU1NJT05fV1JJVEUiLCJQUk9EVUNUX1VQREFURSIsIklOVkVOVE9SWV9SRUFEIiwiUFJPRFVDVF9SRVZJRVdfUkVBRCIsIlBFUk1JU1NJT05fUkVBRCIsIlBST0RVQ1RfUkVWSUVXX01PREVSQVRFIiwiQlJBTkRfUkVBRCIsIklOVkVOVE9SWV9JTVBPUlQiLCJQUk9EVUNUX0lNQUdFX1dSSVRFIiwiTk9USUZJQ0FUSU9OX0FETUlOX1JFQUQiXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJuZ3V5ZW4gbmd1eWVuIiwicHJlZmVycmVkX3VzZXJuYW1lIjoibmd1eWVuMCIsImdpdmVuX25hbWUiOiJuZ3V5ZW4iLCJmYW1pbHlfbmFtZSI6Im5ndXllbiIsImVtYWlsIjoibnRuMjAyMjAwNEBnbWFpbC5jb20ifQ.rYVvXNB0g0pOe6d1qcHLrx8Gp3O3XE1lWTlciOhC0KImOxD4P2FaePTtMdaNF-OeO9Hm4z07P2DHhZL0x65jgrZDxAnMKRYD7uB1Kj2s4DoeplPNQswYuYe1FxGTp5tSJ7QgBkJmx8VGv9SLSlIBPibr5iLA-nIba3hohGWBPJZvvlMneQF1jvACr6nRtqakkCD3TVjRXTm610X3Wro8Zol-jc3SvCHuV9yEKYZJQqyB-sa6qya5e2pfdfs5LPKd1tM38brnHizDw8RTGfPr3hKV6tdbI0Yrm8j6DQEao5a-aA9PGk_Wv1yXeJ87CLov2V0F74Cef7vE7M7cXbgKFg"
CORRELATION_ID=""

usage() {
  cat <<'EOF'
Usage: update-all-inventory-to-10.sh [options]

Options:
  --product-base-url URL     Product API base URL (default: http://localhost:8090/api/v1/products)
  --inventory-base-url URL   Inventory API base URL (default: http://localhost:8085/api/v1/inventory)
  --quantity N               Quantity to set for all products (default: 10)
  --page-size N              Page size when fetching products (default: 100)
  --bearer-token TOKEN       Bearer token for Authorization header
  --correlation-id ID        Correlation ID (auto-generated if not provided)
  -h, --help                 Show this help

Example:
  sh _scripts/update-all-inventory-to-10.sh \
    --bearer-token "<JWT>" \
    --product-base-url "http://localhost:8090/api/v1/products" \
    --inventory-base-url "http://localhost:8085/api/v1/inventory" \
    --quantity 10
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: '$1' is required but not installed." >&2
    exit 1
  fi
}

generate_correlation_id() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
    return
  fi

  if [ -r /proc/sys/kernel/random/uuid ]; then
    cat /proc/sys/kernel/random/uuid
    return
  fi

  # Fallback best-effort value
  date +"cid-%s"
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --product-base-url)
        PRODUCT_BASE_URL="$2"
        shift 2
        ;;
      --inventory-base-url)
        INVENTORY_BASE_URL="$2"
        shift 2
        ;;
      --quantity)
        QUANTITY="$2"
        shift 2
        ;;
      --page-size)
        PAGE_SIZE="$2"
        shift 2
        ;;
      --bearer-token)
        BEARER_TOKEN="$2"
        shift 2
        ;;
      --correlation-id)
        CORRELATION_ID="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown option: $1" >&2
        usage
        exit 1
        ;;
    esac
  done
}

parse_args "$@"

require_cmd curl
require_cmd jq

case "$QUANTITY" in
  ''|*[!0-9]*)
    echo "Error: --quantity must be a non-negative integer." >&2
    exit 1
    ;;
esac

case "$PAGE_SIZE" in
  ''|*[!0-9]*)
    echo "Error: --page-size must be a positive integer." >&2
    exit 1
    ;;
esac

if [ "$PAGE_SIZE" -le 0 ]; then
  echo "Error: --page-size must be greater than 0." >&2
  exit 1
fi

if [ -z "$CORRELATION_ID" ]; then
  CORRELATION_ID="$(generate_correlation_id)"
fi

AUTH_HEADER=""
if [ -n "$BEARER_TOKEN" ]; then
  AUTH_HEADER="Authorization: Bearer $BEARER_TOKEN"
fi

build_headers() {
  if [ -n "$AUTH_HEADER" ]; then
    printf '%s\n' "-H" "$AUTH_HEADER" "-H" "X-Correlation-Id: $CORRELATION_ID"
  else
    printf '%s\n' "-H" "X-Correlation-Id: $CORRELATION_ID"
  fi
}

request_get() {
  url="$1"

  if [ -n "$AUTH_HEADER" ]; then
    curl -sS --fail \
      -H "$AUTH_HEADER" \
      -H "X-Correlation-Id: $CORRELATION_ID" \
      "$url"
  else
    curl -sS --fail \
      -H "X-Correlation-Id: $CORRELATION_ID" \
      "$url"
  fi
}

request_put_json() {
  url="$1"
  body="$2"

  if [ -n "$AUTH_HEADER" ]; then
    curl -sS --fail \
      -X PUT \
      -H "$AUTH_HEADER" \
      -H "X-Correlation-Id: $CORRELATION_ID" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "$url" >/dev/null
  else
    curl -sS --fail \
      -X PUT \
      -H "X-Correlation-Id: $CORRELATION_ID" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "$url" >/dev/null
  fi
}

extract_page_payload() {
  jq -c '
    if (.data != null) then .data
    elif (.result != null) then .result
    else .
    end
  '
}

page=0
updated=0
skipped=0
failed=0
total_pages=1

while [ "$page" -lt "$total_pages" ]; do
  product_uri="${PRODUCT_BASE_URL}?page=${page}&size=${PAGE_SIZE}&sort=updatedAt,desc"

  if ! product_response="$(request_get "$product_uri")"; then
    echo "Error: failed to fetch product page=$page" >&2
    exit 1
  fi

  page_payload="$(printf '%s' "$product_response" | extract_page_payload)"

  content_count="$(printf '%s' "$page_payload" | jq -r '.content | if type=="array" then length else 0 end')"

  i=0
  while [ "$i" -lt "$content_count" ]; do
    product_json="$(printf '%s' "$page_payload" | jq -c ".content[$i]")"
    product_id="$(printf '%s' "$product_json" | jq -r '.id // empty')"
    product_sku="$(printf '%s' "$product_json" | jq -r '.sku // empty')"
    product_name="$(printf '%s' "$product_json" | jq -r '.name // ""')"

    if [ -z "$product_id" ]; then
      skipped=$((skipped + 1))
      echo "Warning: skip product without id." >&2
      i=$((i + 1))
      continue
    fi

    if [ -z "$product_sku" ]; then
      skipped=$((skipped + 1))
      echo "Warning: skip product $product_id because SKU is missing." >&2
      i=$((i + 1))
      continue
    fi

    payload="$(jq -nc \
      --arg productId "$product_id" \
      --arg sku "$product_sku" \
      --arg reason "Bulk reset inventory quantity to $QUANTITY" \
      --argjson quantity "$QUANTITY" \
      '{productId: $productId, sku: $sku, quantity: $quantity, reason: $reason}')"

    if request_put_json "$INVENTORY_BASE_URL" "$payload"; then
      updated=$((updated + 1))
      echo "Updated $product_id | $product_sku | $product_name"
    else
      failed=$((failed + 1))
      echo "Warning: failed product $product_id | $product_sku" >&2
    fi

    i=$((i + 1))
  done

  total_pages_raw="$(printf '%s' "$page_payload" | jq -r '.totalPages // empty')"
  case "$total_pages_raw" in
    ''|*[!0-9]*)
      total_pages=$((page + 1))
      ;;
    *)
      total_pages="$total_pages_raw"
      ;;
  esac

  page=$((page + 1))
done

echo ""
echo "Done. Updated=$updated Skipped=$skipped Failed=$failed Quantity=$QUANTITY"
