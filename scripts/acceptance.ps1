param(
    [string]$BaseUrl='http://localhost:18080',
    [string]$PeerBaseUrl='http://localhost:18081',
    [string]$ComposeProject='agentic-url-shortener',
    [string]$OperatorToken=$env:AGENTIC_OPERATOR_TOKEN,
    [switch]$ApproveFixtureOutcomes,
    [ValidateSet('greenfield','brownfield','ambiguous','repair','safe-stop','failover')]
    [string[]]$Scenarios=@('greenfield','brownfield','ambiguous','repair','safe-stop','failover')
)
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($OperatorToken)) { throw 'Configure AGENTIC_OPERATOR_TOKEN' }
$BaseUrl=$BaseUrl.TrimEnd('/'); $PeerBaseUrl=$PeerBaseUrl.TrimEnd('/')
$evidenceRoot=Join-Path $PSScriptRoot '..\runtime-evidence'
$null=New-Item -ItemType Directory -Force -Path $evidenceRoot
$headers=@{'X-Operator-Id'='acceptance-fixture-reviewer';'X-Operator-Token'=$OperatorToken}
function Wait-Workflow([string]$Id) {
    $deadline=(Get-Date).AddSeconds(600)
    do {
        $workflow=Invoke-RestMethod "$BaseUrl/api/v1/workflows/$Id"
        if($workflow.workflow.status -in @('AWAITING_CLARIFICATION','AWAITING_CHANGE_APPROVAL','AWAITING_RELEASE_APPROVAL','SAFE_STOPPED','FAILED')) { return $workflow }
        if((Get-Date) -ge $deadline) { throw "Timed out inspecting $Id" }
        Start-Sleep -Milliseconds 400
    } while($true)
}
# Running this harness authorizes exact plan approvals only for these disposable, platform-owned fixtures.
# This automated acceptance identity is recorded explicitly; it is not evidence of independent human code review.
foreach($scenario in $Scenarios) {
    $requirement='Create URL-shortener with HTTP 302 redirects'; $repository='greenfield-url-shortener'
    if($scenario -eq 'greenfield') { $requirement='Create URL-shortener with HTTP 302 redirects, aliases, expiresAt expiry timestamp, inspection, deactivation, total and UTC daily analytics, rate limit 60 requests per client per 60 seconds' }
    if($scenario -eq 'ambiguous') { $requirement='Create URL-shortener with HTTP 301 and HTTP 302 redirects' }
    if($scenario -in @('brownfield','repair','safe-stop')) {
        $requirement='Add total and UTC daily click analytics to URL-shortener'
        $repository=switch($scenario) {'brownfield' {'url-shortener'} 'repair' {'repair-url-shortener'} 'safe-stop' {'safe-stop-url-shortener'}}
    }
    $submitted=Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows" -ContentType 'application/json' -Body (@{requirement=$requirement;repositoryPath=$repository} | ConvertTo-Json)
    $id=$submitted.workflow.id; $workflow=Wait-Workflow $id
    if($scenario -eq 'ambiguous') {
        if($workflow.workflow.status -ne 'AWAITING_CLARIFICATION' -or $null -ne $workflow.intelligence.workspace) { throw 'Ambiguity did not stop before mutation' }
        $workflow | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $evidenceRoot "$scenario-paused.json")
        & "$PSScriptRoot\demo.ps1" ambiguous -BaseUrl $BaseUrl -WorkflowId $id -Clarification 302 -OperatorToken $OperatorToken -OperatorId 'acceptance-fixture-reviewer' *> (Join-Path $evidenceRoot "$scenario-clarification.log")
        $workflow=Wait-Workflow $id
    }
    if($workflow.workflow.status -ne 'AWAITING_CHANGE_APPROVAL') { throw "$scenario failed to produce a reviewable plan" }
    $workflow | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $evidenceRoot "$scenario-plan.json")
    & "$PSScriptRoot\demo.ps1" $scenario -BaseUrl $BaseUrl -PeerBaseUrl $PeerBaseUrl -ComposeProject $ComposeProject -WorkflowId $id -ApprovedPlanHash $workflow.intelligence.planHash -OperatorToken $OperatorToken -OperatorId 'acceptance-fixture-reviewer' -TimeoutSeconds 600 *> (Join-Path $evidenceRoot "$scenario-demo.log")
    $result=Invoke-RestMethod "$PeerBaseUrl/api/v1/workflows/$id/engineering"
    if($scenario -notin @('safe-stop','failover') -and $ApproveFixtureOutcomes) {
        $candidate=@($result.artifacts | Where-Object type -eq 'ENGINEERING_OUTCOME')[-1]
        $null=Invoke-RestMethod -Method Post -Uri "$PeerBaseUrl/api/v1/workflows/$id/release-approvals" -Headers $headers -ContentType 'application/json' -Body (@{expectedRevision=$workflow.workflow.currentRevision;outcomeHash=$candidate.sha256;decision='APPROVED';reason='Explicit automated acceptance-fixture outcome approval; not independent human review'} | ConvertTo-Json)
        $result=Invoke-RestMethod "$PeerBaseUrl/api/v1/workflows/$id/engineering"
        if(-not $result.outcome.releaseReady) { throw 'Exact fixture outcome approval failed' }
    }
    $result | ConvertTo-Json -Depth 40 | Set-Content (Join-Path $evidenceRoot "$scenario-engineering.json")
    Invoke-RestMethod "$PeerBaseUrl/api/v1/workflows/$id" | ConvertTo-Json -Depth 40 | Set-Content (Join-Path $evidenceRoot "$scenario-workflow.json")
    Write-Output "$scenario passed; workflow=$id; persisted evidence exported to runtime-evidence"
    if($scenario -eq 'failover') {
        $deadline=(Get-Date).AddSeconds(120)
        do {
            try { if((Invoke-RestMethod "$BaseUrl/actuator/health/readiness").status -eq 'UP') { break } } catch {}
            if((Get-Date) -ge $deadline) { throw 'Restart readiness timed out' }; Start-Sleep -Seconds 1
        } while($true)
    }
}
