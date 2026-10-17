# K18B-B — Sohbette bot kimliği rozeti (ad+emoji+model afişi) — RAPOR

- **Kart:** t_8de5cfe3 (cd-android) · **Tarih:** 2026-10-17
- **Çalışma kopyası:** ANA HAT `/Users/gokhanuzman/Documents/Codex/2026-10-07/files-mentioned-by-the-user-g/work/Hermes-Mobil`,
  taban `feat/ema-unified-20261007` (G1 merge 29396dd sonrası) — ana hat kopyasında doğrudan çalışıldı, dal açılmadı (görev talimatı).
- **Kaynak:** K18B raporu §5 K18B-B + MausBot "Pick a brain per bot" deseni (yalnız fikir; CREDITS.md güncellendi).

## Bulunan

- Sohbet mesaj modelinde (`ChatItem`) bot kimliği alanı yok; kimlik oturumun
  profil + aktif model bilgisinde. ChatViewModel HOTSPOT'una dokunmadan rozet
  yalnız görüntüleme katmanından çözülebilir (görev kısıtı: hotspot'u minimize et).
- G1'in `BotRoster` verisi (`data/BotRoster.kt`, `BotRosterStore` cihaz JSON'u)
  ad+emoji+profil+model taşıyor — rozet kimliğinin tek doğal kaynağı.
- Emülatör kanıt altyapısı hazır: `tools/k18b-g1/mock_gateway.py` (sahte
  gateway, JSON-RPC + REST) + `roster_flow.py` dump/tap/shot deseni.

## Yapılan

1. **`ui/BotBadge.kt` (yeni, saf mantık + Compose satırı):**
   - `BotBadgeUi(emoji, name, modelLabel)` + `botBadgeLabel()` — ham kart id'si
     ASLA görünmez (D-05).
   - `resolveBotBadge(cards, profile, model)`: kart eşleme sırası **birebir
     model > modeli boş kart > profil eşleşen ilk kart**; kart yoksa genel
     kimlik (🤖 + profil adı / "Yönlendirici"); profil+model bilinmiyorsa
     `null` (rozet yok). **Model afişi oturumun aktif modelinden** — model
     değişince afiş yenilenir, bot adı kalır.
   - `botBadgeRowKeys(rows)`: kullanıcı satırıyla bölünmüş her **bot grubunun
     ilk satırı** rozet alır; sistem (Notice) satırı grubu bölmez ve tek başına
     rozet almaz; kullanıcı mesajlarında rozet YOK.
   - `BotBadgeRow`: emoji+ad (koyu, yarı-kalın) + model etiketi (ince çerçeveli
     pill); erişilebilirlik `contentDescription = "🔍 Ad · model"`.
2. **`ui/ChatScreen.kt` (27 satır):** `ChatRow` görünürlüğü private→internal;
   rozet `remember` ile roster kartları + `currentProfile ?: seçili çip` +
   `state.currentModel`'den çözülür; gün ayırıcının altında, mesajın üstünde
   çizilir. **ChatViewModel'e dokunulmadı.**
3. **`BotBadgeLogicTest.kt` (23 test):** kart eşleme/afiş güncelleme/genel
   kimlik/null kuralları (12), grup başına tek rozet + kullanıcı/sistem
   ayrımı (9), wiring-grep (2) — JarvisUiWiringTest deseni.
4. **`CREDITS.md`:** MausBot (Apache-2.0) girişi K18B-G1/B olarak güncellendi
   ("Pick a brain per bot" deseni + `ui/BotBadge.kt`).
5. **`tools/k18b-b/badge_flow.py`:** emülatör kanıt akışı (seed/roster/devam/
   sohbet/model). `run-as` komutları TEK dizi quoting'le geçilir (adb argv
   birleştirme tuzağı — bkz. ders defteri).

## Atlanan

- **Mesaj-bazlı kimlik anlık görüntüsü (snapshot) YOK — bilinçli tasarım kararı:**
  rozet kimliği her bot grubunda oturumun O ANKİ profil+model'ini gösterir;
  model değişince tüm afişler yenilenir. Mesaj başına tarihsel model kaydı
  için `ChatItem`'a alan eklenmesi gerekir → ChatViewModel hotspot'u büyür;
  görev kısıtı gereği yapılmadı. İstenirse takip kartı: "ChatItem.botTag
  snapshot + geçmiş mesajlarda tarihsel afiş".
- Arena tarafına dokunulmadı (görev: "Arena/normal sohbet ayrımı korunur");
  ArenaScreen'deki `BotCard(ans)` Arena yanıt kartı ayrı tiptir, çakışma yok.
- Rozet tıklanabilirliği / profil kartına yönlendirme — talep edilmedi (LOW not).

