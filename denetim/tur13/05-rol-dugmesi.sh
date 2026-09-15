#!/bin/bash
# Tur-13 — rol dugmesi: sistem diyalogu aciliyor mu (ekran kaniti) + rol atamasi
# denemesi. NOT: rol atamasi sistem diyaloguyla ZORLANMIYOR (gorev kisiti);
# yonlendirme testi icin kabuk `cmd role` kullanilir ve SONUNDA GERI ALINIR.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== 0) rol durumu (oncesi) ==="
$ADB -s emulator-5554 shell cmd role get-role-holders android.app.role.ASSISTANT | tee "$OUT/rol-once.txt"
$ADB -s emulator-5554 shell settings get secure assistant | tee "$OUT/secure-assistant-once.txt"

echo "=== 1) 'Hermes'i varsayilan asistan yap' dugmesine dokun ==="
python3 "$OUT/tapbul.py" "$OUT/ayarlar-asistan.xml" "Hermes'i varsay" --tap
sleep 4
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m2 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-rol-diyalog.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/rol.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/rol.xml > "$OUT/rol-diyalog.xml"
python3 "$OUT/tiklanabilir.py" "$OUT/rol-diyalog.xml" | head -12
python3 "$OUT/tapbul.py" "$OUT/rol-diyalog.xml" "asistan" | head -6

echo "=== 2) diyalogu KAPAT (rol diyalogla zorlanmiyor) ==="
$ADB -s emulator-5554 shell input keyevent 4
sleep 3
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m2 "topResumedActivity"
echo "BITTI"
