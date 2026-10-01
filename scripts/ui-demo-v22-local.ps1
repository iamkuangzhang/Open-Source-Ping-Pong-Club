param(
  [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
  [string]$EnvFile = "backend/.env.local",
  [int]$Seed = 202602
)

$ErrorActionPreference = "Stop"
$runner = Join-Path $PSScriptRoot "e2e-v1-local.ps1"
& $runner -BaseUrl $BaseUrl -EnvFile $EnvFile -Seed $Seed -UiDemo
