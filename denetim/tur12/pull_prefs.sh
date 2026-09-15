#!/bin/bash
# Tur-12: uygulamanın (emülatör) sakladığı ayarları/profil tokenini host'a çek.
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12
"$ADB" -s emulator-5554 shell run-as "$PKG" ls files
echo "--- datastore ---"
"$ADB" -s emulator-5554 shell run-as "$PKG" ls files/datastore
echo "--- prefs dump -> host ---"
"$ADB" -s emulator-5554 shell run-as "$PKG" cat files/datastore/settings.preferences_pb > "$OUT/prefs-dump.bin" 2>/dev/null
ls -la "$OUT/prefs-dump.bin"
