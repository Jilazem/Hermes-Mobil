# K18B-G3 — Merkezi onay kutusu + sesli onay (riskli ajan eylemleri)

Kart: t_0f6f8132 · İş: cd-android · Tarih: 2026-10-17
Dal: `feat/k18b-g3-onay-kutusu` (taban: `feat/k19-model-listesi` — t_35ec8257
"yeni branch t_0e126468 değil, son birleşen K19 tabanı" kararına uygun)
Depo: `/Volumes/EX/80-YEREL/hermes-workspace/wt-k18b-g3`

## Ne yapıldı

### 1) Merkezi onay kutusu (saf Kotlin katmanı)

- **`app/src/main/java/com/hermes/mobile/data/ApprovalCenter.kt`** (yeni):
  - `ApprovalEntry` (ne istiyor / araç / argüman özeti / neden durdu / oturum),
  - `ApprovalCenter` (tek kayıt kaynağı: `submit`, `submitFromEvent`,
    `decide`, `slashCommand`, `settleByPrompt`, `reset`) — kart ile kutu
    **aynı kaydı** paylaşır (`ap-N`); `decide` çift cevabı engeller
    (zaten yanıtlandıysa `false` döner, ikinci komut gönderilmez).
  - `ApprovalVoice.parse` — sesli girdiden onay/red komutu çıkarımı;
    belirsiz/çelişkili girdi → `null` (**fail-closed, asla tahminle uygulanmaz**).
  - `ApprovalGuard` — araç sınıfı kararı: salt-okunur → **devam** (görünür
    açıklama), bilinmeyen araç → **dur** (`guardReason` deseni, fail-closed).
  - Komut dili DEĞİŞMEDİ: `slashCommand()` → `/approve` | `/deny [sebep]`
    (gateway `slash.exec`). Kart ve kutu aynı fonksiyonu kullanır.
  - `submitFromEvent` yalnız gerçek gateway sözleşmesi alanlarını okur
    (`description`, `command`, `prompt_id` — `CanonicalApprovalPrompt`);
    alan yoksa UYDURULMAZ (`tool=null`, `argsSummary=null`).

### 2) Ekranlar

- **`ui/ApprovalsScreen.kt`** (yeni): "Senin onayın bekleniyor" listesi —
  bekleyen kartlar (Ne istiyor / Araç / Argümanlar / Neden durdu + oturum
  etiketi), Onayla/Reddet, **Sesli onay** (RecognizerIntent + fail-closed
  parse), **"Bazı eylemler durmaz"** kartı (salt-okunur/bilgi sorusu/
  telefon araçları sınıfları açıkça yazılır), Geçmiş (Onaylandı / İptal
  edildi / Başka kanaldan yanıtlandı).
- **Ayarlar > Gizlilik ve bildirim > Onay merkezi** (`SettingsCategory.OnayMerkezi`)
  + Pano kartı (`PanelGroups`, `PanelScreen` — `Icons.Default.FactCheck`).
- Sohbet-içi kart ile ortak kayıt: `ChatItem.Approval.key == ApprovalEntry.id`;
  biri yanıtlarsa diğeri "Başka kanaldan yanıtlandı" gösterir
  (`ChatViewModel` `collectLatest` ile senkron).

### 3) Bağlantılar

- `ChatViewModel`: `approval.request` / `sudo.request` olayları merkezi
  kutuya düşer; `answerApproval(key, approve, reason, sessionId)` —
  kutudan gelen yanıt ilgili oturumun `slash.exec`'ine gider.
- `JarvisBrain` (sesli asistan) aynı olayları kutuya işler;
  `clarify.request`/`secret.request` **girmez** (soru — onay değil).
- **D-05 düzeltmesi**: kart başlığına ham süreç içi id (`mock-s2` gibi)
  sızıyordu; artık konu yoksa "Sohbet" (ChatViewModel + submitFromEvent
  savunması, birim testiyle kilitlendi).

## Doğrulama (adım adım kanıt)

