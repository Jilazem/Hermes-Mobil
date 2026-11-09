# CREDITS — K18 taslağı (rakip repo kredileri)

> **TASLAK (K18 kartı, 2026-10-17):** Repo kökündeki mevcut `CREDITS.md`'ye **ek bölüm** olarak
> birleştirilecektir (mevcut ShawnPana/phone-harness girişi korunur). Bu kartta KOD alınmamıştır;
> benimseme ayrı kartlarla (K18-a…K18-h) ve aşağıdaki kredi şartıyla yapılacaktır.
>
> **Kural (patron talimatı, MUTLAK):** Benimsenen her fikir/kod için repo + yazar + lisans + bağlantı
> buraya yazılır; ilgili kart tamamlanırken girdi "benimsendi" olarak güncellenir.
>
> **Lisans tavanı:** AGPL-3.0 ve lisanssız kaynaklardan **kod kopyalanmaz, dönüştürülerek kullanılmaz** —
> yalnız bağımsız fikir benimsenir ve fikir kaynağı olarak kredilenir.

## 1. Hy4ri/hermes-mobile — Apache-2.0 (KOD benimsemeye uygun)

- **Repo:** https://github.com/Hy4ri/hermes-mobile (dal `dev`)
- **Yazar:** Hy4ri (GitHub)
- **Lisans:** Apache License 2.0 — https://github.com/Hy4ri/hermes-mobile/blob/dev/LICENSE
- **Durum:** ADAY — henüz benimsenmedi (K18-b, K18-d, K18-g)
- **Benimsenecek desenler/kod:**
  - Uygulama kilidi (biyometrik) + bildirim önizleme gizlilik sınırları → **K18-d**
  - mTLS, CA sabitleme, kullanıcı CA'sı, Cloudflare Access özel başlıkları → **K18-b**
  - Webhook testi, süreç izleme, canlı log filtre, gateway durum ekranı → **K18-g**
  - Fikir: cron yönetim ekranı, Kanban/milestone görünümleri, F-Droid + Weblate dağıtımı

## 2. omarqaterge/hermes-mobile-app — MIT (KOD benimsemeye uygun)

- **Repo:** https://github.com/omarqaterge/hermes-mobile-app
- **Yazar:** omarqaterge (GitHub)
- **Lisans:** MIT License — https://github.com/omarqaterge/hermes-mobile-app/blob/main/LICENSE
- **Durum:** ADAY — henüz benimsenmedi (K18-a, K18-c, K18-f, K18-g)
- **Benimsenecek desenler/kod:**
  - Bildirimden onay/izin/komut + durum çubuğu "island" (Android 16 Live Update) → **K18-a**
  - Hafıza ekranı + her hafıza yazımının diff görünümü → **K18-c**
  - Dosya checkpoint / geri alma → **K18-f**
  - Fikir: cron sonuç bildirimi, offline mesaj kuyruğu, HTML/3D "canvas"

## 3. goncharik/hermes-mobile — MIT (KOD benimsemeye uygun)

- **Repo:** https://github.com/goncharik/hermes-mobile
- **Yazar:** goncharik (GitHub)
- **Lisans:** MIT License — https://github.com/goncharik/hermes-mobile/blob/main/LICENSE
- **Durum:** ADAY — henüz benimsenmedi (K18-h)
- **Benimsenecek desenler/kod:**
  - Bağlam doluluk göstergesi (context pill) + sıraya mesaj koyma → **K18-h**
  - Cevabı yeni sohbete dallandırma; oturum pin/rename/kalıcı silme → **K18-h**
  - Fikir: "Her yerden onayla" mobil onay akışı, OAuth/OIDC giriş, iPad geniş düzen

## 4. CodeUpdaterBot/Hermes-Mobile-App — AGPL-3.0 (YALNIZ FİKİR — KOD YASAK)

- **Repo:** https://github.com/CodeUpdaterBot/Hermes-Mobile-App
- **Yazar:** CodeUpdaterBot (GitHub)
- **Lisans:** GNU AGPL-3.0 — https://github.com/CodeUpdaterBot/Hermes-Mobile-App/blob/main/LICENSE
- **Durum:** FİKİR KAYNAĞI — AGPL uyumsuzluğu nedeniyle **hiçbir kod/dönüştürülmüş kod kullanılmaz**
- **Esinlenilen fikirler:**
  - Çok-Botlu grup sohbeti (atıflı, akışlı) → **K18-e**
  - Güvenli uzak eşleştirme yaklaşımı: Tailscale kılavuzu + `/api/status` keşif kontrolü, host gateway kurucu betik fikri → **K18-b**
  - Fikir: foldable/tablet üç panolu düzen, canlı Bot aynası, onay/soru gelen kutusu

