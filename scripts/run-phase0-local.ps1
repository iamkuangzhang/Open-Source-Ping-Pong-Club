param(
    [Parameter(Mandatory = $true)]
    [string]$AdminPasswordBase64
)

$ErrorActionPreference = 'Stop'
$psqlCommand = Get-Command psql -ErrorAction SilentlyContinue
$psql = if ($psqlCommand) { $psqlCommand.Source } else { (Get-ChildItem 'C:\Program Files\PostgreSQL' -Recurse -Filter psql.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName) }
if (-not $psql -or -not (Test-Path $psql)) { throw 'psql.exe was not found.' }

$adminPassword = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($AdminPasswordBase64))
$bytes = New-Object byte[] 24
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$projectPassword = ([System.BitConverter]::ToString($bytes)).Replace('-', '')
$jwtBytes = New-Object byte[] 32
$jwtRng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
$jwtSecret = ([System.BitConverter]::ToString($jwtBytes)).Replace('-', '')

$env:PGPASSWORD = $adminPassword
try {
    & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c "ALTER ROLE opensource_pingpong PASSWORD '$projectPassword';" | Out-Null
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/opensource_pingpong_dev'
$env:SPRING_DATASOURCE_USERNAME = 'opensource_pingpong'
$env:SPRING_DATASOURCE_PASSWORD = $projectPassword
$env:SPRING_DATA_REDIS_HOST = '127.0.0.1'
$env:SPRING_DATA_REDIS_PORT = '6379'
$env:APP_JWT_SECRET = $jwtSecret

$backend = Join-Path $PSScriptRoot '..\backend'
$localEnv = Join-Path $backend '.env.local'
[System.IO.File]::WriteAllLines($localEnv, @(
    'SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/opensource_pingpong_dev',
    'SPRING_DATASOURCE_USERNAME=opensource_pingpong',
    "SPRING_DATASOURCE_PASSWORD=$projectPassword",
    'SPRING_DATA_REDIS_HOST=127.0.0.1',
    'SPRING_DATA_REDIS_PORT=6379',
    "APP_JWT_SECRET=$jwtSecret"
), [System.Text.UTF8Encoding]::new($false))
$log = Join-Path $backend 'phase0-startup.log'
Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
$process = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'mvnw.cmd spring-boot:run > phase0-startup.log 2>&1' -WorkingDirectory $backend -WindowStyle Hidden -PassThru

for ($attempt = 1; $attempt -le 30; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
        [pscustomobject]@{ processId = $process.Id; health = $health } | ConvertTo-Json -Depth 8 -Compress
        exit 0
    } catch {
        if ($process.HasExited) { break }
    }
}

throw "Spring Boot did not become healthy. Inspect $log without exposing credentials."
