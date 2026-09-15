#!/bin/bash
# tur-12b: APK icindeki TUM dex dosyalarinda yeni UI metni / eski metin arama
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman || exit 1
APK=app/build/outputs/apk/debug/app-debug.apk
D=/tmp/tur12b_dex
rm -rf "$D"
mkdir -p "$D"
unzip -q -o "$APK" 'classes*.dex' -d "$D"
echo "--- dex dosyalari ---"
ls "$D"
for f in "$D"/classes*.dex; do
  n=$(basename "$f")
  yeni=$(grep -ac "bazen 5 dk" "$f")
  eski=$(grep -ac "3 dakikaya kadar" "$f")
  isit=$(grep -ac "Isıtmasız ilk yanıt" "$f")
  sn420=$(grep -ac "sn'de tamamlanmad" "$f")
  echo "$n : yeni_ui=$yeni eski_ui=$eski isit_ipucu=$isit warm_mesaji=$sn420"
done
