param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$OperatorToken = $env:AGENTIC_OPERATOR_TOKEN,
    [string]$OperatorId = 'local-reviewer',
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if ([string]::IsNullOrWhiteSpace($OperatorToken)) { throw 'Supply -OperatorToken matching AGENTIC_OPERATOR_TOKEN on the running application' }
if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 120) { throw 'TimeoutSeconds must be between 1 and 120' }
$headers = @{ 'X-Operator-Id' = $OperatorId; 'X-Operator-Token' = $OperatorToken }

function Submit-Requirement {
    param([string]$Requirement, [string]$Repository)
    $body = @{ requirement=$Requirement; repositoryPath=$Repository } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows" -ContentType 'application/json' -Body $body
}
function Wait-Workflow {
    param([string]$Id, [string]$ExpectedState)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $current = Invoke-RestMethod "$BaseUrl/api/v1/workflows/$Id"
        if ($current.workflow.status -eq $ExpectedState) { return $current }
        if ($current.workflow.status -in @('SAFE_STOPPED','FAILED','CANCELLED')) {
            $current | ConvertTo-Json -Depth 15 | Write-Output
            throw "Workflow $Id stopped in $($current.workflow.status)"
        }
        Start-Sleep -Milliseconds 300
    }
    throw "Workflow $Id did not reach $ExpectedState before timeout"
}
function Assert-Rejected {
    param([string]$Uri, [string]$Body, [int]$Status)
    try { $null = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Uri -ContentType 'application/json' -Body $Body -ErrorAction Stop }
    catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Status) { return }
        throw
    }
    throw "Expected HTTP $Status but the request succeeded"
}
function Assert-Gated {
    param($Workflow)
    if ($Workflow.sourceMutationAllowed -or $Workflow.executionEnabled) { throw 'Engineering execution unexpectedly enabled' }
    $implementation = @($Workflow.tasks | Where-Object { $_.role -eq 'IMPLEMENTATION' })
    if (@($implementation | Where-Object { $_.state -ne 'PENDING' }).Count -ne 0) { throw 'Engineering task ran before its gate' }
}

$green = Submit-Requirement 'Create a URL-shortener with HTTP 302 redirects' 'greenfield-url-shortener'
$green = Wait-Workflow $green.workflow.id 'AWAITING_CHANGE_APPROVAL'
Assert-Gated $green
if (-not $green.intelligence.repository.greenfield -or $green.intelligence.attempts.Count -ne 4) { throw 'Greenfield analysis evidence missing' }

$brown = Submit-Requirement 'Add total and UTC daily analytics to the URL-shortener' 'url-shortener'
$brown = Wait-Workflow $brown.workflow.id 'AWAITING_CHANGE_APPROVAL'
Assert-Gated $brown
if ($brown.intelligence.repository.greenfield -or $brown.intelligence.repository.dataFlows.Count -eq 0) { throw 'Brownfield data-flow evidence missing' }
if ($brown.intelligence.planHash -eq $green.intelligence.planHash) { throw 'Different requirements produced identical plans' }

$ambiguous = Submit-Requirement 'Create a URL-shortener with expiry' 'greenfield-url-shortener'
$ambiguous = Wait-Workflow $ambiguous.workflow.id 'AWAITING_CLARIFICATION'
Assert-Gated $ambiguous
if ($null -ne $ambiguous.intelligence.repository -or $null -ne $ambiguous.intelligence.plan) { throw 'Ambiguity gate was bypassed' }
$clarification = @{ expectedRevision=1; answers=@{ 'Q-EXPIRY'='24 hours' } } | ConvertTo-Json -Depth 5
Assert-Rejected "$BaseUrl/api/v1/workflows/$($ambiguous.workflow.id)/clarifications" $clarification 401
$null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows/$($ambiguous.workflow.id)/clarifications" `
    -Headers $headers -ContentType 'application/json' -Body $clarification
$clarified = Wait-Workflow $ambiguous.workflow.id 'AWAITING_CHANGE_APPROVAL'
Assert-Gated $clarified
if ($clarified.workflow.currentRevision -ne 2 -or $clarified.revision.parentRevisionId -ne $ambiguous.revision.id `
        -or $clarified.intelligence.invalidatedArtifactIds.Count -lt 2) { throw 'Clarification revision lineage or invalidation missing' }

$replan = @{ expectedRevision=1; reason='Additional requested behavior'; requirement='Add total and UTC daily analytics and deactivation to URL-shortener' } | ConvertTo-Json
$null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/workflows/$($brown.workflow.id)/replan" `
    -Headers $headers -ContentType 'application/json' -Body $replan
$replanned = Wait-Workflow $brown.workflow.id 'AWAITING_CHANGE_APPROVAL'
Assert-Gated $replanned
if ($replanned.workflow.currentRevision -ne 2 -or $replanned.intelligence.planHash -eq $brown.intelligence.planHash `
        -or $replanned.intelligence.reusedArtifactIds.Count -ne 1) { throw 'Replanning or verified repository reuse missing' }
Assert-Rejected "$BaseUrl/api/v1/workflows" '{"requirement":"Create shortener","repositoryPath":"greenfield-url-shortener","output":"Implementation completed."}' 400

Write-Output 'Intelligence checks passed: automatic dynamic plans; brownfield reasoning; ambiguity gate; authenticated clarification; revision invalidation; verified reuse; no engineering mutation.'
foreach ($result in @($green,$brown,$ambiguous,$clarified,$replanned)) {
    [pscustomobject]@{
        workflowId=$result.workflow.id; revision=$result.workflow.currentRevision; status=$result.workflow.status
        planHash=$result.intelligence.planHash; criteria=$result.intelligence.requirement.criteria
        questions=$result.intelligence.requirement.questions; repository=$result.intelligence.repository
        tasks=$result.intelligence.plan.tasks; attempts=$result.intelligence.attempts
        invalidated=$result.intelligence.invalidatedArtifactIds; reused=$result.intelligence.reusedArtifactIds
    } | ConvertTo-Json -Depth 15
}
