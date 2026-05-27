param(
    [ValidateSet("local", "ec2")]
    [string]$Mode = "ec2",

    [string[]]$Services = @(),

    [int]$DelaySeconds = 8
)

$ErrorActionPreference = "Stop"

$localTargets = @{
    "discovery-server"      = "run-discovery"
    "config-server"         = "run-config"
    "api-gateway"           = "run-gateway"
    "identity-service"      = "run-identity"
    "user-service"          = "run-user"
    "product-service"       = "run-product"
    "inventory-service"     = "run-inventory"
    "order-service"         = "run-order"
    "payment-service"       = "run-payment"
    "notification-service"  = "run-notification"
    "chat-service"          = "run-chat"
    "audit-service"         = "run-audit"
    "storage-service"       = "run-storage"
    "promotion-service"     = "run-promotion"
}

$ec2Targets = @{
    "api-gateway"           = "run-gateway-ec2"
    "identity-service"      = "run-identity-ec2"
    "user-service"          = "run-user-ec2"
    "product-service"       = "run-product-ec2"
    "inventory-service"     = "run-inventory-ec2"
    "order-service"         = "run-order-ec2"
    "payment-service"       = "run-payment-ec2"
    "shipping-service"      = "run-shipping-ec2"
    "notification-service"  = "run-notification-ec2"
    "chat-service"          = "run-chat-ec2"
    "audit-service"         = "run-audit-ec2"
    "storage-service"       = "run-storage-ec2"
    "news-service"          = "run-news-ec2"
    "promotion-service"     = "run-promotion-ec2"
}

$defaultLocalServices = @(
    "discovery-server",
    "config-server",
    "api-gateway",
    "identity-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "payment-service",
    "notification-service",
    "chat-service",
    "audit-service",
    "storage-service",
    "promotion-service"
)

$defaultEc2Services = @(
    "api-gateway",
    "identity-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "payment-service",
    "shipping-service",
    "notification-service",
    "chat-service",
    "audit-service",
    "storage-service",
    "news-service",
    "promotion-service"
)

if ($Services.Count -eq 0) {
    $Services = if ($Mode -eq "ec2") { $defaultEc2Services } else { $defaultLocalServices }
}

$targets = if ($Mode -eq "ec2") { $ec2Targets } else { $localTargets }
$backendPath = $PSScriptRoot.Replace("'", "''")

foreach ($service in $Services) {
    if (-not $targets.ContainsKey($service)) {
        $valid = ($targets.Keys | Sort-Object) -join ", "
        throw "Unknown service '$service' for mode '$Mode'. Valid services: $valid"
    }

    $target = $targets[$service]
    $title = "nexatech $service ($Mode)".Replace("'", "''")
    $command = "`$Host.UI.RawUI.WindowTitle = '$title'; Set-Location -LiteralPath '$backendPath'; make $target"

    Write-Host "Opening terminal for $service -> make $target"
    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-NoExit",
        "-Command",
        $command
    )

    if ($DelaySeconds -gt 0) {
        Start-Sleep -Seconds $DelaySeconds
    }
}
