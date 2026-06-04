#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <service-name>" >&2
    exit 1
fi

SERVICE_NAME="$1"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

import_dotenv_file() {
    local path="$1"
    local required="${2:-false}"
    local line name value

    if [[ ! -f "$path" ]]; then
        if [[ "$required" == "true" ]]; then
            echo "Missing env file at $path" >&2
            exit 1
        fi
        return 0
    fi

    while IFS= read -r line || [[ -n "$line" ]]; do
        line="$(trim "$line")"
        if [[ -z "$line" || "$line" == \#* || "$line" != *=* ]]; then
            continue
        fi

        name="$(trim "${line%%=*}")"
        value="$(trim "${line#*=}")"
        export "$name=$value"
    done < "$path"
}

import_dotenv_keys() {
    local path="$1"
    shift
    local allowed=("$@")
    local line name value key

    if [[ ! -f "$path" ]]; then
        return 0
    fi

    while IFS= read -r line || [[ -n "$line" ]]; do
        line="$(trim "$line")"
        if [[ -z "$line" || "$line" == \#* || "$line" != *=* ]]; then
            continue
        fi

        name="$(trim "${line%%=*}")"
        value="$(trim "${line#*=}")"
        for key in "${allowed[@]}"; do
            if [[ "${name,,}" == "${key,,}" ]]; then
                export "$name=$value"
                break
            fi
        done
    done < "$path"
}

import_dotenv_file "$SCRIPT_DIR/.env"
import_dotenv_file "$SCRIPT_DIR/.env.ec2" true

export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-2}"
export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:-0}"
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT="${SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT:-5000}"
export SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT="${SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT:-30000}"
export SPRING_JPA_OPEN_IN_VIEW="${SPRING_JPA_OPEN_IN_VIEW:-false}"

case "$SERVICE_NAME" in
    api-gateway)
        ;;
    product-service)
        # Only merge service secrets; keep EC2 infra from .env.ec2
        import_dotenv_keys "$SCRIPT_DIR/product-service/.env" \
            AWS_ACCESS_KEY_ID \
            AWS_SECRET_ACCESS_KEY \
            AWS_REGION \
            AWS_S3_BUCKET \
            PRODUCT_INTERNAL_API_KEY
        ;;
    inventory-service)
        ;;
    identity-service)
        ;;
    storage-service)
        ;;
    order-service)
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_orders"
        ;;
    payment-service)
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_payments"
        ;;
    shipping-service)
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_shipping"
        ;;
    notification-service)
        export DB_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_notifications"
        ;;
    chat-service)
        export DB_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_chats"
        ;;
    audit-service)
        export SPRING_MONGODB_URI="${MONGO_AUDIT_URL:-mongodb://nexatech:nexatech123@${EC2_HOST}:27017/nexatech_audit?authSource=admin&authMechanism=SCRAM-SHA-256}"
        export KAFKA_GROUP_ID="${AUDIT_KAFKA_GROUP_ID:-audit-service-group}"
        export AUDIT_LOG_TOPIC="${AUDIT_LOG_TOPIC:-audit-topic}"
        export SPRING_KAFKA_CONSUMER_GROUP_ID="$KAFKA_GROUP_ID"
        export SPRING_KAFKA_CONSUMER_GROUPID="$KAFKA_GROUP_ID"
        export SPRING_KAFKA_TOPIC_AUDIT_LOG="$AUDIT_LOG_TOPIC"
        export SPRING_KAFKA_TOPIC_AUDITLOG="$AUDIT_LOG_TOPIC"
        ;;
    user-service)
        export DATASOURCE_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_users"
        export DATASOURCE_USERNAME="$DB_USERNAME"
        export DATASOURCE_PASSWORD="$DB_PASSWORD"
        export KAFKA_GROUP_ID="user-service-group"
        import_dotenv_keys "$SCRIPT_DIR/user-service/.env" \
            AWS_ACCESS_KEY_ID \
            AWS_SECRET_ACCESS_KEY \
            AWS_REGION \
            AWS_S3_BUCKET
        ;;
    news-service)
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${EC2_HOST}:5432/nexatech_news"
        export SPRING_DATASOURCE_USERNAME="$DB_USERNAME"
        export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
        export SERVER_PORT="${SERVER_PORT:-8092}"
        if [[ -z "${SPRING_CLOUD_CONFIG_URI:-}" && -n "${CONFIG_SERVER_URI:-}" ]]; then
            export SPRING_CLOUD_CONFIG_URI="$CONFIG_SERVER_URI"
        fi
        import_dotenv_keys "$SCRIPT_DIR/news-service/.env" \
            SPRING_CLOUD_CONFIG_URI \
            SERVER_PORT \
            AWS_ACCESS_KEY_ID \
            AWS_SECRET_ACCESS_KEY \
            AWS_REGION \
            AWS_S3_BUCKET
        ;;
    promotion-service)
        export DATABASE_URL="postgresql://${DB_USERNAME}:${DB_PASSWORD}@${EC2_HOST}:5432/nexatech_promotions"
        export KAFKA_BROKERS="$KAFKA_BOOTSTRAP_SERVERS"
        cd "$SCRIPT_DIR/promotion-service"
        npm run start:dev
        exit $?
        ;;
    recommendation-service)
        import_dotenv_file "$SCRIPT_DIR/python-services/recommendation-service/.env.ec2"
        cd "$SCRIPT_DIR/python-services/recommendation-service"
        uv run main.py
        exit $?
        ;;
    promotion-migrate)
        export DATABASE_URL="postgresql://${DB_USERNAME}:${DB_PASSWORD}@${EC2_HOST}:5432/nexatech_promotions"
        cd "$SCRIPT_DIR/promotion-service"
        npx prisma migrate deploy
        exit $?
        ;;
    promotion-seed)
        if [[ -z "${SSH_KEY:-}" ]]; then
            echo "SSH_KEY is required for promotion-seed" >&2
            exit 1
        fi
        seed_path="$SCRIPT_DIR/promotion-service/prisma/seed.sql"
        ssh -i "$SSH_KEY" "ubuntu@${EC2_HOST}" \
            "docker exec -i nexatech-infra-postgres psql -U nexatech -d nexatech_promotions" < "$seed_path"
        exit $?
        ;;
    *)
        echo "Unknown service: $SERVICE_NAME" >&2
        exit 1
        ;;
esac

cd "$SCRIPT_DIR"
mvn -pl "$SERVICE_NAME" spring-boot:run
