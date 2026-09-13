#!/usr/bin/env bash
# MS-3 file:// akış kanıtı: ShareProxy -> staging -> hedef (Yeni konu) ->
# POST /api/files/upload-stream (proxy 9150'ye gerçek token basar) -> 2xx +
# sunucuda inen dosya. Proxy swap sayesinde token emülatöre sızmaz.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2
MARK="tur3-dosya-$(date +%s)"
echo "$MARK icerigi: gercek sunucuya inen kanit dosyasi" > /tmp/prova.txt

# logcat + proxy işaretleri sıfırla
: > /tmp/proxy.log
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop $PK
sleep 1
$ADB -s emulator-5554 shell am start -n $PK/com.hermes.mobile.MainActivity >/dev/null
sleep 5

# paylasilacak dosyayi uygulamanin kendi dizinine koy (file:// erisilebilir)
$ADB -s emulator-5554 shell "run-as $PK sh -c 'echo $MARK > files/prova-dosya.txt'"

echo "== dosya paylasimi: ShareProxy'ye file:// EXTRA_STREAM =="
# ONEM: adb shell iki kez parse eder — tum am komutunu TEK tırnakla shell'e ver.
# ONEM: EXTRA anahtarı BÜYÜK olmalı: android.intent.extra.STREAM (küçük "stream"
# getParcelableExtra(EXTRA_STREAM)'de null döner — TUR-2 tuzağı, yine düşüldü).
$ADB -s emulator-5554 shell "am start -n $PK/com.hermes.mobile.ShareProxyActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'TUR3-dosya-akis-kaniti' --eu android.intent.extra.STREAM 'file:///data/user/0/$PK/files/prova-dosya.txt' -f 1" >/dev/null
sleep 3

# staging kopyasi olustu mu?
echo "--- staging ---"
$ADB -s emulator-5554 shell "run-as $PK ls -la cache/share_inbox 2>/dev/null" | tail -3

# hedef ekraninda "+ New topic" / Yeni konu dokun
$ADB -s emulator-5554 shell uiautomator dump /sdcard/sh.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/sh.xml > /tmp/sh.xml
NEWBOUNDS=$(grep -oE 'text="[^"]*(New topic|Yeni konu)[^"]*"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' /tmp/sh.xml | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | tail -1)
echo "Yeni konu bounds: $NEWBOUNDS"
# merkez hesapla
COORDS=$(echo "$NEWBOUNDS" | grep -oE '[0-9]+')
X1=$(echo "$COORDS" | sed -n 1p); Y1=$(echo "$COORDS" | sed -n 2p)
X2=$(echo "$COORDS" | sed -n 3p); Y2=$(echo "$COORDS" | sed -n 4p)
CX=$(( (X1+X2)/2 )); CY=$(( (Y1+Y2)/2 ))
echo "tap -> $CX,$CY"
$ADB -s emulator-5554 shell input tap $CX $CY
sleep 6

echo "--- upload cagrisi (proxy) ---"
grep -i "upload-stream" /tmp/proxy.log || echo "proxy'de upload-stream YOK"
echo "--- sunucuda inen dosya (uploads/) ---"
ls -la ~/.hermes/uploads/ 2>/dev/null | grep -i prova || echo "uploads/ prova yok - tumu:"
ls ~/.hermes/uploads/ 2>/dev/null | tail -5
echo "--- staging sonrasi (yukleme sonrasi silinmeli) ---"
$ADB -s emulator-5554 shell "run-as $PK ls -la cache/share_inbox 2>/dev/null" | tail -2
echo "--- diag upload izi ---"
$ADB -s emulator-5554 shell "run-as $PK sh -c 'grep -h ShareUpload files/diag.log | tail -3'"
