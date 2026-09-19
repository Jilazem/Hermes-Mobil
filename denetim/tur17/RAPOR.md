# Tur-17 RAPOR — B1 ışık şeması dallanması + B2 aksan ayrımı

Tarih: 2026-09-19 · Uzman: @android · Kart: t_01e87e0e
Dal: `feat/tur17-tasarim-b1b2` (main bb375d9 üzerinde) — MAIN'E MERGE YOK, PUSH YOK.
Tasarım kaynağı: `/Users/gokhanuzman/007-HERMES/04-ARASTIRMA/hermes-mobil-tasarim/TASARIM-RAPORU.md` §3.1 + §4 B1/B2.
**Düzeltme turu (denetim 1/3 FAIL sonrası): bu raporun altındaki "DENETİM 1/3 DÜZELTMELERİ" bölümü geçerlidir — 2. APK md5 ve yeni kanıtlar orada.**

## Bulunan
- Theme.kt L58 tek `darkColorScheme()` — nous (açık) seçiliyken Material3 bileşenleri koyu default'larla çiziliyordu (B1).
- hermes preset'inde accent = textPrimary = #FFE6CB (B2 çekirdek ihlali); midnight/ember/mono/cyberpunk'ta da aksan-metin ton kardeşliği.
- 13 semantik rol proxy mimarisi (CompositionLocal) korunarak 6 yeni rol eklenebilir durumda.

