#!/bin/bash
# Tur-13 — Dijital asistan secici listesi (Hermes Asistan gorunuyor mu) +
# rol Hermes iken satir ve DOGRUDAN yonlendirme (soguk + sicak).
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== C1) dijital asistan secici listesini ac (SECIM YAPILMAZ) ==="
python3 "$OUT/tapbul.py" "$OUT/b4-yonlendirme.xml" "Dijital asistan uygulamas" --tap
sleep 4
$ADB -s emulator-5554 shell uiautomator dump /sdcard/c1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/c1.xml > "$OUT/c1-asistan-secici.xml"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-asistan-secici.png"
python3 "$OUT/tapbul.py" "$OUT/c1-asistan-secici.xml" "Hermes"
python3 "$OUT/tapbul.py" "$OUT/c1-asistan-secici.xml" "Google"

echo "=== C2) geri don: rol satiri ==="
$ADB -s emulator-5554 shell input keyevent 4
sleep 2
$ADB -s emulator-5554 shell input keyevent 4
sleep 2
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity"

echo "=== D1) rol Hermes (kabuk) ==="
$ADB -s emulator-5554 shell cmd role add-role-holder android.app.role.ASSISTANT com.hermes.mobile.v2
$ADB -s emulator-5554 shell cmd role get-role-holders android.app.role.ASSISTANT | tee "$OUT/rol-hermes.txt"
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/d1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/d1.xml > "$OUT/d1-rol-hermes.xml"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-rol-hermes.png"
python3 "$OUT/tapbul.py" "$OUT/d1-rol-hermes.xml" "varsay" | head -6

echo "=== D2) SOGUK: force-stop + ASSIST (chooser olmamali) ==="
$ADB -s emulator-5554 shell am force-stop com.hermes.mobile.v2
sleep 2
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST > "$OUT/assist-soguk.txt" 2>&1
cat "$OUT/assist-soguk.txt"
sleep 9
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-soguk.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/d2.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/d2.xml > "$OUT/d2-soguk.xml"
python3 "$OUT/tapbul.py" "$OUT/d2-soguk.xml" "Asistan haz"
python3 "$OUT/tapbul.py" "$OUT/d2-soguk.xml" "otomatik oku"

echo "=== D3) SICAK: ana ekran + ASSIST ==="
$ADB -s emulator-5554 shell input keyevent 3
sleep 4
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST > "$OUT/assist-sicak.txt" 2>&1
cat "$OUT/assist-sicak.txt"
sleep 6
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-sicak.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/d3.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/d3.xml > "$OUT/d3-sicak.xml"
python3 "$OUT/tapbul.py" "$OUT/d3-sicak.xml" "Asistan haz"

echo "=== D4) diag log (asistan modu kayitlari) ==="
$ADB -s emulator-5554 shell run-as com.hermes.mobile.v2 cat files/diag.log > "$OUT/diag-tur13.log" 2>/dev/null
grep -c "asistan" "$OUT/diag-tur13.log" || true
tail -6 "$OUT/diag-tur13.log"
echo "BITTI (C/D)"
