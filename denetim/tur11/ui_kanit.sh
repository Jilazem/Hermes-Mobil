#!/bin/bash
# Tur-11 UI kanıtı: sohbet ekranında bas-konuş mikrofonu ve Ayarlar'daki
# "Sesli mesaj" bölümü gerçekten çiziliyor mu? (uiautomator dökümü)
ADB=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb
PKG=com.hermes.mobile.v2
OUT=/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur11

"$ADB" -s emulator-5554 logcat -c -b crash
"$ADB" -s emulator-5554 shell am force-stop "$PKG"
"$ADB" -s emulator-5554 shell am start -n "$PKG/com.hermes.mobile.MainActivity" >/dev/null
sleep 8

# 1) Sohbet ekranı dökümü (bas-konuş düğmesi metni burada).
"$ADB" -s emulator-5554 shell uiautomator dump /sdcard/ui-sohbet.xml >/dev/null 2>&1
"$ADB" -s emulator-5554 shell cat /sdcard/ui-sohbet.xml > "$OUT/ui-sohbet.xml"

# 2) Ayarlar sekmesine git (alt sekme çubuğu: Ayarlar hücresinin merkezine dokun).
BOUNDS=$("$ADB" -s emulator-5554 shell cat /sdcard/ui-sohbet.xml | tr '>' '>\n' | grep 'text="Ayarlar"' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
echo "Ayarlar hucresi: $BOUNDS"
X1=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1/')
Y1=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\2/')
X2=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\3/')
Y2=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\4/')
if [ -n "$X1" ]; then
    CX=$(( (X1 + X2) / 2 )); CY=$(( (Y1 + Y2) / 2 ))
    "$ADB" -s emulator-5554 shell input tap "$CX" "$CY"
    sleep 3
    "$ADB" -s emulator-5554 shell uiautomator dump /sdcard/ui-ayarlar.xml >/dev/null 2>&1
    "$ADB" -s emulator-5554 shell cat /sdcard/ui-ayarlar.xml > "$OUT/ui-ayarlar.xml"
    echo "dokunulan: $CX,$CY"
fi
echo "=== crash tamponu ==="
"$ADB" -s emulator-5554 logcat -d -b crash | tail -3
ls -la "$OUT"/ui-*.xml
