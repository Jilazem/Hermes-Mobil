#!/bin/bash
# Tur-13 — rol Hermes iken: rol satiri tazelemesi + soguk/sicak asistan acilisi
# (vekil bilesen = sistemin asistan yolunda actigi bilesen).
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== 1) SOGUK acilis (force-stop -> vekil) ==="
$ADB -s emulator-5554 shell am force-stop com.hermes.mobile.v2
sleep 2
$ADB -s emulator-5554 shell am start -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > "$OUT/alias-soguk.txt" 2>&1
cat "$OUT/alias-soguk.txt"
sleep 9
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-soguk.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/e1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/e1.xml > "$OUT/e1-soguk.xml"
echo "--- asistan seridi:"
python3 "$OUT/tapbul.py" "$OUT/e1-soguk.xml" "Asistan haz"
python3 "$OUT/tapbul.py" "$OUT/e1-soguk.xml" "otomatik oku"

echo "=== 2) rol satiri (asistan bolumu) ==="
python3 "$OUT/tapbul.py" "$OUT/e1-soguk.xml" "Ayarlar" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/e2.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/e2.xml > "$OUT/e2-ayarlar.xml"
python3 "$OUT/tapbul.py" "$OUT/e2-ayarlar.xml" "Telefon asistan" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/e3.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/e3.xml > "$OUT/e3-rol-hermes.xml"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-rol-hermes.png"
python3 "$OUT/tapbul.py" "$OUT/e3-rol-hermes.xml" "varsayilan asistan"
python3 "$OUT/tapbul.py" "$OUT/e3-rol-hermes.xml" "Hermes:"
python3 "$OUT/tapbul.py" "$OUT/e3-rol-hermes.xml" "Google"

echo "=== 3) SICAK acilis (ana ekran -> vekil) ==="
$ADB -s emulator-5554 shell input keyevent 3
sleep 4
$ADB -s emulator-5554 shell am start -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > "$OUT/alias-sicak.txt" 2>&1
cat "$OUT/alias-sicak.txt"
sleep 6
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-sicak.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/e4.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/e4.xml > "$OUT/e4-sicak.xml"
python3 "$OUT/tapbul.py" "$OUT/e4-sicak.xml" "Asistan haz"

echo "=== 4) diag (asistan + rol kayitlari) ==="
$ADB -s emulator-5554 shell run-as com.hermes.mobile.v2 cat files/diag.log > "$OUT/diag-tur13.log" 2>/dev/null
grep "asistan" "$OUT/diag-tur13.log" | tail -8
echo "BITTI"
