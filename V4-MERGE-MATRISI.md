# V4 — MERGE MATRİSİ (modül → kaynak)

**Tarih:** 2026-10-18 · **Uzman:** cd-android · **Kart:** t_370402ca
**Taban:** `feat/ema-unified-20261007` (c5c20f0) · **Hedef dal:** `feat/v4-birlesik`
**Karşılaştırma:** EMA=c5c20f0 ↔ V3=`origin/feat/k16-patron-onceligi`, merge-base **5bd04e9**
**Kanıt yöntemi:** `git merge-base`, `git diff --name-only 5bd04e9 <dal>` (EMA 195 / V3 153 dosya), ortak-değişen 25 dosya (`comm -12`), salt-V3 app/src listesi (`comm -23`), `git grep`/`git show` içerik kanıtları; K28 RAPOR-K28.md S3–S5.

## 1. Kapsam İÇİ — V3'ten port edilecekler (V3 üstün, EMA'da kanıtla YOK)

| # | Modül | V3 kaynak (kanıt) | EMA durum kanıtı | Port yöntemi |
|---|---|---|---|---|
| M1 | Oturum birleştirme (K13c) | `data/SessionMergeLogic.kt` (118 satır, saf), `SessionMergeLogicTest` (129 satır, 8 test), `ui/SessionDrawer.kt` merge akışı (1205b64, +232), `AppViewModel` +48, `HermesClient` +11 (`POST /api/czip/merge`), `MainActivity` +8 | `git cat-file -e EMA:...SessionMergeLogic.kt` → YOK; EMA SessionDrawer'da "merge/birleştir" grep → BOŞ | Saf dosya olduğu gibi alınır; drawer/AppViewModel/MainActivity/HermesClient EMA sürümüne ELLE entegre (çakışan 4 dosya ortak-değişen listede) |
| M2 | Oturum izolasyonu (K12b) | `data/ChatIsolation.kt` (24 satır, saf) + `ChatIsolationTest` + ChatViewModel `foreignEvent` filtresi (70b54a5) | EMA ChatViewModel grep `session_id\|ChatIsolation\|foreign` → YOK (yalnız yorum); ChatIsolation YOK | Saf dosya eklenir; EMA ChatViewModel'in (base'den +249/−113 değişmiş) olay işleyicisine filtre ELLE entegre |
| M3 | Oturum silme (K10c) | `data/SessionDeleteLogic.kt` (29 satır, saf) + `SessionDeleteLogicTest` + `DELETE /api/sessions/{id}` (277732e) | SessionDeleteLogic YOK; EMA HermesClient'ta DELETE grep → YOK | Saf dosya + endpoint + EMA drawer silme akışına bağlama |
| M4 | Yoğunluk düşüşü + bildirim (K16) | 7e22abe (ChatViewModel +38, HermesClient +58, LocalModelClient +32, `K16BusyFailoverTest` 6 test) + e402536 (ChatViewModel +32: bildirim oturum açılışında kaybolmasın) | EMA ChatViewModel grep `429\|502\|503\|failover` → YOK | EMA ChatViewModel/HermesClient/LocalModelClient'a ELLE (üç dosya EMA'da da değişmiş; cherry-pick çakışır) |
| M5 | K19 model listesi (fa0aa42) | LocalModelLogic: `emptyModelListMessage`, `unconfiguredMessage`, boş-gövde→anlaşılır hata, retry/fallback çipi, DEFAULT_MODEL (canlı `/v1/models` ile) + `BrainPickerTest`/`LiveModelChoiceTest` | EMA'da seçici `ui/ModelPickerSheet.kt`; BrainPickerSheet EMA'da YOK (supersede spec t_79769ac7) | K19 MANTIĞI LocalModelClient'a + EMA ModelPickerSheet'e ELLE; testler EMA mimarisine uyarlanır; DEFAULT_MODEL canlı uçtan doğrulanır |
| M6 | K29-a: czip merge ÖNERİSİ | Taban: V3 `CzipGateway.kt`/`CzipLogic.kt`/`CzipLogicTest` + HermesClient `czipCatalog()` (GET /api/czip/packages) | EMA'da czip önerisi YOK (K28 S5-1: "YOK") | V3 saf mantığı alınır; öneri kartı SessionDrawer'a eklenir (yeni UI) |
| M7 | K29-b: oturumu yeniye taşıma | SessionMergeLogic sözleşmesi ("kaynaklara dokunulmaz") + SessionFlagsStore | EMA'da taşıma YOK (K28 S5-2) | Tek-kaynak merge (`sources=[sid]`) → yeni oturum; onaylı kaynak arşivle/pasife al (yerel bayrak); sunucu sözleşmesi korunur |

