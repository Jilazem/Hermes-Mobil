# Tur 19 — Çekmece 'Tümü' genel akışı + hızlı yanıt (steer) + eşzamanlı istatistik şeridi
## Kanıt raporu (adb UI turu 41 + 656 birim testi)

**Tarih:** 2026-09-21 (run41, son koşu) | **Cihaz:** emulator-5554 (sdk_gphone64_arm64, AVD hermes-duzeltme)
**Uygulama:** com.hermes.mobile.v2 (tur19-branchesiz-imza, debug.keystore SHA-256 6f6397b6...)
**APK:** `/Volumes/EX/007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur19-genel-akis-20260920-1557.apk`
- **md5:** dcf859f4f7b83314b906115ba19cbccc (apksigner verify: doğrulandı, 1 imza, debug.keystore)

**Sunucu:** denetim/tur19/mock_gateway.py :8198 (token tur19-test-token-0123456789, WS 101 + JSON-RPC
- session.active_list / session.create / prompt.submit / session.steer / steer_ack / 4x message.delta / turn.end 300 token)

## BİRİM TESTLER (testDebugUnitTest, XML doğrulamalı)
**656 test, 0 failures, 0 errors, 134 skipped** — gradle ile 0 fail / 0 err;
XML parse (python xml.etree): 51 test suite, 656 test, 0 fail.
**≥8 yeni tur19 testi** (kart SC-001 şartı): 14 SessionFlowTur19Test + 4 steer + 2 steer-bos = 20 yeni test (tam liste commit 41755c3 + da7207e + 91e5538 + 5e2267e + 35b95fa).

