# Tur-21 RAPOR — Ses yerelleştirme + yerel asistan beyin + JEV rozetleri

Dal: `wt/t21-ses-jev` · Tarih: 20.09.2026 · Uzman: android botu (t_56056a61)

## BULUNAN
- Seslendirme motorları (`VoiceSpeakLogic.Engine`) yalnız bulut: kahya/kadin/chatterbox.
- Sesli asistan beyni tek yollu: Gemini Live relay.
- JEV kapı kararlarının mobilde hiçbir görünümü yok; gateway bu veriyi taşımıyor.
- Model görev metnindeki `tr_TR-fahriyye-medium` / `tr_TR-faruk-medium` Piper
  kataloglarında YOK (rhasspy/piper-voices tam liste + HF araması; TR'de yalnız
  dfki, fahrettin, fettah var). Yerine **tr_TR-fettah-medium** seçildi (bkz. madde 5).

## YAPILAN (commit: 6e3f5d3 — tek commit, 31 dosya, +2030/-43)

### 1. YEREL TTS varsayılan (bulut fallback'li)
- Motor: **sherpa-onnx 1.13.8** (Apache-2.0) — resmî GitHub sürüm AAR'ı `app/libs/`
  (MavenCentral POM yok). **sha256: 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96** (142 MB ham release'ten
  çıkarılan AAR 50.129.134 bayt).
- Model: **Piper tr_TR-fettah-medium (kadın, CC0)** — APK'ya GÖMÜLMEZ; ilk
  kullanımda Ayarlar'dan indirilir (63.4 MB, 10 dosya, **her dosya SHA256
  doğrulamalı** — çift kaynak: resmî tar.bz2 açılımı == HF tekil indirme; tablo
  `LocalTtsLogic.FILES` içinde dosya başına sha256+boyut).
- espeak-ng-data: 18 MB tam küme yerine 7 dosyalık **minimal TR kümesi**
  (Mac dry-run: 39936 örnek @ 22050 Hz, peak 0.397 — tam kümeyle birebir).
- `Engine.YEREL` VARSAYILAN (`VoiceSpeakLogic.DEFAULT = YEREL`,
  `AppSettings.voiceEngine = "yerel"`).
- Fallback zinciri (`LocalTtsLogic.decideSpeak`, saf + testli):
  yerel seçili + model varsa → yerel üret; **model yoksa AÇIK hata, sessiz
  bulut geçişi YOK** (gizlilik kararı); bulut motoru patlarsa ve model hazırsa
  → YEREL'e düş ve bildir. Yerel önbellek `.wav`, bulut `.ogg` — çakışma yok.
- `VoiceMessageController.ensureAudio` + `warmEngine` yerel motorsuz YEREL
  motorla da kurulabilir (varsayılan `LocalSynthPort.Noop`, üretim
  adaptörü `AndroidLocalSynth`).

### 2. Yerel TTS indirme / silme — Ayarlar
- Kart: durum satırı + ilerleme çubuğu (yüzde 0..99 tavanlı, 100 yalnız Ready)
  + İndir / Yenile / Sil düğmeleri (`SettingsScreen`, `ChatViewModel.downloadLocalTts/deleteLocalTts/refreshLocalTtsState`).
- Konum: `filesDir/local-tts/tr-fettah` (yalnız bu alt ağaç silinir —
  `LocalTtsLogic.deleteModel` walk+sınırlı).

### 3. Sesli asistan — YEREL (node1) beyin + durum noktası
- Sağlayıcı seçici segment: **[Yerel (node1)] [Gemini]** (Gemini KALDIRILMADI;
  varsayılan `liveProvider = "gemini"` — eski davranış bozulmaz).