| Adım | Komut/ekran | Sonuç |
|---|---|---|
| Derleme | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| Birim test | `./gradlew :app:testDebugUnitTest` | **suites=97 tests=1002 fail=0 err=0 skip=0** (taban 983 + yeni 19 = 1002 ✓) |
| Paket | `./gradlew :app:assembleDebug` | arm64-v8a 60,8 MB md5 `919fa35e9da247441f140946fb4f68bb`; x86_64 66,1 MB md5 `eb77da7b3a4904d83e478a1fff8be66d` |
| Emülatör kurulum | `adb -s emulator-5554 install -r …app-arm64-v8a-debug.apk` | Success; açılışta çökme YOK (`logcat -b crash` boş) |
| Onay kartları | `01-sohbet-onay-kartlari.png` | Sohbet içinde 2 eşzamanlı onay kartı (ne istiyor + Onayla/Reddet) |
| Merkezi liste | `02-onay-merkezi-bekleyenler.png` | "2 bekleyen", araç/argüman/guard satırları, "Bazı eylemler durmaz" kartı, sesli onay düğmesi |
| Onayla → devam | mock log + `03-onayla-eylem-devam.png`, `03b-sohbet-onay-sonuc.png` | `slash.exec "/approve"` ✓; sohbet: kart "Onaylandı", ajan "✓ Onaylandı — eylem devam ediyor." |
| Reddet → iptal | mock log + `04-reddet-iptal.png`, `06-sohbet-tam-akis.png` | `slash.exec "/deny"` ✓; kart "Reddedildi", ajan "✗ Reddedildi — eylem iptal edildi." |
| Çift cevap engeli | hızlı çift dokunuş, mock sayaç | 2 dokunuş → **1** `slash.exec` (3→4) |
| `approval.settled` | `05-onay-merkezi-gecmis.png` | Kayıt "Başka kanaldan yanıtlandı" ile kapandı |
| Kamera izni istat. | izin merkezi ekranları | e-posta alanı eklendi (t_c49d6269 düzeltmesi korundu) |

**Kanıt dosyaları:** `denetim/k18b-g3/` (7 PNG + `mock-gateway-kanit.log` —
RPC/olay JSON satırları: `slash.exec /approve`, `slash.exec /deny`,
`approval.request`, `approval.settled`).

### Emülatör akışının neden mock gateway ile yapıldığı

Onay isteği ajan davranışına bağlı rastgele bir olay; gerçek gateway'e bu
olayı istemek için çalışan bir ajan görevi gerekir. Kanıt için **aynı
protokollü** (tui_gateway JSON-RPC + olay akışı, `GatewayWsClient.kt` dokümanı)
yerel sahte sunucu yazıldı (`tools/k18b-g3/mock_gateway.py`, yalnız stdlib):
uygulama gerçek APK olarak emülatörde koştu, yalnız sunucu yereldi. Gerçek
gateway sözleşmesi `ui-tui/src/gatewayTypes.ts` + `createGatewayEventHandler.ts`
okunarak doğrulandı (payload alanları birebir).

## ATLANDI / kısıtlar

- **Sesli onayın gerçek konuşma ile testi**: headless emülatörde mikrofon
  girişi yok → konuşma testi ATLANDI (ekran: sesli onay düğmesi görünür,
  `ApprovalVoice.parse` 6 birim testiyle kilitli — TR/EN, sıra sıfatları,
  ret sebebi, belirsiz girdi → null).
- Otomatik onay döngüsü ("always allow") K18B-G3 kapsamı DIŞI (t_4f98423d).
- Mock REST yüzeyi yalnız açılış için asgari (`/api/sessions`, `/api/status`
  vb. boş dönümler); oyun/panel ekranları mock'la dolu kanıtlanmadı —
  onay akışı kanıtı için yeterli.

## CREDITS

`CREDITS.md` oluşturuldu:
- **MausBot** (milind-soni/OpenMausBot, Apache-2.0) — "Waiting on your yes"
  merkezi onay kutusu + "coverage garanti değildir" uyarısı desenleri
  (**fikir**; kod kopyalanmadı).
- **Kelivo** (Chevey339/kelivo, AGPL-3.0) — araç-bazlı onay görünümü
  (**yalnız fikir**; AGPL gereği kod KOPYALANMADI).

## Commit listesi (bu dal)

1. `feat(k18b-g3): merkezi onay kutusu + sesli onay` — ApprovalCenter,
   ApprovalsScreen, ChatViewModel/JarvisBrain bağları, Settings/Panel girişleri,
   ApprovalCenterTest (19 test), CREDITS.md, mock gateway + kanıtlar.

## Açık riskler

- Mock gateway emülatör kanıtı içindir; gerçek gateway'le uçtan uca onay
  akışı (gerçek ajan göreviyle) ayrı bir QA kartında yapılabilir (LOW).
- Sesli girdi cihazda manuel doğrulanmalı (mikrofon); parse katmanı testli.

## Denetmene not

En küçük diff hedeflendi: yeni 3 dosya + 6 dosyada nokta atışı bağlantı.
Cevap yolu (`/approve`,`/deny`) gateway protokolüyle birebir aynı kaldı —
yenilik yalnız toplama/görüntüleme katmanında. D-05 (ham id etiketi) test
sırasında yakalanıp düzeltildi ve birim testiyle kilitlendi.