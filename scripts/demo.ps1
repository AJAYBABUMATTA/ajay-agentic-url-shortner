param(
    [Parameter(Position=0)][ValidateSet('greenfield')][string]$Scenario = 'greenfield',
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$OperatorToken = $env:AGENTIC_OPERATOR_TOKEN,
    [string]$WorkflowId,
    [string]$ApprovedPlanHash,
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 600) { throw 'TimeoutSeconds must be between 1 and 600' }
if ($ApprovedPlanHash -and -not $WorkflowId) { throw 'An exact approved hash requires its WorkflowId' }
if (-not $WorkflowId) {
    $body = @{ requirement='Create a URL-shortener with HTTP 302 redirects'; repositoryPath='greenfield-url-shortener' } | ConvertTo-Json
    $submitted = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows" -ContentType 'application/json' -Body $body
    $WorkflowId = $submitted.workflow.id
}
$timer = [Diagnostics.Stopwatch]::StartNew()
do {
    $workflow = Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId"
    if ($workflow.workflow.status -in @('SAFE_STOPPED','FAILED','CANCELLED','AWAITING_CLARIFICATION')) {
        $workflow | ConvertTo-Json -Depth 15 | Write-Output
        throw "Workflow stopped: $($workflow.workflow.status)"
    }
    if ($workflow.workflow.status -eq 'AWAITING_CHANGE_APPROVAL') { break }
    if ($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw 'Planning timed out' }
    Start-Sleep -Milliseconds 300
} while ($true)
if (-not $ApprovedPlanHash) {
    $workflow.intelligence.plan | ConvertTo-Json -Depth 15 | Write-Output
    Write-Output "Review the plan above. WorkflowId: $WorkflowId"
    Write-Output "Exact plan hash: $($workflow.intelligence.planHash)"
    Write-Output 'To approve this evidence and run generation, rerun with -WorkflowId and -ApprovedPlanHash using these exact values, plus -OperatorToken.'
    return
}
if ($ApprovedPlanHash -cne $workflow.intelligence.planHash) { throw 'ApprovedPlanHash does not match the current persisted plan' }
if ([string]::IsNullOrWhiteSpace($OperatorToken)) { throw 'Supply the configured operator token' }
$headers = @{ 'X-Operator-Id'='demo-reviewer'; 'X-Operator-Token'=$OperatorToken }
$approval = @{ expectedRevision=$workflow.workflow.currentRevision; planHash=$ApprovedPlanHash; decision='APPROVED'; reason='Human reviewed this exact greenfield plan through the API demo' } | ConvertTo-Json
$null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows/$WorkflowId/change-approvals" -Headers $headers -ContentType 'application/json' -Body $approval
$timer.Restart()
do {
    $result = Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId/engineering"
    if ($result.state -eq 'FAILED') {
        $result | ConvertTo-Json -Depth 20 | Write-Output
        throw "Engineering stopped: $($result.decision). Inspect persisted BUILD_EVIDENCE logs before retrying."
    }
    if ($result.state -eq 'SUCCEEDED') { break }
    if ($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw "Execution timed out; inspect workflow $WorkflowId before any retry" }
    Start-Sleep -Milliseconds 500
} while ($true)
$outcome = $result.outcome
if ($null -eq $outcome -or $outcome.releaseReady -or $outcome.build.exitCode -ne 0 -or $outcome.build.timedOut `
    -or $outcome.build.classification -ne 'NONE' -or @($outcome.build.discoveredTests).Count -ne 5 `
    -or @($outcome.build.failedTests).Count -ne 0 -or @($outcome.build.compiledProductionPaths).Count -ne 4 `
    -or -not $outcome.build.coverage.available -or @($outcome.traceability).Count -ne 2) { throw 'Vertical-slice evidence gate failed' }
foreach ($criterion in $outcome.traceability) {
    if (@($criterion.productionPaths).Count -eq 0 -or @($criterion.testPaths).Count -eq 0 -or @($criterion.executedTests).Count -eq 0) { throw 'Criterion traceability is incomplete' }
}
Write-Output 'Greenfield execution passed: agent proposals applied; 4 production files compiled; 5 HTTP tests executed; coverage persisted; release remains gated.'
[pscustomobject]@{ workflowId=$WorkflowId; state=$result.state; workspace=$result.workspace; outcome=$outcome;
    artifacts=@($result.artifacts | Select-Object id,taskId,type,sha256,inputHashes) } | ConvertTo-Json -Depth 20
