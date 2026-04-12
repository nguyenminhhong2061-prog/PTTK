$ErrorActionPreference = "Stop"

$BaseUrl = "http://localhost:5001"

function Invoke-TestRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Url,

        [string]$Body,

        [int[]]$ExpectedStatus = @(200)
    )

    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    Write-Host "$Method $Url"
    if ($Body) {
        Write-Host "Body: $Body"
    }

    try {
        $params = @{
            Uri = $Url
            Method = $Method
        }

        if ($Body) {
            $params["ContentType"] = "application/json"
            $params["Body"] = $Body
        }

        $response = Invoke-WebRequest @params
        $statusCode = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        $exception = $_.Exception
        if ($exception.Response -and $exception.Response.StatusCode) {
            $statusCode = [int]$exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Close()
        } else {
            Write-Host "Request failed before receiving HTTP response: $($_.Exception.Message)" -ForegroundColor Red
            return
        }
    }

    if ($ExpectedStatus -contains $statusCode) {
        Write-Host "Status: $statusCode (expected)" -ForegroundColor Green
    } else {
        Write-Host "Status: $statusCode (unexpected, expected: $($ExpectedStatus -join ', '))" -ForegroundColor Yellow
    }

    if ([string]::IsNullOrWhiteSpace($content)) {
        Write-Host "Response body: <empty>"
    } else {
        Write-Host "Response body:"
        Write-Host $content
    }
}

$CreateQuestionBody = '{"content":"Spring Boot co ho tro embedded server hay khong?","optionA":"Khong","optionB":"Co","optionC":"Chi tren Linux","optionD":"Chi voi Docker","correctAnswer":"B","createdBy":"teacher_001"}'
$UpdateQuestionBody = '{"content":"Spring Boot co ho tro embedded web server hay khong?","optionA":"Khong","optionB":"Co","optionC":"Chi voi Maven","optionD":"Chi voi Gradle","correctAnswer":"B","createdBy":"teacher_001"}'
$CreateExamBody = '{"title":"Kiem tra API co ban","description":"De test tao exam moi","durationMinutes":30,"questionIds":[2,3],"createdBy":"teacher_001"}'
$UpdateDraftExamBody = '{"title":"Kiem tra Java Co Ban 02 Updated","description":"Cap nhat noi dung bai thi","durationMinutes":25,"questionIds":[2,3],"createdBy":"teacher_001"}'
$UpdatePublishedExamBody = '{"title":"Khong duoc sua","description":"Exam da publish","durationMinutes":20,"questionIds":[8],"createdBy":"teacher_004"}'
$PublishStatusBody = '{"status":"published"}'
$CloseStatusBody = '{"status":"closed"}'
$DraftStatusBody = '{"status":"draft"}'
$InvalidDurationExamBody = '{"title":"Exam loi","description":"duration sai","durationMinutes":301,"questionIds":[2],"createdBy":"teacher_001"}'
$InvalidUuidExamBody = '{"title":"Exam loi id","description":"id sai","durationMinutes":30,"questionIds":["abc"],"createdBy":"teacher_001"}'

Write-Host "Running API checks against $BaseUrl" -ForegroundColor Magenta

Invoke-TestRequest -Name "Health" -Method "GET" -Url "$BaseUrl/health" -ExpectedStatus @(200)

Invoke-TestRequest -Name "List Questions" -Method "GET" -Url "$BaseUrl/questions?page=1&limit=20" -ExpectedStatus @(200)
Invoke-TestRequest -Name "List Questions by createdBy" -Method "GET" -Url "$BaseUrl/questions?page=1&limit=5&createdBy=teacher_001" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Get Question Detail" -Method "GET" -Url "$BaseUrl/questions/1" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Create Question" -Method "POST" -Url "$BaseUrl/questions" -Body $CreateQuestionBody -ExpectedStatus @(201)
Invoke-TestRequest -Name "Update Question" -Method "PUT" -Url "$BaseUrl/questions/1" -Body $UpdateQuestionBody -ExpectedStatus @(200)
Invoke-TestRequest -Name "Delete Draft Question" -Method "DELETE" -Url "$BaseUrl/questions/7" -ExpectedStatus @(204)
Invoke-TestRequest -Name "Delete Published Question" -Method "DELETE" -Url "$BaseUrl/questions/8" -ExpectedStatus @(409)
Invoke-TestRequest -Name "Get Missing Question" -Method "GET" -Url "$BaseUrl/questions/not-found" -ExpectedStatus @(404)

