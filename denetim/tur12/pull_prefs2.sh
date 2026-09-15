#!/bin/bash
# Tur-12: uygulamanın emülatördeki tohum tokeni ve ayar deposu (mock eşleşmesi için).
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12
echo "--- tur6_token.txt ---"
"$ADB" -s emulator-5554 shell run-as "$PKG" cat files/tur6_token.txt
echo "--- shared_prefs ---"
"$ADB" -s emulator-5554 shell run-as "$PKG" ls shared_prefs
"$ADB" -s emulator-5554 shell run-as "$PKG" ls shared_prefs 2>/dev/null | while read -r f; do
  echo "### $f"
  "$ADB" -s emulator-5554 shell run-as "$PKG" cat "shared_prefs/$f"
  echo
done
