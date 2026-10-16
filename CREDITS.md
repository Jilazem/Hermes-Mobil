# CREDITS — fikir kaynakları

Bu depodaki bazı tasarım desenleri dış projelerden **esinlenilmiştir**. Kod
kopyalanmamıştır; her yeniden uygulama bu deponun kendi mimarisine (saf çekirdek
+ Android katmanı + birim test) göre yazılmıştır.

## ShawnPana/phone-harness

- **Lisans:** MIT — https://github.com/ShawnPana/phone-harness
- **Esinlenilen desenler:**
  - `wait_for_text` / `wait_for_app` / `wait_stable` → `screen.wait` aracı
    (deadline'lı bekle-doğrula; `FullControl.parseWait/waitSatisfied`).
  - `scroll_until` / `scroll_collect` → `screen.scroll_step` aracı
    (momentum'suz içerik kaydırma + bitiş algılama; "`scroll` says what you
    want to SEE" yön semantiği).
  - `phone-harness doctor` → Ayarlar'daki **Doctor** kartı (birleşik izin
    zinciri taraması + "şimdi yap" aksiyonu).
  - `cloud ls` (tek liste, satır başına durum) → **Canlı envanter** kartı.
  - "görev bitince haber ver" → Artemis görevi tamamlanınca `phone_notify`
    bildirimi.
- **Not:** phone-harness doctor içeriği upstream `main@68d883f` (PR #138) ile
  gelir; yerel pinli kurulumda (0.3.2) yoktur. Buradaki Doctor alan listesi
  CZİP `8pa3sv` bulgusu ve GAP raporu kapsamından alınmıştır.