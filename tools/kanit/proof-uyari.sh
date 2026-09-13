#!/usr/bin/env bash
# YENI-2 kaniti: okunamayan icerik (MediaStore URI, kabuk grant'i dusuyor)
# hedefe secilince SEssiz kalmamali — Toast kullaniciya gorunur olmali.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2
grep -c "" /dev/null 2>/dev/null || true

$ADB -s emulator-5554 shell uiautomator dump /sdcard/w2.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/w2.xml > /tmp/w2.xml
echo "--- uyarı metni ekranda/görünür katmanda ---"
grep -o 'text="[^"]*okunamad[^"]*"' /tmp/w2.xml || echo "dump'ta yok (toast ayrı pencere olabilir)"
echo "--- toast logcat kaniyi ---"
$ADB -s emulator-5554 logcat -d | grep -iE "Toast.*hermes|NotificationAccess|TextToast" | tail -5
echo "--- diag (gorunur uyari + sessiz olmayan kayip) ---"
$ADB -s emulator-5554 shell "run-as $PK sh -c 'grep -h ShareUpload files/diag.log | tail -3'"
