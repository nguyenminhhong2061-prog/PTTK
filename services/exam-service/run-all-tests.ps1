$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ResetScript = Join-Path $ProjectRoot "reset-and-seed.ps1"
$TestScript = Join-Path $ProjectRoot "test-api.ps1"

Write-Host "Running full API verification workflow..." -ForegroundColor Magenta

if (-not (Test-Path $ResetScript)) {
    throw "Missing script: $ResetScript"
}

if (-not (Test-Path $TestScript)) {
    throw "Missing script: $TestScript"
}

Write-Host ""
Write-Host "Step 1/2: Reset and seed database" -ForegroundColor Cyan
& $ResetScript

Write-Host ""
Write-Host "Step 2/2: Run API tests" -ForegroundColor Cyan
& $TestScript

Write-Host ""
Write-Host "All steps completed." -ForegroundColor Green
