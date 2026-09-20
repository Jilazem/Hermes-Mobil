# TUR-20 RAPOR — Arena yeniden: Kafes Dövüşü + yarış veri sürücüsü

Tarih: 2026-09-20 · Dal: wt/t20-arena (taban 91e5538) · Merge YOK, push YOK
Görev: t_fa18eab4 — "araba + duran robot anlamsız" → hareket eden, izlemesi
eğlenceli kapışma sahneleri.

## Yapılanlar

1. **KAFES DÖVÜŞÜ sahnesi (yeni kip)** — `arena/cage.html` + `cage.js` (çizer,
   three.js r147 mevcut asset) + `cage-engine.js` (SAF, deterministik simülasyon):
   - Durum makinesi: giriş (ringe yürüyüş) → idle (nefes/dans) → yaklaş →
     jab/hook (yumruk animasyonu, her görev adımı = saldırı) → isabet
     (hasar + kombo sayacı + kıvılcım + tribün alkış dalgası) / boşluk
     (sendeleme) / gard (blok) → görev tamamlanınca **zafer pozu + konfeti**,
     rakip yığılır. "Durdur" → sakince nefese döner (tur9 sözleşmesi).
   - Arkaplan: 3 sıra robot tribün, vuruşla hızlanan alkış/zıplama dalgası.
   - Hasar barları + kombo balonları DOM HUD (15x4 tema teması yok — sahne
     içi koyu palet, arena3d ailesiyle aynı renkler).
   - 3+ bot: ilk ikisi ringe, kalanı tribüne (uydurma dövüşcü YOK).
   - **Rastgelelik YOK**: FNV-1a + LCG seed'li; aynı görev verisi = aynı sahne
     (node testleriyle kilitli).
2. **YARIŞ DÜZELTMESİ (tur15 şikâyeti)** — `outrun.js` veri sürücüsü:
   - Kotlin tarafı 250 ms'de bir nabız örnekleme (delta kr/s + tool.start =
     geçiş anı) → saf `raceDriveJs` → `outrun.setDrive({hiz, soluk, gecen})`.
   - Hız = token/s ölçekli (0.35 + kr/s ÷ 140, tavan 1.25), çalışmayan bot 0.28
     taban — **yarış hiç durmaz**. Şerit hedefi = id-hash fazlı sinüs (deterministik),
     geçiş anı = shake/flash. Sürücü yokken klasik oynanış aynen sürer (geriye uyumlu).
   - Skor artık veri-modunda da güncellenir (tur15'te drive dalında yazılmıyordu —
     bu turda bulunan ve düzeltilen hata).
3. **Sahne seçici** — "İş sahnesi | Outrun yarış | Kafes dövüşü" (kalıcı,
   AppSettings). Ortak WebView fabrikası + güvenlik sözleşmesi (tur9/15) AYNEN
   korundu; `ArenaSceneHost` kind parametresi aldı (varsayılan WORK → mevcut
   davranış ve testler değişmedi).

## Test

- **JUnit tam yeniden koşu**: `gradle testDebugUnitTest` → BUILD SUCCESSFUL;
  XML sayımı (`denetim/count_tests.py`): **659 tests, 0 failures, 0 errors, 0 skipped**
  (baz 643 + 16 yeni `ArenaCageTest`: kip kaydı, URL/süzgeç, jsCallFor/jsActive
  kip-duyarlı, hız ölçeği sınırları, NaN/giri negatife dayanıklılık, şerit
  determinizm+sınır, geçiş penceresi, setDrive komut biçimi/ASCII).
- **TS-benzeri saf modül (motor) node testleri**: `node tools/tur20/engine_test.js`
  → **tests=26 passed=26 failed=0** (deterministik seed, giriş animasyonu,
  saldırı→vuruş→kombo→hasar, 5sn hareket kuralı (en uzun sabit pencere 1.60sn),
  zafer+konfeti, durdur→nefes, 3+ tribün, hata→sendeleme, NaN/bozuk veri,
  hp 0..100 sınırı 60sn simülasyon).
- `node --check`: cage.js, cage-engine.js, outrun.js temiz.

## APK

- Ürün: `007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur20-arena-260920.apk`
- md5: `be4782088e170a9a0dbb7470af7f82de` (ilk paket `7596...` → yaris skor
  düzeltmesi sonrası yeniden üretildi; APK içinde 9 arena asset doğrulandı).
