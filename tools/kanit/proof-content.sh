#!/usr/bin/env bash
# content:// kanıtı (gerçek izin modeli): adb root kabuk grant'i MediaStore'da
# reddediliyor (gönderen UID sağlayıcı izni taşımıyor — Permission Denial'ın
# KÖK NEDENİ bu, kanıtsız bırakılmadı). Gerçek senaryoda gönderen uygulama
# URI'yi KENDI FileProvider'ından grant'lar: tools-tur3/sender APK'sı bunu
# yapar (com.hermes.sharesender). Bu script sender'ı çağırır; Hermes tarafı
# (ShareProxy -> staging -> upload-stream) hiç değişmeden çalışmalı.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2
MARK="content-akis-$(date +%s)"

: > /tmp/proxy.log
$ADB -s emulator-5554 shell am force-stop $PK; sleep 1
$ADB -s emulator-5554 shell am start -n $PK/com.hermes.mobile.MainActivity >/dev/null; sleep 5

echo "== sender: FileProvider content:// -> ShareProxy (FLAG_GRANT_READ) =="
$ADB -s emulator-5554 shell "am start -n com.hermes.sharesender/.SendActivity --es content '$MARK' --es fname content-prova.txt --es text 'content-provider-kaniti'" >/dev/null
sleep 4
echo "--- ekran (hedef secim + ek cipi) ---"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/k.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/k.xml > /tmp/k.xml
grep -o 'text="[^"][^"]*"' /tmp/k.xml | sort -u | head -12
echo "--- staging kopyasi (content:// OKUNMUS olmali) ---"
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | tail -3
STG=$($ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | grep -oE '[0-9a-f]{8}_content-prova.txt' | head -1)
[ -n "$STG" ] && $ADB -s emulator-5554 shell "run-as $PK cat cache/share_inbox/$STG"
# hedef sec: + New topic
KB=$(grep -oE 'text="[^"]*(New topic|Yeni konu)[^"]*"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' /tmp/k.xml | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | tail -1)
if [ -n "$KB" ]; then
  COORDS=$(echo "$KB" | grep -oE '[0-9]+')
  CX=$(( ($(echo "$COORDS" | sed -n 1p)+$(echo "$COORDS" | sed -n 3p))/2 ))
  CY=$(( ($(echo "$COORDS" | sed -n 2p)+$(echo "$COORDS" | sed -n 4p))/2 ))
  $ADB -s emulator-5554 shell input tap $CX $CY
  sleep 6
fi
echo "--- proxy upload sonucu ---"
grep -i "upload-stream" /tmp/proxy.log || echo "upload YOK"
echo "--- sunucuda inen dosya ---"
ls -la /Users/gokhanuzman/007-HERMES/uploads/ | grep -i content || echo "uploads'te content-prova YOK"
cat /Users/gokhanuzman/007-HERMES/uploads/content-prova.txt 2>/dev/null
echo "--- diag ---"
$ADB -s emulator-5554 shell "run-as $PK sh -c 'grep -h ShareUpload files/diag.log | tail -2'"
