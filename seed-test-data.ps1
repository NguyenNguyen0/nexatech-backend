# seed-test-data.ps1
# =========================================================
# Seeds test orders + payments into the running dev databases
# Must be run from the nexatech-backend folder
# =========================================================

param(
    [string]$CustomerId = "00000000-0000-0000-0000-000000000001"
)

$OrderSql   = Join-Path $PSScriptRoot "postgres-init\02_seed_test_orders.sql"
$PaymentSql = Join-Path $PSScriptRoot "postgres-init\03_seed_test_payments.sql"

function Replace-CustomerId([string]$SqlFile, [string]$TmpFile, [string]$Id) {
    (Get-Content $SqlFile -Raw) -replace "00000000-0000-0000-0000-000000000001", $Id |
        Set-Content $TmpFile -Encoding UTF8
}

# Create temp files with the real customer UUID substituted
$TmpOrders   = [System.IO.Path]::GetTempFileName()
$TmpPayments = [System.IO.Path]::GetTempFileName()

Replace-CustomerId $OrderSql   $TmpOrders   $CustomerId
Replace-CustomerId $PaymentSql $TmpPayments $CustomerId

Write-Host ""
Write-Host "NexaTech — Seed Test Data" -ForegroundColor Cyan
Write-Host "Customer UUID: $CustomerId" -ForegroundColor Yellow
Write-Host ""

# Seed orders
Write-Host "Seeding orders into nexatech_orders..." -ForegroundColor White
Get-Content $TmpOrders | docker exec -i test-postgres-order psql -U nexatech -d nexatech_orders
if ($LASTEXITCODE -eq 0) {
    Write-Host "  -> Orders seeded successfully" -ForegroundColor Green
} else {
    Write-Host "  -> Failed to seed orders (is test-postgres-order running?)" -ForegroundColor Red
}

# Seed payments
Write-Host "Seeding payments into nexatech_payments..." -ForegroundColor White
Get-Content $TmpPayments | docker exec -i test-postgres-payment psql -U nexatech -d nexatech_payments
if ($LASTEXITCODE -eq 0) {
    Write-Host "  -> Payments seeded successfully" -ForegroundColor Green
} else {
    Write-Host "  -> Failed to seed payments (is test-postgres-payment running?)" -ForegroundColor Red
}

# Cleanup
Remove-Item $TmpOrders, $TmpPayments -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Done! Test your order pages at http://localhost:3004/order" -ForegroundColor Cyan
Write-Host ""
Write-Host "TIP: To use your real Keycloak user UUID, run:" -ForegroundColor DarkGray
Write-Host "  .\seed-test-data.ps1 -CustomerId '<your-keycloak-user-uuid>'" -ForegroundColor DarkGray