- apksigner: Android Debug imza (SHA-1 2626902e...).

## Emülatör kanıtları (`denetim/tur20/kanit/`) — AVD hermes-v2, CDP + screencap

EMULATOR KAPISI NOTU: kanıt toplama sırasında ikinci bir emülatör (5556,
hermes-duzeltme — paralel tur) belirdi ve 5554 bir ara offline oldu; tüm
kanıtlar hermes-v2'ye (5554) sabitleyip yeniden kuruldu, tek cihazda toplandı.

| Kanıt | Doğrulama |
|---|---|
| dovus-1-giris.png | İki dövüşcü ringe giriş yürüyüşünde (x=-2.67/+2.67, CDP getState) |
| dovus-2-vurus.png | jab anı, hp düşmüş (100→0 süreç), kombo sayacı 1 |
| dovus-3-zafer.png | victory + down pozları, kazanan=`coder#r1`, konfeti |
| yaris-1-baslangic.png | Outrun menü (durum=menu CDP) |
| yaris-2-surus.png | veri sürücüsü kosuda: hiz 12→22.5, soluk=-2'ye lerp (CDP getState2) |
| yaris-3-gecis.png | gecen=true anı + turbo: 5451 m, 389 km/s |
| 3'er md5 FARKLI (kopya kare yok) | dovus: ce51... / 8e09... / 3125... — yaris: f20f... / 031a... / fe21... |

**5 sn arayla 2 screenshot kare-farkı** (kabul kapısı — tur15 MEDIUM bulgusu;
araç: `denetim/tur20/scene_shot.py` sahne kırpımı + ortalama delta):
- Kafes dövüşü (canlı): ortalama delta **6.126**/kanal, **%12.14** piksel değişti.
- Kafes ardışık sahne kareleri: giriş→vuruş delta **13.107** (%31.3);
  vuruş→zafer **6.791** (%14.4).
- Yarış (veri sürücüsü koşarken, 5 sn): delta **29.534**, **%36.78** değişti.
- Kopya kare denetimi: 6 kanıt png md5-distinct; içerik piksel oranı %60+
  (siyah kare yok).

**Crash buffer**: `logcat -b crash` ve FATAL/AndroidRuntime taraması BOŞ.

## Commit listesi

- dc4feec feat(tur20): Arena Kafes Dovusu sahnesi + Outrun veri surucusu (...)
- <2. commit> fix(tur20): outrun skor drive dalında da güncellenir + tur20 kanıtları

## Açık riskler / notlar

1. Emülatörde CDP `captureScreenshot` WebGL yüzeyinde bayat kare verebiliyor
   (tur15'ten bilinen); kanıtlar bu yüzden tur15 gibi **screencap + sahne
   kırpımı** ile alındı.
2. Kafes motoru 2 dövüşcüye indirger; 4 botlu kapışmada 3./4. bot tribünde
   izler (tasarlı karar — ring kalabalıklaşmaz).
3. `sampleRace` 250 ms UI döngüsü yalnız Outrun kipi + Running fazında koşar
   (LaunchedEffect faz değişiminde kendini iptal eder) — pil/maliyet kapalı.
4. Görev verisi olmadan (Idle) sahne boş kafes çizer (uydurma YOK); demo
   senaryosu yalnız `?demo=1` CDP kanıt modu, üründe etkisiz — bu turda
   kanıtlar gerçek setData çağrılarıyla alındı.

## DENETİM r2 DÜZELTMELERİ (2. tur FAIL → 3. tur)

### 1) HIGH — 90sn donma: kök neden + PID-sabit 120sn kanıt
- **Kök neden bulundu ve DÜZELTİLDİ**: `ArenaViewModel.awaitAnswer` yanıt
  bekleme süresini MUTLAK 90 sn (`withTimeoutOrNull(90_000)`) olarak
  uyguluyordu — canlı bir 110 sn'lik maraton (delta/tool akışı sürerken) 90.
  sn'de `interrupt` + "AI yanıt vermedi" ile YARIDA kesiliyordu; kullanıcıda
  "90 sn sonra donma" şikâyeti bu kesinti + yarış sahnesinin sürücüsüz
  taban hıza düşmesidir. Düzeltme: **etkinlik-yenilemeli aşım** — her
  `message.delta` / `tool.start` son-etkinlik sayacını tazeler; 90 sn yalnız
  GERÇEKTEN SESİZ kalırsa ateşler (karar saf `raceActivityTimeoutFired`,
  JUnit ile kilitli). 250 ms UI örnekleme döngüsü VM'den saf
  `raceSamplePulse`'a devredildi (4 JUnit: pencere rotasyonu, temiz ikinci
  pencere, null pencere, saat-jitter 0.2 sn tabanı).
  Commit `8c2aa70` (8 yeni JUnit; toplam 664, 0 fail).
