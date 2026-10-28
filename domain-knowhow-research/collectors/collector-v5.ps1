# Channel-B collector v5: ensearch=1 + redirect decode (u=a1 base64url) + jina fallback
$ErrorActionPreference = "Continue"
$UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$queries = [System.IO.File]::ReadAllLines("$dir\queries.txt", [System.Text.Encoding]::UTF8) | Where-Object { $_.Trim() -ne "" }
function Decode-Bing($ckUrl) {
  $ckUrl = $ckUrl -replace '&amp;','&'
  $m = [regex]::Match($ckUrl, '[?&]u=a1([A-Za-z0-9\-_]+)')
  if (-not $m.Success) { return $null }
  $b64 = $m.Groups[1].Value.Replace('-', '/').Replace('_', '=')
  switch ($b64.Length % 4) { 2 { $b64 += '==' } 3 { $b64 += '=' } }
  try { return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) } catch { return $null }
}
function Get-Text($u) {
  try {
    $p = Invoke-WebRequest -Uri $u -UserAgent $UA -TimeoutSec 20 -UseBasicParsing
    $t = [System.Text.Encoding]::UTF8.GetString($p.RawContentStream.ToArray())
    if ($t.Length -gt 300) { return ,@($t, $p.StatusCode, "direct") }
  } catch {}
  try {
    $p2 = Invoke-WebRequest -Uri ("https://r.jina.ai/" + $u) -TimeoutSec 25 -UseBasicParsing
    $t2 = [System.Text.Encoding]::UTF8.GetString($p2.RawContentStream.ToArray())
    if ($t2.Length -gt 300) { return ,@($t2, $p2.StatusCode, "jina") }
  } catch {}
  return $null
}
$seen = @{}
$n = 0
foreach ($q in $queries) {
  $qEnc = [uri]::EscapeDataString($q)
  try {
    $r = Invoke-WebRequest -Uri "https://www.bing.com/search?q=$qEnc&ensearch=1&count=12" -UserAgent $UA -TimeoutSec 20 -UseBasicParsing
    $urls = [regex]::Matches($r.Content, 'href="(https?://www\.bing\.com/ck/[^"]+)"') |
      ForEach-Object { Decode-Bing $_.Groups[1].Value } |
      Where-Object { $_ -and $_ -notmatch 'bing\.com|microsoft\.com|msn\.com' } |
      Select-Object -Unique -First 4
    foreach ($u in $urls) {
      if ($seen[$u]) { continue }
      $seen[$u] = $true
      $res = Get-Text $u
      if (-not $res) { Write-Output "SKIP-BLOCKED: $u"; continue }
      $n++
      $host2 = ([uri]$u).Host -replace '[^a-zA-Z0-9\-]','-'
      $slug = "raw-$n-$host2"
      $txt = [string]$res[0] -replace '(?s)<script.*?</script>','' -replace '(?s)<style.*?</style>','' -replace '<[^>]+>',' ' -replace '\s{2,}'," `n"
      if ($txt.Length -gt 8000) { $txt = $txt.Substring(0, 8000) }
      $meta = "# RAW-$n`n- URL: $u`n- Query: $q`n- Channel: B (Bing ensearch, fetch=$($res[2]))`n- Date: $(Get-Date -Format 'yyyy-MM-dd')`n- HTTP: $($res[1])`n`n---`n`n"
      [System.IO.File]::WriteAllText("$dir\$slug.md", $meta + $txt, (New-Object System.Text.UTF8Encoding($true)))
      Write-Output "SAVED: $slug.md <- $u ($($res[2]))"
      Start-Sleep -Milliseconds 600
    }
  } catch { Write-Output "SEARCH-FAIL: $q -> $($_.Exception.Message)" }
  Start-Sleep -Milliseconds 600
}
Write-Output "DONE total=$n unique=$($seen.Count)"
