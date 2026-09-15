#!/bin/bash
# Tur-12 (B-2): emülatör öz-test transkriptini HAM dosya olarak arşivle + tanı.
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12

echo "=== mevcut transkript (tur-11 koşusu, cihazda) ==="
"$ADB" -s emulator-5554 exec-out run-as "$PKG" cat files/voice-selftest.txt > "$OUT/voice-selftest-cihazdaki.txt"
wc -c "$OUT/voice-selftest-cihazdaki.txt"

echo "=== öz-test YENİDEN (tur-12 APK, mock 10.0.2.2:8199) ==="
"$ADB" -s emulator-5554 logcat -c -b crash
"$ADB" -s emulator-5554 shell am force-stop "$PKG"
"$ADB" -s emulator-5554 shell am start -n "$PKG/com.hermes.mobile.ui.VoiceSelfTestActivity" \
    --es base "http://10.0.2.2:8199" --es token "tur12-test-token" --es engine kadin
sleep 30
"$ADB" -s emulator-5554 exec-out run-as "$PKG" cat files/voice-selftest.txt > "$OUT/voice-selftest.txt"
echo "--- transkript ---"
cat "$OUT/voice-selftest.txt"
echo "=== DiagLog (voice satırları, son 20) ==="
"$ADB" -s emulator-5554 exec-out run-as "$PKG" cat files/diag.log > "$OUT/diag-tur12.log"
grep -i "voice" "$OUT/diag-tur12.log" | tail -20
echo "=== crash tamponu (bos olmali) ==="
"$ADB" -s emulator-5554 logcat -d -b crash | tail -5
echo "=== ekran görüntüsü (öz-test) ==="
"$ADB" -s emulator-5554 exec-out screencap -p > "$OUT/emulator-selftest.png"
ls -la "$OUT/emulator-selftest.png"
