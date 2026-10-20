# V5-4 CZIP AUTO — yeni oturumlarda arka plan paketleme güvencesi

Tarih: 20.10.2026 | Uzman: cd-android | Dal: feat/v5-czip (taban k23-son-hal @ 3efff04)
Görev: Görev Hattı #1020-bc91ab · czip promt pvxagh

## Karar: HER İKİ YOL (b birincil, a tamamlayıcı) — gerekçe

- **(b) Gateway/sunucu tarafı kalıcı çözüm:** czip-auto HER oturumda açık
  başlar — `czip/ayar.json` `oto=true` (auto-policy.json, 19.10.2026,
  "all existing and future Hermes sessions") + `czip-auto` eklentisinin
  `on_session_start` hook'u (`czip_auto.py` arka plan paketleyiciyi başlatır;
  `ayar.oku()["oto"]` kapalıysa sessizce durur). Bu katman tüm istemciler
  (CLI, Telegram, mobil) için ortak; tek doğruluk kaynağı burası.
- **(a) Uygulama güvencesi (bu işin kod tarafı):** mobil istemci sunucu
  ayarına güvenmekle kalmaz, yeni oturumda doğrular: `czip oto durum` →
  kapalı/tanınmadıysa `czip oto aç` → sonucu ekranda tek satır bildirimle
  kanıtlar. Tercih `AppSettings.czipAuto` ile hatırlanır (varsayılan AÇIK;
  kapatılırsa uygulama sunucu ayarına dokunmaz).
- Neden ikisi de: (b) tek başına mobil ekranda kanıt üretmez ve profil ayarı
  sürüklenirse sessiz kalır; (a) tek başına her oturumda sunucu garantisini
  yeniden kurmak zorunda kalır. (b) garantiler, (a) doğrular + kanıtlar.

## Yapılan

- `data/CzipAutoLogic.kt` (yeni, SAF): `state(output)` — `czip oto durum`
  ("oto : ON") ve `czip oto aç` ("oto -> ON") çıktılarını okur; `oto_pasif`
  karışmaz; traceback/boş → UNKNOWN (varsayım uydurulmaz). `ensureCommands`,
  `noticeTr/noticeEn`.
- `ChatViewModel.ensureCzipAuto`: `createSessionWithProfile` (BÜTÜN kullanıcı
  oturumları tek geçit: gönder, czip-sürdür, model/reasoning oturumları) →
  arkada durum sorgusu + gerekirse açma + ekranda bildirim. Gönderimi
  bloklamaz; açık durumda bildirim ilk kez yazılır, sonraki oturumlarda
  garanti sessizdir. Görünmez yardımcı oturumlar (runCzipRead,
  improvePrompt, refreshReasoning) kapsam dışı — kullanıcı oturumu değiller.
- `AppSettings.czipAuto: Boolean = true` (hatırlanan tercih) +
  `MainActivity` LaunchedEffect ile `chatViewModel.czipAutoEnabled`.
- Test: `CzipAutoLogicTest` 8/8 yeşil (durum ON/OFF, oto_pasif karışıklığı,
  ayar yazma çıktısı, traceback→UNKNOWN, ensure adımları, bildirim metni).

## Sunucu tarafı eşlik düzeltmesi (repo DIŞI — cd-coder/haber)

`czip oto durum` / `czip ayar` (boş argüman) çöküyordu:
`AttributeError: module 'ayar' has no attribute 'AYAR_YOLU'` — `hkp.py:1342`
eski sabiti kullanıyordu, `ayar.py` artık `ayar_yolu()` fonksiyonu sunuyor
(refactor yarıda kalmış). Tek satır düzeltildi (`_a.AYAR_YOLU` →
`_a.ayar_yolu()`), yedekler: `hkp.py.bak-ayar-yolu-20261020` (aynı dizin;
`~/.hermes/czip-source` o dizine symlink). Bu kanal görevin kabul kanıtı
("durum çıktısı") için zorunluydu.

## Kanıt

- **Birim testleri:** `./gradlew testDebugUnitTest` → **1134 test / 0 fail /
  0 error** (önceki 1126 + 8 yeni; test-results XML toplamı).
- **Derleme:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
  - `app-arm64-v8a-debug.apk` 63.091.511 bayt, MD5 `a8a121745124992a2624852a7a5e9db3`.
- **Emülatör e2e (emulator-5554, com.hermes.mobile.v3, mock gateway 9171 —
  stdlib WS JSON-RPC, ders b77de80d):** YENİ oturum → mock RPC logu:
  `session.create` → `slash.exec /czip-auto durum` (oto: off) →
  `prompt.submit` → `slash.exec /czip-auto ac` (oto -> ON).
  Ekran (uiautomator dump, TextView bounds [37,445][1043,520]):
  "Czip arka plan paketleme (oto): AÇIK — yeni oturumlar kendiliğinden
  paketlenir".
  - `kanit/mock-rpc.log`, `kanit/ui-dump-bildirim.xml`,
    `kanit/v5-4-czip-auto-acik.png` (1080×2400 PNG).
- **Gerçek gateway durum çıktısı (bu oturum = yeni oturum):**
  `kanit/gercek-gateway-durum.txt` — `czip oto durum` → `oto : ON`;
  `czip oto ac` → `oto -> ON`; `auto-start.json` on_session_start damgası
  güncel (pid 19527); `ayar.json oto = True`.

## Atlanan / açık

- Ayarlar ekranında czip-auto anahtarı YOK (kapsam: kabul ölçütü davranış;
  tercih alanı eklendi, UI satırı ayrı iş — LOW).
- Emülatördeki mock, sunucu tarafının birebir kopyası değil (gerçek
  gateway `on_session_start` hook'u mock'ta yok); gerçek kanıt
  `gercek-gateway-durum.txt` ile ayrıca verildi.
- cd-denetmen (Claude) incelemesi: `kod-denetmen-devriye` cron'u (6 saatte
  bir) bu commit'teki diff'i otomatik inceler (V4'teki `invalid_params`
  ortam notu bakidir).

## Dosyalar (bu depo)

- app/src/main/java/com/hermes/mobile/data/CzipAutoLogic.kt (yeni)
- app/src/test/java/com/hermes/mobile/CzipAutoLogicTest.kt (yeni)
- app/src/main/java/com/hermes/mobile/data/AppSettings.kt (+7)
- app/src/main/java/com/hermes/mobile/ChatViewModel.kt (+47)
- app/src/main/java/com/hermes/mobile/MainActivity.kt (+2)
- denetim/v5-4/ (mock + kanıtlar + bu rapor)