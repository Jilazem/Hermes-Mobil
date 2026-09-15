#!/bin/bash
# Tur-12 — dış rota DNS/erişim ölçümü (salt okuma). Tirith: ham URL'ler komut
# satırına yazılmaz, bu dosyadan koşar.
echo "=== DNS (Mac) ==="
dscacheutil -q host -a name hermes.winterfell07.keenetic.pro
echo "=== DNS (emulator) ==="
/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb -s emulator-5554 shell getprop net.dns1
echo "=== genel internet (ornek) ==="
dscacheutil -q host -a name www.google.com | head -4
echo "=== HTTPS denemesi: dis /voice-api (tokensiz, salt GET) ==="
curl -s -o /tmp/tur12-ext-health.txt -w "code=%{http_code} dns=%{time_namelookup} connect=%{time_connect} total=%{time_total}\n" --max-time 15 https://hermes.winterfell07.keenetic.pro/voice-api/health
echo "govde:"; head -c 300 /tmp/tur12-ext-health.txt; echo
