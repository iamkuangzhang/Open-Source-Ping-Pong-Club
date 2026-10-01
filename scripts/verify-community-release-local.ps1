param(
  [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
  [string]$EnvFile = "backend/.env.local"
)

$ErrorActionPreference = "Stop"
$runId = Get-Date -Format "yyyyMMddHHmmss"
$prefix = "release_smoke_$runId"
$password = "LocalOnly!$([guid]::NewGuid().ToString('N').Substring(0, 16))"
Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}

function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
  if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token) }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 12 -Compress
    $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
  }
  try {
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    return [pscustomobject]@{
      status = [int]$response.StatusCode
      payload = if ($content) { $content | ConvertFrom-Json } else { $null }
    }
  } finally {
    $request.Dispose()
  }
}

function Assert-Api([object]$Response, [int]$Status, [string]$Message, [string]$Code = $null) {
  Assert-True ($Response.status -eq $Status) "$Message (expected HTTP $Status, got $($Response.status))"
  if ($Code) { Assert-True ($Response.payload.code -eq $Code) "$Message (expected $Code, got $($Response.payload.code))" }
}

function Read-LocalEnv([string]$Path) {
  $values = @{}
  Get-Content -LiteralPath $Path | ForEach-Object {
    if ($_ -and -not $_.StartsWith("#") -and $_ -match "^([^=]+)=(.*)$") { $values[$matches[1].Trim()] = $matches[2].Trim() }
  }
  return $values
}

function Find-Psql {
  $command = Get-Command psql -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  foreach ($candidate in @("C:\Program Files\PostgreSQL\18\bin\psql.exe", "C:\Program Files\PostgreSQL\17\bin\psql.exe")) {
    if (Test-Path -LiteralPath $candidate) { return $candidate }
  }
  throw "psql was not found"
}

$localEnv = Read-LocalEnv $EnvFile
$dataSourceUrl = $localEnv["SPRING_DATASOURCE_URL"]
Assert-True ($dataSourceUrl -match "^jdbc:postgresql://([^:/]+):([0-9]+)/([^?]+)") "Unsupported datasource URL"
$databaseHost = $matches[1]
$databasePort = $matches[2]
$databaseName = $matches[3]
$databaseUser = $localEnv["SPRING_DATASOURCE_USERNAME"]
$databasePassword = $localEnv["SPRING_DATASOURCE_PASSWORD"]
$psql = Find-Psql

