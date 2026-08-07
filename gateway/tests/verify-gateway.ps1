param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$timeoutOverride = Join-Path $PSScriptRoot "docker-compose.gateway-timeout.yml"

function Invoke-DockerCompose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-ForGateway {
    for ($attempt = 1; $attempt -le 30; $attempt += 1) {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/health" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Gateway did not become healthy within 60 seconds."
}

Push-Location $root
try {
    Invoke-DockerCompose config --quiet
    Invoke-DockerCompose up -d --build gateway submission-service
    Wait-ForGateway
    Invoke-DockerCompose exec -T gateway nginx -t

    $env:BASE_URL = $BaseUrl
    $env:MODE = "rate-limit"
    & node (Join-Path $PSScriptRoot "verify-gateway.mjs")
    if ($LASTEXITCODE -ne 0) { throw "Rate-limit probe failed." }

    Invoke-DockerCompose stop submission-service
    $env:MODE = "bad-gateway"
    & node (Join-Path $PSScriptRoot "verify-gateway.mjs")
    if ($LASTEXITCODE -ne 0) { throw "502 probe failed." }

    Invoke-DockerCompose up -d submission-service
    Invoke-DockerCompose -f docker-compose.yml -f $timeoutOverride up -d --build gateway slow-upstream
    Wait-ForGateway
    $env:MODE = "timeout"
    & node (Join-Path $PSScriptRoot "verify-gateway.mjs")
    if ($LASTEXITCODE -ne 0) { throw "504 probe failed." }

    $requestCount = (& docker compose -f docker-compose.yml -f $timeoutOverride exec -T slow-upstream cat /tmp/request-count).Trim()
    if ($requestCount -ne "1") {
        throw "Expected the timeout probe to reach upstream once, but received $requestCount requests."
    }
    Write-Host "Gateway verification passed."
}
finally {
    Invoke-DockerCompose up -d --build gateway submission-service
    Pop-Location
}
