#!/bin/bash
# Tur-13 — Ayarlar -> "Telefon asistanı" bolumu (rol durumu satiri + dugme).
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13
DUMP="$OUT/ayarlar1.xml"
ROLE="$OUT/ayarlar-asistan.xml"

echo "=== 1) Ayarlar sekmesi ==="
python3 "$OUT/tapbul.py" "$OUT/asistan3.xml" "Ayarlar" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/ay1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/ay1.xml > "$DUMP"

echo "=== 2) kategori listesi (Telefon asistani aranacak) ==="
python3 "$OUT/tapbul.py" "$DUMP" "Telefon asistan"
python3 "$OUT/tiklanabilir.py" "$DUMP" | head -25

echo "=== 3) kategoriye gir ==="
python3 "$OUT/tapbul.py" "$DUMP" "Telefon asistan" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/ay2.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/ay2.xml > "$ROLE"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-ayarlar-asistan-google.png"

echo "=== 4) rol satiri + dugme ==="
python3 "$OUT/tapbul.py" "$ROLE" "varsayilan asistan"
python3 "$OUT/tapbul.py" "$ROLE" "Hermes'i varsay"
python3 "$OUT/tapbul.py" "$ROLE" "otomatik oku"
python3 "$OUT/tapbul.py" "$ROLE" "Yerel (Kahya)"
echo "BITTI"
