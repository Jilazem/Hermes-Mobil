# CREDITS — fikir kaynakları

Bu depodaki bazı tasarım desenleri dış projelerden **esinlenilmiştir**. Kod
kopyalanmamıştır; her yeniden uygulama bu deponun kendi mimarisine (saf çekirdek
+ Android katmanı + birim test) göre yazılmıştır.

## ShawnPana/phone-harness (MIT)

- **Repo:** https://github.com/ShawnPana/phone-harness
- **Lisans:** MIT License (https://github.com/ShawnPana/phone-harness/blob/main/LICENSE)
- **Esinlenilen desenler (K2 paketi):**
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
- **Kullanım (fikir, kod kopyalanmadı) — K1 paketi:** telefon-ajan yüzeyi
  kalıpları —
  - `tap` başarısızlık mesajında ekranda **görünen** etiketleri listeleme
    ("raises with what IS visible"),
  - ekran görüntüsündeki PNG pikseli → ekran noktası dönüşümünün araç
    tanımında yazılması (`screen_bounds`),
  - 2FA/şifre ekranında kontrolü kullanıcıya devretme (`cloud open` →
    `phone_handoff`), son dokunuşun kullanıcıda kalması,
  - hata sınıfı → tek "kullanıcı adımı" eşlemesi (doctor dili),
  - süre dolan oturumda **sessiz fallback yasağı** — kullanıcı hangi oturuma
    düştüğünü görünür bildirimle görsün (B4, CEO kararı).
- **Not:** phone-harness doctor içeriği upstream `main@68d883f` (PR #138) ile
  gelir; yerel pinli kurulumda (0.3.2) yoktur. Buradaki Doctor alan listesi
  CZİP `8pa3sv` bulgusu ve GAP raporu kapsamından alınmıştır.
- **Uygulayan:** cd-android — K1 kartı t_35ec8257 (dal
  `cd-android/k1-phoneharness-t35ec8257`, commit d8c0470) ve K2 kartı
  t_384678f7 (dal `feat/ema-unified-20261007`, commit 3dd7a9e), 16-17.10.2026.
  Kod kopyalanmadan, fikir olarak uygulanmıştır; kopyalanan bir blok yoktur.