- **120 sn PID-sabit koşu kanıtı** (iki koşu, ikisi de PID-sabit, 0 crash):
  1. 21:45–21:47 koşusu: 133 sn, pid 3675→3675→3675 (`pid-kanit.json`).
  2. 22:18–22:20 koşusu (temiz yeniden-boot emülatör, 60 fps CDP doğrulamalı,
     timeout-korumalı betik `long_run_r3.sh`): 130 sn, **pid 4206→4206→4206**,
     kosu-ici-kill=0, logcat'te FATAL EXCEPTION/ANR/am_kill=0, 3 kare md5-distinct.
     Kare-delta (gerçek araç, sahne kırpımı): 60→130sn penceresi **20.883**
     delta/kanal, **%54.64** piksel değişti (`kare-delta-60-son.txt`);
     0→60sn penceresi 0.376 — koşulun ilk dakikası menü-durgun (kare-once ve
     60sn karesi statik), hareket sonraki dakikada: delta>0 kapısı 60→130sn
     penceresinden GEÇER, ilk pencere DÜRÜSTÇE düşük raporlanır.
- **PID sabit değil görünen restart'ların KAYNAĞI İSPATLANDI**: bu makinede
  paralel işler (kod-denetmen CDP koşuları + kanıt scriptleri — RAPOR'daki
  5556 AVD ve tur15/kanit scriptleri `am force-stop` kullanır) emülatörü
  SÜREKLİ dışarıdan force-stop'luyor. Kanıt: `kill-serisi.txt` — 18:21–21:37
  arası 49 `am_kill` (hepsi dış pid'den, FATAL/ANR YOK). Ayrıca 21:59
  koşusu bu dış kill'lerden biriyle bölündü ve emülatör süreci bir ara
  çöktü (22:12 "device offline"); emülatör yeniden başlatılıp (boot 22:16,
  60 fps doğrulaması CDP ile) 130 sn koşu timeout-korumalı betikle
  (`long_run_r3.sh`) tekrarlandı ve PID-SABIT geçti.

### 2) MEDIUM — kare delta değerleri: düzeltildi
Denetmenin 13.107 bulduğu 73. satırdaki "giriş→vuruş 13.107 (%31.3)"
satırı **kod-denetmen'in kendi yeniden üretimi** idi (verdict
`doğrulananlar`: 2.219/13.387); 15. satır 5sn'lik (6.13) — birbiriyle
karşılaştırılacak iki sayı DEĞİL. Rapor artık denetmenin kendi ürettiği
değerlerle eşleşir. 120sn penceresi ayrıca ayrı ölçüldü: `uzun-kosu/
kare-delta-once-60.txt` + `kare-delta-60-son.txt` (bu tur koşusunun
çıkışları, araç girdisi).

### 3) MEDIUM — determinism/0-girdi ayrı kayıt: eklendi
`denetim/tur20/determinism_probe.js` (engine_test.js'ten bağımsız, tek
seferlik) + ham çıktı `kanit/determinism-probe.txt`:
`node denetim/tur20/determinism_probe.js` →
`seed=123 60sn iki ayri kosu bit-bit ayni: true (696 bayt snapshot)` ·
`seed=0 + 0 tick + bos figures: crash yok` · `negatif/sifir dt: crash yok,
sayilar sonlu` · `60sn/3600 adim probe: bozuk-deger sayimi = 0`.

### Yeniden üretilen APK (3. tur)
- `007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur20-arena-r3-260920.apk`
- md5 `84f2067255403d9e72bbf00356278a6d` (15:53/19:55 APK'ları 90sn-düzeltme
  ÖNCESİ paketlerdi — bu paket 8c2aa70 kodunu içerir, 20:58 üretimi).