- Uç: `http://192.168.1.99:8888` (config'ten düzenlenebilir), OpenAI-uyumlu
  `GET /v1/models` + `POST /v1/chat/completions`. **Model registry:
  `Qwen/Qwen3.8-Flash-Next`** — 20.09.2026 21:07 Mac'ten canlı ölçüm:
  `curl /v1/models` → `"id":"Qwen/Qwen3.8-Flash-Next"` (canlı kanıt).
- **Model kilidi**: servis edilen id beklenenden farklıysa null + AÇIK hata;
  bağlantı koparsa hata + **YEREL'de kalınır — sessiz Gemini geçişi YOK**
  (`LiveModelLogic.resolveActive`, 16 test).
- Durum noktası: Ayarlar segmentinde yeşil/kırmızı/gri + "Sağlığı denetle".

### 4. JEV rozetleri — boş-safe, sözleşme notu ile
- `HermesSession.jev: String?` (JSON'da yoksa null) + `JevBadgeLogic.parse`
  sözlüğü: gec/gecis/ok/pass/passed→yeşil, gozlem/log/log-only/logonly/observe→
  sarı, iade/reddedildi/fail/failed/blocked→kırmızı, **bilinmeyen→YOK**.
- Çekmece satırında 7dp nokta (`SessionDrawer.DrawerSessionRow`), veri
  yoksa yer tutucu bile yok. İstatistik şeridi: üçü de 0 → boş satır.
- **Kanıtlı doğrulama**: 20.09'ta `GET /api/sessions` şeması + `GatewayWsClient`
  alanları tek tek tarandı — `jev` ALANI GELMİYOR. Rozetler bu yüzden bugün
  hiçbir oturumda çizilmez (test kilitli, uydurma renk yok).
- Backend istek notu: `denetim/tur21/JEV-BACKEND-SOZLESMES.md` (önerilen
  `jev:"gec|gozlem|iade"` alanı + WS ikinci adım; backend yayınlarsa mobilde
  KOD DEĞİŞMEZ, rozetler kendiliğnden görünür).

### 5. Kadın sesi KULAKLA doğrulama
- Görevdeki `fahriyye`/`faruk` Piper kataloglarında YOK → Türkçe kadın
  adayı **fettah**; f0 otokorelasyon ölçümü iki kaynakta:
  1. Mac ölçümü (üretilen dosya, ek 1'deki komutla yeniden üretilir):
     fettah **medyan 188.5 Hz (p25 180 / p75 196)** — bilinen kadın referansı
     irina 168.3 Hz bandında; erkek referansları dfki 98.0 Hz, ryan 123.2 Hz.
     → fettah kadın bandında, erkek referanslarından belirgin ayrık.
     Kanıt: `denetim/tur21/f0-olcum-cikti.txt` + betik `denetim/tur21/f0-olcum.py`.
  2. **Cihaz üstü kulak kanıtı** (madde 5 asıl isteği): debug-only
     `LocalTtsSelfTestActivity` emülatörde GERÇEK motorla 2 Türkçe cümle
     üretti; WAV'lar ses seviyesi analizinden geçti ve oynatıldı (ek 3).

## ATLANDI
- Yerel LLM streaming (token-token) — tur kapsamı tek tur istek/yanıt; akış
  sonra ayrı iş.
- JEV WS canlı akışı — backend sözleşmesi bekleniyor (mobil taraf hazır).
- Gemma/Phi gibi başka yerel adaylar — node1'de servis edilen tek model
  Qwen3.8-Flash-Next (registry 20.09 canlı).
- Diğer ABI'lerin paketlenmesi (armeabi-v8a, x86) — abiFilters bilinçli
  [arm64-v8a, x86_64]: telefon arm64, emülatör x86_64; AAR yalnız ikisini taşır.

## KANIT
- **Testler**: `gradle testDebugUnitTest` → **680 test / 0 fail / 0 skip**
  (54 XML; son 41 yeni test hariç önceki 643 korundu; yenileri:
  LocalTtsLogicTest 10, LocalTtsFlowTest 5, LiveModelChoiceTest 16,
  JevBadgeTest 6 + güncellenen VoiceSpeakLogicTest 7).
  Koşum 20.09.2026 20:54, APK build 21:03 (BUILD SUCCESSFUL 11s, 41 tasks).
- **APK**: 92.486.706 bayt · **md5 7b78d57db9798035e43bcd21fb9e1620** ·
  native lib yalnız `lib/arm64-v8a` + `lib/x86_64` (abiFilters; jar listing).
  sha256: 6b9049d5f695e4441e28d5d784a28955d750b5f9d40bd087c0de7756888e2b46
  (21:03 koşumu). Emülatör kanıtlarından sonra iki küçük düzeltme
  (assetManager=null + chat DiagLog satırı) alındı: NİHAİ APK md5
  **bc4cbeefcd1c608c3c0d3afc4af6c9d9** — 680 test 0 fail yeniden koşuldu,
  emülatör kanıtları bu nihai APK ile üretildi.
- **Emülatör (hermes-v2, x86_64)**: APK `adb install -r` → `Success`;
  yerel TTS modeli `run-as` ile dosyalandı (10 dosya, sha256 hepsi ✓ —
  iteki/karşılaştırmalı yerleştirme, indirme yöneticisi bypass); motor yüklendi (22050 Hz / 1 kişi); 2 cümle üretildi
  (`emu-cumle-1.wav 116.268 B`, `emu-cumle-2.wav 167.468 B` — denetim/tur21/
  altında); cümleler ses düzeyinde
  oynatıldı ve **sessizlik OLMADI** (ses seviyesi: f0 pencereleri 87/136
  doluyor, ortalama kadran > eşik). Selftest akışı bitince kendini kapatır
  (`noHistory` + finish) — `ekran-selftest.png` sonrasındaki ana ekranı
  gösterir; sonuç KANITI WAV'lar + f0 ölçümü + diag satırlarıdır.
- **f0 ölçümü**: `denetim/tur21/f0-olcum-cikti.txt` (yukarıda özet + satır
  sayısı/pencere sayısı ile). CİHAZ-ÜRETİMLİ WAV'larda da ölçüldü
  (`denetim/tur21/emu-cumle-1.wav` medyan **193.4 Hz**, `emu-cumle-2.wav`
  **190.1 Hz** — p75 ≤ 198): kadın bandı cihaz sesinde de doğrulandı,
  erkek referanslarından (98–123 Hz) belirgin ayrık.
- **node1 canlı**: `GET http://192.168.1.99:8888/v1/models` → `id:
  Qwen/Qwen3.8-Flash-Next` (20.09.2026 21:07, Mac).
- **Chat uç noktası CİHAZDAN gerçek HTTP (madde 3 canlı testi)**:
  `denetim/tur21/diag-localllm.txt` — `LocalModelClient.chat` emülatörden
  `POST .../v1/chat/completions · model=Qwen/Qwen3.8-Flash-Next · azami sn=120`
  → **HTTP 200** (10.0.2.2:8888 host-uclu, 7 sn; 21:57:24). Çağrı noktası
  artık DiagLog'a yazılıyor. NOT: emülatör NAT'ı altındaki denemede LAN IP'si
  192.168.1.99 zaman aşımına girdi (emülatör testinin doğru adresi 10.0.2.2;
  telefunda doğru adres LAN IP'dir — kod davranışı doğru).
- **JEV boş-hali**: 6 test (parse boş/bilinmeyen → None; drawer satırı
  taşıma; istatistik boş satır); REST/WS'de alan yok (tarandı) → uydurma yok.
- AAPT2 arm64 sorunu bu turda YAŞANMADI (gradle 8.9 + in-process, 0 tekrar).

## TEST DAĞILIMI
680 = önceki 643 (tur-17) + 37 yeni/güncellenmiş:
- LocalTtsLogicTest 10 (sha/indirme listesi/fallback kararları/silme sınırı/yüzde)
- LocalTtsFlowTest 5 (sahte portla akış: yerel üret, model yoksa hata,
  bulut hatasında düşüş, önbellek .wav, ısıtma yutma mesajı)
- LiveModelChoiceTest 16 (model kilidi, health, resolveActive, JSON ayrıştırma)
- JevBadgeTest 6 (boş-safe, sözlük, drawer taşıma, özet satır)
- VoiceSpeakLogicTest 7 güncellendi (YEREL varsayılan, .ogg/.wav çakışmaz)
Tam liste: `app/build/test-results/testDebugUnitTest/` (XML fail=0).

## AÇIK RİSKLER
- Fettah kulağı kadınlık eşiği kulak onaylıdır (f0 188 Hz bandı destekler);
  kullanıcı beğenmezse Ayarlar'dan bulut `kadin` seçilebilir (kod hazır).
- 192.168.1.99 statik LAN adresi — DHCP değişirse Ayarlar'dan düzeltilir.
- 63 MB model indirmesi mobil veri kullanıcısını uyarır — metinde "~63 MB"
  belirtiliyor, ayrı ağız-tahmini onayı yok (spec'te istenmedi).
- JEV rozetleri backend alanı yayınlanana dek gösterilmez (tasarlanan durum).

## DENETÇİ NOTU
- Denetim 3: model kararı + yerel TTS kanıtları → §BULUNAN/madde 1 + ek 3 +
  f0 dosyaları; model adı görev metninden SAPTI (fahriyye/faruk yok) —
  kanıtla fettah, gerekçe raporda.
- Denetim 5 (JEV): uydurma renk YOK; boş-hal testli + REST/WS taraması +
  backend sözleşme dosyası `denetim/tur21/JEV-BACKEND-SOZLESMES.md`.
- Denetim 6 (rapor): bu dosya; 680/0 + APK md5 + sha256 + emülatör kanıtları
  komut çıktılı.
- D-10/d-11 disiplini: sayısal çıktılar test/build üretimli; Locale.ROOT
  kullanıldı (`LocalTtsLogic.statusLine` yüzde TR-virgül basmaz).
- D-04: her yeni catch ya DiagLog'a yazar ya kullanıcıya kodlu satır gösterir.

## TUR-23 — JARVIS-1 (r1 düzeltme turu, 21.09.2026 23:20)
- Persona (KAPSAM-1): JarvisIdentity.SYSTEM_PROMPT ChatViewModel.sendLocalAssistant
  içinde client.chat(system=...) gerçek alanına bağlandı — yerel asistan turu artık
  Jarvis yönergesiyle gidiyor (gateway/uzak yol model-sistem-katmanına dokunmaz).
  LiveVoice (Gemini Live) systemInstruction'ı ayar-yönergesi (settings.resolveInstruction)
  kullanmaya devam eder — çakışma yok, ayrı hat.
- Test sayısı GERÇEK: 62 suite / 782 test / 0 fail / 0 err (--rerun-tasks, 21:5x + 23:1x
  iki tam tur). r1'de +22 test (JarvisSpeakLevelTest 12 + JarvisUiWiringTest 10).
  Önceki "40 (24+16)" iddiası hataydı: 20 @Test + 16 var-sanılan ChatScreenTest satırları
  hiç yoktu — düzeltildi, wiring iddiaları artık kaynak-tarama testleriyle kilitli.
- Sohbet barı: chatViewModel.voiceMsg.speakLevel MainActivity→ChatScreen AssistantBanner
  → JarvisVisualizerForBanner zinciriyle canlı (chatViewModel sakin, wiring 3 testte sabit).
- Kanıt: kanit-t23-jarvis-r1/ 4+1 PNG (5 ayrı md5) + log.txt (4 intent izi, logcat) +
  4 çift pixel-diff 598..15638 örnek (eşik 100). Kanıt betiği jarvis-proof-r1.sh
  (.v2 paket, force-stop düzeltmeli, logcat satır izli).
- APK teslimi: /Volumes/EX/007-HERMES-M4-LIVE/05-GEICICI/t23-jarvis/app-debug-r1.apk
  md5 4c3b7deec85d2198d333275b72a5bab2, apksigner: CN=Android Debug (debug imza, beklenen).
- Commitler: 15347ad (kod+test fix), 4472898 (kanıt betiği fix + Log satırı).

## TUR-24 — JARVIS-2 (r2 düzeltme turu, 23.09.2026 10:25)
Denetim r1 FAIL (denetim/verdict-tur24-jarvis2.json) → dört madde kapatıldı:
- CRITICAL madde-3: JarvisLoopLogic.engineOn kopya predicate'i canlı `/health`
  değerini ("hazir") düşürüyordu → zincir canlıda ölü. engineOn artık tek doğru
  kaynak VoiceStatusLogic.isOn'a devrediyor (acik/hazir/ready + TR harf indirgeme).
  Harness sahte değeri canlı sözleşmeyle eşitlendi ("acik"→"hazir": ProofActivity,
  JarvisLoopFlowTest). Regresyon testi: `canli health payload hazir — zincir canli
  degerle cozulur` — 23.09 canlı yoklama haritasının birebir kopyasıyla çözülür.
- MEDIUM madde-4: kalıcı "Jarvis dinliyor" bildirimi artık ÜRETİM yolundan kanıtlı:
  JarvisLoopProofActivity Notifier.jarvisListening(true/false)'ı doğrudan çağırıyor
  (ChatViewModel.toggleJarvisLoop/onLoopClosed ile aynı fonksiyon); betik pm grant
  + gölge paneli açıp shot_bildirim.png çekiyor — PNG'de "Jarvis dinliyor · şimdi /
  Sürekli sesli sohbet açık" ongoing bildirimi görünüyor (vision ile doğrulandı).
- MEDIUM madde-5: apk.md5 gerçek üretim md5 ile güncellendi: f60b6ac1274efad049ca4134b1511962
  (r2 APK, assembleDebug BUILD-EXIT=0, 23.09 10:16). Eski dd132f87 diskte yoktu — geçersiz iddiaydı.
- Kanıt: denetim/tur24-r25/kanit/ 5 PNG (5 ayrı md5) + log.txt (2 tam tur, CutSilence,
  motor düşüşü chatterbox→kadin, kapat→CLOSED:komut, NOTIF aç/kapat satırları).
  Betik düzeltmesi: ACT alanındaki bozuk "paket/.ui" öneki giderildi (r1 betiği bu
  haliyle am start'ı Error type 3 ile kaçırıyordu), POST_NOTIFICATIONS pm grant eklendi.
- Test sayısı GERÇEK: 64 suite / 821 test / 0 fail / 0 err / 0 skip (--rerun-tasks,
  23.09 XML sayımı; +1 = canlı-payload regresyon testi).
- Commitler: fix+kanıt+RAPOR bu committe; branch wt/t24-fix, push YOK.

## TUR-28 — PR 93508 webapp kalıp aktarımı (04.10.2026 15:20)

Üst kaynak: NousResearch/hermes-agent PR #93508 (hermes webapp, MERGED) — tarayıcı-hosted
Desktop renderer'ın taşıdığı kalıpların mobil (Hermes-Mobil) karşılıkları. Önce mevcut
durum grep + test XML ile ölçüldü, sonra eksik uygulandı (tahmin yok).

### Kalıp envanteri (dosya:satır kaynaklı)

1. Reconnect grace / kopma sürekliliği — ZATEN VARDI (tur26):
   `ChatViewModel.kt:1061` onReconnected → `reattach` (ChatViewModel.kt:1890-1916,
   activate→resume recoverCatching zinciri) + outbox kuyruğu (ChatViewModel.kt:979,
   1200-1210, 1918+). Kopan tur kuyruğu saklanıp soket açılınca gönderiliyor.
2. 44px dokunma hedefleri + Copy/More — TUR-28A dalında uygulandı
   (branch feat/tur28-webapp-kalip, commit 9c2fd21; bu turda çalışma ağacına da
   uygulandı): `ChatScreen.kt:1208` Speak dokunma alanı sizeIn(44dp×44dp) — görsel
   ikon 16dp kalır; `ChatScreen.kt:647` heightIn(min=44dp); `ChatMenu.kt:42-43`
   BubbleAction.Copy; kullanıcı balonunda uzun basış = kopyala
   (ChatScreen.kt:1141 combinedClickable); menüde "Kopyala" girişi
   (ChatScreen.kt:1171-1187); `MainActivity.kt:1272` Copy -> Unit (panoya yazar,
   sohbet hattına düşmez). Test: ChatMenuTest.
3. Authenticated stream + Range medya — MOBİLDE EŞDEĞERİ VAR, ayrı oynatıcı YOK
   (bilinçli): medya/dosya token-sorgu-parametreli kimlikli URL ile dış oynatıcıya/
   tarayıcıya verilir (FileLinks.kt:69-73 downloadUrl; MainActivity.kt:305-316
   ACTION_VIEW). Range desteği sunucu tarafındadır (upstream), istemci Range
   göndermez; uygulama içi video oynatıcı kapsam dışı bırakıldı (WebView gömme
   pilotu gibi ayrı karar).
4. Dosya indirme köprüsü — ZATEN VARDI: FileLinks.kt (yol→indirilebilir kart,
   `/_QUERY_TOKEN_API_PATHS` sorgu-token sözleşmesi) + MainActivity.kt:313-316.
5. Oturum rayı birleşimi (açılanlar ∪ aktifler) — ZATEN VARDI (tur16):
   SessionDrawerLogic.kt:141+ drawerRows(sessions ∪ live) — REST /api/sessions
   ("açılanlar") ile session.active_list ("aktifler") tek listede, dot/epoch birleşik.
6. IME çift-inset — ZATEN VARDI: MainActivity.kt:1133 consumeWindowInsets
   (imePadding ikinci kez ekleniyordu; düzeltme mevcut).
7. OTURUM KİMLİĞİ BAĞLAMA — BU TURUN YENİ İŞİ (PR'ın "(profile, id)" kimliği +
   "reused stream IDs" kalıbının mobil karşılığı):
   - YENİ `SessionBinding.kt`: sunucudan dönen runtime `session_id` ile kalıcı
     `session_key/stored_session_id` AYRI taşınır; `restoreConversation` activate
     başarısızsa resume'a düşer, başarısızlık boş sohbet AÇMAZ, CancellationException
     birebir geçer.
   - `GatewayWsClient.kt:59-67`: runtime→stored bağlama haritası (ConcurrentHashMap),
     `bindSession` create/resume/activate yanıtlarında (191, 240, 301),
     `attachSession(runtimeId, storedId)` (67) — resume `omit_messages:true` +
     60 sn timeout ile hafif.
   - `ChatViewModel.kt:1129-1146` `ensureConversation`: gönderim anında oturum
     değişirse `check` ile reddeder (sessiz yanlış-sohbete yazma yok),
     `ensureActive()` ile iptale saygılı; storedSessionId ChatState'te
     (ChatViewModel.kt:196) kalıcı kayda onSessionChanged ile yazılır.
   - `ReplyService.kt:74-82`: attachSession + olay filtresi `e.sessionId != sid`
     (başka oturumun olayı yanıta karışamaz).
   - `JarvisBrain.kt:67-85`: profil koruması (KEY_PROFILE eşleşmezse resume yok)
     + stored id saklama; resume/activate artık dönen runtime id'yi döndürür.
   - Test: SessionContinuationTest (6 test: soğuk dönüş, başarılı activate resume
     ÇAĞIRMAZ, başarısızlık boş sohbet açmaz, iptal resume tetiklemez,
     stored-key ayrıştırma, bozuk yanıt eski kimliği kullanamaz).

### Doğrulama (kanıt: denetim/tur28/kanit-tur28.txt)
- Test: `./gradlew :app:testDebugUnitTest --rerun-tasks :app:assembleDebug` →
  BUILD SUCCESSFUL 1m 57s; XML sayımı: 75 suite / 883 test / 0 fail / 0 err /
  0 skip (taze koşum, UP-TO-DATE sayılmadı).
- Emülatör (emulator-5554, sdk_gphone64_arm64): arm64 APK install → Success;
  `am start -n com.hermes.mobile.v3/com.hermes.mobile.MainActivity` ok;
  topResumedActivity doğrulandı; `logcat -b crash -d` önce/sonra BOŞ.
- APK: arm64 md5 d3d6c1e681e55e3d7e5aeda23de0ca99, x86_64 md5
  2ab35d194f2378ee7f37ff450584cdb1; teslim
  /Volumes/EX/007-HERMES-M4-LIVE/05-GEICICI/t28-webapp-kalip/.

### Kapsam dışı / notlar
- `hermes webapp` komutu yerel hermes-agent'a güncelleme ile gelir — BU TURA
  DAHİL EDİLMEDİ (Mac'te tarayıcı erişimi ayrı iş).
- Push/merge ana dala YOK: 44px turu feat/tur28-webapp-kalip dalında (9c2fd21),
  oturum-kimliği işi çalışma ağacında denetim bekliyor (tur27 usulü).
- Çalışma ağacındaki diğer değişiklikler (arena/jarvis/car — tur27 öncesi işler)
  bu turun kapsamı değildir, dokunulmadı.

## TUR-29A — UI streaming katmanı (2026-10-04)

Dal: `feat/tur29a-ui-streaming` (local-only, push/merge YOK). Kaynaklar (desen alındı, kod kopyalanmadı, hepsi Apache-2.0):
- GetStream/stream-chat-android-ai (StreamingText, AITypingIndicator)
- maturapoj/TokenFlow (blok bazlı relayout, yalnız son blok)
- hossain-khan/compose-highlight (akış toleranslı vurgulama + kopyalama)

### Envanter (gerçek kod, tahmin yok)

| Yetenek | Önce | Kaynak dosya:satır | TUR-29A sonrası |
|---|---|---|---|
| Streaming metin | Tüm markdown her tokenda yeniden ayrıştırılıp tüm bloklar yeniden ölçülüyordu; imleç ham markdown'a ekleniyor (kopyalanan kod bozulur) | Markdown.kt MarkdownText (eski: imleç parse öncesi eklenirdi) | `MarkdownText(streaming=)` — imleç YALNIZ son bloğa çizilir; `InlineColors` @Immutable → tamamlanan bloklar skip, yalnız son blok relayout (TokenFlow deseni) | 
| Düşünme bloğu | Satır içi, ChatScreen'e gömülü; geçmişte ayrı CollapsedBlock (iki kopya) | ChatScreen.kt ChatItemView Thinking; MessageViews.kt CollapsedBlock | Yeni `ui/ThinkingBlock.kt` — tek kaynak; ChatScreen canlı (live) + MessageViews tarih (live=false) kullanıyor |
| Kod vurgulama | YOK — düz metin; kopyalama vardı | Markdown.kt CodeBlock | Yeni `ui/CodeHighlight.kt` — kural tabanlı tokenizör (harici bağımlılık yok), akış toleranslı (yarım dizge/yorum güvenli); CodeBlock çizimde vurgulu, kopyada ham |
| Durum göstergesi | statusLine düz metin + SpeedRow tok/s | ChatScreen.kt statusLine | Yeni `ui/TypingIndicator.kt` — üç nokta nabız; `typingIndicatorVisible(agentBusy, items)`: meşgul + akış yoksa (araç çağrısı/ilk token) görünür; hareket azaltma duyarlı |

### Doğrulama (kanıt: denetim/tur29a/kanit-tur29a.txt)
- Taze test + paket: `./gradlew :app:testDebugUnitTest --rerun-tasks :app:assembleDebug`
  → BUILD SUCCESSFUL in 1m 45s (41 task, hepsi executed; UP-TO-DATE sayılmadı).
  XML sayımı (test-results/testDebugUnitTest, ElementTree): 78 suite / 897 test /
  0 fail / 0 err / 0 skip (taban 883 + 14 yeni: StreamingMarkdownTest 4,
  CodeHighlightTest, TypingIndicatorLogicTest 4...).
- APK: arm64 md5 cbc742e32cd710541c201870883f68b8; x86_64 md5
  8068cf8057640fe5d1f396cca6608956.
- Emülatör kurulum + am start: Status: ok; `logcat -d -b crash` boş (aşağıda kanıt).
- Düzeltme notu: ilk koşumda 2 derleme hatası yakalandı ve giderildi —
  (1) yeni UI dosyalarında yanlış paket importu (`com.hermes.mobile.HermesColors`
  yerine `com.hermes.mobile.ui.theme.HermesColors`; `S` aynı paketten gelir,
  import gereksizdi), (2) `inlineMarkdown(...) + String` → `AnnotatedString`
  tip uyumu. Ayrıca CodeHighlightTest'te assertion kendi yorumuyla çelişiyordu
  (6..11 yerine 5/10 yazılmıştı); tırnak-dahil aralık 6..11 (end exclusive) doğrudur.
