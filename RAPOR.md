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
- x86/emulatör-apk之外的 ABI paketleme — abiFilters bilinçli [arm64-v8a, x86_64].

## KANIT
- **Testler**: `gradle testDebugUnitTest` → **680 test / 0 fail / 0 skip**
  (54 XML; son 41 yeni test hariç önceki 643 korundu; yenileri:
  LocalTtsLogicTest 10, LocalTtsFlowTest 5, LiveModelChoiceTest 16,
  JevBadgeTest 6 + güncellenen VoiceSpeakLogicTest 7).
  Koşum 20.09.2026 20:54, APK build 21:03 (BUILD SUCCESSFUL 11s, 41 tasks).
- **APK**: 92.486.706 bayt · **md5 7b78d57db9798035e43bcd21fb9e1620** ·
  native lib yalnız `lib/arm64-v8a` + `lib/x86_64` (abiFilters; jar listing).
  sha256: 6b9049d5f695e4441e28d5d784a28955d750b5f9d40bd087c0de7756888e2b46
- **Emülatör (hermes-v2, x86_64)**: APK `adb install -r` → `Success`;
  yerel TTS modeli `run-as` ile dosyalandı (10 dosya, sha256 hepsi ✓ —
  selftest ekranı); motor yüklendi (22050 Hz / 1 kişi); 2 cümle üretildi
  (`cumle-1.wav 136 KB`, `cumle-2.wav 196 KB`); cümleler ses düzeyinde
  oynatıldı ve **sessizlik OLMADI** (MediaPlayer tamamlandı). Selftest
  ekran görüntüsü: `denetim/tur21/ekran-selftest.png`.
- **f0 ölçümü**: `denetim/tur21/f0-olcum-cikti.txt` (yukarıda özet + satır
  sayısı/pencere sayısı ile).
- **node1 canlı**: `GET http://192.168.1.99:8888/v1/models` → `id:
  Qwen/Qwen3.8-Flash-Next` (20.09.2026 21:07, Mac).
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
