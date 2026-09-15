#!/bin/bash
# Tur-13 — dogru niyet: ACTION_ASSIST + vekil bilesen (sistemin asistan yolunda
# gonderdigi niyetin aynisi). Soguk + sicak.
set -u
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13

echo "=== 1) SOGUK (force-stop -> ACTION_ASSIST + vekil) ==="
$ADB -s emulator-5554 shell am force-stop com.hermes.mobile.v2
sleep 2
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > "$OUT/assist-vekil-soguk.txt" 2>&1
cat "$OUT/assist-vekil-soguk.txt"
sleep 9
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-soguk.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/f1.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/f1.xml > "$OUT/f1-soguk.xml"
python3 "$OUT/tapbul.py" "$OUT/f1-soguk.xml" "Asistan haz"
python3 "$OUT/tapbul.py" "$OUT/f1-soguk.xml" "otomatik oku"
python3 "$OUT/tapbul.py" "$OUT/f1-soguk.xml" "Yerel hat"

echo "=== 2) SICAK (ana ekran -> ayni niyet) ==="
$ADB -s emulator-5554 shell input keyevent 3
sleep 4
$ADB -s emulator-5554 shell am start -a android.intent.action.ASSIST -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > "$OUT/assist-vekil-sicak.txt" 2>&1
cat "$OUT/assist-vekil-sicak.txt"
sleep 6
$ADB -s emulator-5554 shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity"
$ADB -s emulator-5554 exec-out screencap -p > "$OUT/emulator-assist-sicak.png"
$ADB -s emulator-5554 shell uiautomator dump /sdcard/f2.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/f2.xml > "$OUT/f2-sicak.xml"
python3 "$OUT/tapbul.py" "$OUT/f2-sicak.xml" "Asistan haz"

echo "=== 3) VOICE_COMMAND niyeti (mumkunse) ==="
$ADB -s emulator-5554 shell input keyevent 3
sleep 3
$ADB -s emulator-5554 shell am start -a android.intent.action.VOICE_COMMAND -n com.hermes.mobile.v2/com.hermes.mobile.AssistantAlias > "$OUT/voice-command.txt" 2>&1
cat "$OUT/voice-command.txt"
sleep 6
$ADB -s emulator-5554 shell uiautomator dump /sdcard/f3.xml > /dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/f3.xml > "$OUT/f3-voice-command.xml"
python3 "$OUT/tapbul.py" "$OUT/f3-voice-command.xml" "Asistan haz"

echo "=== 4) diag ==="
$ADB -s emulator-5554 shell run-as com.hermes.mobile.v2 cat files/diag.log > "$OUT/diag-tur13.log" 2>/dev/null
grep "asistan" "$OUT/diag-tur13.log" | tail -8
echo "=== 5) crash tamponu (bos olmali) ==="
$ADB -s emulator-5554 logcat -d -b crash 2>/dev/null | tail -3
echo "BITTI"
