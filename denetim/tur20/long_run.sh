#!/bin/sh
# Tur-20 denetim r2/1: 120 sn sahne kosusu kaniti.
# PID oncesi/sonrasi + logcat dump + kosu ici 2 ekran karesi -> kanit/uzun-kosu/
ADB="$1"; S="$2"; OUT="denetim/tur20/kanit/uzun-kosu"
mkdir -p "$OUT"
TS() { date "+%Y-%m-%d %H:%M:%S"; }

echo "=== UZUN KOSU KANITI (tur20, denetim r2 madde 1) ===" > "$OUT/ozet.txt"
echo "baslangic: $(TS)" >> "$OUT/ozet.txt"

# 0) logcat'i temizle (clear sonrasi -d yalniz yeni satirlari dondurur; -T bozuk
#    zaman formati 2. turda 0-byte dump uretmisti — bu kaldirildi).
$ADB -s "$S" logcat -c

# 1) PID oncesi
$ADB -s "$S" shell pidof com.hermes.mobile.v2 > "$OUT/pid-oncesi.txt" 2>&1
echo "pid-oncesi: $(cat "$OUT/pid-oncesi.txt" | tr -d '\r')" >> "$OUT/ozet.txt"

# 2) sahne karesi 1
$ADB -s "$S" exec-out screencap -p > "$OUT/kare-once-full.png"
python3 denetim/tur20/scene_shot.py crop "$OUT/kare-once-full.png" "$OUT/kare-once.png" >/dev/null

# 3) 120 sn bekle; 60. sn'da ara kare + ara PID al (restart ani yakala)
sleep 60
$ADB -s "$S" exec-out screencap -p > "$OUT/kare-60sn-full.png"
python3 denetim/tur20/scene_shot.py crop "$OUT/kare-60sn-full.png" "$OUT/kare-60sn.png" >/dev/null
$ADB -s "$S" shell pidof com.hermes.mobile.v2 | tr -d '\r' > "$OUT/pid-60sn.txt"
echo "kare-60sn alindi: $(TS) | pid-60sn: $(cat "$OUT/pid-60sn.txt")" >> "$OUT/ozet.txt"
sleep 60

# 4) sahne karesi 2 (kosu sonu)
$ADB -s "$S" exec-out screencap -p > "$OUT/kare-son-full.png"
python3 denetim/tur20/scene_shot.py crop "$OUT/kare-son-full.png" "$OUT/kare-son.png" >/dev/null

# 5) PID sonrasi + restart kontrolu
$ADB -s "$S" shell pidof com.hermes.mobile.v2 > "$OUT/pid-sonrasi.txt" 2>&1
echo "pid-sonrasi: $(cat "$OUT/pid-sonrasi.txt" | tr -d '\r')" >> "$OUT/ozet.txt"

# 6) logcat dump (clear sonrasi tamamini al) + kill/ANR aramasi
$ADB -s "$S" logcat -d -v time > "$OUT/logcat-dump.txt" 2>&1
echo "logcat-satir: $(wc -l < "$OUT/logcat-dump.txt" | tr -d ' ')" >> "$OUT/ozet.txt"

# 7) kare delta (gercek arac girdisi, 60sn->son arasi)
python3 denetim/tur20/scene_shot.py diff "$OUT/kare-60sn.png" "$OUT/kare-son.png" > "$OUT/kare-delta-60-son.txt" 2>&1
python3 denetim/tur20/scene_shot.py diff "$OUT/kare-once.png" "$OUT/kare-60sn.png" > "$OUT/kare-delta-once-60.txt" 2>&1
echo "delta-once-60: $(cat "$OUT/kare-delta-once-60.txt")" >> "$OUT/ozet.txt"
echo "delta-60-son:  $(cat "$OUT/kare-delta-60-son.txt")" >> "$OUT/ozet.txt"

# 8) crash/restart/kill ozeti
grep -E "FATAL|AndroidRuntime|ANR |has died|Force removing|Force stopping com.hermes|Killing .*com.hermes" "$OUT/logcat-dump.txt" > "$OUT/crash-ozet.txt"
echo "crash-satir-sayisi: $(wc -l < "$OUT/crash-ozet.txt" | tr -d ' ')" >> "$OUT/ozet.txt"
echo "bitis: $(TS)" >> "$OUT/ozet.txt"
echo "BUTUN ADIMLAR BITTI" >> "$OUT/ozet.txt"
