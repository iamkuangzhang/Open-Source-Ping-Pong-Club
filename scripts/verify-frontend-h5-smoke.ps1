param(
  [string]$BaseUrl = "http://127.0.0.1:5176"
)

$ErrorActionPreference = "Stop"
$edgeCandidates = @(
  "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
  "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
)
$edge = $edgeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $edge) { throw "Microsoft Edge was not found" }

$artifactRoot = Join-Path $PSScriptRoot "..\.release-rc\frontend-smoke"
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$routes = @(
  @{ path = "pages/competition/index"; expected = "比赛" },
  @{ path = "pages/play/index"; expected = "约球" },
  @{ path = "pages/community/index"; expected = "社区" },
  @{ path = "pages/profile/index"; expected = "我的" },
  @{ path = "pages/auth/login"; expected = "登录" },
  @{ path = "pages/auth/register"; expected = "注册" },
  @{ path = "pages/competition/detail"; expected = "比赛详情" },
  @{ path = "pages/competition/manage"; expected = "赛事管理" },
  @{ path = "pages/play/detail"; expected = "约球详情" },
  @{ path = "pages/community/announcement-detail"; expected = "公告" },
  @{ path = "pages/community/market-detail"; expected = "二手详情" },
  @{ path = "pages/community/notifications"; expected = "通知" },
  @{ path = "pages/membership/card"; expected = "成员" },
  @{ path = "pages/rating/mine"; expected = "积分" },
  @{ path = "pages/admin/index"; expected = "管理" },
  @{ path = "pages/admin/membership-review"; expected = "成员审核" }
)

$index = 0
foreach ($route in $routes) {
  $index++
  $stdout = Join-Path $artifactRoot "route-$index.html"
  $stderr = Join-Path $artifactRoot "route-$index.log"
  $profile = Join-Path $artifactRoot "edge-profile-$index"
  $arguments = @(
    "--headless",
    "--disable-gpu",
    "--no-first-run",
    "--disable-extensions",
    "--user-data-dir=$profile",
    "--virtual-time-budget=3500",
    "--dump-dom",
    "$BaseUrl/#/$($route.path)"
  )
  Start-Process -FilePath $edge -ArgumentList $arguments -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -Wait
  $dom = Get-Content -LiteralPath $stdout -Raw
  if ($dom -notmatch [regex]::Escape($route.expected)) {
    throw "H5 route smoke failed: $($route.path)"
  }
  Write-Output "$($route.path)=PASS"
}
