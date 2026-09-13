#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
ADB="$ANDROID_HOME/platform-tools/adb"
for i in $(seq 1 120); do
  state=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$state" = "1" ] && { echo "BOOT_OK ($i)"; exit 0; }
  sleep 2
done
echo "BOOT_TIMEOUT"; exit 1
