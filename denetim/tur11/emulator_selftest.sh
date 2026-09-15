#!/bin/bash
# Tur-11 emülatör öz-testi: mock voice_api (Mac, 8199) uygulamanın gerçek
# istemci koduyla koşulur. Emülatör Mac'e 10.0.2.2 ile ulaşır.
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
BASE_URL="http://10.0.2.2:8199"
TOKEN="tur11-test-token"

"$ADB" -s emulator-5554 logcat -c -b crash
"$ADB" -s emulator-5554 shell am force-stop "$PKG"
"$ADB" -s emulator-5554 shell am start -n "$PKG/com.hermes.mobile.ui.VoiceSelfTestActivity" \
    --es base "$BASE_URL" --es token "$TOKEN" --es engine kadin
sleep 25
echo "=== ekran dokumu (uiautomator) ==="
"$ADB" -s emulator-5554 shell uiautomator dump /sdcard/voice-selftest.xml >/dev/null 2>&1
"$ADB" -s emulator-5554 shell cat /sdcard/voice-selftest.xml | tr '>' '>\n' | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$'
echo "=== uygulama ici sonuc dosyasi ==="
"$ADB" -s emulator-5554 shell run-as "$PKG" cat files/voice-selftest.txt
echo "=== DiagLog (son voice satirlari) ==="
"$ADB" -s emulator-5554 shell run-as "$PKG" cat files/diag.log | grep -i voice | tail -15
echo "=== crash tamponu (bos olmali) ==="
"$ADB" -s emulator-5554 logcat -d -b crash | tail -5
echo "=== ekran goruntusu ==="
"$ADB" -s emulator-5554 exec-out screencap -p > /Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur11/emulator-sesli-mesaj.png
ls -la /Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur11/emulator-sesli-mesaj.png
