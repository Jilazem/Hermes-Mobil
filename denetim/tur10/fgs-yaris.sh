#!/bin/bash
# F1 emülatör kanıtı: start-foreground-service → hemen stopservice yarışı.
# Çökme (crash buffer) boş kalmalı; servis kalıntı bırakmamalı.
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
SVC=$PKG/com.hermes.mobile.data.AwaitReplyService

$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 logcat -b crash -c
for i in $(seq 1 20); do
  $ADB -s emulator-5554 shell am start-foreground-service -n "$SVC" > /dev/null 2>&1
  $ADB -s emulator-5554 shell am stopservice -n "$SVC" > /dev/null 2>&1
done
sleep 6
echo "=== CRASH BUFFER (bos olmali) ==="
$ADB -s emulator-5554 logcat -d -b crash | tail -6
echo "=== SERVIS DURUMU ==="
$ADB -s emulator-5554 shell dumpsys activity services $PKG | grep -c "AwaitReplyService"
echo "=== MAIN LOG'DA FGS/CRASH IZI ==="
$ADB -s emulator-5554 logcat -d | grep -c "ForegroundServiceDidNotStartInTime"
echo "=== UYGULAMA DIAG (yeni satirlar) ==="
$ADB -s emulator-5554 shell run-as $PKG tail -6 files/diag.log
