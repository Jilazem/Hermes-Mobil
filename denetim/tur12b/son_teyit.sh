#!/bin/bash
# tur-12b son teyit: APK icinde yeni metin + 420 sn sabiti (dex), calisma agaci temiz mi
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman || exit 1
APK=app/build/outputs/apk/debug/app-debug.apk
echo "--- APK dex icinde yeni UI metni ve sabit ---"
unzip -p "$APK" classes.dex > /tmp/tur12b_classes.dex
echo "yeni-ui-metni(bazen 5 dk) = $(grep -ac "bazen 5 dk" /tmp/tur12b_classes.dex)"
echo "eski-ui-metni(3 dakikaya kadar) = $(grep -ac "3 dakikaya kadar" /tmp/tur12b_classes.dex)"
echo "warm-420-mesaji = $(grep -ac "sn'de tamamlanmad" /tmp/tur12b_classes.dex)"
echo "--- sabit (kaynak) ---"
grep -n "420_000L" app/src/main/java/com/hermes/mobile/data/VoiceApiEndpoints.kt
echo "--- git ---"
git log --oneline -2
git status --short
git log --oneline origin/main -1 2>/dev/null || true
echo "--- yarn: uzak dal farki (push YOK teyidi) ---"
git rev-list --count @{u}..HEAD 2>/dev/null || echo "upstream yok (push edilmedi)"
rm -f /tmp/tur12b_classes.dex
