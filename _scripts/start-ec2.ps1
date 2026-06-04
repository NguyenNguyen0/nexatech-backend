param(
    [Parameter(Mandatory = $true)]
    [string]$ServiceName
)

$ErrorActionPreference = "Stop"

function Import-DotEnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [switch]$Required
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        if ($Required) {
            throw "Missing env file at $Path"
        }
        return
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $parts = $line -split "=", 2
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
    }
}

function Import-DotEnvKeys {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string[]]$Keys
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $allowed = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase)
    foreach ($key in $Keys) {
        [void]$allowed.Add($key)
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $parts = $line -split "=", 2
        $name = $parts[0].Trim()
        if ($allowed.Contains($name)) {
            [Environment]::SetEnvironmentVariable($name, $parts[1].Trim(), "Process")
        }
    }
}

Import-DotEnvFile -Path (Join-Path $PSScriptRoot ".env")
Import-DotEnvFile -Path (Join-Path $PSScriptRoot ".env.ec2") -Required

if (-not $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE) {
    $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "2"
}
if (-not $env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE) {
    $env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "0"
}
if (-not $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT) {
    $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = "5000"
}
if (-not $env:SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT) {
    $env:SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT = "30000"
}
if (-not $env:SPRING_JPA_OPEN_IN_VIEW) {
    $env:SPRING_JPA_OPEN_IN_VIEW = "false"
}

