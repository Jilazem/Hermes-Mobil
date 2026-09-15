# Tur-7 — Çoklu cihaz bağlantı kontrolü (devralma / reconnect fırtınası)

Tarih: 2026-09-15 · Bot: android uzmanı · Dal: `feat/android-uzman-devralma`
Commit: `d2abbad` (push YOK) · Proje: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman`

Kısıtlara uyuldu: canlı relay'e (9180/9181) bağlanılmadı, yeniden başlatılmadı;
tüm testler sandbox kopyada (9280/9281) koştu; model/config değişmedi; token'lar
log'a ham yazılmıyor (maskeli); push yok.

---

## 1. BULGU

### 1.1 Canlı kanıt (ölçüm, tahmin değil)

`/Users/gokhanuzman/gx10-tasima/logs/phone-bridge.err.log` (6.3 MB) tam olarak analiz edildi:

| Ölçüm | Değer |
|---|---|
| Toplam `/phone` bağlantısı (101) | 16 725 |
| Devir satırı ("yeni bağlantı geldi…") | 16 676 |
| Bağlanan cihaz dağılımı | `samsung` 16 710 · `Google` (emülatör) 12 · `?` 11 |
| İstemci | **tamamı** `okhttp/4.12.0` |
| Fırtına tepeleri | 2026-09-09 13:00–22:00 arası **saatte ~1 500 devir**; 2026-09-11 12:00–21:00 arası saatte ~300 |
| Bugün (09-15) | 12:56:33 · 12:57:56 · 12:59:18 · 13:00:41 · 13:02:02 · 13:03:26 · 13:04:47 · 13:06:11 · 13:07:31 · 13:08:56 → **sabit ~82 sn periyot** |
| Görev metnindeki "×14" | iyimser: 12:49–13:10 penceresinde **716** devir var |

Yani fırtınanın kaynağı bugün emülatör değil, **gerçek telefonun kendisi**:
kendi ölü soketini ~82 sn'de bir yeniden bağlanarak deviriyor.

### 1.2 Kök neden — iki katmanlı ve ölçümle doğrulandı

**(a) İstemci devralmayı öğrenemiyor.** Telefon uygulamasının kendi tanı günlüğünde
(`files/diag.log`, 1 491 satır) `[bridge]` için **tek bir `closed code=` satırı yok**;
yalnız 8 adet şu hata var:

```
E [bridge] failed — SocketTimeoutException: sent ping but didn't receive pong
  within 40000ms (after 2 successful ping/pongs)
