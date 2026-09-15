#!/bin/bash
# Tur-12: ayar deposunu host'a çek (voiceUrl/voiceEngine yazmak için).
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12
"$ADB" -s emulator-5554 shell run-as "$PKG" cat shared_prefs/hermes_settings.xml > "$OUT/hermes_settings.xml"
"$ADB" -s emulator-5554 shell run-as "$PKG" cat shared_prefs/hermes_profiles.xml > "$OUT/hermes_profiles.xml"
wc -c "$OUT/hermes_settings.xml" "$OUT/hermes_profiles.xml"
