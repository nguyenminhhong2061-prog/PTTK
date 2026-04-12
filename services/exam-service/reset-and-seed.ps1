$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SeedFile = Join-Path $ProjectRoot "src\main\resources\db\seed.sql"
$ComposeFile = Join-Path $ProjectRoot "compose.yaml"
$DbService = "exam-db"

Write-Host "Resetting and seeding database..." -ForegroundColor Magenta
Write-Host "Seed file: $SeedFile"
Write-Host "Compose file: $ComposeFile"
Write-Host "DB service: $DbService"

if (-not (Test-Path $SeedFile)) {
    throw "Seed file not found: $SeedFile"
}

if (-not (Test-Path $ComposeFile)) {
    throw "Compose file not found: $ComposeFile"
}

$dbContainerId = docker compose -f $ComposeFile ps -q $DbService
if (-not $dbContainerId) {
    throw "Database service '$DbService' is not running. Start docker compose first."
}

Write-Host ""
Write-Host "Deleting existing data from exam_questions, exams, questions..." -ForegroundColor Cyan
docker compose -f $ComposeFile exec -T $DbService mysql -uroot -pverysecret -e "USE exam_db; DELETE FROM exam_questions; DELETE FROM exams; DELETE FROM questions;"

Write-Host ""
Write-Host "Importing seed.sql..." -ForegroundColor Cyan
Get-Content $SeedFile | docker compose -f $ComposeFile exec -T $DbService mysql -uroot -pverysecret

Write-Host ""
Write-Host "Verifying row counts..." -ForegroundColor Cyan
docker compose -f $ComposeFile exec -T $DbService mysql -uroot -pverysecret -e "USE exam_db; SELECT 'questions' AS table_name, COUNT(*) AS total FROM questions UNION ALL SELECT 'exams' AS table_name, COUNT(*) AS total FROM exams UNION ALL SELECT 'exam_questions' AS table_name, COUNT(*) AS total FROM exam_questions;"

Write-Host ""
Write-Host "Database reset and seed completed." -ForegroundColor Green