switch ($ServiceName) {
    "api-gateway" {
        if (-not $env:CHAT_SERVICE_URI) {
            $env:CHAT_SERVICE_URI = "http://127.0.0.1:8089"
        }
        if (-not $env:CHAT_SERVICE_WS_URI) {
            $env:CHAT_SERVICE_WS_URI = $env:CHAT_SERVICE_URI
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_MAX_CONNECTIONS) {
            $env:GATEWAY_HTTP_CLIENT_MAX_CONNECTIONS = "200"
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_MAX_CONNECTIONS_PER_ROUTE) {
            $env:GATEWAY_HTTP_CLIENT_MAX_CONNECTIONS_PER_ROUTE = "100"
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_CONNECTION_REQUEST_TIMEOUT) {
            $env:GATEWAY_HTTP_CLIENT_CONNECTION_REQUEST_TIMEOUT = "2s"
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_RESPONSE_TIMEOUT) {
            $env:GATEWAY_HTTP_CLIENT_RESPONSE_TIMEOUT = "45s"
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_CONNECT_TIMEOUT) {
            $env:GATEWAY_HTTP_CLIENT_CONNECT_TIMEOUT = "3s"
        }
        if (-not $env:GATEWAY_HTTP_CLIENT_READ_TIMEOUT) {
            $env:GATEWAY_HTTP_CLIENT_READ_TIMEOUT = "45s"
        }
        if (-not $env:SPRING_HTTP_CLIENTS_CONNECT_TIMEOUT) {
            $env:SPRING_HTTP_CLIENTS_CONNECT_TIMEOUT = $env:GATEWAY_HTTP_CLIENT_CONNECT_TIMEOUT
        }
        if (-not $env:SPRING_HTTP_CLIENTS_READ_TIMEOUT) {
            $env:SPRING_HTTP_CLIENTS_READ_TIMEOUT = $env:GATEWAY_HTTP_CLIENT_READ_TIMEOUT
        }
    }
    "product-service" {
        # Only merge service secrets; keep EC2 infra from .env.ec2
        Import-DotEnvKeys -Path (Join-Path $PSScriptRoot "product-service/.env") -Keys @(
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_REGION",
            "AWS_S3_BUCKET",
            "PRODUCT_INTERNAL_API_KEY"
        )
    }
    "inventory-service" {}
    "identity-service" {}
    "storage-service" {}
    "order-service" {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_orders"
    }
    "payment-service" {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_payments"
    }
    "shipping-service" {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_shipping"
    }
    "notification-service" {
        $env:DB_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_notifications"
    }
    "chat-service" {
        $env:DB_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_chats"
        $env:SPRING_DATA_MONGODB_URI = if ($env:MONGO_CHAT_URL) { $env:MONGO_CHAT_URL } else { "mongodb://nexatech:nexatech123@$($env:EC2_HOST):27017/nexatech_chats?authSource=admin&authMechanism=SCRAM-SHA-256" }
        $env:SPRING_MONGODB_URI = $env:SPRING_DATA_MONGODB_URI
        $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = if ($env:CHAT_SERVICE_HIKARI_MAXIMUM_POOL_SIZE) { $env:CHAT_SERVICE_HIKARI_MAXIMUM_POOL_SIZE } else { "8" }
        $env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = if ($env:CHAT_SERVICE_HIKARI_MINIMUM_IDLE) { $env:CHAT_SERVICE_HIKARI_MINIMUM_IDLE } else { "2" }
        $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = if ($env:CHAT_SERVICE_HIKARI_CONNECTION_TIMEOUT) { $env:CHAT_SERVICE_HIKARI_CONNECTION_TIMEOUT } else { "15000" }
        $env:SPRING_JPA_OPEN_IN_VIEW = "false"
    }
    "audit-service" {
        $env:SPRING_MONGODB_URI = if ($env:MONGO_AUDIT_URL) {
            $env:MONGO_AUDIT_URL
        } else {
            "mongodb://nexatech:nexatech123@$($env:EC2_HOST):27017/nexatech_audit?authSource=admin&authMechanism=SCRAM-SHA-256"
        }
        $env:KAFKA_GROUP_ID = if ($env:AUDIT_KAFKA_GROUP_ID) { $env:AUDIT_KAFKA_GROUP_ID } else { "audit-service-group" }
        $env:AUDIT_LOG_TOPIC = if ($env:AUDIT_LOG_TOPIC) { $env:AUDIT_LOG_TOPIC } else { "audit-topic" }
        $env:SPRING_KAFKA_CONSUMER_GROUP_ID = $env:KAFKA_GROUP_ID
        $env:SPRING_KAFKA_CONSUMER_GROUPID = $env:KAFKA_GROUP_ID
        $env:SPRING_KAFKA_TOPIC_AUDIT_LOG = $env:AUDIT_LOG_TOPIC
        $env:SPRING_KAFKA_TOPIC_AUDITLOG = $env:AUDIT_LOG_TOPIC
    }
    "user-service" {
        $env:DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_users"
        $env:DATASOURCE_USERNAME = $env:DB_USERNAME
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD
        $env:KAFKA_GROUP_ID = "user-service-group"
        Import-DotEnvKeys -Path (Join-Path $PSScriptRoot "user-service/.env") -Keys @(
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_REGION",
            "AWS_S3_BUCKET"
        )
    }
    "news-service" {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_news"
        $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME
        $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
        if (-not $env:SERVER_PORT) {
            $env:SERVER_PORT = "8092"
        }
        if (-not $env:SPRING_CLOUD_CONFIG_URI -and $env:CONFIG_SERVER_URI) {
            $env:SPRING_CLOUD_CONFIG_URI = $env:CONFIG_SERVER_URI
        }
        Import-DotEnvKeys -Path (Join-Path $PSScriptRoot "news-service/.env") -Keys @(
            "SPRING_CLOUD_CONFIG_URI",
            "SERVER_PORT",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_REGION",
            "AWS_S3_BUCKET"
        )
    }
    "promotion-service" {
        $env:DATABASE_URL = "postgresql://$($env:DB_USERNAME):$($env:DB_PASSWORD)@$($env:EC2_HOST):5432/nexatech_promotions"
        $env:KAFKA_BROKERS = $env:KAFKA_BOOTSTRAP_SERVERS
        $env:SERVICE_NAME = "promotion-service"
        $env:PORT = "8093"
        $env:EUREKA_INSTANCE_PORT = $env:PORT
        $env:EUREKA_INSTANCE_HOSTNAME = if ($env:EUREKA_INSTANCE_IP_ADDRESS) { $env:EUREKA_INSTANCE_IP_ADDRESS } else { "127.0.0.1" }
        Set-Location (Join-Path $PSScriptRoot "promotion-service")
        npm run start:dev
        exit $LASTEXITCODE
    }
    "recommendation-service" {
        Import-DotEnvFile -Path (Join-Path $PSScriptRoot "python-services/recommendation-service/.env.ec2")
        Set-Location (Join-Path $PSScriptRoot "python-services/recommendation-service")
        uv run main.py
        exit $LASTEXITCODE
    }
    "promotion-migrate" {
        $env:DATABASE_URL = "postgresql://$($env:DB_USERNAME):$($env:DB_PASSWORD)@$($env:EC2_HOST):5432/nexatech_promotions"
        Set-Location (Join-Path $PSScriptRoot "promotion-service")
        npx prisma migrate deploy
        exit $LASTEXITCODE
    }
    "promotion-seed" {
        if (-not $env:SSH_KEY) {
            throw "SSH_KEY is required for promotion-seed"
        }
        $seedPath = Join-Path $PSScriptRoot "promotion-service/prisma/seed.sql"
        Get-Content -Raw -LiteralPath $seedPath |
            ssh -i $env:SSH_KEY "ubuntu@$($env:EC2_HOST)" "docker exec -i nexatech-infra-postgres psql -U nexatech -d nexatech_promotions"
        exit $LASTEXITCODE
    }
    default {
        throw "Unknown service: $ServiceName"
    }
}

mvn -pl $ServiceName spring-boot:run
