# CREDITS

Bu depoda fikir olarak uygulanan dış kaynaklar.

## ShawnPana/phone-harness (MIT)

- **Repo:** https://github.com/ShawnPana/phone-harness
- **Lisans:** MIT License (https://github.com/ShawnPana/phone-harness/blob/main/LICENSE)
- **Kullanım (fikir, kod kopyalanmadı):** telefon-ajan yüzeyi kalıpları —
  - `tap` başarısızlık mesajında ekranda **görünen** etiketleri listeleme
    ("raises with what IS visible"),
  - ekran görüntüsündeki PNG pikseli → ekran noktası dönüşümünün araç
    tanımında yazılması (`screen_bounds`),
  - 2FA/şifre ekranında kontrolü kullanıcıya devretme (`cloud open` →
    `phone_handoff`), son dokunuşun kullanıcıda kalması,
  - hata sınıfı → tek "kullanıcı adımı" eşlemesi (doctor dili),
  - süre dolan oturumda **sessiz fallback yasağı** — kullanıcı hangi oturuma
    düştüğünü görünür bildirimle görsün (B4, CEO kararı).
- **Uygulayan:** cd-android, kart t_35ec8257 (16.10.2026) — Hermes-Mobil
  `feat/ema-unified-20261007` tabanlı `cd-android/k1-phoneharness-t35ec8257`
  dalı. Kod kopyalanmadan, fikir olarak uygulanmıştır; kopyalanan bir blok
  yoktur.