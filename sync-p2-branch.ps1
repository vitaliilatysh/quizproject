param(
    [Parameter(Mandatory = $true)]
    [string] $RepositoryUrl,

    [Parameter(Mandatory = $true)]
    [string] $Branch,

    [string] $PayloadZip = '.p2-payload.zip'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath '.git')) {
    throw 'Run this script from the repository root.'
}

if (Test-Path -LiteralPath $PayloadZip) {
    Expand-Archive -LiteralPath $PayloadZip -DestinationPath '.' -Force
}

$selfPath = $MyInvocation.MyCommand.Path
$payloadPath = Resolve-Path -LiteralPath $PayloadZip -ErrorAction SilentlyContinue

$remoteName = 'p2-publish'
$existingRemote = git remote get-url $remoteName 2>$null
if ($LASTEXITCODE -eq 0) {
    git remote set-url $remoteName $RepositoryUrl
} else {
    git remote add $remoteName $RepositoryUrl
}

try {
    git add --all
    git reset -- $selfPath 2>$null
    if ($payloadPath) {
        git reset -- $payloadPath 2>$null
    }

    git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        git commit -m 'Refactor P2 data reliability'
    }

    git push --set-upstream $remoteName "HEAD:refs/heads/$Branch"
    if ($LASTEXITCODE -ne 0) {
        throw 'Git push failed.'
    }
} finally {
    git remote remove $remoteName 2>$null
}
