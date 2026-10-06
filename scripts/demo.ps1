param(
    [Parameter(Position=0)][ValidateSet('greenfield','brownfield','ambiguous','repair','safe-stop')][string]$Scenario='greenfield',
    [string]$BaseUrl='http://localhost:8080',
    [string]$OperatorToken=$env:AGENTIC_OPERATOR_TOKEN,
    [string]$WorkflowId,
    [string]$ApprovedPlanHash,
    [string]$ApprovedOutcomeHash,
    [ValidateSet('APPROVED','REJECTED')][string]$ReleaseDecision='APPROVED',
    [ValidateSet('301','302')][string]$Clarification,
    [int]$TimeoutSeconds=300
)
$ErrorActionPreference='Stop'
$BaseUrl=$BaseUrl.TrimEnd('/')
if($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 600) { throw 'TimeoutSeconds must be between 1 and 600' }
if(($ApprovedPlanHash -or $ApprovedOutcomeHash -or $Clarification) -and -not $WorkflowId) { throw 'Approval/clarification requires its exact WorkflowId' }
$headers=@{'X-Operator-Id'='demo-reviewer';'X-Operator-Token'=$OperatorToken}
function Send-Governed([string]$Action,$Body) {
    if([string]::IsNullOrWhiteSpace($OperatorToken)) { throw 'Supply the configured OperatorToken' }
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows/$WorkflowId/$Action" -Headers $headers -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 10)
}
function Show-Evidence($Result) {
    $Result | ConvertTo-Json -Depth 35 | Write-Output
}
if(-not $WorkflowId) {
    $requirement='Create a URL-shortener with HTTP 302 redirects'; $repository='greenfield-url-shortener'
    if($Scenario -eq 'ambiguous') { $requirement='Create a URL-shortener with HTTP 301 and HTTP 302 redirects' }
    if($Scenario -in @('brownfield','repair','safe-stop')) {
        $requirement='Add total and UTC daily click analytics to URL-shortener'
        $repository=switch($Scenario) { 'brownfield' {'url-shortener'} 'repair' {'repair-url-shortener'} 'safe-stop' {'safe-stop-url-shortener'} }
    }
    $submitted=Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows" -ContentType 'application/json' -Body (@{requirement=$requirement;repositoryPath=$repository} | ConvertTo-Json)
    $WorkflowId=$submitted.workflow.id
}
$timer=[Diagnostics.Stopwatch]::StartNew()
do {
    $workflow=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId"
    $state=$workflow.workflow.status
    if($state -in @('AWAITING_CHANGE_APPROVAL','AWAITING_CLARIFICATION','AWAITING_RELEASE_APPROVAL','RELEASE_READY','SAFE_STOPPED','ROLLED_BACK','CANCELLED','FAILED')) { break }
    if($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw "Workflow timed out. Inspect $WorkflowId before retrying." }
    Start-Sleep -Milliseconds 400
} while($true)
if($state -eq 'AWAITING_CLARIFICATION') {
    if(-not $Clarification) {
        Show-Evidence $workflow
        Write-Output "Ambiguity paused before source mutation. Review Q-REDIRECT and rerun:"
        Write-Output "powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 ambiguous -WorkflowId '$WorkflowId' -Clarification '302' -OperatorToken '<configured-token>'"
        return
    }
    $null=Send-Governed 'clarifications' @{expectedRevision=$workflow.workflow.currentRevision;answers=@{'Q-REDIRECT'=$Clarification}}
    $timer.Restart()
    do {
        $workflow=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId"
        if($workflow.workflow.status -eq 'AWAITING_CHANGE_APPROVAL') { break }
        if($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw 'Clarification planning timed out' }
        Start-Sleep -Milliseconds 300
    } while($true)
    $state=$workflow.workflow.status
}
if($state -eq 'AWAITING_CHANGE_APPROVAL') {
    if(-not $ApprovedPlanHash) {
        Show-Evidence $workflow.intelligence
        Write-Output "Review the exact plan, recovery scope and input hashes. WorkflowId: $WorkflowId"
        Write-Output "Exact plan hash: $($workflow.intelligence.planHash)"
        Write-Output "powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 $Scenario -WorkflowId '$WorkflowId' -ApprovedPlanHash '$($workflow.intelligence.planHash)' -OperatorToken '<configured-token>'"
        return
    }
    if($ApprovedPlanHash -cne $workflow.intelligence.planHash) { throw 'ApprovedPlanHash differs from current evidence' }
    $null=Send-Governed 'change-approvals' @{expectedRevision=$workflow.workflow.currentRevision;planHash=$ApprovedPlanHash;decision='APPROVED';reason='Reviewed exact plan, file scope and bounded recovery'}
    $timer.Restart()
    do {
        $workflow=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId"
        if($workflow.workflow.status -in @('AWAITING_RELEASE_APPROVAL','SAFE_STOPPED','FAILED','CANCELLED')) { break }
        if($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw "Engineering timed out. Inspect $WorkflowId before retrying." }
        Start-Sleep -Milliseconds 500
    } while($true)
    $state=$workflow.workflow.status
}
$result=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId/engineering"
if($Scenario -eq 'safe-stop') {
    if($state -ne 'SAFE_STOPPED' -or $null -ne $result.outcome -or @($result.rollbacks).Count -eq 0 -or @($result.rollbacks | Where-Object {-not $_.verified}).Count -ne 0 -or @($result.artifacts | Where-Object type -eq 'BUILD_EVIDENCE').Count -eq 0) { Show-Evidence $result; throw 'Safe-stop evidence gate failed' }
    Write-Output 'Safe stop passed: real compiler failure; unsupported repair; baseline restored; no release outcome.'
    Show-Evidence $result; return
}
if($state -notin @('AWAITING_RELEASE_APPROVAL','RELEASE_READY')) { Show-Evidence $result; throw "Workflow stopped: $state" }
if(-not $result.outcome.featureComplete -or $result.outcome.build.exitCode -ne 0 -or @($result.outcome.build.failedTests).Count -ne 0 -or -not $result.outcome.build.coverage.available) { Show-Evidence $result; throw 'Feature-completion evidence gate failed' }
if($Scenario -eq 'repair' -and @($result.recovery | Where-Object action -eq 'REPAIR').Count -eq 0) { throw 'Real repair evidence missing' }
foreach($criterion in $result.outcome.traceability) {
    if(@($criterion.productionPaths).Count -eq 0 -or @($criterion.testPaths).Count -eq 0 -or @($criterion.executedTests).Count -eq 0) { throw 'Criterion traceability incomplete' }
}
if($state -eq 'AWAITING_RELEASE_APPROVAL' -and $ApprovedOutcomeHash) {
    $candidate=@($result.artifacts | Where-Object type -eq 'ENGINEERING_OUTCOME')[-1]
    if($ApprovedOutcomeHash -cne $candidate.sha256) { throw 'ApprovedOutcomeHash differs from current evidence' }
    $null=Send-Governed 'release-approvals' @{expectedRevision=$workflow.workflow.currentRevision;outcomeHash=$ApprovedOutcomeHash;decision=$ReleaseDecision;reason='Reviewed exact engineering outcome, tests, risks and limitations'}
    $result=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$WorkflowId/engineering"
    if($ReleaseDecision -eq 'APPROVED' -and (-not $result.outcome.releaseReady -or @($result.outcome.gates | Where-Object {-not $_.passed}).Count -ne 0)) { throw 'Final release gate failed' }
    if($ReleaseDecision -eq 'REJECTED' -and ($null -ne $result.outcome -or @($result.rollbacks | Where-Object verified).Count -eq 0)) { throw 'Rejection restoration evidence missing' }
    Write-Output "Exact outcome decision recorded: $ReleaseDecision"
} elseif($state -eq 'AWAITING_RELEASE_APPROVAL') {
    $candidate=@($result.artifacts | Where-Object type -eq 'ENGINEERING_OUTCOME')[-1]
    Write-Output "$Scenario engineering passed: actual generated production compiled, tests executed, coverage and recovery persisted; release remains gated."
    Write-Output "Review the full outcome below, then approve or reject its exact hash: $($candidate.sha256)"
    Write-Output "powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 $Scenario -WorkflowId '$WorkflowId' -ApprovedOutcomeHash '$($candidate.sha256)' -ReleaseDecision APPROVED -OperatorToken '<configured-token>'"
}
Show-Evidence ([pscustomobject]@{workflowId=$WorkflowId;engineering=$result})