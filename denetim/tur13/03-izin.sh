#!/bin/bash
# Tur-13 — izin diyalogu kaniti + izin ver + asistan ekrani.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== 1) izin diyalogu ekrani ==="
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-izin.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/izin.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/izin.xml > "$OUT/izin1.xml"
python3 "$OUT/tiklanabilir.py" "$OUT/izin1.xml"

echo "=== 2) diyalog metinleri ==="
python3 "$OUT/agac.py" "$OUT/izin1.xml" "izin" | head -20
echo "BITTI"
