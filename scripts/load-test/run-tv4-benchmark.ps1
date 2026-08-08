param(
    [ValidateSet("baseline", "protected", "both")]
    [string]$Profile = "both",
    [string]$BaseUrl = "http://localhost:8080",
    [int]$ExamId,
    [int]$StudentCount = 200,
    [string]$DbPassword = "changeme"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$baselineOverride = Join-Path $root "gateway/tests/docker-compose.tv4-baseline.yml"
$resultDirectory = Join-Path $PSScriptRoot "results"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker Desktop is required."
}
$k6Command = Get-Command k6 -ErrorAction SilentlyContinue
if ($k6Command) {
    $k6Executable = $k6Command.Source
} else {
    $k6InstalledPath = Join-Path $env:ProgramFiles "k6\k6.exe"
    if (-not (Test-Path $k6InstalledPath)) {
        throw "k6 is required. Install it, then re-run this script."
    }
    $k6Executable = $k6InstalledPath
}

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

function Invoke-Profile {
    param([string]$Name)

    if ($Name -eq "baseline") {
        Invoke-DockerCompose -f docker-compose.yml -f $baselineOverride up -d --force-recreate --no-deps gateway
    } else {
        Invoke-DockerCompose up -d --force-recreate --no-deps gateway
    }
    Wait-ForGateway

    $runId = "$(Get-Date -Format 'yyyyMMddHHmmss')-$Name"
    $env:BASE_URL = $BaseUrl
    $env:STUDENT_COUNT = $StudentCount.ToString()
    $env:RUN_ID = $runId
    if ($ExamId) { $env:EXAM_ID = $ExamId.ToString() } else { Remove-Item Env:EXAM_ID -ErrorAction SilentlyContinue }

    & node (Join-Path $PSScriptRoot "prepare-data.js")
    if ($LASTEXITCODE -ne 0) { throw "Could not create fresh submissions for $Name." }

    $env:SCENARIO = "steady"
    & $k6Executable run (Join-Path $PSScriptRoot "submit-load-test.js")
    if ($LASTEXITCODE -ne 0) { throw "Steady k6 run failed for $Name." }

    & node (Join-Path $PSScriptRoot "prepare-data.js")
    if ($LASTEXITCODE -ne 0) { throw "Could not create fresh submissions for the spike run." }

    $env:SCENARIO = "spike"
    & $k6Executable run (Join-Path $PSScriptRoot "submit-load-test.js")
    if ($LASTEXITCODE -ne 0) { throw "Spike k6 run failed for $Name." }

    $evidencePath = Join-Path $resultDirectory "$runId-system-evidence.txt"
    @(
        "TV4 benchmark run: $runId",
        "Profile: $Name",
        "Timestamp: $(Get-Date -Format o)",
        "",
        "Outbox status counts:"
    ) | Set-Content -Path $evidencePath -Encoding utf8
    & docker compose exec -T submission-db mysql -uroot "-p$DbPassword" submission_db -e "SELECT status, COUNT(*) AS total FROM outbox_events GROUP BY status;" | Add-Content -Path $evidencePath
    "`nProcessed event counts:" | Add-Content -Path $evidencePath
    & docker compose exec -T statistics-db mysql -uroot "-p$DbPassword" statistics_db -e "SELECT COUNT(*) AS processed_events FROM processed_events;" | Add-Content -Path $evidencePath
    "`nRabbitMQ queues:" | Add-Content -Path $evidencePath
    & docker compose exec -T rabbitmq rabbitmqctl list_queues name messages | Add-Content -Path $evidencePath
    "`nCircuit-breaker evidence (available after TV2 is integrated):" | Add-Content -Path $evidencePath
    & docker compose logs --no-color --tail 200 submission-service | Select-String -Pattern "CircuitBreaker|circuit" | Add-Content -Path $evidencePath

    Write-Host "Completed $Name benchmark: $runId"
}

Push-Location $root
try {
    Invoke-DockerCompose config --quiet
    Invoke-DockerCompose up -d --no-deps gateway
    Wait-ForGateway

    if ($Profile -in @("baseline", "both")) { Invoke-Profile "baseline" }
    if ($Profile -in @("protected", "both")) { Invoke-Profile "protected" }
}
finally {
    Invoke-DockerCompose up -d --force-recreate --no-deps gateway
    Pop-Location
}
