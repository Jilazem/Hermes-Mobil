# K21 İKİNCİ KOŞUM KANITI — canlı emülatör UI koşumu (cd-coder, 18.10.2026 15:37)

İlk KANIT.md'de emülatör offline olduğundan jest ekran kanıtı yapılamamıştı; bu turda
emülatör (emulator-5554, API 34) çevrimiçi olduğu için 5 kabul maddesinin tamamı
**gerçek UI üzerinde** koşuldu. Ortam: app-arm64-v8a-debug.apk (d015dad derlemesi),
k21mock profili → mock sunucu (denetim/tur22/mock_tur22.py türevi, 10.0.2.2:9171).

## Kabul maddeleri (brief bcsx9b) — koşum sonucu

| # | Madde | Sonuç | Kanıt |
|---|-------|-------|-------|
| 1 | Sola tam çek → arşiv + "Geri Al" | ✓ | 08-arsivlendi.png ("Arşivlendi · Geri al" satırı), 09 sonrası satır listede geri geldi |
| 2 | Sağa ilk çek → satır yarıda TAKILIR, ipucu görünür | ✓ | 04-takili.png: kırmızı zemin + "Silmek için tekrar çek", satır içeriği x=543'e kaymış (yarı) |
| 3 | Takılıyken ikinci tam çek → gizlenir + "Geri Al" | ✓ | 06-silindi.png: satır listeden kayboldu, "Silindi · Geri al"; 07 sonrası satır geri geldi |
| 4 | Sıfırlama yolu (ARM altına geri sürükleme / başka yere dokunma) | ✓ | 05-sifirlandi.png: kırmızı zemin/ipucu kalktı, satır x=84'e toplandı |
| 5 | Test paketi tamamen geçiyor | ✓ | Taze koşum (--rerun-tasks): **1080 test, 0 hata, 0 atlandı** (BUİLD SUCCESSFUL) |

## Koşum adımları (adb input, koordinatlar 1080x2400)

1. arm: `input swipe 100 762 600 762 800` → satır takıldı (uiautomator: "Silmek için
   tekrar çek" [121,762][449,804], "K21 Oturum A" x=543)
2. sıfırlama: `input swipe 700 762 350 762 400` → satır x=84'e toplandı
3. arm tekrar → takılı
4. sil: `input swipe 475 762 1450 762 800` → satır gizlendi + "Silindi [64,613] ·
   Geri al [765,613][854,650]"; Geri al dokunuşu (810,631) → satır geri geldi
5. arşiv: `input swipe 800 762 30 762 800` → "Arşivlendi · Geri al"; dokunuş → geri geldi
6. Final durum dump'ı: her iki satır da listede (undo'lar işledi).

Not: undo satırı 5 sn'de otomatik temizleniyor (SessionDrawer.kt:189 delay(5_000));
kanıt kareleri bu pencere içinde alındı. Silme sunucuya GİTMEDİ (yerel gizleme +
restoreSession; mock sunucu logunda DELETE yok).

## Dosyalar

- k21-final.mp4 — tüm zincirin ekran kaydı (sha256 ilk 16: e5bc69366032660a)
- 04-takili, 05-sifirlandi, 06-silindi, 07-geri-alindi, 08-arsivlendi, 09-arsiv-geri .png
- Test kanıtı: /tmp/k21-verify-test.log → EXIT=0, TOTAL=1080 FAIL=0 SKIPPED=0
- assembleDebug: BUILD SUCCESSFUL (apk: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk)
