param(
    [string]$Serial = 'emulator-5554',
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$EvidenceDirectory = ''
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskEvidence = if ($EvidenceDirectory) { $EvidenceDirectory } else { Join-Path $taskWorkspace 'artifacts/now-playing' }
New-Item -ItemType Directory -Path $taskEvidence -Force | Out-Null
$taskResults = [System.Collections.Generic.List[object]]::new()

function Invoke-AuditAdb {
    param([string[]]$Arguments)
    $result = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Device command failed: $result" }
    return $result
}
function Get-AuditSession {
    param([string]$Name)
    $value = (Invoke-AuditAdb -Arguments @('shell', 'dumpsys', 'media_session')) -join "`n"
    Set-Content -LiteralPath (Join-Path $taskEvidence "$Name.txt") -Value $value
    return $value
}
function Assert-Audit {
    param([string]$Name, [bool]$Passed)
    $taskResults.Add([pscustomobject]@{ name = $Name; passed = $Passed })
    if (!$Passed) { throw "Media playback regression: $Name" }
}
function Start-AuditAction {
    param([string]$Action)
    Invoke-AuditAdb -Arguments @('shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.tan.gratify.dev/com.tan.media3.MediaPlaybackAuditActivity', '--es', 'audit_action', $Action) | Out-Null
}
function Send-AuditMediaKey {
    param([string]$Key)
    Invoke-AuditAdb -Arguments @('shell', 'cmd', 'media_session', 'dispatch', $Key) | Out-Null
    Start-Sleep -Milliseconds 800
}
function Wait-AuditPlayback {
    param([string]$Name, [string]$State, [int]$Attempts = 25, [string]$Description = '')
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $snapshot = Get-AuditSession $Name
        if ($snapshot -match "state=$State" -and (!$Description -or $snapshot -match [regex]::Escape($Description))) { return $snapshot }
        Start-Sleep -Milliseconds 200
    }
    return $snapshot
}

