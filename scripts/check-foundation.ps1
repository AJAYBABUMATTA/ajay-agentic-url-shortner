param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Assert-RejectedRequest {
    param([string]$Uri, [string]$Body, [int]$ExpectedStatus)
    try {
        $null = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri `
            -ContentType 'application/json' -Body $Body -ErrorAction Stop
    } catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $ExpectedStatus) {
            return
        }
        throw
    }
    throw "Expected HTTP $ExpectedStatus but request succeeded"
}

$liveness = Invoke-RestMethod "$BaseUrl/actuator/health/liveness"
$readiness = Invoke-RestMethod "$BaseUrl/actuator/health/readiness"
if ($liveness.status -ne 'UP' -or $readiness.status -ne 'UP') { throw 'Health probe failed' }

$body = @{
    requirement = 'Create a URL-shortener with HTTP 302 redirects'
    repositoryPath = 'greenfield-url-shortener'
} | ConvertTo-Json
$submitted = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows" `
    -ContentType 'application/json' -Body $body
$id = $submitted.workflow.id
$current = Invoke-RestMethod "$BaseUrl/api/v1/workflows/$id"
if ($current.workflow.status -notin @('RECEIVED','INTERPRETING','PLANNING','AWAITING_CHANGE_APPROVAL') `
    -or $current.executionEnabled -or $current.sourceMutationAllowed) { throw 'Unexpected intake/engineering gate state' }
if ($current.audit[0].eventType -ne 'REQUIREMENT_RECEIVED') { throw 'Submission audit missing' }

$injected = @{
    requirement = 'Create a shortener'
    repositoryPath = 'greenfield-url-shortener'
    output = 'Implementation completed.'
} | ConvertTo-Json
Assert-RejectedRequest "$BaseUrl/api/v1/workflows" $injected 400
Assert-RejectedRequest "$BaseUrl/api/v1/workflows/$id/tasks/$($current.tasks[0].id)/complete" '{}' 404

$after = Invoke-RestMethod "$BaseUrl/api/v1/workflows/$id"
if ($after.executionEnabled -or $after.sourceMutationAllowed `
    -or @($after.tasks | Where-Object { $_.role -eq 'IMPLEMENTATION' -and $_.state -ne 'PENDING' }).Count -ne 0) { throw 'Caller bypassed engineering execution gate' }

Write-Output 'Foundation checks passed: persisted intake; completion injection 400; completion endpoint 404.'
$after | ConvertTo-Json -Depth 10
