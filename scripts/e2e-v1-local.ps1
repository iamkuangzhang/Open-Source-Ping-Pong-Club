param(
  [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
  [string]$EnvFile = "backend/.env.local",
  [int]$Seed = 202601,
  [switch]$UiDemo
)

$ErrorActionPreference = "Stop"
$demo = if ($UiDemo) { Get-Content -LiteralPath (Join-Path $PSScriptRoot "ui-demo-v22-data.json") -Raw -Encoding UTF8 | ConvertFrom-Json } else { $null }

function Read-LocalEnv([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    throw "Local env file not found: $Path"
  }
  $values = @{}
  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
      $values[$matches[1].Trim()] = $matches[2].Trim()
    }
  }
  return $values
}

function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
  $headers = @{}
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }
  $options = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
    ContentType = "application/json"
  }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 20
    $options.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
  }
  try {
    $response = Invoke-RestMethod @options
  } catch {
    $payload = $null
    $raw = $null
    try { $payload = $_.ErrorDetails.Message | ConvertFrom-Json } catch {}
    if (-not $payload -and $_.Exception.Response) {
      try {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $raw = $reader.ReadToEnd()
        $reader.Dispose()
        if ($raw) { $payload = $raw | ConvertFrom-Json }
      } catch {}
    }
    if ($payload -and $payload.code) {
      throw "$Method $Path failed: $($payload.code) $($payload.message)"
    }
    if ($raw) { throw "$Method $Path failed: $raw" }
    throw
  }
  if (-not $response.success) {
    throw "$Method $Path failed: $($response.code) $($response.message)"
  }
  return $response.data
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}

function Find-Psql {
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidates = @(
    "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe"
  )
  foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate) { return $candidate }
  }
  throw "psql was not found"
}