## 5. amirghm/hermes-agent-mobile — LİSANS YOK (YALNIZ FİKİR — KOD YASAK)

- **Repo:** https://github.com/amirghm/hermes-agent-mobile
- **Yazar:** amirghm (GitHub)
- **Lisans:** **Yok** (repo `license: null` — API ile doğrulandı 2026-10-17)
- **Durum:** FİKİR KAYNAĞI — lisanssız olduğu için kod benimsemek yasak
- **Esinlenilen fikirler:**
  - Telegram gruplarında çoklu-ajan rolleri (ekip mantığı) → **K18-e**
  - Fikir: cron ile web/fiyat izleme + uyarı, sesli çalışma akışı (STT/TTS)

## 6. Binair-Dev/HermesAgentMobile — MIT (referans; kod adayı yok)

- **Repo:** https://github.com/Binair-Dev/HermesAgentMobile
- **Yazar:** Binair-Dev (GitHub)
- **Lisans:** MIT License — https://github.com/Binair-Dev/HermesAgentMobile/blob/main/LICENSE
- **Kendi kredisi (dürürlükte):** proot kabuk/terminal/app mimarisi [mithun50/openclaw-termux](https://github.com/mithun50/openclaw-termux) kaynaklıdır.
- **Esinlenilen fikirler:** cihaz-üstü (Termux/proot) gateway kurulumu, tek dokunuşla kurulum, canlı gateway log görüntüleyici → **G15** (gelecek stratejik kart)

## 7. sesaloy/hermes-mobile — MIT (referans; kod adayı yok)

- **Repo:** https://github.com/sesaloy/hermes-mobile
- **Yazar:** sesaloy (GitHub)
- **Lisans:** MIT License — https://github.com/sesaloy/hermes-mobile/blob/main/LICENSE
- **Esinlenilen fikirler:** APK içine gömülü Hermes (Chaquopy + ARM64), `android_debug` aracı ile eşleştirilmiş cihaz tanılama, Skills Hub → **G15**

## 8. Padilha-BR/hermes-agent-android-phone-only — MIT (referans; kod adayı yok)

- **Repo:** https://github.com/Padilha-BR/hermes-agent-android-phone-only
- **Yazar:** Padilha-BR (GitHub)
- **Lisans:** MIT License — https://github.com/Padilha-BR/hermes-agent-android-phone-only/blob/main/LICENSE
- **Esinlenilen fikirler:** Android bellek bütçesi ölçüm rehberi, watchdog + Termux:Boot ile ayakta tutma, "loopback güven sınırı değildir" güvenlik notları → **G15**

## 9. Keşif kaynağı — GitHub araması (9. madde)

- **Kaynak:** https://github.com/search?q=hermes+apk (ve `hermes agent mobile`, `hermes mobile` sorguları — GitHub Search API, 2026-10-17)
- Bu aramadan envantere eklenen adaylar (henüz benimsenmedi; ileride değerlendirilecek):
  [p697/clawket](https://github.com/p697/clawket) (AGPL-3.0), [clawvader-tech/hermes-telegram-miniapp](https://github.com/clawvader-tech/hermes-telegram-miniapp) (MIT), [przbadu/hermes-ui](https://github.com/przbadu/hermes-ui) (MIT), [filipj9/Hermes-Control-Deck](https://github.com/filipj9/Hermes-Control-Deck) (MIT), [unsupportedpastels/mercury](https://github.com/unsupportedpastels/mercury) (MIT), [jeromeleeqy-hash/Hermes-Android](https://github.com/jeromeleeqy-hash/Hermes-Android) (lisanssız), [shilp26/hermes-go](https://github.com/shilp26/hermes-go) (MIT), [stasstepv/hermes-pwa](https://github.com/stasstepv/hermes-pwa) (MIT), [2winter-dev/iHermes](https://github.com/2winter-dev/iHermes) (MIT), [demi-hl/hermes-ios-app](https://github.com/demi-hl/hermes-ios-app) (MIT), [areu01or00/Hermes-Agent-Mobile-Client](https://github.com/areu01or00/Hermes-Agent-Mobile-Client) (MIT), [synthalorian/hermes-wingman](https://github.com/synthalorian/hermes-wingman) (Apache-2.0), [willscott-v2/hermes-mobile-pwa](https://github.com/willscott-v2/hermes-mobile-pwa) (MIT), [Lord1Egypt/PocketClaw](https://github.com/Lord1Egypt/PocketClaw) (MIT), [plcunha/Hermes-Mobile](https://github.com/plcunha/Hermes-Mobile) (MIT)

---

### Mevcut giriş (korunacak — dokunulmayacak)

*(repo kökündeki CREDITS.md'den)*
- **ShawnPana/phone-harness** (MIT) — K1/K2 paketi telefon-ajan yüzeyi kalıpları (bkz. mevcut CREDITS.md).