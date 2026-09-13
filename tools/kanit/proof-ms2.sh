#!/usr/bin/env bash
# MS-2 kanıtı: MainActivity exported=true ama eski ACTION_SEND dalı kaldırıldı.
# Dışarıdan explicit `am start -n MainActivity -a SEND` artık DÜŞMANCA: taslağa
# metin/enjekte dosya DÜŞMEMELİ, nonce'suz paylaşımdan upload TETİKLENMEMELİ.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2

echo "== MS-2: explicit ACTION_SEND -> MainActivity (nonce yok) =="
$ADB -s emulator-5554 logcat -c
# Uygulamayı temiz başlat (eski pending temiz olsun)
$ADB -s emulator-5554 shell am force-stop $PK
sleep 1
$ADB -s emulator-5554 shell am start -n $PK/com.hermes.mobile.MainActivity >/dev/null
sleep 4
# Düşmanca explicit intent: SEND + metin + dosya (nonce YOK, ShareProxy ATLANIYOR)
$ADB -s emulator-5554 shell "am start -n $PK/com.hermes.mobile.MainActivity -a android.intent.action.SEND --es android.intent.extra.TEXT 'ENJEKTE-METIN-DUSMANCA' -t text/plain" >/dev/null 2>&1
$ADB -s emulator-5554 shell am start -n $PK/com.hermes.mobile.MainActivity >/dev/null
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/ms2.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/ms2.xml > /tmp/ms2.xml
echo "--- Composer/EditText içeriği (ENJEKTE-METIN-DUSMANCA ARAMASI) ---"
grep -o 'ENJEKTE-METIN-DUSMANCA' /tmp/ms2.xml && echo "KIRILMA: enjekte metin taslakta GÖRÜNÜYOR" || echo "GÜVENDE: enjekte metin taslakta YOK"
echo "--- crash buffer ---"
$ADB -s emulator-5554 logcat -d -b crash | grep "$PK" | head -5
true
echo "--- MainActivity handleShareIntent nonce-dışı red logu ---"
$ADB -s emulator-5554 logcat -d | grep -iE "sharehandoff|nonce|share" | grep -iv "upload-stream" | tail -8 || true
