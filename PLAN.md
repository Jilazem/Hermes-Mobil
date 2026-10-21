# V4 — Birleşik Hat PLAN.md (EMA + V3 en iyiler)

## 1020-192115 dar kapsam
Yalnız backend/bot_framework/UI-SPEC.md Android bağlantısı: liste/şablon/edit/sohbet/SAF.
Önceki araç bütçesi hatasına karşı erken BOT-ANDROID.md ve ayrık loglar.
Kabul: tokenlı gerçek HTTP backend, Android regresyon+compile+lint, XML sayımı ve emülatör UI dump. Gerçek model ile LocalResponder ayrılır; telefon/relay/Drive/push/restart yok. Nihai bağımsız QC ayrıca.

Katman: büyük

## Amaç

Hermes-Mobil'de V4 birleşik hat kurulacak: taban `feat/ema-unified-20261007` (c5c20f0),
yeni dal `feat/v4-birlesik`. Android Auto + sohbet ekranı EMA'dan korunacak; V3 hattının
(eski hat, `origin/feat/k16-patron-onceligi`) kanıtla üstün olduğu oturum yığını
(K13c birleştirme, K12b izolasyon, K10c silme, K16 yoğunluk düşüşü), K19 model listesi
düzeltmesi ve K29'un iki talebi (czip merge önerisi, oturumu yeniye taşıma) porta
edilecek. Ayrıca patron ek talebi (18.10, kart yorumu): **oturum açılış konumu** —
sohbet açıldığında en sona otomatik atlama yok, son okunan mesajdan sonrasına (ilk
okunmamış satıra) konumlanma (M8; V3 TUR-31 mekanizması taban, cihazda saklanan
son-okunan konumuyla; sohbet arayüzünün kendisi EMA'da kalır). Teslim: tüm birim testleri geçen bir dal + arm64 debug APK
(`05-GEICICI/2026-10-17-apk-v4/`) + MD5/sha256 + değişen dosya listesi. Paket adı
`com.hermes.mobile.v3` KALIR (build.gradle.kts'e dokunulmaz). Detay matris:
`V4-MERGE-MATRISI.md`.

## Kabul ölçütleri

1. `feat/v4-birlesik` dalında `./gradlew testDebugUnitTest` 0 fail ile biter; port edilen
   test sınıflarının tamamı (SessionMergeLogicTest, ChatIsolationTest, SessionDeleteLogicTest,
   K16BusyFailoverTest, K19 uyarlamaları) yeşildir ve EMA tabanındaki mevcut ~1069 testte
   regresyon yoktur (komut + sayısal özet kart yorumunda).
2. Oturum birleştirme UX'i çalışır: çekmecede çoklu seçim → "Birleştir…" → onay →
   `POST /api/czip/merge` → yeni oturum üst verisi; emülatör ekran kanıtı kart yorumunda.
3. "Oturumu yeniye taşı" akışı emülatörde ekran kanıtıyla gösterilir (tek kaynak merge +
   kaynak arşivle) ve kaynak oturumun ileti içeriği telefona inmez (SessionMergeLogic
   sözleşmesi; birim testiyle kilitli).
4. czip merge ÖNERİSİ kartı `GET /api/czip/packages` verisiyle gösterilir; mock gateway
   e2e kanıtı kart yorumunda.
5. Android Auto + sohbet ekranı (car/*, ChatScreen.kt) V4 diff'inde korunur; K18B rozeti
   dahil EMA davranışında regresyon yoktur (mevcut EMA test paketi yeşil kalarak kanıtlanır).
6. `app-arm64-v8a-debug.apk` üretilir; MD5 + sha256 + `apksigner verify` çıktısı kartta
   (komut çıktısı + dosya yolu).
7. cd-denetmen denetimi GEÇTİ verdict'i kart yorumunda.
8. Oturum açılış konumu (patron eki): oturum aç → ilk okunmamış mesaj görünür; okuyup
   çık → tekrar aç → kaldığı yerden devam (son-okunan konumu cihazda saklanır, mesaj
   sayısı değişince kaymaya masun — konum satır indeksiyle değil mesaj kimliği/zamanla
   tutulur); "En sona git" düğmesi/çipi her zaman erişilebilir. Saf kararlar birim
   testiyle kilitli (ChatScreenScrollTest).

## Kaynaklar

- RAPOR-K28.md (t_ce75b660 ekleri) — merge matrisi dayanağı, S1–S5.
- `origin/feat/k16-patron-onceligi` — V3 kaynak; commitler: 1205b64 (K13c), 70b54a5 (K12b),
  277732e (K10c), 7e22abe + e402536 (K16), fa0aa42 (K19).
- `origin/feat/k19-model-listesi` — fa0aa42 (t_79769ac7'yi süpersede eder).
- EMA tabanı: `feat/ema-unified-20261007` c5c20f0; EMA'da model seçici `ui/ModelPickerSheet.kt`.
- Worktree: `~/.hermes/kanban/workspaces/t_370402ca/v4` (kirli ana checkout'a dokunulmaz —
  ders 49dbdf74).
- plan_kapisi.py: /Volumes/EX/007-HERMES-M4-LIVE/00-SISTEM/super-uzman/plan_kapisi.py
- Canlı model adı doğrulama: yerel uç `/v1/models` (ad şüphesinde komutla doğrula).

## Adımlar

1. V4 dalını kur: `git worktree add v4 -b feat/v4-birlesik c5c20f0` ve matris + bu planı
   commit'le.
   Doğrulama: `git -C v4 status -sb` → `## feat/v4-birlesik`, temiz ağaç; plan_kapisi.py
   PLAN.md üzerinde exit 0 (GEÇTİ).

2. M1–M3 oturum yığını portu: saf dosyaları V3'ten al (SessionMergeLogic, ChatIsolation,
   SessionDeleteLogic + testleri); HermesClient'a `POST /api/czip/merge` ve
   `DELETE /api/sessions/{id}` ekle; EMA ChatViewModel olay işleyicisine ChatIsolation
   filtresi; SessionDrawer'a K13c çoklu seçim + Birleştir + Sil-onay akışını EMA'nın
   1205 satırlık drawer'ına entegre et; AppViewModel/MainActivity hunk'larını aktar.
   Doğrulama: `./gradlew :app:testDebugUnitTest --tests "*SessionMerge*"` vb. 0 fail;
   `git grep foreignEvent app/src` entegrasyon kanıtı.

3. M4 K16 yoğunluk failover portu: 7e22abe + e402536 mantığını EMA'nın
   ChatViewModel/HermesClient/LocalModelClient'ına elle aktar (429/502/503 → sıradaki
   adrese düşüş + sebep bildirimi; bildirim oturum açılışında kaybolmasın).
   Doğrulama: `K16BusyFailoverTest` (6 test) EMA mimarisinde 0 fail.

4. M5 K19 portu: LocalModelLogic'e boş-gövde→anlaşılır hata, `unconfiguredMessage`,
   retry/fallback çipi mantığı; EMA ModelPickerSheet'e sahte-çip-yok + Yeniden dene;
   DEFAULT_MODEL canlı `/v1/models` çıktısıyla doğrulanıp güncellenir.
   Doğrulama: K19 mantık testleri (EMA uyarlaması) 0 fail; canlı uçtan `curl /v1/models`
   çıktısı raporda.

5. M6–M7 K29 UX: czip katalog okuma (GET /api/czip/packages) + öneri kartı (ortak czip
   paketi olan oturumlara "birleştirelim mi?"); "Yeniye taşı" aksiyonu (tek kaynak merge +
   onaylı kaynak arşivle).
   Doğrulama: yeni saf-mantık testleri 0 fail; mock-gateway e2e emülatör ekran kanıtı
   (screencap yolu kartta).

6. M8 oturum açılış konumu: V3 TUR-31 mekanizmasını port et (seenRows/unreadRows +
   "sona git" yüzen buton, f7ee83f); EMA ChatScreen'e "ilk okunmamış satıra konumlan"
   davranışı ekle — son-okunan konumu oturum başına cihazda saklanır (mesaj kimliği/
   zaman damgası; satır indeksi değil, mesaj sayısı değişse de kaymaz), "En sona git"
   her zaman erişilebilir.
   Doğrulama: ChatScreenScrollTest (saf kararlar: ilk-okunmamış hedefi, sayaç, buton
   görünürlüğü) 0 fail; saklanan konum mesaj sayısı değişimine dayanıklılık testi.

7. Tam paket: `./gradlew testDebugUnitTest` (tümü) + `assembleDebug` arm64 APK.
   Doğrulama: test özeti "X tests, 0 failed" satırı; APK yolu
   `05-GEICICI/2026-10-17-apk-v4/`, `md5 -q` + `shasum -a 256` + `apksigner verify`
   çıktıları kart yorumuna yazılır.

8. Denetim: commit listesi + kanıtlarla cd-denetmen'e gönderim.
   Doğrulama: denetim GEÇTİ verdict'i kart yorumunda; bulgu varsa döngü: düzelt → 6'yı tekrarla.

## Riskler / Patron kararı

- R1 SessionDrawer/AppViewModel/MainActivity/ChatViewModel hem EMA hem V3'te değişti →
  cherry-pick çakışır; elle entegre şart (matris §4). K21/K22/K24/K25 SONRA V4'e merge
  edilecek; çakışma çıkarsa ÇÖZÜLMEZ, raporlanır (prompt kuralı).
- R2 `SessionDrawer.kt` hotspot: K21 swipe + K25 rename de aynı dosyada — kart yorumuna
  `hotspot:` notu düşülecek; merge sırası CEO'ya bırakılır.
- R3 DEFAULT_MODEL: K19 commit'i "GLM-5.3-Flash-EXL3" yazar; canlı uç adı farklıysa
  (bellek: deepseek-ai/DeepSeek-V4.1-Flash) ADIM 4'te canlı `/v1/models` ile doğrulanır —
  kör kopya yok.
- R4 Kapsam kararı (matris §3): Arena/Panel/Jarvis/K13a/K13b/K14 ve K13d bots mağazası
  V4'e gelmiyor (K18B süpersed + bağlı bileşen ağı). Patron bunlardan birini isterse ayrı
  kartla eklenir — bu kart bloklanmaz, karar matriste gerekçeli kayıtlı.
- R5 Emülatör kanıtı için mock gateway: canlı `hermes serve` tek-instance kilidi nedeniyle
  ikinci serve AÇILMAZ; 8198'e stdlib mock ws sunucusu (ders b77de80d kuralı) konur; adb
  reverse list + netstat ile bağlantı doğrulanır (ders 1b2961fa).
- R6 Ana checkout `feat/k21-swipe-jestleri`'nde ve KİRLİ (K21'in kayıtsız dosyaları);
  tüm iş worktree'da, ana ağaca dokunulmaz.
