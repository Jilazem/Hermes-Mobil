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

## K18B-G3 — Merkezi onay kutusu + sesli onay

### milind-soni/OpenMausBot (Apache-2.0) — "MausBot"

- **Repo:** https://github.com/milind-soni/OpenMausBot
- **Site:** https://mausbot.com
- **Yazar:** milind-soni (ve katkıda bulunanlar)
- **Lisans:** Apache License 2.0
  (https://github.com/milind-soni/OpenMausBot/blob/main/LICENSE)
- **Esinlenilen desenler (fikir):**
  - "Waiting on your yes" — merkezi ONAY KUTUSU: riskli eylemler tek listede
    bekler, kullanıcı onayla/reddet ile geçirir,
  - sesli onay girişi ("approve / deny" konuşmayla),
  - "approval coverage garanti değildir" uyarısı: hangi eylem sınıflarının
    onaysız devam ettiği AÇIKÇA yazılır (bizdeki karşılığı: "Bazı eylemler
    durmaz" kartı — salt-okunur araçlar onaysız devam eder).
- **Uygulama:** K18B-G3 (kart t_0f6f8132) — `data/ApprovalCenter.kt`
  (ApprovalCenter/ApprovalGuard/ApprovalVoice), `ui/ApprovalsScreen.kt`.
- **Not:** kod kopyalanmadı; Apache-2.0 fikir düzeyinde serbesttir. Karar
  mekanizması bizim `/approve` / `/deny` (gateway `slash.exec`) hattımızdır.

### Chevey339/kelivo (AGPL-3.0) — "Kelivo"

- **Repo:** https://github.com/Chevey339/kelivo
- **Yazar:** Chevey339
- **Lisans:** GNU AGPL-3.0
  (https://github.com/Chevey339/kelivo/blob/master/LICENSE)
- **Esinlenilen desenler (YALNIZ FİKİR):** MCP **araç-bazlı onay**
  (per-tool approval) — onayın araç/argüman düzeyinde gösterilmesi fikri;
  kartta "Araç:" ve "Argümanlar:" satırları bu fikrin karşılığıdır.
- **Uygulama:** K18B-G3 (kart t_0f6f8132) — onay kartı alanları.
- **Not:** AGPL-3.0 nedeniyle bu depodan kod KOPYALANMAMIŞTIR; yalnız tasarım
  fikri alınmıştır. Türev kod AGPL yükümlülüğü doğurmaz.

### milind-soni/OpenMausBot "MausBot" (Apache-2.0) — roster deseni (K18B-G1)

- **Repo:** https://github.com/milind-soni/OpenMausBot
- **Site:** https://mausbot.com
- **Yazar:** milind-soni (ve katkıda bulunanlar)
- **Lisans:** Apache License 2.0
  (https://github.com/milind-soni/OpenMausBot/blob/main/LICENSE)
- **Esinlenilen desenler (fikir) — K18B-G1:**
  - **Roster:** kalıcı bot kartı — ad, kişilik, model; her bot bir kimlik,
    geçici seçim değil,
  - **pin / duplicate:** kart sabitleme ve çoğaltma ("(kopya)" adlandırma),
  - "ajanslar kişiler gibi" — bot listesi insan listesi UX'iyle yönetilir.
- **Uygulama:** K18B-G1 (kart t_b5ebf51f) — `data/BotRoster.kt`
  (BotCard/BotRosterLogic), `data/BotRosterStore.kt`, `ui/BotRosterScreen.kt`.
- **Not:** kod kopyalanmadı; Apache-2.0 fikir düzeyinde serbesttir. Pin/duplicate
  kuralları kendi saf mantığımız (`BotRosterLogic`) olarak yazıldı ve birim
  testleriyle kilitlendi.

### cedar-panda/Holt "Holt" (AGPL-3.0) — karakter-bazlı yapı (K18B-G1)

- **Repo:** https://github.com/cedar-panda/Holt
- **Yazar:** cedar-panda
- **Lisans:** GNU AGPL-3.0 (repo lisans alanı; K18B raporu §1 satır 7 doğruladı)
- **Esinlenilen desenler (YALNIZ FİKİR):** karakter-bazlı bot imalatı — her
  karakterin kendi sohbet/hafıza/özet ayarı; kartta "kişilik" alanının
  kullanıcının girdisi olarak saklanması bu fikrin karşılığıdır.
- **Uygulama:** K18B-G1 (kart t_b5ebf51f) — `BotCard.personality` alanı +
  `ui/BotRosterScreen.kt` editör formu.
- **Not:** AGPL-3.0 nedeniyle bu depodan kod KOPYALANMAMIŞTIR; yalnız tasarım
  fikri alınmıştır. Türev kod AGPL yükümlülüğü doğurmaz.

### Weltenwanderer1/ai-buddy "AI-Buddy" (MIT) — evrilen persona (K18B-G1)

- **Repo:** https://github.com/Weltenwanderer1/ai-buddy
- **Yazar:** Weltenwanderer1
- **Lisans:** MIT License (K18B raporu §1 satır 3: lisans MIT doğrulandı)
- **Esinlenilen desenler (YALNIZ FİKİR):** persona + öz-alan ("self-image")
  yapılandırılabilirliği — persona'nın kullanıcı girdisi olduğu ve cihazda
  kaldığı ilkesi (bot kişiliği ayrı bir alan olarak saklanır, varsayılan
  değildir).
- **Uygulama:** K18B-G1 (kart t_b5ebf51f) — `BotCard.personality` (kullanıcı
  girdisi, `bot-roster.json` içinde cihazda kalır; sunucuya ayrı alan olarak
  gönderilmez).
- **Not:** kod kopyalanmadı; MIT fikir düzeyinde serbesttir.
