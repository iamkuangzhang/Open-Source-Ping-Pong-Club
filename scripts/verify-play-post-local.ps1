param(
  [string]$BaseUrl = "http://127.0.0.1:8080/api/v1"
)

$ErrorActionPreference = "Stop"
$runId = Get-Date -Format "yyyyMMddHHmmss"
$prefix = "bugfix_play_$runId"
$password = "LocalOnly!$([guid]::NewGuid().ToString('N').Substring(0, 16))"
Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()

function Invoke-ApiRaw([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
  $request = [System.Net.Http.HttpRequestMessage]::new(
    [System.Net.Http.HttpMethod]::new($Method),
    "$BaseUrl$Path"
  )
  if ($Token) {
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
  }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
  }
  try {
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $payload = if ($content) { $content | ConvertFrom-Json } else { $null }
    return [pscustomobject]@{ status = [int]$response.StatusCode; payload = $payload }
  } finally {
    $request.Dispose()
  }
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}

function Register-TestUser([int]$Index) {
  $response = Invoke-ApiRaw "POST" "/auth/register" @{
    account = "$prefix`_u$Index"
    password = $password
    nickname = "Play hardening user $Index"
  }
  Assert-True ($response.status -eq 200) "Registration failed for test user $Index"
  return $response.payload.data.accessToken
}

$tokens = 1..3 | ForEach-Object { Register-TestUser $_ }
$eventTime = [DateTimeOffset]::UtcNow.AddDays(1).ToString("o")
$create = Invoke-ApiRaw "POST" "/play-posts" @{
  title = "$prefix local HTTP verification"
  content = "Create/list/join/capacity/leave verification"
  visibility = "PUBLIC"
  maxParticipants = 2
  eventTime = $eventTime
  location = "Hardening test table"
  contact = "In-app notification"
} $tokens[0]
Assert-True ($create.status -eq 200) "Play post creation failed with HTTP $($create.status)"
$postId = $create.payload.data.id

$list = Invoke-ApiRaw "GET" "/play-posts"
Assert-True ($list.status -eq 200) "Play post list failed"
Assert-True (@($list.payload.data | Where-Object { $_.id -eq $postId }).Count -eq 1) "Created play post was not returned by the list API"

$join = Invoke-ApiRaw "POST" "/play-posts/$postId/participants" $null $tokens[0]
Assert-True ($join.status -eq 200) "First join failed"

$duplicate = Invoke-ApiRaw "POST" "/play-posts/$postId/participants" $null $tokens[0]
Assert-True ($duplicate.status -eq 409) "Duplicate join did not return HTTP 409"
Assert-True ($duplicate.payload.code -eq "PLAY_ALREADY_JOINED") "Duplicate join returned the wrong business code"

$secondJoin = Invoke-ApiRaw "POST" "/play-posts/$postId/participants" $null $tokens[1]
Assert-True ($secondJoin.status -eq 200) "Second participant could not join"

$full = Invoke-ApiRaw "POST" "/play-posts/$postId/participants" $null $tokens[2]
Assert-True ($full.status -eq 409) "Capacity overflow did not return HTTP 409"
Assert-True ($full.payload.code -eq "PLAY_POST_FULL") "Capacity overflow returned the wrong business code"

$leave = Invoke-ApiRaw "DELETE" "/play-posts/$postId/participants/me" $null $tokens[0]
Assert-True ($leave.status -eq 200) "Leaving the play post failed"

$joinAfterLeave = Invoke-ApiRaw "POST" "/play-posts/$postId/participants" $null $tokens[2]
Assert-True ($joinAfterLeave.status -eq 200) "Leaving did not release a participant place"

$detail = Invoke-ApiRaw "GET" "/play-posts/$postId"
Assert-True ($detail.status -eq 200) "Play post detail failed"
Assert-True ([int]$detail.payload.data.participant_count -eq 2) "Final participant count is not 2"
$httpClient.Dispose()

[pscustomobject]@{
  result = "PASS"
  testPrefix = $prefix
  postId = $postId
  create = "PASS"
  list = "PASS"
  join = "PASS"
  duplicateJoin = "PASS (409 PLAY_ALREADY_JOINED)"
  capacity = "PASS (409 PLAY_POST_FULL)"
  leaveAndRelease = "PASS"
} | ConvertTo-Json
