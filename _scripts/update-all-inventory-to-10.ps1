param(
    [string]$ProductBaseUrl = "http://localhost:8090/api/v1/products",
    [string]$InventoryBaseUrl = "http://localhost:8085/api/v1/inventory",
    [int]$Quantity = 10,
    [int]$PageSize = 100,
    [string]$BearerToken = "",
    [string]$CorrelationId = ""
)

$ErrorActionPreference = "Stop"

if ($Quantity -lt 0) {
    throw "Quantity must be greater than or equal to 0."
}

$headers = @{}
if ($BearerToken.Trim()) {
    $headers["Authorization"] = "Bearer $($BearerToken.Trim())"
}
if ($CorrelationId.Trim()) {
    $headers["X-Correlation-Id"] = $CorrelationId.Trim()
} else {
    $headers["X-Correlation-Id"] = [guid]::NewGuid().ToString()
}

function Get-EnvelopeData {
    param([object]$Response)

    if ($null -ne $Response.data) {
        return $Response.data
    }
    if ($null -ne $Response.result) {
        return $Response.result
    }
    return $Response
}

$page = 0
$updated = 0
$skipped = 0
$failed = 0

do {
    $productUri = "{0}?page={1}&size={2}&sort=updatedAt,desc" -f $ProductBaseUrl, $page, $PageSize
    $productResponse = Invoke-RestMethod -Method Get -Uri $productUri -Headers $headers
    $productPage = Get-EnvelopeData $productResponse
    $products = @($productPage.content)

    foreach ($product in $products) {
        if (-not $product.id) {
            $skipped++
            Write-Warning "Skip product without id."
            continue
        }

        if (-not $product.sku) {
            $skipped++
            Write-Warning "Skip product $($product.id) because SKU is missing."
            continue
        }

        $payload = @{
            productId = $product.id
            sku = $product.sku
            quantity = $Quantity
            reason = "Bulk reset inventory quantity to $Quantity"
        } | ConvertTo-Json

        try {
            Invoke-RestMethod `
                -Method Put `
                -Uri $InventoryBaseUrl `
                -Headers $headers `
                -ContentType "application/json" `
                -Body $payload | Out-Null

            $updated++
            Write-Host "Updated $($product.id) | $($product.sku) | $($product.name)"
        } catch {
            $failed++
            Write-Warning "Failed product $($product.id) | $($product.sku): $($_.Exception.Message)"
        }
    }

    $totalPages = if ($null -ne $productPage.totalPages) { [int]$productPage.totalPages } else { $page + 1 }
    $page++
} while ($page -lt $totalPages)

Write-Host ""
Write-Host "Done. Updated=$updated Skipped=$skipped Failed=$failed Quantity=$Quantity"