function Invoke-ProjectSql([string]$Sql, [switch]$Scalar) {
  $sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) "opensource-pingpong-release-$([guid]::NewGuid().ToString('N')).sql"
  $env:PGPASSWORD = $databasePassword
  try {
    [System.IO.File]::WriteAllText($sqlFile, $Sql, [System.Text.UTF8Encoding]::new($false))
    $result = & $psql -h $databaseHost -p $databasePort -U $databaseUser -d $databaseName -X -v "ON_ERROR_STOP=1" -t -A -f $sqlFile
    if ($LASTEXITCODE -ne 0) { throw "psql failed with exit code $LASTEXITCODE" }
    if ($Scalar) { return ($result | Select-Object -First 1).Trim() }
  } finally {
    Remove-Item -LiteralPath $sqlFile -ErrorAction SilentlyContinue
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

function Register-User([int]$Index) {
  $registration = Invoke-Api "POST" "/auth/register" @{
    account = "$prefix`_u$Index"
    password = $password
    nickname = "Release smoke user $Index"
  }
  Assert-Api $registration 200 "Register user $Index"
  $me = Invoke-Api "GET" "/users/me" $null $registration.payload.data.accessToken
  Assert-Api $me 200 "Read user $Index profile"
  return [pscustomobject]@{ id = $me.payload.data.id; token = $registration.payload.data.accessToken }
}

function Assert-SameInstant([string]$Expected, [string]$Actual, [string]$Name) {
  $expectedInstant = [DateTimeOffset]::Parse($Expected).ToUniversalTime()
  $actualInstant = [DateTimeOffset]::Parse($Actual).ToUniversalTime()
  $difference = [Math]::Abs(($expectedInstant - $actualInstant).TotalMilliseconds)
  Assert-True ($difference -lt 1) "$Name changed during PostgreSQL/API round-trip: $Expected -> $Actual"
}

try {
  $anonymousCompetitionWrite = Invoke-Api "POST" "/admin/competitions" @{}
  Assert-Api $anonymousCompetitionWrite 401 "Anonymous competition write" "AUTH_TOKEN_EXPIRED"
  $anonymousPlayWrite = Invoke-Api "POST" "/play-posts" @{}
  Assert-Api $anonymousPlayWrite 401 "Anonymous play-post write" "AUTH_TOKEN_EXPIRED"
  $anonymousMarketWrite = Invoke-Api "POST" "/market-posts" @{}
  Assert-Api $anonymousMarketWrite 401 "Anonymous market-post write" "AUTH_TOKEN_EXPIRED"

  $manager = Register-User 1
  $seller = Register-User 2
  $other = Register-User 3

  $ordinaryCompetitionWrite = Invoke-Api "GET" "/admin/competitions/00000000-0000-0000-0000-000000000001/entries" $null $seller.token
  Assert-Api $ordinaryCompetitionWrite 403 "Ordinary user competition management" "PERMISSION_DENIED"

  Invoke-ProjectSql @"
insert into user_organization_roles(id,user_id,role_id,granted_by,granted_at)
select gen_random_uuid(), '$($manager.id)', id, null, current_timestamp
from organization_roles where code='PRESIDENT'
on conflict (user_id,role_id) do nothing;
"@

  $expiresAt = [DateTimeOffset]::UtcNow.AddDays(7).ToString("yyyy-MM-dd'T'HH:mm:ss.000000'Z'")
  $unauthorizedAnnouncement = Invoke-Api "POST" "/admin/announcements" @{ title = "$prefix denied"; content = "denied"; visibility = "PUBLIC" } $seller.token
  Assert-Api $unauthorizedAnnouncement 403 "Ordinary user announcement permission" "PERMISSION_DENIED"
  $announcement = Invoke-Api "POST" "/admin/announcements" @{ title = "$prefix announcement"; content = "Release announcement"; visibility = "PUBLIC"; expiresAt = $expiresAt } $manager.token
  Assert-Api $announcement 200 "Create announcement"
  $announcementId = $announcement.payload.data.id
  $announcementDetail = Invoke-Api "GET" "/announcements/$announcementId"
  Assert-Api $announcementDetail 200 "Read announcement"
  $announcementDbTime = Invoke-ProjectSql "select expires_at from community_posts where id='$announcementId';" -Scalar
  Assert-SameInstant $expiresAt $announcementDetail.payload.data.expires_at "Announcement expiry API"
  Assert-SameInstant $expiresAt $announcementDbTime "Announcement expiry database"

  $eventTime = [DateTimeOffset]::UtcNow.AddDays(2).ToString("yyyy-MM-dd'T'HH:mm:ss.000000'Z'")
  $play = Invoke-Api "POST" "/play-posts" @{ title = "$prefix play"; content = "Time round-trip"; visibility = "PUBLIC"; maxParticipants = 4; eventTime = $eventTime; location = "Release table"; contact = "In-app" } $seller.token
  Assert-Api $play 200 "Create play post"
  $playId = $play.payload.data.id
  $playDetail = Invoke-Api "GET" "/play-posts/$playId"
  Assert-Api $playDetail 200 "Read play post"
  $playDbTime = Invoke-ProjectSql "select event_time from community_posts where id='$playId';" -Scalar
  Assert-SameInstant $eventTime $playDetail.payload.data.event_time "Play time API"
  Assert-SameInstant $eventTime $playDbTime "Play time database"

  $market = Invoke-Api "POST" "/market-posts" @{ title = "$prefix blade"; content = "Release marketplace smoke"; visibility = "PUBLIC"; price = "650"; contact = "In-app" } $seller.token
  Assert-Api $market 200 "Create market post"
  $marketId = $market.payload.data.id
  $marketList = Invoke-Api "GET" "/market-posts"
  Assert-Api $marketList 200 "List market posts"
  Assert-True (@($marketList.payload.data | Where-Object { $_.id -eq $marketId }).Count -eq 1) "Created market post was not listed"
  $marketDetail = Invoke-Api "GET" "/market-posts/$marketId"
  Assert-Api $marketDetail 200 "Read market post"
  $marketDbTime = Invoke-ProjectSql "select created_at from community_posts where id='$marketId';" -Scalar
  Assert-SameInstant $marketDetail.payload.data.created_at $marketDbTime "Market publish time"
  $marketUpdate = Invoke-Api "PUT" "/market-posts/$marketId" @{ title = "$prefix blade updated"; content = "Updated by seller"; visibility = "PUBLIC"; price = "620"; contact = "In-app" } $seller.token
  Assert-Api $marketUpdate 200 "Seller update market post"
  $otherUpdate = Invoke-Api "PUT" "/market-posts/$marketId" @{ title = "unauthorized"; content = "unauthorized"; visibility = "PUBLIC"; price = "1" } $other.token
  Assert-Api $otherUpdate 403 "Non-seller update" "MARKET_POST_NOT_OWNER"
  $otherSold = Invoke-Api "POST" "/market-posts/$marketId/mark-sold" $null $other.token
  Assert-Api $otherSold 403 "Non-seller mark sold" "MARKET_POST_NOT_OWNER"
  $sellerSold = Invoke-Api "POST" "/market-posts/$marketId/mark-sold" $null $seller.token
  Assert-Api $sellerSold 200 "Seller mark sold"
  $soldDetail = Invoke-Api "GET" "/market-posts/$marketId"
  Assert-True ($soldDetail.payload.data.status -eq "SOLD") "Market post status is not SOLD"

  $application = Invoke-Api "POST" "/membership/applications" @{ realName = "Release User"; studentNumber = "RC$runId"; college = "Release College"; major = "Table Tennis"; grade = "2026"; phone = "13000000002"; reason = "Release notification smoke" } $seller.token
  Assert-Api $application 200 "Submit membership application"
  $approval = Invoke-Api "POST" "/admin/membership/applications/$($application.payload.data.id)/approve" @{ comment = "Release approved" } $manager.token
  Assert-Api $approval 200 "Approve membership application"
  $notifications = Invoke-Api "GET" "/notifications" $null $seller.token
  Assert-Api $notifications 200 "Read notifications"
  $notification = @($notifications.payload.data | Where-Object { $_.notification_type -eq "MEMBERSHIP_REVIEW" })[0]
  Assert-True ($null -ne $notification) "Membership review did not create a notification"
  $otherRead = Invoke-Api "POST" "/notifications/$($notification.id)/read" $null $other.token
  Assert-Api $otherRead 404 "User reading another user's notification" "NOTIFICATION_NOT_FOUND"
  $singleRead = Invoke-Api "POST" "/notifications/$($notification.id)/read" $null $seller.token
  Assert-Api $singleRead 200 "Read one notification"
  $notificationsAfterRead = Invoke-Api "GET" "/notifications" $null $seller.token
  $readNotification = @($notificationsAfterRead.payload.data | Where-Object { $_.id -eq $notification.id })[0]
  Assert-True ($null -ne $readNotification.read_at) "Notification read_at was not returned"
  $notificationDbTime = Invoke-ProjectSql "select read_at from notifications where id='$($notification.id)';" -Scalar
  Assert-SameInstant $readNotification.read_at $notificationDbTime "Notification read_at"

  $rejectedApplication = Invoke-Api "POST" "/membership/applications" @{ realName = "Release Other"; studentNumber = "RCO$runId"; college = "Release College"; major = "Table Tennis"; grade = "2026"; phone = "13000000003"; reason = "Release read-all smoke" } $other.token
  Assert-Api $rejectedApplication 200 "Submit second membership application"
  $rejection = Invoke-Api "POST" "/admin/membership/applications/$($rejectedApplication.payload.data.id)/reject" @{ comment = "Release rejected" } $manager.token
  Assert-Api $rejection 200 "Reject membership application"
  $readAll = Invoke-Api "POST" "/notifications/read-all" $null $other.token
  Assert-Api $readAll 200 "Read all notifications"
  $unreadCount = Invoke-ProjectSql "select count(*) from notifications where user_id='$($other.id)' and read_at is null;" -Scalar
  Assert-True ([int]$unreadCount -eq 0) "read-all left unread notifications"

  [pscustomobject]@{
    result = "PASS"
    testPrefix = $prefix
    anonymousWrites = "PASS (401)"
    ordinaryAdminAccess = "PASS (403)"
    announcementPermissions = "PASS"
    marketOwnershipAndSold = "PASS"
    notificationOwnershipReadAndReadAll = "PASS"
    playTimeRoundTrip = "PASS"
    announcementTimeRoundTrip = "PASS"
    marketPublishTimeRoundTrip = "PASS"
    notificationReadTimeRoundTrip = "PASS"
  } | ConvertTo-Json
} finally {
  $httpClient.Dispose()
}
