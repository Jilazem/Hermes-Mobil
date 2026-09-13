#!/usr/bin/env bash
# MS-1 kanıtı: dosya paylaşımında "Vazgeç"e basılınca YÜKLEME OLMAMALI,
# taslak metin DÜŞMEMELİ, staging kopyası SİLİNMELİ. Proxy log upload-stream
# İÇERMEMELİ (sahte-pozitif koruması: önce akışın çalıştığını ispatla —
# ayrı proof-dosya.sh zaten POST 200 verdi).
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PK=com.hermes.mobile.v2
: > /tmp/proxy.log
$ADB -s emulator-5554 shell am force-stop $PK
sleep 1
$ADB -s emulator-5554 shell am start -n $PK/com.hermes.mobile.MainActivity >/dev/null
sleep 5
$ADB -s emulator-5554 shell "run-as $PK sh -c 'echo iptal-edilecek-icerik > files/iptal-dosya.txt'"

echo "== Vazgeç akışı: dosya paylaş -> hedef ekran -> Vazgeç =="
$ADB -s emulator-5554 shell "am start -n $PK/com.hermes.mobile.ShareProxyActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'IPTAL-METIN-TASLAGA-DUSMEMELI' --eu android.intent.extra.STREAM 'file:///data/user/0/$PK/files/iptal-dosya.txt' -f 1" >/dev/null
sleep 3
echo "--- staging (kopya OLUŞTU mu — iptal öncesi) ---"
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | grep iptal && STAGED=1 || STAGED=0
$ADB -s emulator-5554 shell uiautomator dump /sdcard/c.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/c.xml > /tmp/c.xml
CB=$(grep -oE 'text="(Cancel|Vazgeç)"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' /tmp/c.xml | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | tail -1)
echo "Cancel bounds: $CB"
COORDS=$(echo "$CB" | grep -oE '[0-9]+')
X1=$(echo "$COORDS" | sed -n 1p); Y1=$(echo "$COORDS" | sed -n 2p)
X2=$(echo "$COORDS" | sed -n 3p); Y2=$(echo "$COORDS" | sed -n 4p)
CX=$(( (X1+X2)/2 )); CY=$(( (Y1+Y2)/2 ))
$ADB -s emulator-5554 shell input tap $CX $CY
sleep 4
echo "--- staging sonrası (iptalle SİLİNMELİ) ---"
$ADB -s emulator-5554 shell "run-as $PK ls cache/share_inbox" | grep iptal && echo "KIRILMA: kopya duruyor" || echo "GÜVENDE: kopya silindi"
echo "--- proxy: upload-stream OLMAMALI ---"
grep -c "upload-stream" /tmp/proxy.log
echo "--- taslakta IPTAL-METIN OLMAMALI ---"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/c2.xml >/dev/null
$ADB -s emulator-5554 shell cat /sdcard/c2.xml > /tmp/c2.xml
grep -o "IPTAL-METIN-TASLAGA-DUSMEMELI" /tmp/c2.xml && echo "KIRILMA: taslak doldu" || echo "GÜVENDE: taslak temiz"
echo "--- ws durumu (bridge ile online mi) ---"
$ADB -s emulator-5554 shell "run-as $PK sh -c 'tail -4 files/diag.log'" | grep -E "ws|Open" | tail -3
grep "ws" /tmp/proxy.log | tail -2
