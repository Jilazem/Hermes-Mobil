#!/bin/bash
# Tur-24 JARVIS-2 — surekli sesli sohbet dongusu kaniti.
# Kullanim: bash tools/kanit/t24-loop-proof.sh <kanit-klasoru>
# On kosul: emulator acik, debug APK kurulu (paket .v2).
# Gercek JarvisLoopController + gercek ticker koar; cevre sahte (script'li
# genlik, mock transport, anlik player). Beklenen: 2 tam tur + "kapat"
# komutuyla kapanis; 4 farkli faz PNG'si (farkli md5).
set -u
ADB=${ADB:-/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb}
DEV=${DEV:-emulator-5554}
PKG=com.hermes.mobile.v2
ACT=com.hermes.mobile.ui.JarvisLoopProofActivity
OUT=${1:?kanit klasoru verin}
mkdir -p "$OUT"
$ADB -s "$DEV" shell "am force-stop $PKG"; $ADB -s "$DEV" logcat -c
# Bildirim URETIM yolu izin kontrolu yapiyor (Notifier.jarvisListening) — verilmezse sessiz atlar, PNG bos kalir.
$ADB -s "$DEV" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
$ADB -s "$DEV" shell "am start -n $PKG/$ACT" >/dev/null
# Faz pencereleri: t=1s Listening(tur1) | t=3.3s Speaking/ENGINE(tur1)
#                   | t=8s Listening(tur2 gec)  | t=14s Kapanis
shot() { $ADB -s "$DEV" shell screencap -p /sdcard/$1.png && $ADB -s "$DEV" pull /sdcard/$1.png "$OUT/$2" >/dev/null; }
sleep 1.0;  shot s1 shot_tur1_dinle.png
sleep 2.3;  shot s2 shot_tur1_oku.png
sleep 4.5;  shot s3 shot_tur2_dinle.png
# Madde-4: kalici "Jarvis dinliyor" bildirimi URETIM yolundan (Notifier) —
# golge paneli acik PNG. Bildirim kapanista kaldirilacagi icin burada cekilir.
$ADB -s "$DEV" shell cmd statusbar expand-notifications 2>/dev/null
sleep 0.6;  shot s5 shot_bildirim.png
$ADB -s "$DEV" shell cmd statusbar collapse 2>/dev/null
sleep 3;    shot s4 shot_kapanis.png
$ADB -s "$DEV" logcat -d | grep -a "JARVIS-LOOP-PROOF" | sed 's/.*JARVIS-LOOP-PROOF. *: //' > "$OUT/log.txt"
grep -q "KANIT TAMAM" "$OUT/log.txt" && echo "VERDICT: 2 tur + kapat komutu KANITLANDI" || { echo "VERDICT: eksik"; exit 1; }
md5 -q "$OUT"/shot_*.png