function Invoke-ProjectSql([string]$Sql) {
  $env = Read-LocalEnv $EnvFile
  $url = $env["SPRING_DATASOURCE_URL"]
  if (-not $url -or $url -notmatch "^jdbc:postgresql://([^:/]+):([0-9]+)/([^?]+)") {
    throw "SPRING_DATASOURCE_URL is missing or unsupported"
  }
  $psql = Find-Psql
  $dbHost = $matches[1]
  $dbPort = $matches[2]
  $dbName = $matches[3]
  $dbUser = $env["SPRING_DATASOURCE_USERNAME"]
  $dbPassword = $env["SPRING_DATASOURCE_PASSWORD"]
  if (-not $dbUser -or -not $dbPassword) { throw "Datasource username/password missing in local env" }
  $env:PGPASSWORD = $dbPassword
  $sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) "opensource-pingpong-$([guid]::NewGuid().ToString('N')).sql"
  try {
    [System.IO.File]::WriteAllText($sqlFile, $Sql, [System.Text.UTF8Encoding]::new($false))
    & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v "ON_ERROR_STOP=1" -q -f $sqlFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "psql failed with exit code $LASTEXITCODE" }
  } finally {
    Remove-Item -LiteralPath $sqlFile -ErrorAction SilentlyContinue
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

function Register-User([string]$Account, [string]$Password, [string]$Nickname) {
  try {
    $tokens = Invoke-Api "POST" "/auth/register" @{ account = $Account; password = $Password; nickname = $Nickname }
  } catch {
    $tokens = Invoke-Api "POST" "/auth/login" @{ account = $Account; password = $Password }
  }
  $me = Invoke-Api "GET" "/users/me" $null $tokens.accessToken
  return [pscustomobject]@{ account = $Account; id = $me.id; accessToken = $tokens.accessToken; refreshToken = $tokens.refreshToken }
}

function Submit-Membership([object]$User, [int]$Index) {
  Invoke-Api "POST" "/membership/applications" @{
    realName = $(if ($UiDemo) { $demo.members[$Index - 1] } else { "E2E User $Index" })
    studentNumber = $(if ($UiDemo) { "UIDEMO$Index" } else { "E2E$Index" })
    college = $(if ($UiDemo) { $demo.college } else { "E2E College" })
    major = $(if ($UiDemo) { $demo.major } else { "Pingpong" })
    grade = "2026"
    phone = "130000000$Index"
    reason = $(if ($UiDemo) { $demo.reason } else { "V1 local e2e" })
  } $User.accessToken
}

function Approve-Membership([object]$Manager, [string]$ApplicationId) {
  Invoke-Api "POST" "/admin/membership/applications/$ApplicationId/approve" @{ comment = "e2e approve" } $Manager.accessToken | Out-Null
}

function Create-Competition([object]$Manager, [string]$Title, [string]$Format, [string]$Type, [int]$MaxParticipants) {
  $now = [DateTimeOffset]::UtcNow
  return Invoke-Api "POST" "/admin/competitions" @{
    title = $Title
    description = $(if ($UiDemo) { $demo.competitionDescription } else { "Local E2E competition" })
    type = $Type
    format = $Format
    scope = "PUBLIC"
    review = $false
    maxParticipants = $MaxParticipants
    bestOf = 3
    location = $(if ($UiDemo) { $demo.defaultLocation } else { "E2E Table" })
    registrationStart = $now.AddMinutes(-5).ToString("o")
    registrationEnd = $now.AddMinutes(30).ToString("o")
    startTime = $now.AddMinutes(60).ToString("o")
  } $Manager.accessToken
}

function Run-All-Matches([object]$Manager, [string]$CompetitionId) {
  for ($round = 0; $round -lt 20; $round++) {
    $schedule = @(Invoke-Api "GET" "/competitions/$CompetitionId/schedule")
    $ready = @($schedule | Where-Object { $_.status -eq "READY" -and $_.player_a_user_id -and $_.player_b_user_id })
    if ($ready.Count -eq 0) { break }
    foreach ($match in $ready) {
      Invoke-Api "POST" "/admin/matches/$($match.id)/start" $null $Manager.accessToken | Out-Null
      $fresh = Invoke-Api "GET" "/matches/$($match.id)"
      Invoke-Api "PUT" "/admin/matches/$($match.id)/score" @{
        version = [int64]$fresh.version
        games = @(@{ a = 11; b = 8 }, @{ a = 11; b = 9 })
      } $Manager.accessToken | Out-Null
    }
  }
}

$runId = Get-Date -Format "yyyyMMddHHmmss"
$password = "E2eLocal!$runId"
$prefix = $(if ($UiDemo) { "ui_demo_$runId" } else { "e2e_v1_$runId" })

Invoke-Api "GET" "/competitions" | Out-Null

$users = @()
for ($i = 1; $i -le 8; $i++) {
  $nickname = $(if ($UiDemo) { $demo.members[$i - 1] } else { "E2E P$i" })
  $users += Register-User "$prefix`_p$i" $password $nickname
}
$manager = $users[0]

Invoke-ProjectSql @"
insert into user_organization_roles(id,user_id,role_id,granted_by,granted_at)
select gen_random_uuid(), u.id, r.id, null, current_timestamp
from users u, organization_roles r
where u.id = '$($manager.id)' and r.code = 'PRESIDENT'
on conflict (user_id, role_id) do nothing;
"@

$applications = @()
for ($i = 0; $i -lt $users.Count; $i++) {
  $applications += Submit-Membership $users[$i] ($i + 1)
}
foreach ($application in $applications) {
  Approve-Membership $manager $application.id
}

foreach ($user in $users) {
  $rating = Invoke-Api "GET" "/ratings/me" $null $user.accessToken
  Assert-True ($rating.current_rating -eq 1400) "Initial rating was not 1400 for $($user.account)"
}

$qr = Invoke-Api "POST" "/membership/me/qr" $null $manager.accessToken
$verification = Invoke-Api "GET" "/public/member-verifications/$($qr.token)"
Assert-True ($verification.status -eq "ACTIVE") "QR verification did not return ACTIVE"

$se = Create-Competition $manager $(if ($UiDemo) { $demo.finishedCompetitions[0] } else { "$prefix Rated Single Elimination" }) "SINGLE_ELIMINATION" "RATED" 8
Invoke-Api "POST" "/admin/competitions/$($se.id)/publish" $null $manager.accessToken | Out-Null
foreach ($user in $users) {
  Invoke-Api "POST" "/competitions/$($se.id)/entries" $null $user.accessToken | Out-Null
}
Invoke-Api "POST" "/admin/competitions/$($se.id)/close-registration" $null $manager.accessToken | Out-Null
Invoke-Api "POST" "/admin/competitions/$($se.id)/arrange" @{ seed = $Seed } $manager.accessToken | Out-Null
Invoke-Api "POST" "/admin/competitions/$($se.id)/start" $null $manager.accessToken | Out-Null
Run-All-Matches $manager $se.id
Invoke-Api "POST" "/admin/competitions/$($se.id)/enter-result-confirming" $null $manager.accessToken | Out-Null
Invoke-Api "POST" "/admin/competitions/$($se.id)/finalize" $null $manager.accessToken | Out-Null
for ($i = 0; $i -lt 9; $i++) {
  Invoke-Api "POST" "/admin/competitions/$($se.id)/finalize" $null $manager.accessToken | Out-Null
}

$finished = Invoke-Api "GET" "/competitions/$($se.id)"
Assert-True ($finished.status -eq "FINISHED") "Single elimination competition did not finish"
$ledger = Invoke-Api "GET" "/ratings/me/history" $null $manager.accessToken
Assert-True (@($ledger | Where-Object { $_.source_type -eq "MATCH" }).Count -gt 0) "Rating ledger did not contain MATCH rows"

$rr = Create-Competition $manager $(if ($UiDemo) { $demo.finishedCompetitions[1] } else { "$prefix Daily Round Robin" }) "ROUND_ROBIN" "DAILY" 4
Invoke-Api "POST" "/admin/competitions/$($rr.id)/publish" $null $manager.accessToken | Out-Null
foreach ($user in @($users | Select-Object -First 4)) {
  Invoke-Api "POST" "/competitions/$($rr.id)/entries" $null $user.accessToken | Out-Null
}
Invoke-Api "POST" "/admin/competitions/$($rr.id)/close-registration" $null $manager.accessToken | Out-Null
Invoke-Api "POST" "/admin/competitions/$($rr.id)/arrange" @{ seed = $Seed } $manager.accessToken | Out-Null
$rrSchedule = @(Invoke-Api "GET" "/competitions/$($rr.id)/schedule")
Assert-True ($rrSchedule.Count -eq 6) "Round robin did not create 6 matches for 4 players"
Invoke-Api "POST" "/admin/competitions/$($rr.id)/start" $null $manager.accessToken | Out-Null
Run-All-Matches $manager $rr.id
Invoke-Api "POST" "/admin/competitions/$($rr.id)/enter-result-confirming" $null $manager.accessToken | Out-Null
Invoke-Api "POST" "/admin/competitions/$($rr.id)/finalize" $null $manager.accessToken | Out-Null

if ($UiDemo) {
  foreach ($demoCompetition in $demo.openCompetitions) {
    $competition = Create-Competition $manager $demoCompetition.title $demoCompetition.format $demoCompetition.type $demoCompetition.max
    Invoke-Api "POST" "/admin/competitions/$($competition.id)/publish" $null $manager.accessToken | Out-Null
  }

  $demo.announcements | ForEach-Object {
    Invoke-Api "POST" "/admin/announcements" @{ title = $_.title; content = $_.content; visibility = "PUBLIC" } $manager.accessToken | Out-Null
  }

  $demo.playPosts | ForEach-Object {
    $playPost = $_
    $eventTime = [DateTimeOffset]::UtcNow.AddDays(2).ToString("o")
    Invoke-Api "POST" "/play-posts" @{ title = $playPost.title; content = $demo.playDescription; visibility = "PUBLIC"; maxParticipants = $playPost.max; location = $playPost.location; contact = $demo.contact; eventTime = $eventTime } $manager.accessToken | Out-Null
  }

  $demo.marketPosts | ForEach-Object {
    $marketPost = $_
    Invoke-Api "POST" "/market-posts" @{ title = $marketPost.title; content = $marketPost.content; visibility = "PUBLIC"; price = $marketPost.price; contact = $demo.contact } $manager.accessToken | Out-Null
  }
}

$leaderboard = Invoke-Api "GET" "/ratings/leaderboard"
Assert-True (@($leaderboard).Count -ge 8) "Leaderboard did not include E2E members"

[pscustomobject]@{
  runId = $runId
  users = $users.Count
  ratedSingleElimination = $se.id
  dailyRoundRobin = $rr.id
  qrStatus = $verification.status
  singleEliminationStatus = $finished.status
  result = "PASS"
} | ConvertTo-Json -Depth 5
