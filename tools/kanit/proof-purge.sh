#!/usr/bin/env bash
# ONERI-1 kaniti: purgeStale artik HER ShareProxyActivity girisinde kosuyor
# (eskiden yalniz HermesApp.onCreate — surec gunlerce ayakta kalabiliyordu).
# Bayat (1+ saatlik) kopya koy -> proxy'yi tetikle -> kopya silinmis olmali.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2

# bayat dosya: 2 saat once zaman damgali
$ADB -s emulator-5554 shell "run-as $PK sh -c 'mkdir -p cache/share_inbox; echo eski > cache/share_inbox/bayat-dosya.txt; toybox touch -d \"2 hours ago\" cache/share_inbox/bayat-dosya.txt 2>/dev/null || true'"
echo "--- proxy oncesi ---"
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | grep -c bayat
# proxy'yi bos bir SEND ile tetikle (paylasim yoken; salt onCreate purge kossun)
$ADB -s emulator-5554 shell "am start -n $PK/com.hermes.mobile.ShareProxyActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'purge-test'" >/dev/null
sleep 2
$ADB -s emulator-5554 shell input keyevent KEYCODE_BACK 2>/dev/null
echo "--- proxy sonrasi (0 olmali) ---"
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | grep -c bayat
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | head -5
