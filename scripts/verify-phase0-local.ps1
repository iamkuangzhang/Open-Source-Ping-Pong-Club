param(
    [Parameter(Mandatory = $true)]
    [string]$AdminUser,
    [Parameter(Mandatory = $true)]
    [string]$AdminPassword,
    [Parameter(Mandatory = $true)]
    [string]$ApplicationUser,
    [Parameter(Mandatory = $true)]
    [string]$ApplicationPassword,
    [Parameter(Mandatory = $true)]
    [string]$DatabaseName,
    [Parameter(Mandatory = $true)]
    [string]$PostgresHost,
    [Parameter(Mandatory = $true)]
    [int]$PostgresPort,
    [Parameter(Mandatory = $true)]
    [string]$RedisHost,
    [Parameter(Mandatory = $true)]
    [int]$RedisPort
)

$ErrorActionPreference = 'Stop'
if ($DatabaseName -notmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$') {
    throw 'DatabaseName must start with a letter and contain only letters, digits, or underscores (maximum 63 characters).'
}
$psqlCommand = Get-Command psql -ErrorAction SilentlyContinue
$psql = if ($psqlCommand) { $psqlCommand.Source } else { (Get-ChildItem 'C:\Program Files\PostgreSQL' -Recurse -Filter psql.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName) }
if (-not (Test-Path $psql)) { throw 'psql.exe was not found. Install PostgreSQL client tools or add psql to PATH.' }
$redisCli = (Get-Command redis-cli -ErrorAction Stop).Source

$env:PGPASSWORD = $AdminPassword
& $psql -w -h $PostgresHost -p $PostgresPort -U $AdminUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $DatabaseName OWNER $ApplicationUser;"
Remove-Item Env:PGPASSWORD

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${PostgresHost}:$PostgresPort/$DatabaseName"
$env:SPRING_DATASOURCE_USERNAME = $ApplicationUser
$env:SPRING_DATASOURCE_PASSWORD = $ApplicationPassword
$env:SPRING_DATA_REDIS_HOST = $RedisHost
$env:SPRING_DATA_REDIS_PORT = $RedisPort

& $redisCli -h $RedisHost -p $RedisPort PING
Push-Location "$PSScriptRoot\..\backend"
try {
    & .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
