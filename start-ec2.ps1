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
    "api-gateway" {}
    "product-service" {}
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
    }
    "user-service" {
        $env:DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_users"
        $env:DATASOURCE_USERNAME = $env:DB_USERNAME
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD
        $env:KAFKA_GROUP_ID = "user-service-group"
    }
    "news-service" {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:EC2_HOST):5432/nexatech_news"
        $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME
        $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
    }
    "promotion-service" {
        $env:DATABASE_URL = "postgresql://$($env:DB_USERNAME):$($env:DB_PASSWORD)@$($env:EC2_HOST):5432/nexatech_promotions"
        $env:KAFKA_BROKERS = $env:KAFKA_BOOTSTRAP_SERVERS
        Set-Location (Join-Path $PSScriptRoot "promotion-service")
        npm run start:dev
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