try {
    Invoke-AuditAdb -Arguments @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-AuditAdb -Arguments @('shell', 'cmd', 'media_session', 'dispatch', 'stop') | Out-Null
    Invoke-AuditAdb -Arguments @('shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.tan.gratify.dev/com.tan.media3.MediaPlaybackAuditActivity', '--ez', 'crossfade', 'false') | Out-Null
    $initial = Wait-AuditPlayback 'session-initial' 'PLAYING' 50
    Assert-Audit 'Initial metadata and playback' ($initial -match 'description=Audit Track 1, Gratify Test Artist' -and $initial -match 'state=PLAYING')

    Invoke-AuditAdb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    Start-Sleep -Seconds 1
    Assert-Audit 'Home preserves playback' ((Wait-AuditPlayback 'session-home' 'PLAYING') -match 'state=PLAYING')
    Send-AuditMediaKey 'next'
    Assert-Audit 'Next advances exactly one track' ((Wait-AuditPlayback 'session-next' 'PLAYING' -Description 'description=Audit Track 2,') -match 'description=Audit Track 2,')
    Send-AuditMediaKey 'pause'
    Assert-Audit 'System pause' ((Wait-AuditPlayback 'session-pause' 'PAUSED') -match 'state=PAUSED')
    Send-AuditMediaKey 'play'
    Assert-Audit 'System play' ((Wait-AuditPlayback 'session-play' 'PLAYING') -match 'state=PLAYING')
    Send-AuditMediaKey 'previous'
    Assert-Audit 'Previous returns to prior track' ((Wait-AuditPlayback 'session-previous-item' 'PLAYING' -Description 'description=Audit Track 1,') -match 'description=Audit Track 1,')

    Start-AuditAction 'finish'
    Start-Sleep -Seconds 1
    Assert-Audit 'Task removal stops playback even with legacy keep-alive preference' ((Get-AuditSession 'session-task-removed') -notmatch 'state=PLAYING')
    $service = (Invoke-AuditAdb -Arguments @('shell', 'dumpsys', 'activity', 'services', 'com.tan.gratify.dev')) -join "`n"
    Set-Content -LiteralPath (Join-Path $taskEvidence 'service-task-removed.txt') -Value $service
    Assert-Audit 'Task removal clears media playback foreground service' ($service -notmatch 'SimpleMediaService')
    Invoke-AuditAdb -Arguments @('shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.tan.gratify.dev/com.tan.media3.MediaPlaybackAuditActivity', '--ez', 'crossfade', 'false') | Out-Null
    Assert-Audit 'Fresh launch after task removal resumes normally' ((Wait-AuditPlayback 'session-after-task-removal' 'PLAYING') -match 'state=PLAYING')
    $notification = (Invoke-AuditAdb -Arguments @('shell', 'dumpsys', 'notification', '--noredact')) -join "`n"
    Set-Content -LiteralPath (Join-Path $taskEvidence 'notification.txt') -Value $notification
    Assert-Audit 'Native MediaStyle title, artist, bitmap artwork' ($notification -match 'Notification\$MediaStyle' -and
        $notification -match 'android.title=String \(Audit Track' -and $notification -match 'Gratify Test Artist' -and $notification -match 'android.largeIcon=Icon')

    Invoke-AuditAdb -Arguments @('shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.tan.gratify.dev/com.tan.media3.MediaPlaybackAuditActivity', '--ez', 'crossfade', 'true') | Out-Null
    Start-Sleep -Seconds 2
    Start-AuditAction 'seek_to_fade'
    $transitionFound = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ((Get-AuditSession 'session-crossfade-transition') -match 'description=Audit Track 2,') {
            $transitionFound = $true
            break
        }
        Start-Sleep -Milliseconds 100
    }
    Assert-Audit 'Crossfade publishes incoming track metadata' $transitionFound
    Send-AuditMediaKey 'pause'
    Start-Sleep -Seconds 1
    $paused = Get-AuditSession 'session-during-fade-pause'
    Assert-Audit 'Pause during fade retains incoming player' ($paused -match 'state=PAUSED' -and $paused -match 'description=Audit Track 2,')
    Send-AuditMediaKey 'play'
    Start-Sleep -Seconds 1
    $resumed = Get-AuditSession 'session-crossfade-resume'
    Assert-Audit 'Resume after cancelled fade plays without buffering' ($resumed -match 'state=PLAYING' -and $resumed -match 'description=Audit Track 2,')

    Start-AuditAction 'transient_focus'
    # Android may fade audio before delivering the focus-loss callback.
    Assert-Audit 'Transient audio focus loss pauses' ((Wait-AuditPlayback 'session-focus-loss' 'PAUSED' 13) -match 'state=PAUSED')
    Start-Sleep -Seconds 3
    Assert-Audit 'Audio focus gain resumes' ((Get-AuditSession 'session-focus-gain') -match 'state=PLAYING')
    Start-AuditAction 'transient_focus'
    Start-Sleep -Milliseconds 2300
    Send-AuditMediaKey 'pause'
    Start-Sleep -Seconds 3
    Assert-Audit 'User pause prevents automatic focus resume' ((Get-AuditSession 'session-focus-user-pause') -match 'state=PAUSED')
    Send-AuditMediaKey 'play'

    Start-AuditAction 'stop_on_exit'
    Start-Sleep -Seconds 1
    Assert-Audit 'Explicit stop-on-exit stops audio' ((Get-AuditSession 'session-stop-on-exit') -notmatch 'state=PLAYING')
    # Restore the fixture's default setting and leave no test audio playing.
    Invoke-AuditAdb -Arguments @('shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.tan.gratify.dev/com.tan.media3.MediaPlaybackAuditActivity', '--ez', 'crossfade', 'false') | Out-Null
    Assert-Audit 'Player and shared scope survive service recreation' ((Wait-AuditPlayback 'session-recreated' 'PLAYING') -match 'state=PLAYING')
    Send-AuditMediaKey 'stop'
} finally {
    $taskResults | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskEvidence 'verification.json')
}
Write-Output "$($taskResults.Count) native media regression checks passed on $Serial."

