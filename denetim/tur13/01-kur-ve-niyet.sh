#!/bin/bash
# Tur-13 — APK kurulumu + ASSIST niyeti çözümlemesi (kanıt).
# Tirith: her adb çağrısı tam literal yolla, tek satır.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
APK=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/app/build/outputs/apk/debug/app-debug.apk
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13
mkdir -p "$OUT"

echo "=== 1) APK ==="
ls -la "$APK"
md5 -q "$APK"
shasum -a 256 "$APK" | cut -d' ' -f1

echo "=== 2) kurulum ==="
$ADB -s emulator-5554 install -r "$APK" 2>&1 | tail -5

echo "=== 3) surum ==="
$ADB -s emulator-5554 shell dumpsys package com.hermes.mobile.v2 2>/dev/null | grep -m1 versionName

echo "=== 4) ASSIST cozumlemesi (sistem) ==="
$ADB -s emulator-5554 shell cmd package query-activities -a android.intent.action.ASSIST 2>&1 | head -40
echo "--- VOICE_COMMAND:"
$ADB -s emulator-5554 shell cmd package query-activities -a android.intent.action.VOICE_COMMAND 2>&1 | head -40

echo "=== 5) query-activities cikti dosyasi ==="
$ADB -s emulator-5554 shell cmd package query-activities -a android.intent.action.ASSIST > "$OUT/query-assist.txt" 2>&1
$ADB -s emulator-5554 shell cmd package query-activities -a android.intent.action.VOICE_COMMAND > "$OUT/query-voice-command.txt" 2>&1
wc -l "$OUT/query-assist.txt" "$OUT/query-voice-command.txt"

echo "=== 6) etiket dogrulama (aapt2: vekil aktivite etiketi) ==="
/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/build-tools/35.0.0/aapt2 dump xmltree --file AndroidManifest.xml "$APK" > "$OUT/manifest-xmltree.txt" 2>&1
grep -n -A6 "AssistantAlias" "$OUT/manifest-xmltree.txt" | head -40
echo "--- etiket satiri:"
grep -n "assistant_label\|Hermes Asistan" "$OUT/manifest-xmltree.txt" | head -5

echo "=== 7) kurulu pakete karsi filtrenin gorunurlugu ==="
$ADB -s emulator-5554 shell cmd package resolve-activity -a android.intent.action.ASSIST 2>&1 | head -20
echo "BITTI"