D [bridge] reconnect #1 in 4000ms
I [bridge] connecting to ws://10.0.2.2:9180/phone?token=***
```

Zincir: relay yuvayı yeni cihaza veriyor → düşürülen istemciye close kodu **ulaşmıyor**
→ istemci 40 sn sonra ping zaman aşımıyla "ağ hatası" görüyor → **hemen** (4 sn) yeniden
bağlanıyor → diğerini deviriyor → döngü. Canlı relay'de 13:07:31 ve 13:08:56 devirleri,
uygulama günlüğündeki 13:08:51 hatasıyla **bire bir** örtüşüyor.

**(b) Ayırt edici sinyal yoktu.** Eski relay `close(code=4000, message=b"replaced")`
gönderiyordu. Bunu sandbox'ta ölçtüm: **aiohttp istemcileri 4000/replaced alıyor** —
yani kod "hiç gitmiyor" değil. Ama OkHttp 4.12.0 bu close çerçevesini uygulamaya
**hiçbir zaman** ulaştırmıyor (ne eski 4000'de ne yeni 4001'de; `closed code=` satırı
hâlâ yok). Sahada güvenilecek tek kanal **uygulama düzeyinde bir kare** olduğu için
düzeltme tek kanala bağlanmadı.

**(c) Ek bulgu — token log'a ham düşüyordu.** Erişim log'u istek satırını olduğu gibi
yazıyordu: `"GET /phone?token=xBisGCjp7ghW783nqb7AGZUMx-…"` → 6.3 MB'lık log dosyası
tam yetkili bir sır hâline gelmişti. Düzeltildi (bkz. §2.1).

### 1.3 İstemci tarafındaki ikinci kusur

`PhoneBridgeService.onClosed`, close kodunu **hiç okumuyordu**: her kapanışta
`scheduleReconnect()` çağrılıyor, geri çekilme ise üsseldi (`2·2^n`, 300 sn tavan) ve
**her başarılı bağlanmada sayaç sıfırlandığı için** hiç büyümüyordu → pratikte sabit
4–8 sn'lik yeniden bağlanma.

---

## 2. DÜZELTME

### 2.1 Relay — `/Users/gokhanuzman/007-HERMES/20-ARACLAR/phone_bridge.py`
Yedek: `phone_bridge.py.bak-tur7-20260915-1314` (canlı süreç bu dosyadan koşuyor ama
yeni kod **yüklenmedi/deploy edilmedi** — deploy ayrı adım).

* `TAKEOVER_CODE = 4001`, `TAKEOVER_REASON = "devralindi: yeni cihaz baglandi"`.
* `Phone.attach()` artık **iki kanaldan** haber veriyor (`_retire`):
  1. `{"event":"taken_over","reason":…,"code":4001,"device":…}` metin karesi,
  2. ardından `close(code=4001, message="devralindi: yeni cihaz baglandi")`.
  Her ikisi de 3 sn zaman aşımıyla korunuyor; yeni bağlantının el sıkışması bloke olmuyor.
* Log: `devralındı: <eski cihaz> → <yeni cihaz>`.
* `/status`: geriye uyumlu yeni alan `last_takeover: {from, to, ts}`.
* Token maskeleme: `_TokenMaskFilter` + `_install_token_mask()` (kök işleyicilere ve
  `aiohttp.*` logger'larına bağlanıyor). **Not:** aiohttp erişim kaydını kendisi
  biçimlendirdiği için (`logger.info(fmt % values)`) maskeleme `record.args`'ta değil
  `record.msg`'te yapılmalı — ilk denemem tam bu yüzden tutmadı, ölçümle bulundu.
* İzin/yetki mantığına, port env adlarına (`PHONE_BRIDGE_PORT`, `PHONE_BRIDGE_TIMEOUT`)
  dokunulmadı; yeni env: `PHONE_BRIDGE_TAKEOVER_TIMEOUT`.

### 2.2 Uygulama
* **Yeni** `data/BridgePolicy.kt` — saf mantık (JVM'de test edilebilir):
  * `decision(code, takenOverSignalled, closedByUser)` → `RETRY | TAKEN_OVER | STOP`
    (4001 **veya** `taken_over` karesi → `TAKEN_OVER`; kullanıcı kapattıysa `STOP`),
  * `backoffMs(attempt)` → **2 → 5 → 15 → 30 → 60 sn** (60'ta tavan, taşma yok),
  * `isTakeoverFrame(text)` — bozuk karede istisna fırlatmaz,
  * `BridgeState { OFF, CONNECTING, CONNECTED, RETRYING, TAKEN_OVER }`.
* `PhoneBridgeService`:
  * `onMessage` önce devralma karesini kontrol ediyor → `enterTakenOver()`
    (otomatik yeniden bağlanma **durur**, bekleyen zamanlayıcı iptal edilir),
  * `onClosed(4001)` aynı yola gidiyor (kare gelmese bile),
  * `onFailure` devralma sonrası **yeniden bağlanmıyor** (sahada tam bu oluyordu),
  * `onStartCommand`: devralma kilidi açılmadan `connect()` çağrılmıyor
    (MainActivity'nin her `start()`'ı kilidi delemez),
  * `ACTION_RECONNECT` → tek seferlik bağlanma; `reconnect(context)` ile dışarı açık,
  * kalıcı bildirim duruma göre değişiyor; devralındı hâlinde **bildirime dokunmak
    yeniden bağlanıyor**.
* `ServerProfile.bridgeUrl` + `effectiveBridgeUrl`: açık adres her şeyi ezer; boşsa eski
  davranış (ev ağında 9180, dışarıda `/phone-bridge/phone`). **Neden:** köprü portu
  9180'e gömülüydü, profildeki port yok sayılıyordu — sandbox'a yönlenmek imkânsızdı.
* `SettingsScreen` → "Telefon denetimi" altında **Köprü durumu** kartı: renkli durum
  (bağlı / devralındı / yeniden bağlanıyor / bağlanıyor / kapalı) + "Yeniden bağlan".
* `Tur6SeedActivity` (yalnız debug): `--es bridge <ws-url>` ile sandbox köprü seçimi.

---

## 3. KANIT

Kanıt dosyaları: `denetim/tur7/`

### 3.1 Birim testleri — 325 / 0 / 0 / 0
`unit-test-sayimi.txt` (`app/build/test-results/testDebugUnitTest`, `--rerun-tasks`):
```
XML dosya sayisi: 31
GENEL: tests=325 failures=0 errors=0 skipped=0
```
Önceki tur: 311. Yeni `BridgeTakeoverTest`: +14 (close kodu → politika, geri çekilme
merdiveni ve tavan, kare tanıma, köprü adresi türetmesi).

### 3.2 Relay — sahte istemcilerle 17/17 PASS (`relay-test-cikti.txt`)
```
1A aktif: /status device=A            PASS
1B ilk baglantida last_takeover yok   PASS
2A A'ya close cercevesi geldi         PASS
2B close kodu 4001                    PASS
2C close reason devir metni           PASS
2D A'ya uygulama duzeyinde taken_over karesi geldi  PASS
2E yuva B'ye gecti                    PASS
2F /status last_takeover {from:A,to:B}  PASS
3A B'ye de 4001 gitti                 PASS
3B yuva A2'de                         PASS
3C bekleme penceresinde (5 sn) EK DEVIR YOK  PASS   <- DÖNGÜ YOK
3D yuva hala A2'de                    PASS
3E A2 hic devralinmadi                PASS
4A sessizce dusen soket sonrasi yeni baglanti yuva aldi  PASS
4B devralma < 4 sn (kilit/zaman asimi yok)  PASS
4C OLU soket icin sahte devir kaydi uretilmedi  PASS
=== OZET: 17/17 PASS
```
Tek devir üretiliyor, sunucu **kendi kendine** ek devir üretmiyor.

### 3.3 Emülatör — uçtan uca (gerçek OkHttp 4.12.0)
Kurulan APK md5 `d9b60147d536912b47c44d48e9075a6f`; emülatör profili sanbox'a
(`http://10.0.2.2:9281`, köprü `ws://10.0.2.2:9280/phone`) yönlendirildi.
**Canlı relay'e emülatör bağlanmadı.**

