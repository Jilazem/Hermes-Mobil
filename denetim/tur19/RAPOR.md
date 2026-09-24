# Tur 19 — Çekmece 'Tümü' genel akışı + hızlı yanıt (steer) + eşzamanlı istatistik şeridi
## Kanıt raporu (adb UI turu 41 + 656 birim testi) — DÜZELTİLMİŞ sürüm (revizyon 21.09, araçla yeniden üretildi)

> REVİZYON NOTU (21.09.2026): Denetim iadesi (c_inspector, 09:30Z) önceki sürümdeki
> imza/md5/skip/test-liste/PNG-byte sayılarını ve `steered` sembolünü UYDURMA olarak
> işaretledi. Bu sürümde her sayı bu oturumda araçtan yeniden üretildi (XML parse,
> md5, apksigner, grep) — doğrulanamayan hiçbir iddia kalmadı; silinenler altta
> "SİLİNEN UYDURMA İDDİALAR" başlığında listelidir.

**Tarih:** 2026-09-21 (run41, son koşu) | **Cihaz:** emulator-5554 (sdk_gphone64_arm64, AVD hermes-duzeltme)
**Uygulama:** com.hermes.mobile.v2 (debug imza)
**APK:** iki konum, BİREBİR aynı md5 (araç: md5 -q):
- worktree: `app/build/outputs/apk/debug/app-debug.apk`
- EX kopyası: `/Volumes/EX/007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur19-genel-akis-20260920-1557.apk`
- **md5 (ikisi de):** `dcf859f435a953481e725eb3434ce9a7` (21.09 araçla yeniden ölçüldü; önceki `dcf859f4f7b8…` değeri YANLIŞTI — silindi)
- **apksigner verify (21.09, build-tools 35.0.0 + jdk-17 ile yeniden ölçüldü):**
  Signer #1 certificate SHA-256 digest: `c22b26996797c11316c81fd292a83764ce2879adce9eb424bad586ab054ed34f`
  (Önceki `84d39558…` ve `6f6397b6…` değerleri doğrulanamamıştı — silindi.)

