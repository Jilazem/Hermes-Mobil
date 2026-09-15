#!/bin/bash
# Tur-13 — ASSIST niyetiyle acilis (soguk+sicak) + ekran kaniti.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13
mkdir -p "$OUT"

echo "=== 1) soguk acilis: uygulamayi durdur ==="
$ADB -s emulator-5554 shell am force-stop com.hermes.mobile.v2
$ADB -s emulator-5554 shell "dumpsys activity activities | grep -c 'com.hermes.mobile.v2'" || true

echo "=== 2) ASSIST niyeti (implicit, istek BIREBIR) ==="
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST > "$OUT/assist-start-1.txt" 2>&1
cat "$OUT/assist-start-1.txt"
sleep 7

echo "=== 3) ekran ==="
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-1.png"
ls -la "$OUT/emulator-assist-1.png"

echo "=== 4) ekran dokumu ==="
$ADB -s emulator-5554 shell uiautomator dump /sdcard/assist1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/assist1.xml > "$OUT/assist1.xml"
wc -c "$OUT/assist1.xml"

echo "=== 5) on plandaki aktivite ==="
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m6 "topResumedActivity\|mResumedActivity\|ResumedActivity" 

echo "=== 6) uygulama ic log (asistan modu) ==="
$ADB -s emulator-5554 shell run-as com.hermes.mobile.v2 cat files/diag.log 2>/dev/null | tail -12

echo "=== 7) crash tamponu (bos olmali) ==="
$ADB -s emulator-5554 logcat -d -b crash 2>/dev/null | tail -5
echo "BITTI"
