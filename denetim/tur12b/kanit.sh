#!/bin/bash
# tur-12b kanıt: yeniden adlandırılan testler + APK sha256/boyut
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman || exit 1
echo "--- test XML zaman damgası ---"
ls -l app/build/test-results/testDebugUnitTest/TEST-com.hermes.mobile.VoiceApiClientTest.xml app/build/test-results/testDebugUnitTest/TEST-com.hermes.mobile.VoiceStatusLogicTest.xml
echo "--- yeni test adları (XML) ---"
grep -o 'name="[^"]*420[^"]*"' app/build/test-results/testDebugUnitTest/TEST-com.hermes.mobile.VoiceApiClientTest.xml app/build/test-results/testDebugUnitTest/TEST-com.hermes.mobile.VoiceStatusLogicTest.xml
echo "--- eski ad kaldı mı (0 olmalı) ---"
grep -c 'name="sentez zaman asimi sozlesme geregi 300 sn"' app/build/test-results/testDebugUnitTest/TEST-com.hermes.mobile.VoiceApiClientTest.xml
echo "--- APK ---"
stat -f "boyut=%z bayt" app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
echo "--- sabitler ---"
grep -n "SYNTH_TIMEOUT_MS = \|WARM_TIMEOUT_MS = \|MAX_RECORD_MS = " app/src/main/java/com/hermes/mobile/data/VoiceApiEndpoints.kt app/src/main/java/com/hermes/mobile/data/VoiceStatusLogic.kt
