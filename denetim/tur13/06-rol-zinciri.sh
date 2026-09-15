#!/bin/bash
# Tur-13 — kurulum sonrasi tam zincir: rol satiri (Google) -> dugme -> yonlendirme
#                       -> rol Hermes -> dogrudan yonlendirme (soguk/sicak).
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
APK=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/app/build/outputs/apk/debug/app-debug.apk
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== A) kurulum ==="
$ADB -s emulator-5554 install -r "$APK" 2>&1 | tail -3
md5 -q "$APK" | tee "$OUT/apk-md5.txt"
shasum -a 256 "$APK" | cut -d' ' -f1 | tee "$OUT/apk-sha256.txt"
$ADB -s emulator-5554 shell dumpsys package com.hermes.mobile.v2 2>/dev/null | grep -m1 versionName

echo "=== B) rol Google'a geri (ozgün durum) ==="
$ADB -s emulator-5554 shell cmd role remove-role-holder android.app.role.ASSISTANT com.hermes.mobile.v2 > /dev/null 2>&1
$ADB -s emulator-5554 shell cmd role add-role-holder android.app.role.ASSISTANT com.google.android.googlequicksearchbox > /dev/null 2>&1
$ADB -s emulator-5554 shell cmd role get-role-holders android.app.role.ASSISTANT | tee "$OUT/rol-google.txt"

echo "=== B2) uygulamayi ASSIST ile ac ve Ayarlar > Telefon asistani ==="
$ADB -s emulator-5554 shell am force-stop com.hermes.mobile.v2
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > /dev/null 2>&1
sleep 8
$ADB -s emulator-5554 shell uiautomator dump /sdcard/b1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/b1.xml > "$OUT/b1-asistan.xml"
python3 "$OUT/tapbul.py" "$OUT/b1-asistan.xml" "Ayarlar" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/b2.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/b2.xml > "$OUT/b2-ayarlar.xml"
python3 "$OUT/tapbul.py" "$OUT/b2-ayarlar.xml" "Telefon asistan" --tap
sleep 3
$ADB -s emulator-5554 shell uiautomator dump /sdcard/b3.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/b3.xml > "$OUT/b3-asistan-bolum.xml"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-rol-google.png"
python3 "$OUT/tapbul.py" "$OUT/b3-asistan-bolum.xml" "Google"

echo "=== B3) dugme: sistem diyalogu + yonlendirme ==="
python3 "$OUT/tapbul.py" "$OUT/b3-asistan-bolum.xml" "Hermes'i varsay" --tap
sleep 6
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m2 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-rol-yonlendirme.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/b4.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/b4.xml > "$OUT/b4-yonlendirme.xml"
python3 "$OUT/tiklanabilir.py" "$OUT/b4-yonlendirme.xml" | head -10
python3 "$OUT/tapbul.py" "$OUT/b4-yonlendirme.xml" "asistan" | head -6
echo "BITTI (B)"
