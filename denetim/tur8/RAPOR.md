# Tur-8 — Sol ray (oturum paneli) + klavye düzeni düzeltmeleri

Tarih: 2026-09-15 · Bot: android uzmanı · Dal: `feat/android-uzman-devralma`
Proje: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman` · Başlangıç HEAD: `d2abbad` (tur-7)
Push: **YOK** · Model/config değişimi: **YOK** · Relay (9180/9181): **dokunulmadı**

Emülatör: `emulator-5554` (1080×2400, 420dpi = 2.625 px/dp, tr-rTR).
Ölçümler `adb` + `uiautomator dump` + `dumpsys window` + ekran görüntüsü ile alındı;
hiçbir sayı tahmin değildir.

---

## SORUN 1 — Sol ray: açılan 2./sonraki oturumlar görünmüyor

### 1.1 Bulgu (ÖNCE — eski APK, sahada ölçüm)

`Oturumlar > Canlı` listesinden oturumlar sırayla açıldı (kart = "sohbete devam"):
üst başlık **"0 çalışıyor · 4 açık oturum"** iken:

| Adım | Açılan oturum | Ray (sol şerit) |
|---|---|---|
| 1 | Greeting | `[GR]` |
| 2 | Altınkale 1956 ada 1 parsel | `[AL]` ← GR DÜŞTÜ |
| 3 | hermes-ustasi-devriye | `[HE]` ← AL DÜŞTÜ |

Kök neden (kod): `SessionRail`in süzgeci `s.isWorking || s.isWaiting || s.isStarting ||
(currentSessionId == s.id || s.dbId)` idi. Sunucu "açık" (idle) oturumları
`session.active_list`te tutuyor ama ray idle'ları hiç göstermiyordu; kullanıcı bir
sonraki oturuma geçtiği anda öncekinin hücresi düşüyordu. Üst sayaç ("4 açık oturum")
ile ray tek hücreye inince çelişki ekranda görünüyordu.

Kanıt: `ONCE-ray-1-greeting.png`, `ONCE-ray-2-altinkale.png`, `ONCE-ray-3-hermes.png`,
`ONCE-ray-ozet.json` (her adımda dump'tan çıkarılan ray hücreleri).

### 1.2 Düzeltme — saf karar fonksiyonu + uygulama içi "son açılanlar"

Yeni dosya `app/src/main/java/com/hermes/mobile/ui/SessionRailLogic.kt`; karar
Compose'dan bağımsız, birim testli:

- **Birleşim:** ray = sunucunun AÇIK listesi (idle dahil) ∪ uygulama içi son açılanlar ∪
  geçerli oturum.
- **Dedupe:** hem `dbId` (session_key) hem süreç içi `id` ile (tur-5 dersi: aynı oturum
  iki kimlikle gelebiliyor). Aynı dbId'yi paylaşan iki kayıt varsa en son etkin olan kalır.
- **Sıra:** en son etkileşim üstte (`lastActive`/`openedAt` aynı saniye ölçeğinde
  karşılaştırılır); **geçerli oturum her koşulda en üstte**.
- **Tavan:** 6 hücre (`RAIL_LIMIT`); tavan doluyken geçerli oturum asla dışarıda kalmaz.
- **Durum eşlemesi:** `done`/bilinmeyen durum → `idle` (yani "açık"); `working` parlayan
  kenarlık, `waiting`/`starting` de vurgulu.
- **Kapanma:** kayıt sunucunun listesinde **görülmüş** ve artık yoksa → sunucu kapatmış
  sayılır, hücre iner. Hiç görülmemiş kayıt (REST'ten açılan geçmiş oturum) kanıt
  yokluğunda kalır. `active_list` hiç başarıyla gelmediyse (`LiveState.fetched=false`)
  "kapandı" hükmü verilmez — ağ sarsıntısı ray'ı boşaltmaz (tur-7 dersi).
- **Kullanıcı kapatması:** ray hücresine **uzun basma** hücreyi indirir; oturum
  yeniden açılırsa işaret silinir (`clearRailDismissal`).
- **Sayaç tutarlılığı:** `Oturumlar > Canlı` başlığındaki "N açık oturum" artık
  `openSessionCount()` ile — ray ile aynı küme, aynı dedupe (dbId).

Uygulama içi halka `ChatViewModel`de (`recentRail`, `railDismissed`); kayıt
`continueSession()` içinde tek noktadan yapılır, yani ray/Oturumlar/paylaşım hedefi
hangi yoldan gelinirse gelinsin aynı davranış.

### 1.3 Test (birim) — `SessionRailTur8Test` (18 test)

Birikim (idle oturumlar kalır), dbId ve liveId dedupe, uygulama kaydı ↔ sunucu kaydı
çiftlenmez, sunucuda olmayan açılan oturum kalır, sunucudan düşen görülmüş kayıt iner,
liste hiç gelmediyse korunur, kullanıcı kapatması + yeniden açılış, tavan + geçerli
oturum, sıra (geçerli en üstte), status eşlemesi, halka tekilleştirme/tavan,
"görüldü" damgası, sayaç-ray eşitliği, ham id etiketi ("?").

### 1.4 E2E (SONRA — yeni APK, emülatör, 5 oturum)

| Adım | Kaynak | Açılan oturum | Ray |
|---|---|---|---|
| 1 | Canlı | hermes-ustasi-devriye | `[HE, GR, AL, WH]` |
| 2 | Canlı | Altınkale 1956 ada 1 | `[AL, HE, GR, WH]` |
| 3 | Geçmiş | Greeting | `[GR, AL, HE, WH]` |
| 4 | Geçmiş | Pavo Saglik Takibi | `[PA, GR, AL, HE, WH]` ← **5 hücre** |
| 5 | Geçmiş | hermes-ustasi-devriye | `[HE, PA, GR, AL, WH]` |

Her adımda yalnız yeni açılan öne geçti; **hiçbir açık oturum düşmedi**. Adım 4/5'te
"Geçmiş"ten açılan oturumlar sunucunun açık listesinde olmadığı hâlde ray'da kaldı
(uygulama içi "son açılanlar" birleşimi çalışıyor). Oturum sayısı ölçüm sırasında
sunucuda dalgalandı (adım 1-2'de Canlı sayacı "4 açık oturum" okundu; Geçmiş'ten
açılanlar sunucu listesine girmeden de ray'da kaldı) — beklenen davranış bu.

Kanıt görüntüleri: `SONRA-ray-1-4hucre.png`, `SONRA-ray-4-pavo.png`,
`SONRA-ray-5-hermes.png`, `SONRA-ray-ozet.json`.

Etkileşim doğrulaması:
- **Seçme:** ray'daki `AL` hücresine dokunuldu → sohbet o oturuma geçti (Altınkale
  içeriği geldi, ray sırası `AL` en üste geçti) — `SONRA-ray-secim.png`.
- **Kapatma:** `WH` hücresine uzun basıldı → ray'dan indi — `SONRA-ray-kapatma.png`.
  (Yeniden açılışta geri gelmesi birim testle kilitlendi; sahada o oturum sunucu
  tarafından kapatıldığı için E2E'de tekrarlanamadı — bkz. "Dürüst kalanlar".)

---

## SORUN 2 — Klavye açılınca düzen bozuluyor

### 2.1 Ölçüm (ÖNCE) — kök neden tahmin değil, ölçüm

`dumpsys window` (ÖNCE, `ONCE-pencere-dokumu.txt`):
- Uygulama penceresi **yeniden boyutlanmıyor**: `frame=[0,0][1080,2400]` (edge-to-edge).
- Klavye penceresi dokunma bölgesi: `SkRegion((0,1517,1080,2400))` → **klavye üst kenarı y=1517**.
- Composer (EditText) `[268,1076][906,1223]`.

Yani composer klavyenin **294 px = 112 dp** yukarısında asılı kalıyordu; arada görünür
hiçbir öğe yok (ölü boşluk). Sohbet alanı da aynı miktarda kısalıyordu → "metin
yutulmuş, alt satırlar görünmez".

Kök neden: `Scaffold` alt çubuğu (NavigationBar + sistem çubuğu ≈ 322 px) içerik
kutusuna **padding** olarak uygulanıyor; içerideki `ChatScreen` `Modifier.imePadding()`
ise klavye insetini TAM yükseklik olarak bir kez daha ekliyordu → inset **iki kez**
sayılıyordu. (Klavye açıkken alt çubuk zaten klavyenin arkasında kalıyor, yani
rezerve edilen alan boşa gidiyordu.)

### 2.2 Düzeltme

1. **Çift inset (asıl neden).** `MainActivity`: içerik kutusuna
   `.consumeWindowInsets(innerPadding)` eklendi. Artık içerideki `imePadding()`
   hesaplaması "klavye − alt çubuk" kadar; composer klavyenin tam üstüne oturuyor.
2. **İçerik kesilmesi.** `ChatScreen`: kullanıcı **niyeti** ayrı tutuluyor
   (`userPinnedBottom`; yalnız kullanıcı kaydırırken güncellenir). Görünür alan
   yüksekliği değişince (`snapshotFlow { listState.layoutInfo.viewportSize.height }`)
   kullanıcı dipteyse liste yeniden dibe yaslanıyor → klavye açılınca son satırlar
   görünür kalıyor; yukarıda okuyan kullanıcının konumu **bozulmuyor**.
   Karar saf fonksiyonda (`shouldPinToBottom`) ve testli.
3. **Öneri/ikon blokları kararı: GİZLE.** Klavye açıkken bot (profil) çip satırı
   gizleniyor (~36 dp mesaj alanına kalıyor); bot seçimi oturum başında yapılan bir
   karardır, yazarken gereksiz. Hız satırı (akış göstergesi) ve composer'ın kendi
   ek/gönder ikonları yerinde kalır; "/" komut önerisi listesi de composer'ın parçası
   olduğu için gizlenmez (yazarken gerekli). Klavye kapanınca çipler aynı yerine döner.

### 2.3 Ölçüm (SONRA) — aynı yöntemle

| Ölçüm | ÖNCE | SONRA |
|---|---|---|
| Composer (EditText) | `[268,1076][906,1223]` | `[268,1349][906,1496]` |
| Klavye üst kenarı | 1517 | 1517 (değişmedi) |
| **Aradaki boşluk** | **294 px = 112 dp** | **21 px = 8 dp** (composer'ın kendi alt dolgusu) |
| Klavye açıkken son mesaj | görünür alan 122 dp kısa | son mesajın alt kenarı 1338, composer üstü 1349 → **11 px aralık** (kesilme yok) |

Ekran görüntüsü kanıtı: `ONCE-klavye-bosluk.png` ↔ `SONRA-klavye-bosluk.png`
(görsel modelle de okundu: "composer ile klavye arasında belirgin boşluk yok,
composer klavyenin hemen üstünde").
Konum koruma ölçümü (kb2 C/D): yukarı kaydırılmışken klavye açıldı → ilk görünür
mesaj aynı kaldı (`KONUM KORUNDU: True`).

---

## 3. Derleme / test / kurulum

| Kontrol | Sonuç |
|---|---|
| `gradle testDebugUnitTest assembleDebug` | **BUILD SUCCESSFUL** |
| Birim test sayısı (JUnit XML'den sayıldı) | **344 test, 0 hata, 0 atlanan** (tur-8 öncesi 325 → +19) |
| Yeni testler | `SessionRailTur8Test` 18 + `ChatScrollTest` (dibe yaslama) 1 |
| APK | `app/build/outputs/apk/debug/app-debug.apk` · 24.713.050 bayt · md5 `4a70f3c44fc2805412cd39515cfb3b0f` |
| Kurulum | `adb install -r` → **Success** (emülatör) |
| Çökme tamponu | `adb logcat -d -b crash` → **boş** |
| Canlı relay | dokunulmadı (yalnız emülatör; köprü `agent=false`) |

Kanıt: `unit-test-sayimi.txt`.

---

## 4. Dürüst kalanlar

1. **Ray sayacı ray'dan küçük görünebilir (tasarım gereği).** "N açık oturum" sunucunun
   açık listesini sayar; ray ise ek olarak "Geçmiş"ten açılan (sunucuda açık olmayan)
   oturumları da taşır. 1.4'te 4 "açık"a karşılık rayda 5 hücre vardı. İstenirse
   rayın altına küçük bir "son açılanlar" ayracı eklenebilir.
2. **"Kapat → yeniden aç → geri gelir" E2E'si tamamlanamadı:** hedef oturum
   (hermes-ustasi-devriye) tam ölçüm anında sunucu tarafından kapatıldı; davranış
   birim testle kilitlendi, sahada tekrar denenmedi. Kapatma (uzun basma → hücre iner)
   sahada doğrulandı.
3. **Ray öğeleri uygulama yeniden başlatılınca sıfırlanır** (halka bellekte). Sunucunun
   açık listesi yine gösterilir; kalıcılık istenirse `AppSettings.lastSession` gibi
   diske yazılabilir (bu turda istenmedi).
4. **Kök olmayan kusur (bu turun kapsamı dışı):** açılışta çıkan "Önceki açılış bir
   çökmeyle kapandı…" şeridi üst kısmı kaplıyor ve `Oturumlar` ekranındaki
   Canlı/Tümü/Geçmiş sekmelerini **gizliyor** (ölçüldü: şerit y 152–393, sekmeler aynı
   bantta; şeride dokununca sekmeler açılıyor). Çökme şeridi bir kez kapatılınca sorun
   kalmıyor; kalıcı çözüm (şeridi başlığın altına almak/otomatik sönmesi) ayrı tur.
5. **Sürüş/AR modu ve diğer sekmeler** klavye inset değişikliğinden etkilenmez:
   `consumeWindowInsets` yalnız ana içerik kutusunda; `CameraScreen` ve tam ekran
   kipler Scaffold gövdesinde ayrı dallar.

---

## 5. Değişen / eklenen dosyalar

- `ui/SessionRailLogic.kt` (YENİ) — ray karar fonksiyonları (railEntries, pushRecent,
  markSeenLive, clearRailDismissal, openSessionCount, statusOf)
- `ui/SessionRail.kt` — birleşik ray, uzun basma ile kapatma, durum vurguları
- `ChatViewModel.kt` — `recentRail`, `railDismissed`, `observeLiveRail`, `dismissRailEntry`
- `LiveSessionsViewModel.kt` — `LiveState.fetched`
- `MainActivity.kt` — `consumeWindowInsets(innerPadding)` (klavye), ray bağlantıları
- `ui/ChatScreen.kt` — dibe yaslama (viewport ölçütlü), klavye açıkken çip satırını gizle
- `ui/LiveSessionsScreen.kt` — sayaç tek kaynaktan (`openSessionCount`)
- `test/SessionRailTur8Test.kt` (YENİ, 18 test), `test/ChatScrollTest.kt` (+1 test)
