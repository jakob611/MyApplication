# PowerShell Widget Debug Script
# Run this in PowerShell to check widget registration

Write-Host "=== Widget Debug Check ===" -ForegroundColor Cyan

$apkPath = "C:\Users\tomin\AndroidStudioProjects\MyApplication\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at: $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host "`n1. Checking APK size..." -ForegroundColor Yellow
$size = (Get-Item $apkPath).Length / 1MB
Write-Host "APK Size: $([math]::Round($size, 2)) MB"

Write-Host "`n2. Checking if widget class is in APK..." -ForegroundColor Yellow
$jar = "jar"
if (Get-Command jar -ErrorAction SilentlyContinue) {
    jar tf $apkPath | Select-String "WaterWidget"
} else {
    Write-Host "jar command not found, skipping..." -ForegroundColor Gray
}

Write-Host "`n3. Installing APK..." -ForegroundColor Yellow
adb install -r $apkPath

Write-Host "`n4. Checking if app is installed..." -ForegroundColor Yellow
adb shell pm list packages | Select-String "myapplication"

Write-Host "`n5. Checking widget registration..." -ForegroundColor Yellow
adb shell pm dump com.example.myapplication | Select-String -Pattern "widget|receiver|Water" -Context 1,1

Write-Host "`n6. Forcing widget update..." -ForegroundColor Yellow
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE

Write-Host "`n7. Checking logcat for widget messages..." -ForegroundColor Yellow
Write-Host "Run this command in another terminal:" -ForegroundColor Cyan
Write-Host "adb logcat -s WaterWidget:* AndroidRuntime:E *:F" -ForegroundColor White

Write-Host "`n=== Debug Complete ===" -ForegroundColor Green
Write-Host "If widget is still not visible:" -ForegroundColor Yellow
Write-Host "1. Restart your phone"
Write-Host "2. Clear Glow Upp app data"
Write-Host "3. Check Settings -> Apps -> Glow Upp -> Look for 'Widgets' section"

Write-Host "`n=== Performance Test ===" -ForegroundColor Cyan
Write-Host "After installing, test widget responsiveness:" -ForegroundColor Yellow
Write-Host "1. Add widget to home screen"
Write-Host "2. Rapidly tap + or - buttons (10+ times)"
Write-Host "3. Widget should update INSTANTLY (< 100ms)"
Write-Host "4. Tap center to sync from Firestore (slower, ~500ms)"
Write-Host ""
Write-Host "Expected behavior:" -ForegroundColor Green
Write-Host "  +/- clicks: Instant response (no lag)"
Write-Host "  Center tap: Brief delay (syncing from server)"
Write-Host "  Offline: Works perfectly from cache"

