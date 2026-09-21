#!/bin/bash
# Tur22 kanıt: çekmece spring açılış + mesaj girişi + iskelet + boş durumlar
# Ekran kaydı: 30 sn — kullanıcı akışı: ☰ çekmece aç → yükle → satır seç →
# mesaj gönder → boş arşiv sekmesi.
set -u
ADB="adb -s emulator-5554"
OUT=$1   # video mp4 cihazda
$ADB shell screenrecord --time-limit 28 --bit-rate 6000000 /sdcard/tur22-anim.mp4 &
REC=$!
sleep 3
# 1) Çekmece aç (☰ — sol üst ilk tıklanabilir, 105,215)
$ADB shell input tap 105 215
sleep 1.5
# 2) Çekmece kapat (ortaya/sağa tap)
$ADB shell input tap 700 700
sleep 1
# 3) Mesaj yaz + gönder (gönder butonu 46dp, EditText sağı)
$ADB shell input tap 500 2030
sleep 1
$ADB shell input text "tur22"
sleep 1
$ADB shell input keyevent 66   # enter ile değil, gönder butonuyla — ama önce frame 2
# 4) Çekmece'yi tekrar aç, Arşiv sekmesi (boş durum)
$ADB shell input tap 105 215
sleep 2
wait $REC
echo KAYIT_BITTI