**Devralma anı** (`uygulama-diag-SONRA-sandbox.log`):
```
13:20:19 I [bridge] connected, advertising 6 tools
13:20:33 W [bridge] sunucu devralma bildirdi (taken_over karesi)
```
Sunucu tarafı (`sandbox-relay.log`):
```
13:20:33 devralındı: Google sdk_gphone64_arm64 → B-test-cihaz
/status → {"device":"B-test-cihaz","last_takeover":{"from":"Google sdk_gphone64_arm64","to":"B-test-cihaz","ts":…}}
```

**"Yeniden bağlanma denemesi YOK" — ölçüm** (devralmadan sonraki pencere):
```
[bridge] connecting  : 0
[bridge] reconnect   : 0
[bridge] failed      : 1   (SocketTimeoutException — ama ardindan baglanma YOK)
```
Karşılaştırma, **önce** (canlı relay, eski kod): aynı olaylar **her seferinde**
`reconnect #1 in 4000ms` + `connecting` üretiyordu (13:00:36, 13:03:21, 13:06:06,
13:08:51 → 4 döngü / 9 dakika).

**Bildirim** (`dumpsys notification --noredact`, `bildirim-dokumu.xml`):
```
android.title=String (Köprü devralındı)
android.text=String (Köprü başka bir cihaz tarafından devralındı — yeniden bağlanmak için dokun)
```
Ekran görüntüsü: `devralindi-bildirim.png` (gölgede "Sessiz" bölümünde).

**"Yeniden bağlan" akışı — iki giriş noktası da çalışıyor:**
* Bildirime dokunma → `13:24:12.838 I [bridge] kullanici yeniden baglan istedi` →
  `connecting` → `connected`; relay: `devralındı: B-test-cihaz → Google sdk_gphone64_arm64`;
  bildirim "Ajan telefona bağlı / yalnız okuma"ya döndü. **Tek** devir, fırtına yok.