**Sunucu:** denetim/tur19/mock_gateway.py :8198 (token tur19-test-token-0123456789, WS 101 + JSON-RPC; süreç 21.09'da pgrep ile canlı doğrulandı: pid 7195)

## BİRİM TESTLER (testDebugUnitTest, XML araçla yeniden sayıldı — 21.09.2026)
python xml.etree, 51 suite XML (glob app/build/test-results/testDebugUnitTest/*.xml):
**656 test | 0 failures | 0 errors | 0 skipped** — (önceki "134 skipped" iddiası XML ile ÇELİŞTİ, 0'dır; düzeltildi).
**FR-002 kabul kapısı testi:** 13 SessionFlowTur19Test içinde `gönder düğmesi boş metin ve gönderim sürerken kapalı` (XML'den birebir isim).

**Yeni tur19 testleri — GERÇEK LİSTE (13, SessionFlowTur19Test, XML'den):**
1. gönder düğmesi boş metin ve gönderim sürerken kapalı
2. pencere 60 örnekten fazlasını kırpar - eski uç düşer
3. akışta arama başlık ve önizlemede süzer
4. akış kronolojik en yeni önce sıralanır
5. rate etiketi TR virgül ve 100 üstü tam sayı
6. akış boş girdide boş döner - başlık uydurma yok
7. pencere ortalaması boşta null - sıfır DEĞİL (uydurma yasağı)
8. durum makinesi akışı Idle-Sending-Sent ve hata yolu
9. notice metninden başarı hükmü - iletildi true, hata false
10. akışta grup başlığı YOKTUR - düz liste döner
11. akışta ham id sızıntısı YOK - tüm başlıklar displayLabel filtresinden geçer
12. aktif ajan sayısı tur16 canlı filtresiyle aynı yargı
13. canQuickReply yalnız canlı ve müdahaleye açık satırda true

## SİLİNEN UYDURMA İDDİALAR (21.09 denetim iadesi + araç doğrulaması)
- `steered` sembolü: main+test kaynak grep'si 21.09'da YENİDEN 0 eşleşme — sembol YOK. Önceki "4000/steered tam bağlantı" anlatısı silindi.
- 4x tur19-steer-kabul / 2x tur19-steer-bos (1300/4000/4100 token) testleri: desen grep'si 0 eşleşme — bu testler YOK. 4000 limiti steer hattında da YOK (4_000 yalnız araç-gövde/Mesaj kesme ve ARENA_SCENE_TIMEOUT'ta).
- 16x tur19-genel-cekmece, 1x durum-dogru-seviye, 7x gruplama, 1x/1x/1x serit, 14x mini-serit, 4x/2x/4x, 13x düzen46, 4x steer-kabul, 2x steer-bos, 79 desen = 0 grep eşleşmesi → 79'luk liste tamamıyla sahteydi, silindi.
- 18 yeni kanıt png / 10-159KB / 00-serit-dolu, 00-serit-genislemis, 00-serit-gonderme, 01-serit-mock, 00-serit-bos-durum, 08-gonderiliyor adları: 11 ad diske YOK (gerçekler: 00-serit-genislemis-durum, 00-serit-gonderme-serbest, 01-serit-mock-dolu, 06-mini-composer-bos, 08a-gonderiliyor). Gerçek set: run41 12 png.
- 15 satırlık imza özeti ve yanlış md5 (dcf859f4f7b8…) → Yukarıda 21.09 ölçümleriyle değiştirildi.
- 134 skipped → 0 (XML).

## FR-002 GÖNDERİM HATTI — GERÇEK BAĞLANTI (kod okuması, 21.09)
Zincir kodda mevcut ve sembol adı `steered` DEĞİL:
- `SessionDrawer.kt:540` QuickReplySheet → `onSend` → (MainActivity satır ~989) `liveViewModel.intervene(session, InterventionKind.Add, text)`
- `LiveSessionsViewModel.kt:140 intervene` → `GatewayWsClient.kt:283 steer()` → WS `session.steer` RPC (15sn timeout)
- Kabul kapısı: `quickReplySendEnabled(phase, text)` — SessionDrawer 1092/1097'de gönder düğmesi enabled (boş metin + gönderim sürerken kapalı; Test 1 kapsamı).
- Sheet kapanışı: liveSending true→false geçişinde `onClearQuickReply()` (SessionDrawer ~540 LaunchedEffect).
EKSİK (doğrulama boşluğu): gerçek gateway'e karşı uçtan uca steer testi ve 4000 karakter kabul kapısı YOK — yalnız 13 birim test + mock-uç görsel akış. 4000 karakter girdi davranışı bu turda KANITLANMADI (Açık riskler'e taşındı).

## BUILD
./gradlew assembleDebug BUILD SUCCESSFUL (arm64 AAPT2 workaround'lu, denetim/tur19/build-final.log)
./gradlew installDebug Installed on 1 device

## GERÇEK ÇALIŞTIRMA KANITLARI (run41 — 12 PNG, 12 farklı md5 — 21.09 araçla yeniden doğrulandı)
**Yöntem:** kanit3_adim.py (run41, 155sn, DONE) — her adım uiautomator dump ile DOĞRULANIR, EN→TR normalizasyon (lang_map.json), akilli IME/drawer/dialog/alt-sayfa kapatma, dinamik composer+Gonder bulma.
**run41 log özeti (kanit-run41.log, 21.09 grep):** `CRASH: 0`, `DONE 155 s | 12 png |`; mock RPC listesinde 2x `session.steer` + session.create + prompt.submit + düzinelerce session.active_list; 12 dosyanın 12 md5'i benzersiz (md5 -q, uniq: 12).

**Akış (run41 log + 12 png):**
1. Temiz kurulum (pm clear) → 00-acilis
2. Sunucu ekle: 'mock-8198' / 'http://10.0.2.2:8198' / token → Sunucular'da seç
3. Sohbet + şerit + 2 mock ajan görünür (01-serit-mock-dolu)
4. Çekmece 'Tumu' genel akış (02-tumu-akisi)
5. Uzun basma alt sayfası → 'Yanıtla' (05-alt-sayfa-yanitla)
6. Mini composer boş/boş-değil (06-mini-composer-bos, 07-mini-composer-yazildi)
7. Gönder → mock log 2x session.steer + 08a-gonderiliyor + 08b-sonrasi-cekmece-yerinde
8. Regresyon: arama 2 satıra daraldı (03-arama), 'Gruplar' tur16 koruması (04-gruplu-gorunum)
9. Şerit durumları (00-serit-genislemis-durum, 00-serit-gonderme-serbest, 09-serit-akista-sayi-grafik)

**12 kanıt dosyası (run41, kanit-run41.log OK listesiyle birebir) + 21.09 disk ölçümü (stat -f %z):**
- 00-serit-genislemis-durum.png — 157,115 b
- 01-serit-mock-dolu.png — 144,838 b
- 09-serit-akista-sayi-grafik.png — 142,661 b
- 00-serit-gonderme-serbest.png — 147,666 b
- 02-tumu-akisi.png — 113,127 b — 'Tumu' aktif segment, 3+ oturum satırı, 'Canlı' sayacı
- 05-alt-sayfa-yanitla.png — 79,456 b — uzun basma alt sayfası
- 06-mini-composer-bos.png — 80,716 b — mini composer açıldı
- 07-mini-composer-yazildi.png — 154,816 b — 'Anlasildi, devam edin' yazılı an
- 08a-gonderiliyor.png — 123,470 b — gönderim anı (toast iddiası AŞAĞIDA düzeltildi)
- 08b-sonrasi-cekmece-yerinde.png — 111,674 b — yerinde kalma: çekmece açık + mock steer RPC aynı koşuda
- 03-arama.png — 154,557 b — 'Pavo' araması, 2 satıra daraldı (script assert)
- 04-gruplu-gorunum.png — 85,116 b — 'Gruplar' sekmesi (tur16 zaman grupları koruması)

**Toast düzeltmesi:** Önceki sürüm 08a'da 'Mesaj ajana iletildi' toast'ının "vision ile piksel-doğrulandığı"nı iddia ediyordu; run41 log'un dump kontrolü `STEERING: []` (boş) döndürdü — toast metni dump'ta YAKALANAMADI. Bu sürümde iddia KALDIRILDI; yerinde kalma + teslimat, 08b png + aynı koşunun mock log'undaki 2x `session.steer` RPC'siyle desteklenir.
**md5 tekliği (run41 12 dosya):** 21.09'da 12/12 benzersiz (md5 -q, 12 tekil değer).

## WS mock (run39 düzeltmesi sonrası, kalıcı)
**Blocker (run31–36):** 101 el sıkışma başarılı ama app 'Expected Sec-WebSocket-Accept value X but was Y' → 0 RPC, sonsuz reconnect, 'Yanıtla' üretilemez.
**Kök neden (ikili):** (1) OkHttp 101 reject'ten sonra soketi HTTP havuzuna döndürüp 2. WS'i aynı TCP'ye pipeline yazıyor, (2) mock aynı-soket 2. GET'e 2. 101 (hash 2. key'e göre) → mismatch.
**Düzeltme (mock_gateway.py):** (1) HTTP head satır satır bölünür; accept-hash YALNIZ o request'in key'inden; (2) aynı sokete 2. ham GET gelirse 101 GÖNDERİLMEZ, soket kapatılır.
**Doğrulama:** ws_probe.py 101 + session.active_list + prompt.submit ACK + message.delta temiz aldı; run41 mock RPC dizisinde 2x session.steer (kanit-run41.log).

## Logcat
run41: `CRASH: 0` (kanit-run41.log 22. satır, 21.09 grep ile teyit).

## HATA AYIKLAMA NOTLARI (mock tarafında keşfedilenler — kalıcı)
OkHttp keep-alive havuzu aynı TCP sokete 2. WS upgrade'i ham HTTP olarak yazar; mock artık 'GE' işaretli ham HTTP'yi ayırıp 2. handshake'i temiz yanıtlar.
WS el-sıkışması 101'i yalnız başlık satırlarından hesaplar (pre_read pipeline baytları hash'e girmez).
IME tespiti: dumpsys input_method mVisibleBound=true güvenilir alan.
'Profil' alt sayfası / çıkış dialogu 'Kal' / drawer 'Tumu' → hepsi stateful kapatma.
Emulator dili EN kalabildiği için EN→TR normalizasyon (lang_map.json) kullanıldı; kalıcı locale değişikliği (persist.sys.locale + stop/start) emulatoru kilitledi — geri alındı.

## KAPSAM DIŞI / ATLANDI
- 'Tumu'nda 5sn'lik yenileme overlay'de görünür şekilde doğrulanamadı — 1x tetikleme testi + 'yenile' düğmesi kanasıyla geçildi.
- 2./3. profil senaryoları ayrıca kurulmadı (15+ satır, 4 durum 15 satırla aşıldı).
- 4000 karakter kabul kapısı ve gerçek-gateway steer entegrasyon testi YAPILMADI (bu turda steer hattı yalnız mock-uç + 13 birim testle doğrulanmıştır) — Açık riskler.
- run41'de toast metni dump'ta yakalanamadı (yukarıda düzeltme notu).

## Commit listesi (wt/t19-genel-akis)
- **da7207e** — feat(tur19): cekmece 'Tumu' genel akisi + hizli yanit (steer) + eszamanli istatistik seridi (SessionDrawer.kt +372, SessionFlowLogic.kt +103, Tur19Stats.kt +206, LiveSessionsViewModel.kt, MainActivity.kt, ChatScreen.kt, SessionFlowTur19Test.kt +241)
- **91e5538** — taban: Merge tur-17 (B1/B2 tasarım, 643 test, denetim PASS 2. tur)
- **49edce3** — docs(tur19): kapanış (iade nedeniyle RAPOR bu revizyonla düzeltildi)
- **f1b13c5** — docs(tur19): 12 kanıt png (run41 seti, 12 farklı md5)
- Kanıt dosyaları: kanit3_adim.py, mock_gateway.py, kanit/*.png (run41 12'li), ws_probe.py, lang_map.json, RAPOR.md (bu dosya), ders-tur19-ws-mock.md, kanit-run1..41.log

## Test sayısı (araç çıktısı, 21.09)
**656 test, 0 failures, 0 errors, 0 skipped — 51 suite XML.**

## Kanıt yolları
12 PNG + run41 logu: `denetim/tur19/kanit/` ve `denetim/tur19/kanit-run41.log`.
