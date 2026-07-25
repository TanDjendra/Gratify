Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "     Gratify Windows Launcher Installer" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

$SrcDir = Join-Path $PSScriptRoot "desktopApp\build\compose\binaries\main-release\app\Gratify"
$DestDir = Join-Path $env:USERPROFILE "AppData\Local\Programs\Gratify"

# Check if build exists, if not, compile it
$ExePath = Join-Path $SrcDir "Gratify.exe"
if (-not (Test-Path $ExePath)) {
    Write-Host "App build not found. Building Gratify desktop app first..." -ForegroundColor Yellow
    Write-Host "Running: .\gradlew.bat :desktopApp:createReleaseDistributable" -ForegroundColor Yellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c gradlew.bat :desktopApp:createReleaseDistributable" -WorkingDirectory $PSScriptRoot -Wait -NoNewWindow
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed! Please check logs."
        Read-Host "Press Enter to exit..."
        exit 1
    }
}

Write-Host ""
Write-Host "[1/3] Copying files to $DestDir ..." -ForegroundColor Green
if (-not (Test-Path $DestDir)) {
    New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
}

Copy-Item -Path "$SrcDir\*" -Destination $DestDir -Recurse -Force

Write-Host "[2/3] Creating Start Menu Shortcut..." -ForegroundColor Green
$ProgramsDir = [Environment]::GetFolderPath([System.Environment+SpecialFolder]::Programs)
$StartMenuPath = Join-Path $ProgramsDir "Gratify.lnk"
$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut($StartMenuPath)
$Shortcut.TargetPath = Join-Path $DestDir "Gratify.exe"
$Shortcut.WorkingDirectory = $DestDir
$Shortcut.IconLocation = "$(Join-Path $DestDir 'Gratify.exe'),0"
$Shortcut.Description = "Gratify - YouTube Music Client"
$Shortcut.Save()

Write-Host "[3/3] Creating Desktop Shortcut..." -ForegroundColor Green
$DesktopDir = [Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
$DesktopPath = Join-Path $DesktopDir "Gratify.lnk"
$Shortcut = $WshShell.CreateShortcut($DesktopPath)
$Shortcut.TargetPath = Join-Path $DestDir "Gratify.exe"
$Shortcut.WorkingDirectory = $DestDir
$Shortcut.IconLocation = "$(Join-Path $DestDir 'Gratify.exe'),0"
$Shortcut.Description = "Gratify - YouTube Music Client"
$Shortcut.Save()

Write-Host ""
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " Gratify installed successfully!" -ForegroundColor Cyan
Write-Host ""
Write-Host " You can now search for 'Gratify' in the Start Menu," -ForegroundColor White
Write-Host " or double-click the 'Gratify' shortcut on your Desktop." -ForegroundColor White
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""
Read-Host "Press Enter to exit..."
