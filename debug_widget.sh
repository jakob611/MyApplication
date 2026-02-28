#!/bin/bash
# Widget Debug Script
# Run this to check if widget is properly registered

echo "=== Checking if APK contains widget resources ==="
aapt dump resources app-debug.apk | grep -i widget

echo -e "\n=== Checking manifest for widget receiver ==="
aapt dump xmltree app-debug.apk AndroidManifest.xml | grep -A5 -i widget

echo -e "\n=== Checking if widget provider class exists ==="
unzip -l app-debug.apk | grep WaterWidget

echo -e "\n=== Installing and checking on device ==="
adb install -r app-debug.apk
adb shell pm dump com.example.myapplication | grep -i widget

echo -e "\n=== Widget should appear in: ==="
echo "Settings -> Apps -> Glow Upp -> App info"
echo "Look for 'Widget' section"