## Kanıt (komut + çıktı + yol)

- **Birim testler:** `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --no-build-cache`
  → `BUILD SUCCESSFUL in 16s`; `denetim/count_tests.py` → **98 suite / 1069 tests / 0 failures / 0 errors / 0 skipped**
  (taban 1046 + 23 yeni). Log: `denetim/k18b-b/test-kozum.log`.
- **APK:** `./gradlew :app:assembleDebug` → EXIT=0; `app-arm64-v8a-debug.apk`
  **md5 0f0f8b53afaed25302e4b1f9b0d08d79** (emülatör arm64-v8a'ya kuruldu);
  log: `denetim/k18b-b/assemble.log`, `denetim/k18b-b/apk-md5.txt`.
- **Emülatör (emulator-5554, com.hermes.mobile.v3, mock gateway :9199):**
  - `em-01-botlarim-kart.png` — G1 roster kartı ("Arastirmaci") Botlarım'da görünür (fikstür gerçek G1 verisi).
  - `em-03-tur1-cevap.png` / `em-04-tur2-cevap.png` — **bot'lu + botsuz karşılaştırma:**
    kullanıcı balonlarında rozet YOK; her bot grubunun başında
    `🔍 Arastirmaci` + `deepseek-ai/DeepSeek-V4.1-Flash` afişi (görsel doğrulama).
  - `em-06-model-secici.png` — Menü → "Model ve profiller" sheet.
  - `em-07-afis-guncellendi.png` — model `Hermes-4-405B`'ye değişti:
    rozet `🔍 Arastirmaci [Hermes-4-405B]` — **ad kaldı, afiş güncellendi**
    (uiautomator dump: content-desc `🔍 Arastirmaci · Hermes-4-405B`, iki bot
    grubunda da; kullanıcı mesajlarında rozet düğümü yok).
  - Akış stdout'u + tüm dump'lar: `denetim/k18b-b/ui-*.xml`, betik:
    `tools/k18b-b/badge_flow.py` (reprodüksiyon: mock_gateway.py 9199 açıkken
    `python3 tools/k18b-b/badge_flow.py hepsi`).
- **Mock gateway RPC kanıtı:** `message.delta`/`message.complete` olayları
  mock-s2 oturumunda işlendi (ekran görüntülerindeki "Mock yanit (mock-s2)"
  satırları + `denetim/k18b-b/mock-gateway.log`).

## Commit listesi

- `feat(k18b-b): sohbette bot kimliği rozeti — ad+emoji+model afişi` (bu iş:
  BotBadge.kt, ChatScreen.kt, BotBadgeLogicTest.kt, CREDITS.md,
  tools/k18b-b/, denetim/k18b-b/)

## Test sayısı

- 98 suite / **1069** test / 0 fail / 0 err (taban 1046 → +23: BotBadgeLogicTest).

## Açık riskler

- Rozet kimliği render-time çözülüyor (bkz. Atlanan): uzun oturumda model
  değişirse ESKİ mesaj gruplarının afişi de yenilenir — tarihsel doğruluk yok.
  Sözleşme testte kilitli (`model degisince afis guncellenir bot adi kalir`);
  ürün kararı gerekiyorsa snapshot'lı sürüm takip kartı ister.
- Roster kartı ile oturum profili uyuşmazsa (ad farkı, kart silinmiş) rozet
  genel kimliğe (🤖 + profil adı) düşer — sessiz boşluk yok, davranış fail-safe.
- Aynı profilde çok kart varsa birebir model eşleşmesi öncelikli; ikisi de
  yoksa ilk kart seçilir (kararlı sıra, testli).

## Denetmene not

- ChatViewModel hotspot'una **hiç dokunulmadı** (diff: yalnız ChatScreen.kt
  +27, yeni dosyalar, CREDITS.md). `ChatRow` private→internal tek görünürlük
  değişikliği — davranış yok.
- "bot olmayan mesajlarda görünmez" yorumu: KULLANICI mesajları + yalnız
  sistem satırından oluşan gruplar rozetsiz; bot turları (asistan/düşünme/
  araç/onay) her zaman rozetli (kart yoksa genel kimlikle). Bu yorum
  `BotBadgeLogicTest`'in 9 gruplama testinde kilitli.
- Kırılma modları: boş/null profil+model (rozet yok, testli), kart silinmiş
  (genel kimlik, testli), model değişimi (afiş yenilenir, testli), Notice
  ortası (grup bölünmez, testli), tam sohbet akışı/fullChatFlow (aynı
  satır-mantığı), klavye/aç-kapa (rozet remember'ı state.items'a bağlı değil,
  yeniden çizim güvenli).