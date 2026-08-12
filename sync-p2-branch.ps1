param(
    [Parameter(Mandatory = $true)]
    [string] $RepositoryUrl,

    [Parameter(Mandatory = $true)]
    [string] $Branch
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath '.git')) {
    throw 'Run this script from the repository root.'
}

$remoteName = 'p2-publish'
$existingRemote = git remote get-url $remoteName 2>$null
if ($LASTEXITCODE -eq 0) {
    git remote set-url $remoteName $RepositoryUrl
} else {
    git remote add $remoteName $RepositoryUrl
}

try {
    git push --set-upstream $remoteName "HEAD:refs/heads/$Branch"
    if ($LASTEXITCODE -ne 0) {
        throw 'Git push failed.'
    }
} finally {
    git remote remove $remoteName 2>$null
}
