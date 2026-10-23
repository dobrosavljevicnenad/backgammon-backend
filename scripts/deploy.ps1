param(
    [switch]$Frontend,
    [switch]$Backend,
    [string]$KeyPath = "$env:USERPROFILE\Downloads\backgammon-key (1).pem",
    [string]$EC2Host = "ubuntu@13.53.253.19",
    [string]$FrontendBucket = "backgammon-clarivate",
    [string]$DistributionId = "E3CDPYIEVNDLPZ"
)

$ErrorActionPreference = "Stop"

if (-not $Frontend -and -not $Backend) {
    $Frontend = $true
    $Backend = $true
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backendDir = Join-Path $projectRoot "backgammon-backend"
$frontendDir = Join-Path $projectRoot "backgammon-frontend"
$remoteTarget = "/home/ubuntu/app/backgammon-backend/target"

if ($Backend) {
    Write-Host "== Backend: build" -ForegroundColor Cyan
    Push-Location $backendDir
    try {
        & .\mvnw.cmd -B -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    } finally {
        Pop-Location
    }

    $jar = Join-Path $backendDir "target\backend-0.0.1-SNAPSHOT.jar"

    Write-Host "== Backend: upload JAR to EC2" -ForegroundColor Cyan
    scp -i $KeyPath $jar "${EC2Host}:/home/ubuntu/backend-new.jar"
    if ($LASTEXITCODE -ne 0) { throw "scp failed" }

    Write-Host "== Backend: swap JAR (old one kept as .prev) and restart service" -ForegroundColor Cyan
    $remoteCmd = "set -e; cp -f $remoteTarget/backend-0.0.1-SNAPSHOT.jar $remoteTarget/backend-0.0.1-SNAPSHOT.jar.prev || true; mv -f /home/ubuntu/backend-new.jar $remoteTarget/backend-0.0.1-SNAPSHOT.jar; sudo systemctl restart backgammon; sleep 8; systemctl is-active backgammon"
    ssh -i $KeyPath $EC2Host $remoteCmd
    if ($LASTEXITCODE -ne 0) { throw "Remote restart failed. Check: ssh -i <key> $EC2Host 'sudo journalctl -u backgammon -n 100'" }

    Write-Host "Backend deployed." -ForegroundColor Green
}

if ($Frontend) {
    Write-Host "== Frontend: build" -ForegroundColor Cyan
    Push-Location $frontendDir
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "Angular build failed" }
    } finally {
        Pop-Location
    }

    if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
        throw "AWS CLI is not installed. Install it with: winget install Amazon.AWSCLI (then run 'aws configure')"
    }

    $dist = Join-Path $frontendDir "dist\frontend\browser"

    Write-Host "== Frontend: sync to S3 bucket $FrontendBucket" -ForegroundColor Cyan
    aws s3 sync $dist "s3://$FrontendBucket" --delete
    if ($LASTEXITCODE -ne 0) { throw "S3 sync failed" }

    Write-Host "== Frontend: invalidate CloudFront cache" -ForegroundColor Cyan
    aws cloudfront create-invalidation --distribution-id $DistributionId --paths "/*" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "CloudFront invalidation failed" }

    Write-Host "Frontend deployed." -ForegroundColor Green
}