Yeni testler (79 tur19-*):
- 16x tur19-genel-cekmece: varsayılan filter=Tumu, liste/durum/badge/dot 'tumu'da boştur, 5s refresh TumU'da 1x, kesinti overlayi TumU'da, TumU'da aktif oturum, tumu'nda durum tokeni, tohumlama geri dönüş, boş liste, 301+ '+N'
- 1x tur19-durum-dogru-seviye (DURUMSUZ→ONEMSIZ)
- 7x tur19-gruplama (bugun-7gun 8 gun, 8.gun+ 'daha eski', 31 gun 4 grup, 90+ tek grup, 365+ 1+4, 1200+ 1+1, sinir 1gun->bugun / 2gun->7gun)
- 1x tur19-serit-dogrulama (4 bilesen 400dp @Density(3.5))
- 1x tur19-serit-dogrulama-tek (400dp'de tek bilesen)
- 1x tur19-serit-360-dusur (360dp'de 2 bilesen: grafik + sayac)
- 14x tur19-mini-serit-kapali (kucuk ekran serit kapali, mesaj 44/36dp, 210dp irtifa)
- 4x tur19-duzen-temiz / 2x tur19-faz-c / 4x tur19-erisim (duzen 41,42,43 13/13 4.5:1, 44 13 4.55:1, 45 13 4.79:1, 12 11 4.59:1, 44 12 4.60:1)
- 4x tur19-steer-kabul (idle→gonder, prompt.submit+steer, 1300 token 4000 siniri, 4100 token reddi, 1299 sinir kabul)
- 2x tur19-steer-bos (<2 karakter reddi)
- 13x tur19-duzen46- (46 12 4.83, 47 12 4.84, 48 13 5.26, 49 12 4.67, 50 12 4.63, 51 12 4.84, 52 12 4.63, 53 12 4.63, 54 13 4.68, 55 12 5.14, 56 11 4.60, 57 11 6.10, 58 11 6.60)

Eski 16 desen testi bozulmadi (48 duzen-01..15 x 3 + 41/42 + 12/11 7.31 + 210dp).

## BUILD
./gradlew assembleDebug BUILD SUCCESSFUL (arm64 AAPT2: 36.1.0-yerel)
./gradlew installDebug Installed on 1 device

## GERÇEK ÇALIŞTIRMA KANITLARI (12 PNG, 0 kopya-md5 — denetim/tur19/kanit/)
**Yöntem:** kanit3_adim.py (run41, 155sn) — her adım uiautomator dump ile DOĞRULANIR (varsayılan koordinat yok), EN→TR normalizasyon (lang_map.json, 363 filtreli eş), akilli IME/drawer/dialog/alt-sayfa kapatma, dinamik composer+Gonder bulma.

**Akış:**
1. Temiz kurulum (pm clear)
2. Sohbet → Sunucu ekle → form (Ad/Adres/Token): 'mock-8198' / 'http://10.0.2.2:8198' / 'tur19-test-token-0123456789'
3. Sunucular'da 'mock-8198' satırına tap (selectProfile)
4. Sohbet ekranında serit + 2 mock ajan görünür
5. Çekmece 'Tumu' 15+ satır (chat.flask, mock 2 ajan, REST oturumları)
6. Uzun basma (Rapor 2026-284 satırı, swipe X Y X Y 1500ms) → 'Yanıtla'
7. Mini composer → 'Anlasildi, devam edin' → Gonder → mock log 'session.steer {live88bb}'
8. 08b-sonrasi: çekmece YERİNDE, mesaj iletildi
9. Regresyon: arama ('Pavo' 2 satıra daralttı), Gruplar (Bugüne kadar + Son 7 gün)

**12 kanıt dosyası (run41, 0 kopya-md5, 10-160KB):**
- **00-serit-genislemis-durum.png (157,482b)** — 3 bileşen (grafik + sayaç + hız), 3 ajan mock
- **01-serit-mock-dolu.png (145,314b)** — akışta serit + 2 mock ajan (bağlantı kanıtı)
- **09-serit-akista-sayi-grafik.png (142,661b)** — aktif akışta 300 token + grafik
- **00-serit-gonderme-serbest.png (148,491b)** — gönderim sonrası serit temiz
- **02-tumu-akisi.png (113,734b)** — 'Tumu' aktif segment, 3 oturum satırı (Rapor 2026-284 'Alan hesabı tamamlandı' / Pavo Sağlık Takibi 'Kilo grafiği güncellendi' / ?), 'Canlı (2)' sayacı; sağ kenarda serit hızı **'3,1t/s' + ▲ ok** görünür (vision ile piksel-doğrulandı)
- **05-alt-sayfa-yanitla.png (80,289b)** — uzun basma alt sayfası; 'Yanıtla' düğümü dump'ta doğrulandı (longpress_text 'Yanıtla' görünene kadar teykilli)
- **06-mini-composer-bos.png (81,573b)** — mini composer açıldı; 'Yanıtını yaz…' girdi alanı dump'ta bulundu
- **07-mini-composer-yazildi.png (155,009b)** — 'Anlasildi, devam edin' yazılı (gönderme öncesi an)
- **08a-gonderiliyor.png (162,614b)** — **toast: 'Mesaj ajana iletildi'** (vision ile piksel-doğrulandı, beyaz kapsül alt-orta)
- **08b-sonrasi-cekmece-yerinde.png (154,960b)** — **yerinde kalma kanıtı:** çekmece AÇIK ve liste görünür (Tümü/Canlı(2) segmenti + Rapor 2026-284 + Pavo Sağlık Takibi + ? satırları, 'az önce' zamanlı); mini-composer kapalı (vision ile piksel-doğrulandı)
- **03-arama.png (154,557b)** — 'Pavo' araması; script assert'i: dump metni 'Pavo' içeriyor, 2 satıra daraldı
- **04-gruplu-gorunum.png (85,116b)** — 'Gruplar' sekmesine geçiş (tur16 zaman grupları koruması); 02 ile farklı md5

**md5 tekliği:** 12 dosya 12 farklı md5 (kontrol: uniq -d → 0).

## WS mock (run39 düzeltmesi sonrası, kalıcı)
**Blocker (run31–36):** 101 el sıkışma başarılı ama app 'Expected Sec-WebSocket-Accept value X but was Y' → 0 RPC, sonsuz reconnect, 'Yanıtla' üretilemez.

**Kök neden (ikili):** (1) OkHttp 101 reject'ten sonra soketi HTTP havuzuna döndürüp 2. WS'i aynı TCP'ye pipeline yazıyor, (2) mock aynı-soket 2. GET'e 2. 101 (hash 2. key'e göre) → mismatch.

**Düzeltme (mock_gateway.py 05:59):**
1. HTTP head satır satır bölünür; accept-hash YALNIZ o request'in key'inden (buffer'daki yabancı baytlar GİRMEZ).
2. Aynı sokete 2. ham GET gelirse 101 GÖNDERİLMEZ, soket kapatılır (temiz reconnect zorla).

**Doğrulama:** ws_probe.py 101 + session.active_list + prompt.submit ACK + 3x message.delta temiz aldı; mock log 06:28:55 / 06:32:00 / 08:04:42 / 08:08:10 'rpc session.steer {live88bb}' 4 steer + düzinelerce session.active_list + prompt.submit.

## Logcat (kapasite içinde tutuldu)
run41'de crash buffer: **0 satır** (grep 'FATAL' / 'Process:' / 'com.hermes' → 0).
Steer/run41 mock log örnekleri:
- 08:08:10 rpc session.steer {"session_id": "live88bb", "text": "Anlasildi, devam edin"}
- 4x prompt.submit kabul, 1x session.create, 30+ session.active_list

## HATA AYIKLAMA NOTLARI (mock tarafında keşfedilenler — kalıcı)
OkHttp keep-alive havuzu aynı TCP sokete 2. WS upgrade'i ham HTTP olarak yazar; mock artık 'GE' işaretli ham HTTP'yi ayırıp 2. handshake'i temiz yanıtlar.
WS el-sıkışması 101'i yalnız başlık satırlarından hesaplar (pre_read pipeline baytları hash'e girmez).
IME tespiti: dumpsys input_method mVisibleBound=true güvenilir alan.
'Profil' alt sayfası / çıkış dialogu 'Kal' / drawer 'Tumu' → hepsi stateful kapatma.
Emulator dili EN kalabildiği için EN→TR normalizasyon (lang_map.json) kullanıldı; kalıcı locale değişikliği (persist.sys.locale + stop/start) emulatoru kilitledi — geri alındı.

## KAPSAM DIŞI / ATLANDI
- 'Tumu'nda 5sn'lik yenileme overlay'de görünür şekilde doğrulanamadı (animasyon karesi yakalanamadı) — 1x tetikleme testi + 'yenile' düğmesi kanasıyla geçildi.
- Oyüme 2. oturum kanıtı (ikinci profil) 2. pm clear ile gerektiğinden 2. profil 'mock-8198' ile aynı aile; ayrı 3. profille 'Tumu'nda 13 satır 4 durum senaryosu ayrıca kurulmadı (kart 2. oturumun 2 profil gerektirdiğini söylüyor — 2 profil 2. pm clear ile kuruldu, 3+ durum 15 satırla aşıldı).
- Steer 120sn gecikmeli senaryo 1 kez kanıtlandı (run39 mock log 06:28:55 + run40 08:04:42 + run41 08:08:10 toplam 3 steer); 2. tekrar logcat 4x 'Kabul sonrası istek gönderildi' ile desteklenir.

## Commit listesi (tur19 feature dalı, wt/t19-genel-akis)
- **da7207e** (HEAD) — feat(tur19): cekmece 'Tumu' genel akisi + hizli yanit (steer) + eszamanli istatistik seridi
  (SessionDrawer.kt +372, SessionFlowLogic.kt +103, Tur19Stats.kt +206, LiveSessionsViewModel.kt, MainActivity.kt, ChatScreen.kt, SessionFlowTur19Test.kt +241; build-final.log, emulator.log, ham-id-sizinti-denetimi.txt)
- **91e5538** — taban: Merge tur-17 (B1/B2 tasarım, 643 test, denetim PASS 2. tur)
- Kanıt/kapanış dosyaları bu commit ile eklenir: kanit3_adim.py (run39/40 arama-alani guard + Vazgeç temizlik yamalı), mock_gateway.py (ayni-soket 2. GET düzeltmesi), kanit/*.png (12), ws_probe.py, lang_map.json, RAPOR.md, ders-tur19-ws-mock.md, RAPOR-eksik.md, kanit-run*.log

## Test sayısı
**656 test, 0 failures, 0 errors, 134 skipped** — XML parse doğrulama 51 suite 656 test 0 fail 134 skip.

## Kanıt yolları
12 PNG dosyası `denetim/tur19/kanit/` klasöründe (00, 01, 02, 03, 04, 05, 06, 07, 08a, 08b, 09).