## 2. Kapsam İÇİ — EMA'da kalanlar (patron kararı: "Android Auto + sohbet ekranı EMA")

| Modül | Karar | Gerekçe |
|---|---|---|
| Android Auto (car/HermesCarService, CarSpeechLogic, CarVoiceSession) | **EMA** | Patron 17.10 kararı; salt-V3 `CarChatController/Screen/MicSession/CarChatBridge` EMA'da CarVoiceSession ile DEĞİŞTİRİLDİ — geri port etme regresyon olur (K28 S4 Not) |
| Sohbet ekranı (ChatScreen + bot rozeti K18B-B + onay kutusu K18B-G3) | **EMA** | Patron kararı; K18B zinciri yalnız EMA'da (c5c20f0) |
| Bot kimlikleri (K18B-G1 BotRoster) | **EMA** | V3'ün K13d Bots mağazası/mağaza ekranını SÜPERSEDE eder (BotRoster/BotRosterStore EMA'da; denetimli) |
| Paket kimliği | dokunulmaz | `com.hermes.mobile.v3` debug suffix `.v3` her iki hatta aynı — build.gradle.kts'e DOKUNULMAZ |

## 3. Kapsam DIŞI — V4'e bu kartta gelmeyecekler (gerekçe kaydı)

| Modül (yalnız V3'te evrilmiş) | Karar | Gerekçe |
|---|---|---|
| Arena ekranı evrimi, Panel ekranı evrimi, Jarvis, SubagentTree/K14, İzin Merkezi K13a, VoicePickers K13b, HomeFeed evrimi | **EMA (base) kalır** | Bağımsız özellik hatları; kendi ViewModel/web'leriyle (ArenaViewModel, BotsViewModel, PanelViewModel…) birbirine bağlı — tek tek çekmek ölü kod/çakışma üretir. K28 S4 "kullanışlı" listesi bunları içermiyor. İstenirse ayrı kart |
| BotsScreen / BotStoreView / BotWizardSheet (K13d) | **PORT EDİLMEZ** | EMA K18B BotRoster süpersed (bkz. Bölüm 2) |
| CzipSheet/CzipLogic (V3 UI) | Kısmen: saf mantık M6'da alınır | UI V3'e özgü; öneri kartı EMA drawer'ına yazılır |

> **Patron geçersiz kılma notu:** Bölüm 3'teki modüllerden V4'e alınmasını isteyen olursa
> ayrı kartla eklenir; bu kart birleştirme disiplinini bozmaz (K21/K22/K24/K25 merge'leri
> SONRA feat/v4-birlesik'e gelecek — çakışma çıkarsa RAPORLANIR, kendiliğinden çözülmez).

## 4. Çakışma haritası (ortak-değişen 25 dosyadan port edilenler)

`AppViewModel.kt`, `MainActivity.kt`, `ChatViewModel.kt`, `HermesClient.kt`, `SessionDrawer.kt` —
bu 5 dosyada hem EMA hem V3 değişmiş; **hepsi ELLE entegre** edilir (cherry-pick YOK).
Diğer 20 ortak dosya (car/*, voice/*, manifest, gradle) **EMA sürümünde bırakılır** —
V3'teki değişiklikleri salt-V3 mimarisine bağlı (CarChat*, K13a/b), EMA üstünlük iddiası YOK.

Hotspot: `SessionDrawer.kt` (K21 swipe + K25 rename de aynı dosyada ayrı dallarda) —
V4'te merge akışı eklendikçe sonraki merge'ler çakışacaktır; kart yorumuna `hotspot:` notu düşülecek.
