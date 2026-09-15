# TUR-10 — v2 saha logu düzeltmeleri (F1/F2/F3/F4)

Repo: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman` · dal `feat/android-uzman-devralma`
Başlangıç HEAD: `b1617a1` (main ile aynı) · Push YOK · Canlı relay/gateway/telefon: DOKUNULMADI
Girdi: `/Users/gokhanuzman/007-HERMES/000-TEMP/v2-tani-logu-20260915.txt` + `v2-log-triage.md`

## 0. Özet (kanıt tablosu)

| # | İş | Durum | Ana kanıt |
|---|---|---|---|
| F1 | AwaitReplyService FGS çökmesi | **Kapatıldı** | 12 birim testi + emülatörde 20/20 start→stop yarışı, crash tamponu **boş** |
| F3 | Ölü adres yönetimi | **Kapatıldı** | 14 birim testi + Ayarlar/Sunucu düzenle dialogunda adres sağlık satırları |
| F2 | Soket dayanıklılığı (client) | **Kapatıldı** | 11 + 12 birim testi (parametre merdiveni + kopma defteri), ping 20→15 / 40→20 sn |
| F4 | Spark 403 | **Uygulama tarafı bitti / sunucu tarafı iş kaldı** | Mac ölçümü: LAN `:5555` **200**, `/spark-api` **403**, `/api/sparks` **404** |
| — | Test sayısı | **378 → 442** (0 hata, 0 atlanan) | `app/build/test-results/testDebugUnitTest/*.xml` |
| — | APK | 25.008.564 bayt · md5 `2ef4a8d0e9d52e305640ff7c33e51818` | emülatöre `install -r` → **Success** |

Log referansları: çökme **satır 157/170** (`14:09:32 · thread=main · session=fb2e7567`),
`unreachable` 140 satır (12:46–13:48), `pong timeout` 19 satır (12:51–13:48, ws 20000ms / köprü 40000ms),
`[spark] GET /api/sparks -> 403` **satır 45/46** (12:55:24).

---

## F1 — AwaitReplyService: yarış kökten kapatıldı

**Kök neden (tur-5'in kaçırdığı).** Tur-5 yalnız "ikinci start"ı engelleyen bir
`running` bayrağı koymuştu. Asıl yarış bu değil: sistem `startForegroundService`
sonrası 5 sn içinde `startForeground` bekler; uygulama bu arada `stopService`
çağırınca servis sözleşmeyi tamamlayamıyor ve sistem
`ForegroundServiceDidNotStartInTimeException` fırlatıyor (log satır 157). Ayrıca
`ChatViewModel` her state değişiminde (token başına!) start/stop çağırıyordu.

**Çözüm — `data/AwaitLifecycle.kt` (saf karar makinesi, testli) + servis sözleşmesi:**

1. `onStartCommand`'ın **ilk** ifadesi koşulsuz `startForeground` (hiçbir "gerek
   kalmadı" kararı bunu atlayamaz).
2. Onay gelmeden `stopService` **çağrılmaz** → `pendingStop` işaretlenir, servis
   onaydan sonra `stopSelf()` ile kapanır.
3. `stop`'tan sonra gelen geç `start` yeni servis başlatmaz (start/stop serileştirildi).
4. 3 sn'lik watchdog: onay gelmezse bayrak serbest bırakılır + tanı kaydı (sessiz kilitlenme yok).
5. `ChatViewModel` artık yalnız `agentBusy` **değiştiğinde** haber verir (token başına değil).

**Kanıt.**
- Birim: `AwaitLifecycleTest` **12 test** (çift start, onay-beklerken-stop,
  stop-sonrası-geç-start, iptal edilen durdurma, zaman aşımı, döngü).
- Emülatör (`denetim/tur10/fgs-yaris.sh`): 20 kez `am start-foreground-service` +
  **anında** `am stopservice` → `logcat -b crash` **0 satır**,
  `ForegroundServiceDidNotStartInTime` **0**, kalan servis **0**,
  uygulama tanısında **20/20** `gec start onaylandi ve durduruldu (yaris kapatildi)`.
  (Eski kod bu dizilişte çöküyordu; log satır 157 o çökmenin kendisi.)
- Sınır: gerçek cihazdaki koşum (SM-S918B) bu görevde yapılmadı — telefona
  dokunma yasağı. Aynı yolu emülatör + 12 test kapsıyor.

## F3 — Ölü adresler: kısa süre devre dışı + tek sağlıklı adres

**Kök neden.** `HermesClient.urlOrder()` her istekte **tüm** adayları deniyordu;
ölü LAN adresleri (`192.168.101.10`, `192.168.1.10`) her seferinde 8 sn zaman
aşımına düşüyordu → 140 `unreachable`. `AddressHealth` yoktu.

**Çözüm — `data/AddressHealth.kt` (saf, testli):**
- Art arda başarısız adres artan süreyle devre dışı: **30 sn → 2 dk → 5 dk → 15 dk** (tavan).
- Deneme **tek sağlıklı adres** üzerinden yapılır (`AddressHealth.plan`); hepsi
  devre dışıysa en erken açılacak olan tek başına denenir (sonsuz beklemek yok).
- Başarı sayacı ve devre dışılığı sıfırlar. **Kullanıcı adresi silinmez** —
  yalnız deneme sırası akıllanır.
- Tanı satırı artık nedeni ve süreyi yazıyor:
  `GET /api/status · http://… unreachable (3. kez - 300sn devre disi): …`
- **Adres düzenleme netliği**: Sunucu ekle/düzenle dialogunda "Adres deneme
  durumu" bloğu — her adres için `sağlıklı` / `… sn devre dışı (N başarısız deneme)`
  / `yeniden denenecek` + **"Adresleri şimdi dene"** (bekleyen süreyi sıfırlar).

**Kanıt.** `AddressHealthTest` **14 test** (merdiven, plan sırası, hepsi-devredışı,
profil izolasyonu, reset, kayıt sınırı). Dialog arayüzü derlemede doğrulandı;
ekran kanıtı `denetim/tur10/emulator-uygulama.png` (uygulama çalışıyor, çökme tamponu boş).

## F2 — Soket dayanıklılığı (client) + gözlemlenebilirlik

**Ne değişti** (`data/SocketTuning.kt` — tek ayar noktası):
- Ping penceresi (= OkHttp'de pong bütçesi): **ws 20→15 sn**, **köprü 40→20 sn**.
  Röle **bilerek 45 sn'de bırakıldı**: uzun `hermes_ask` turunda pong gecikiyordu
  (tur-2 ölçümü); pencereyi kısmak o hatayı geri getirirdi.
- **Kopma sonrası hızlı iyileşme**: son başarılı bağlanma ≤60 sn ise geri çekilme
  tavanı 5 sn (ws) / 15 sn (köprü; tur-7 devirme merdiveni korunur), aksi hâlde
  normal merdiven. Tüm gecikmelere **±%25 jitter** — üç kanal aynı saniyede
  yeniden bağlanıp sunucuyu dalgalandırmasın (sahada 16:12:52'de ikisi birden kopmuştu).
- **Bağlantı olayı özeti** (`data/ConnectionJournal.kt`, saf + testli): her kopma
  sınıflandırılır (`pong-timeout`, `network-lost`, `taken-over`, `auth-reject`,
  `client-closed`, `server-closed`) ve tanı kaydına tek satır yazılır:
  `kopma ozeti · son 5 dk 3 · ws=pong-timeout(14:09:32) ← …`. Defter
  `DiagLog.dump()` çıktısına "connection journal" bölümü olarak da gömülür;
  neden metni `redact`'ten geçer (token sızıntısı testli).
- Kanallar: ws, köprü ve röle aynı deftere yazar → "aynı saniyede mi koptu"
  sorusu artık veriyle yanıtlanır.

**Kanıt.** `SocketTuningTest` **11 test**, `ConnectionJournalTest` **12 test**.
Ağ-kök-neden (proxy/Keenetic yolu) bu görevin dışında bırakıldı — yalnız client
sağlamlaştırma + gözlemlenebilirlik yapıldı.

## F4 — Spark 403: kök neden ölçüldü, uygulama tarafı düzeltildi, sunucu tarafı iş kaldı

Mac'ten ölçüm (`denetim/tur10/spark-probe.json`, token dosyadan okunup ekrana
basılmadan; uç: gerçek gateway):

| Host + path | Kod |
|---|---|
| `https://hermes.winterfell07.keenetic.pro/spark-api/api/sparks` (tokenli) | **403** `forbidden` |
| `http://127.0.0.1:9150/api/sparks` (tokenli) | **404** `No such API endpoint` |
| `https://…/api/sparks` (tokensiz) | **401** |
| `http://192.168.1.101:5555/api/sparks` (LAN, doğrudan sparkDash) | **200** ✓ |
| `http://127.0.0.1:5555/api/sparks` / `http://192.168.1.99:5555/…` | bağlantı yok (Caddy vekili ölü adrese bakıyor) |

**Kök neden (kanıtlı):**
1. Hermes panosunda `/api/sparks` ucu **yok** → her yerde 404.
2. Uygulama dış adres aktifken `base + /spark-api` deniyor; Caddy'nin `@spark_ok`
   kapısı `X-Hermes-Session-Token`'ı `SPARK_GATE_TOKEN` ile karşılaştırıyor, ama
   **o değişken Caddy'nin LaunchAgent ortamında tanımlı değil** (plist'te yalnız
   `GBRAIN_LAN_TOKEN`/`PATH`/`XDG_DATA_HOME`) → varsayılan `tanimsiz-erisim-yok`
   hiçbir istekle eşleşmiyor → **403** (tam olarak uygulamada görülen kod).
3. Ayrıca `/spark-api` vekili `192.168.1.99:5555`'e bakıyor; o adres bu ağda yok
   (`No route to host`) — ev ağı yeniden numaralanmış.

**Uygulama tarafında yapılan (`data/SparkEndpoints.kt` + `SparkClient` + panel):**
- Aday listesi tek kaynaktan: LAN host ise **doğrudan `:5555`**, dış ise `/spark-api`;
  son çalışan adres öne alınır (`candidates()` saf fonksiyon, 15 test).
- `SparkClient` adayları sırayla dener, çalışanı hatırlar ve **yalnız okuma** yapar.
- Hepsi başarısızsa panelde **net, eyleme dönük** hata: 403 → "…sunucu tarafı iş:
  dış yol için SPARK_GATE_TOKEN ayarı gerekli; şimdilik ev ağında açın",
  404 → "sparkDash çalışmıyor ya da adres yanlış", erişilemez → denen adres listesi.

**Sunucu tarafı iş (bu görevde YAPILMADI — canlı sunucuya dokunma yasağı):**
- Caddy `:9150` bloğunda `SPARK_GATE_TOKEN` tanımlanmalı (ya da `/spark-api`
  kapısı kaldırılıp yalnız LAN'a bırakılmalı).
- `/spark-api` vekilinin hedefi `192.168.1.99:5555` yerine **gerçek sparkDash
  host'una** (bugün `192.168.1.101:5555`) çevrilmeli.
- Mac'te `:5555` → `192.168.1.99:5555` vekili de aynı nedenle ölü.

## Kısıt uyumu
- Canlı relay/gateway/telefona **dokunulmadı**; hiçbir ajan turu tetiklenmedi.
- Emülatör köprü testleri **kapalı** kaldı (phone_bridge'e bağlanılmadı).
- Push **yok**; değişiklikler dalda commit edildi.
- Emülatör: `hermes-v2` AVD, `SM-S918B` yok.

## Dosya listesi (tur-10)
Yeni: `data/AwaitLifecycle.kt`, `data/AddressHealth.kt`, `data/SocketTuning.kt`,
`data/ConnectionJournal.kt`, `data/SparkEndpoints.kt`
Değişen: `AwaitReplyService.kt`, `ChatViewModel.kt`, `HermesClient.kt`,
`GatewayWsClient.kt`, `PhoneBridgeService.kt`, `LiveVoiceClient.kt`, `DiagLog.kt`,
`ServerProfile.kt` (isPrivateHost tek kaynak), `SparkClient.kt`,
`PanelViewModel.kt`, `ui/ConnectScreen.kt`
Yeni testler (64): `AwaitLifecycleTest` 12 · `AddressHealthTest` 14 ·
`ConnectionJournalTest` 12 · `SocketTuningTest` 11 · `SparkEndpointsTest` 15