* Ayarlar → Telefon denetimi → **Yeniden bağlan** (`ayarlar-kopru-dokumu.xml`:
  `Köprü durumu` + `devralındı` + `Yeniden bağlan` tap=212,810) →
  `13:25:35.163 I [bridge] kullanici yeniden baglan istedi` → `connected`.
  Ekran görüntüleri: `ayarlar-kopru-devralindi.png`, `ayarlar-kopru-bagli.png`.

**Maskeleme kanıtı** (`sandbox-relay.log`): `"GET /phone?token=*** HTTP/1.1" 101`,
ham token içeren satır sayısı **0**.

### 3.4 Canlı ortam korundu
İş sonunda:
* canlı relay **aynı PID (34512)**, 9180/9181 dinliyor, **yeniden başlatılmadı**,
* `curl 127.0.0.1:9181/status` → `{"online":true,"device":"samsung SM-S918B",…}`,
* emülatör köprüsü **KAPALI** (`sandbox /status → online:false`, ajan kanalı kapalı),
* sandbox relay durduruldu (9280/9281 kapalı).

---

## 4. DÜRÜST KALANLAR / RİSKLER

1. **Deploy edilmedi.** Canlı relay hâlâ eski kodu koşuyor (çalışan süreç bellekteki
   kodu kullanıyor). Gerçek telefonu düzeltmek için relay'in yeniden başlatılması
   **ve** telefon uygulamasının yeni APK ile güncellenmesi gerekiyor — ikisi de bu
   görevin kapsamı dışı bırakıldı (deploy CEO adımı).
2. **OkHttp close kodunu hiç görmüyor.** Ölçüldü, açıklaması net değil (aiohttp
   `close()` çerçevesi aiohttp istemcisine ulaşıyor, OkHttp 4.12.0'a ulaşmıyor).
   Düzeltme bu yüzden **iki kanallı**: uygulama düzeyinde `taken_over` karesi kararı
   veriyor, 4001 kodu ikinci savunma hattı. Ayrıca ağ hatası sonrası devralma karesi
   gelmişse `onFailure` yolu da yeniden bağlanmıyor — sahada ihtiyaç duyulan tam bu.
3. **Kilit süreç ömrüyle sınırlı.** `takenOver` kalıcı değil: uygulama süreci ölürse
   (START_STICKY ile yeniden doğarsa) kilit sıfırlanır ve bir kez daha denenir. Tek
   seferlik bu deneme yeni bir fırtına üretmez ama tam sıfırlanma isteniyorsa kalıcı
   bayrak (SettingsStore) gerekir — **yapılmadı**, bilerek (kullanıcı kilitli kalmasın).
4. **Yeni unit testler yalnız politika katmanını sınıyor.** `PhoneBridgeService` bir
   Android `Service` olduğu için JVM testi yok; "4001 sonrası bağlanma denemesi yok"
   iddiası emülatör ölçümüne dayanıyor (§3.3), cihaz-içi otomatik teste değil.
5. **`bridgeUrl` alanı yeni.** Eski profiller boş değerle yüklenip eski davranışı
   koruyor (test edildi), ama bu alanı Ayarlar arayüzünde düzenleyecek bir alan
   **eklenmedi** — yalnız debug tohumlayıcı ve kod yolu var.
6. **Emülatör durumu:** `files/tur6_token.txt` sandbox tokeniyle değiştirilmiş, sonra
   gerçek token'la geri yazılmış (43 bayt; özgün dosya 44 bayt = sonunda satır sonu
   vardı, uygulama `trim()` yaptığı için işlevsel fark yok). Emülatör profili hâlâ
   `ws://10.0.2.2:9280` köprü adresini taşıyor — bir sonraki turda yeniden tohumlanmalı.
7. **Emülatörde önceden kalan bir çökme bildirimi** gördüm (Ayarlar'da
   "⚠ Önceki açılış bir çökmeyle kapandı: on threa…"). Bu turun değişikliğiyle ilgisi
   yok, incelemedim.
8. **Sahadaki 16 676 devirlik geçmişin tamamı bu düzeltmeyle açıklanmıyor:** bugünkü
   ~82 sn'lik periyot net (gerçek telefon kendi ölü soketini deviriyor), ancak
   09-09'daki saatte 1 500 devirlik tepelerin nedeni (muhtemelen o günkü emülatör +
   telefon çifti) ayrıca analiz edilmedi.
