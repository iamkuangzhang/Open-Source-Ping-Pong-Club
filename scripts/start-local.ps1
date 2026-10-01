$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot '..\backend'
$localEnv = Join-Path $backend '.env.local'
if (-not (Test-Path $localEnv)) { throw 'Missing backend/.env.local. Run run-phase0-local.ps1 once to provision local credentials.' }

Get-Content $localEnv | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}

Push-Location $backend
try {
    & .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
