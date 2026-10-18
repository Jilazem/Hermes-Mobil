# K22 — Kabul kanıtları (t_c090bb4c)

Tarih: 18.10.2026 · Commit: fbb00de (feat/k22-yedekleme, taban c5c20f0)
Canlı sürüş: ikinci emülatör (hermes-duzeltme AVD, arm64, emulator-5556),
**taze kurulum** (uninstall → install app-arm64-v8a-debug.apk, sha256 ilk16: 652ebffd5a151e24).

## KABUL (1) — Export dosyası oluşur, schemaVersion'lu, token İÇERMEZ

- UI: Ayarlar → Yedekle/Geri Yükle → Yedekle → SAF CreateDocument
  (önerilen ad `hermes-mobil-yedek-20261018-1741.json`) → KAYDET →
  "Yedek kaydedildi ✓" (kanit/k22_f15_create.png).
- Çekilen dosya (`kanit/k22-export-kanit.json`) üzerinde doğrulama:
  - `schemaVersion: 1` ✓
  - `gizlilikNotu: "token: yok — geri yükleme sonrası yeniden girilir"` ✓
  - `GIZLI-TOKEN-K22` içermiyor ✓, `GIZLI-GEMINI-K22` içermiyor ✓
    (cihazda token DOLUYDU — kanit/k22_f13_token.png'de "Tokeni göster"
    ile GIZLI-TOKEN-K22 görünür; export fail-closed boşalttı)
  - profil.token="" ve geminiApiKey="" yapısal olarak boş ✓
- Unit test: `YedekMantikTest.serialize gizli tokenlari icermmez...` ✓

## KABUL (2) — Fresh kurulumda import → botlar/profiller/flaglar geri gelir

- Taze kurulum (uninstall sonrası ilk açılış "Henüz oturum yok").
- `k22-yedek.json` (2 bot, 1 prompt, 1 profil(token'lı), bayraklar) SAF
  üzerinden içeri alındı → "Geri yükleme tamamlandı ✓".
- Botlarım: "2 kart" — "K22 Araştırmacı" + "K22 Yazıcı" (kanit/k22_f10_botlar.png).
- Profil: "K22 Ev Sunucusu" + token GIZLI-TOKEN-K22 geri geldi
  (kanit/k22_f13_token.png).
- Bayraklar birebir: hidden=[s-gizli-1], archived=[s-arsiv-1],
  renames={s-1: "K22 Yeniden Adlandırıldı"} (re-export dosyasından okundu).
- Tercihler: ayarlar.fontScale=1.1 yedekten geldi.

## KABUL (3) — Birleştir / Üzerine yaz ikisi de çalışır

- Birleştir: taze cihaza 2 bot + profil + bayraklar eklendi (yukarıda).
- Üzerine yaz: `k22-yedek-2.json` (yalnız "K22 Tek Bot") → Üzerine yaz →
  Botlarım "1 kart" — yalnız "K22 Tek Bot" (kanit/k22_f18_uzerine_botlar.png,
  seçim diyaloğu kanit/k22_f17_uzerine.png).
- Unit test: birlestir/uzerineYaz 5 test ✓

## KABUL (4) — Bozuk dosyada çökme yok

- `k22-bozuk.json` ("{bozuk json ---") içeri alındı →
  "Yedek dosyası okunamadı: geçersiz ya da bozuk JSON" (kanit/k22_f19_bozuk.png),
  uygulama canlı: `pidof com.hermes.mobile.v3` = 4802 (çökme yok).

## KABUL (5) — Tüm test paketi

- `./gradlew testDebugUnitTest --rerun-tasks` (taze, cache'siz):
  **BUİLD SUCCESSFUL in 26s, EXIT=0**
- XML raporlarından: **1078 tests, 0 failures, 0 errors, 0 skipped**
  (YedekMantikTest 9/9).
- `assembleDebug` BAŞARILI; APK sha256 ilk16: arm64 `652ebffd5a151e24`,
  x86_64 `9fdcd85bd415d4ea` (fbb00de commit mesajıyla aynı).

## Notlar

- Sohbet İÇERİĞİ yedeklenmez (yalnız bayraklar) — YedekDosya şemasında
  mesaj gövdesi alanı yok; kod incelemesiyle ve testle kilitli.
- Birleştirmede aynı id'de cihazdaki kazanır (token korunur) —
  `birlestir ayni id profilinde cihaz tokenini korur` testi.
- Sürüş betiği: uiautomator dump + input tap (kartsız keşif, repo dışı).