## Yapılan
1. **ThemeLogic.kt (YENİ — saf, Compose'suz, birim testli; SessionDrawerLogic kalıbı):**
   `hexToArgb`, `relativeLuminance` (WCAG), `contrastRatio`, `resolveIsLight(mark, bgArgb)`
   (işaret yoksa luminans, eşik 0.20), `onColorFor(fill,bg)` (dolgulu CTA üstü metin:
   koyu zemin vs beyaz — AA'ya göre kazanan), `argbWithAlpha`, `mixArgb`, `isAccentDistinct`.
2. **FR-001 B1 — Theme.kt:** tek darkColorScheme kırıldı; `palette.lightTheme()`
   (isLight işareti → yoksa luminans) `lightColorScheme`/`darkColorScheme` dallanması
   yapıyor. nous seçiliyken DropdownMenu/TextField/dialog/ripple artık koyu default'suz.
   Çağdaşı: enableEdgeToEdge + WindowCompat — açık temada durum/gezinme çubuğu
   ikonları koyuya çevriliyor (SideEffect, isLight'a bağlı).
3. **FR-002 B2 — Themes.kt preset'leri** (piksel davranışı YALNIZ B2 preset'lerinde değişti, FR-003):
   - hermes → §3.1 "Hermes Teal 2.0" birebir: bg #04171A, surface #0A2327, surfaceDim #071D21,
     border #1B3A3D, borderStrong #275255, **accent #4FD8C0**, text #F2EFE6/#C4D3D0/#7F9A97/#5A7773.
   - midnight #A99CFF→#8C7CFF · ember #FF9A4D→#E06A1F · mono #EAEAEA→#B8C2C6 (soğuk ton) ·
     cyberpunk #00FF41→#00BFFF (yeşil-üzeri-yeşil ton kilitlenmesi; camgille 1.55x ayrım) ·
     slate #58A6FF (zaten ayrık, değişmedi) ·
     **nous → §3.1 ACIK set + accent #0E836C** — sapma notu: §3.1 #0F8C74 "beyaz üstünde 4.6:1"
     iddiası programatik ölçümde 4.18:1 (AA altı); aynı hue ailesinden #0E836C seçildi
     (beyaza 4.69:1, zemine 4.48:1). Testte kilitli.
   - Dolgulu CTA üstü metin = on-accent: koyu aksanlarda koyu zemin (hermes 10.4:1),
     nous'ta beyaz (4.69:1) — `onColorFor` hesabı, ChatScreen "Onayla" dolgulu CTA'sı
     `OnAccent`'a bağlandı; Material3 onPrimary de artık on-accent.
4. **6 yeni semantik rol** (FR-003 kilidine uyumlu): HermesColorScheme'e VARSAYILANLI
   parametreler olarak sonda: `surfaceCard, surfaceOverlay, focus, onAccent, skeleton,
   bubbleUser`; HermesColors proxy'sine karşılıkları. 13 mevcut rolün adı/imzası değişmedi;
   ekrandaki 13 rol kullanımı aynen çalışıyor, yeni roller sonraki turlara (B4 iskelet,
   B8 focus halkası) hazır.
5. **Skin uyumluluğu:** HermesPalette'e tek yeni alan `isLight: Boolean? = null`
   (varsayılanlı → eski skin yaml/JSON deserialize edilir); kullanıcı skin'lerinin
   hex'lerine dokunulmaz, B2 kuralı yalnız 7 BUILTIN preset'e dayatılır.

## Yapılmayan / ATLANDI (FR-007 kapsamında)
- B3 typography, B4 HermesCard, B5+ içerik değişiklikleri — bu turun dışında (talimat).
- §3.1'in yeni 6 rolünün tam §3.1 hex'leri preset-bağımsız TÜRETİLDİ (overlay=bg@%70,
  skeleton=surfaceDim↔border karışımı, focus=accent, onAccent=onColorFor, bubbleUser=surface,
  surfaceCard=surface). Gerekçe: preset'lerde hex alanı açmak 13 rol API'sini ve skin
  şemasını büyütürdü; tasarım "rol adı netleşir, preset'ler kendi hex'inde kalır" diyor.
  Ekranda henüz kullanan yok (hazır altyapı) — piksel etkisi YOK, B3/B4 turunda bağlanacak.
- Nous'un koyu-beyaz sistem çubuğu davranışı yalnız Activity penceresinde uygulanır
  (Compose-only diyaloğu yok) — MainActivity tek Activity olduğu için pratik etki yok.

## Kanıt (komut + çıktı + yol)
- **FR-006 test:** `gradle testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL in 34s`;
  XML sayımı (tüm test-results): **tests=639 failures=0 errors=0 skipped=0**
  (tur16 tabanı 622 + ThemeTur17Test 17 yeni). Log: `denetim/tur17/build-full.log`.
  İlk tur 2 fail verdi ve gerçek bulguyu düzeltti: cyberpunk yeşil-aksan ton kilitlenmesi
  (→ camgip aksan #00BFFF) + test bekleyiş hatası (argbWithAlpha 0 alfa) —
  log: `denetim/tur17/build1.log`.
- **FR-006 APK:** `app/build/outputs/apk/debug/app-debug.apk` →
  `/Users/gokhanuzman/007-HERMES/000-TEMP/hermes-mobile-tur17-tasarim-260919-1104.apk`
  (24.393.489 B, **md5 84c3177b27d27d93ffecb48635f6d4f1**); `adb install -r` → `Success`.
- **FR-005 görsel kanıt (emülatör hermes-v2, paket v2)** — `denetim/tur17/kanit/`:
  1. `01-acik-tema-sohbet.png` — açık tema Sohbet boş durum (§3.1 nous 2.0 #F8FAFB).
  2. `02-acik-tema-ayarlar-tema-seridi.png` — Ayarlar/Görünüm açık; tema şeridi, nous seçili (yeşil çerçeve).
  3. `03-koyu-tema-sohbet-bos-CTA-dolgulu.png` — koyu tema (yeni hermes 2.0); dolgulu
     "Sunucu ekle" CTA'sı mint zemin + koyu on-accent yazı; başlık beyaz → **B2 ayrımı ekranda**.
     (Pikot: ekran zemini #04171A, başlık #F2EFE6 ailesi — PIL sayımı.)
  4. `06-acik-tema-sunucular-form.png` — açık tema Sunucular/sunucu-ekle dialog:
     zemin beyaz, focus/aksan kontörü #0E836C (pikot: 81× #0E836C, 181× #FFFFFF).
     Ekstra: `04-acik-tema-ayarlar-gorunum.png`, `05-acik-tema-oturum-cekmecesi.png`.
  Görsel servisi tur sırasında Gemini 404 verdi (DERSLER D-02 kalıbı) — doğrulama
  PIL pikot okuması + uiautomator metin dökümüyle çift kaynaklı yapıldı.
- **B1 canlı kanıtı:** nous seçiliyken Ayarlar listesi, tema şeridi, sohbet ve dialog
  beyaz zeminle çizildi (pikot #F8FAFB, 93/101); koyuya dönüşte #04171A ölçüldü.

## Değişen dosyalar
- app/src/main/java/com/hermes/mobile/ui/theme/ThemeLogic.kt (YENİ)
- app/src/main/java/com/hermes/mobile/ui/theme/Themes.kt (6 rol + isLight + 7 preset)
- app/src/main/java/com/hermes/mobile/ui/theme/Theme.kt (ışık dallanma + 6 proxy rol + sistem çubuğu)
- app/src/main/java/com/hermes/mobile/ui/ChatScreen.kt (Onayla CTA metni → OnAccent)
- app/src/test/java/com/hermes/mobile/ThemeTur17Test.kt (YENİ, 17 test)
- denetim/tur17/ (log + kanit/)

## Açık riskler
- cyberpunk "deneysel" etiketiyle yaşar (metin neon yeşil, AA'sız — tasarım onaylı istisna).
- on-accent koyu ailesi 0x04171A sabiti yerine paletin kendi koyu yüzeyinden türetilebilir
  (şu an onColorFor koyu aday olarak siyah↔bg kullanıyor); B4 kart turunda netleşir.
- 6 yeni rol ekranda henüz kullanılmıyor — B3/B4 köprüsü gelecek turda.

## Denetmene not
- FR-004 (a)/(b)/(c) üçü de ThemeTur17Test'te; §3.1 değer sapması YALNIZ nous accent'i
  (#0F8C74→#0E836C, AA zorunluluğu, kod+yorum+test üçgeninde belgeli).
- FR-003 API kilidi: 13 rol adı/imzası sabit — testte preset hex birebir kilidi +
  skin deserialize testiyle desteklendi.

---

# DENETİM 1/3 DÜZELTMELERİ (2. tur, 2026-09-19 akşam)

Denetim verdict FAIL (denetim/verdict-tur17.json, commit b8500e6) — 3 madde değişti,
2 LOW madde notlandırıldı. Testler ve B1/gecikler denetmenin kendi koşumuyla sağlam
bulunmuştu; yalnız B2 4. sütun + kanıt dosyaları + kopya görüntüler işlenmiştir.

## 1. HIGH B2-CTA — 4. sütun (CTA metin/dolgu) 15/15 AA üstüne taşındı
- Kök neden (kod okumasıyla): preset'lerde `onAccent` hardcode DEĞİLDİ — 85f2e25'te
  `onAccent = Color(onColorFor(accentArgb, bgArgb))` iki ADAYDAN iyisini seçiyordu
  (koyu zemin ailesi vs beyaz). Ancak 2-aday seçici, her iki aday da AA altında kalan
  dolguları (parlak/açık dolgulu custom skin senaryosu) kurtaramıyordu; seçilen
  "kötülerin iyisi" 4.5'in altında kalabiliyordu (ör. soluk dolguya beyaz 1.82).
- Düzeltme: `ThemeLogic.resolveOnAccent(fill, bg)` (YENİ, saf, testli) — taban aday
  AA'yı geçiyorsa DOKUNMAZ (7 BUILTIN'de taban zaten 4.69–10.64 → piksel regresyonu
  YOK, test kilitli: `resolveOnAccent tabanini korur`). Taban AA altındaysa kazanan
  aday siyaha 1/20–20/20 adımlarla karıştırılıp AA'yı geçen ilk TON döner (monoton
  koyulaşma ⇒ kontrast artar; çözülemezse taban döner + test patlar, sessizlik yok).
- `toColors()` artık `resolveOnAccent` çağırır → customThemes/skin akışından gelen
  her kullanıcı paleti (15 tema) CTA'da AA görür. ChatScreen "Onayla" tek tüketici
  yolu değişmedi — rol değeri düzeldi.
- Ölçüm (4. sütun, resolveOnAccent sonrası): hermes 10.43, midnight 6.07, ember 5.85,
  mono 10.64, cyberpunk 9.48, slate 7.49, nous 4.69 + 8 fixture 5.74–11.88 → 15/15 ≥ 4.5.
- İddia düzeltmesi (dürüstlük): denetmenin "6 sunucu-preset onAccent='#FFFFFF'
  hardcode ediyor" ve copper/rose/amber adlı preset iddiaları repo kodunda
  DOĞRULANAMADI — BUILTIN_THEMES'te 7 preset var, hiçbiri #FFFFFF hardcode taşımıyor,
  copper/rose/amber adları repoda grep 0 (repo-wide + git log -S). 15x4 koleksiyonu
  kodda yok; fixture seti (customThemes/skin senaryosu, Desktop presets.ts
  değerlerinden) ile 15 satır kuruldu ve 4. sütun KODDA GERÇEKTEN 2-aday limitiyle
  AA altına düşebiliyordu — düzeltme bu gerçek kusura yapılır, tespit ifadesi değil.

## 2. HIGH B2-matris — 15x4 dosyaya yazıldı
- `denetim/tur17/15x4-matris-ciktisi.txt` artık TEST ÜRETİMİDİR (ThemeTur17Test
  `15x4 kontrast matrisi...` testi her koşuda dosyayı yazar; 4 sütun + onAccent hex
  + OK/KALDI + resolver-kurtardı işaretli). Manuel elle-yazım değil, testle senkron.
- 15 satırın kaynağı şeffaf: 7 BUILTIN + 8 fixture (fx-*; customThemes/skin akışı
  senaryosu — Desktop preset hex'lerinden türetildi, fixture oldukları dosyada ve
  test yorumunda yazılı).

## 3. MEDIUM GÖRÜNTÜ — kopyalar temizlendi, 2 gerçek Ayarlar teması çekildi
- Kendi ölçümüm (md5, bu iş ağacı): gerçek çift **02 ≡ 04** (ikisi de 547a23b5 —
  02 "Ayarlar tema seridi" konusu 04 "Görünüm" ekranının aynısıydı). Denetmenin
  "02, 01'in kopyası" iddiası ölçümle yanlış (01=0104343c ≠ 547a23b5); "05, 04'ün
  kopyası (48d28254)" de ölçümle DOĞRULANAMADI (05=fde59130, 48d28254 hiçbir
  dosyada yok). Tek gerçek ihlal 02→04 çiftiydi.
- Silinen: 02 (kopya). Konusu yeni 07 ile ayrıca, 04 tek olarak kaldı.
- Yeni gerçek görüntüler (adb screencap, her biri benzersiz md5, PIL pikotlu):
  - `07-ayarlar-tema-seridi-acik-nous-secili.png` — Görünüm ekranı AÇIK (nous);
    şeritte koyu preset kartları (#160800/#08081C/#04171A/#0E0E0E) koyu görsel olarak
    seçili; UIM dump 'Nous (açık)' görünür.
  - `08-ayarlar-tema-seridi-koyu-hermes-secili.png` — aynı ekran hermes'e geçince
    tüm govde #04171A/#0A2327 + aksan #4FD8C0 ile yeniden çizildi (tema şeridi
    önce/sonra çifti GERÇEKTEN iki farklı tema durumunda).
  - Ekstra: `09-koyu-tema-sohbet-CTA-dolgulu-onAccent-koyu.png` — koyu sohbet,
    dolgulu CTA (baskın #4FD8C0 dolgu; denetmen tur17'deki 03 ile çift yok).

## 4. LOW ROL-TUKETICI — 2 rol artık main'de gerçek tüketici buldu
- `HermesColors.BubbleUser`: ChatScreen kullanıcı balonu zemini (L~1063) —
  varsayılan surface ile PİKSEL AYNI, semantik bağ kuruldu.
- 3 rol (SurfaceCard/SurfaceOverlay/Focus/Skeleton) YALNIZ ALTYAPI olarak kaldı;
  tüketici B4 (HermesCard) / B7-B8 turuna planlı — önceki sürümde '5 rol altyapı'dı,
  artık 4.

## 5. LOW MIDGROUND (Markdown.kt L202) — kapsam dışı
- Denetmenin kendi notu gibi ayrı iş kartı; bu turda dokunulmadı.

## Kanıt (bu tur)
- `gradle testDebugUnitTest assembleDebug --rerun-tasks` → BUILD SUCCESSFUL
  (denetim/tur17/build-final.log, GRADLE_EXIT=0); XML sayımı: **tests=643
  failures=0 errors=0 skipped=0** (639 taban + 4 yeni matris/resolver testi).
  Ara koşu: build3-matrix.log ThemeTur17Test 21/0 (yalnız sınıf, --rerun).
- APK: `000-TEMP/hermes-mobile-tur17-tasarim-260919-2224.apk`
  24.362.036 B, **md5 cfb06cd924cf663c38283dcf49eaed8d**;
  `adb install -r` → Success (kurulu, 07–09 görüntüleri bu sürümden).
- 15x4 matris: `denetim/tur17/15x4-matris-ciktisi.txt` (SONUC: 15/15).
- Kopya-md5 taraması kanit/: 0 çift (md5 | uniq -d boş).

## Değişen dosyalar (bu tur)
- ThemeLogic.kt (+WCAG_AA_NORMAL_TEXT, +resolveOnAccent)
- Themes.kt (toColors → resolveOnAccent)
- ChatScreen.kt (kullanıcı balonu → BubbleUser)
- ThemeTur17Test.kt (+4 test: onColorFor taban, resolver-koruma, resolver-kurtarma,
  15x4 matris + dosya üretimi; fixture tablosu)
- denetim/tur17/ (RAPOR güncel, 15x4 matris dosyası, kanit/ 07–09, 02 silindi,
  build2/build3-matrix/build-final.log)

## Açık riskler (bu tur)
- fx-* fixture satırları mobilde KOD OLARAK var olmayan skin senaryolarını temsil
  eder; gerçek cihaz custom-themes testi yapılamadı (prefs şifreli, run-as ile
  okunamıyor). Resolver + 15/15 matris + kurtarma birim testi bu boşluğu kapatır.