Invoke-TestRequest -Name "List Exams" -Method "GET" -Url "$BaseUrl/exams?page=1&limit=20" -ExpectedStatus @(200)
Invoke-TestRequest -Name "List Exams by createdBy" -Method "GET" -Url "$BaseUrl/exams?page=1&limit=10&createdBy=teacher_001" -ExpectedStatus @(200)
Invoke-TestRequest -Name "List Draft Exams" -Method "GET" -Url "$BaseUrl/exams?page=1&limit=10&status=draft" -ExpectedStatus @(200)
Invoke-TestRequest -Name "List Published Exams" -Method "GET" -Url "$BaseUrl/exams?page=1&limit=10&status=published" -ExpectedStatus @(200)
Invoke-TestRequest -Name "List Closed Exams" -Method "GET" -Url "$BaseUrl/exams?page=1&limit=10&status=closed" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Get Exam Detail" -Method "GET" -Url "$BaseUrl/exams/102" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Create Exam" -Method "POST" -Url "$BaseUrl/exams" -Body $CreateExamBody -ExpectedStatus @(201)
Invoke-TestRequest -Name "Update Draft Exam" -Method "PUT" -Url "$BaseUrl/exams/102" -Body $UpdateDraftExamBody -ExpectedStatus @(200)
Invoke-TestRequest -Name "Update Published Exam" -Method "PUT" -Url "$BaseUrl/exams/108" -Body $UpdatePublishedExamBody -ExpectedStatus @(409)

Invoke-TestRequest -Name "Publish Draft Exam" -Method "PATCH" -Url "$BaseUrl/exams/103/status" -Body $PublishStatusBody -ExpectedStatus @(200)
Invoke-TestRequest -Name "Close Published Exam" -Method "PATCH" -Url "$BaseUrl/exams/108/status" -Body $CloseStatusBody -ExpectedStatus @(200)
Invoke-TestRequest -Name "Invalid Draft To Closed" -Method "PATCH" -Url "$BaseUrl/exams/104/status" -Body $CloseStatusBody -ExpectedStatus @(400)
Invoke-TestRequest -Name "Closed To Draft" -Method "PATCH" -Url "$BaseUrl/exams/116/status" -Body $DraftStatusBody -ExpectedStatus @(400)

Invoke-TestRequest -Name "Get Exam Questions Without Answers" -Method "GET" -Url "$BaseUrl/exams/109/questions" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Get Exam Questions With Answers" -Method "GET" -Url "$BaseUrl/exams/109/questions?includeAnswers=true" -ExpectedStatus @(200)
Invoke-TestRequest -Name "Get Missing Exam Questions" -Method "GET" -Url "$BaseUrl/exams/not-found/questions" -ExpectedStatus @(404)

Invoke-TestRequest -Name "Create Exam Invalid Duration" -Method "POST" -Url "$BaseUrl/exams" -Body $InvalidDurationExamBody -ExpectedStatus @(400)
Invoke-TestRequest -Name "Create Exam Invalid UUID" -Method "POST" -Url "$BaseUrl/exams" -Body $InvalidUuidExamBody -ExpectedStatus @(400)
Invoke-TestRequest -Name "List Exams Invalid Status Filter" -Method "GET" -Url "$BaseUrl/exams?status=abc" -ExpectedStatus @(400)

Write-Host ""
Write-Host "Finished API checks." -ForegroundColor Magenta
