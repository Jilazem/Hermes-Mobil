# Tur-17 RAPOR — B1 ışık şeması dallanması + B2 aksan ayrımı

Tarih: 2026-09-19 · Uzman: @android · Kart: t_01e87e0e
Dal: `feat/tur17-tasarim-b1b2` (main bb375d9 üzerinde) — MAIN'E MERGE YOK, PUSH YOK.
Tasarım kaynağı: `/Users/gokhanuzman/007-HERMES/04-ARASTIRMA/hermes-mobil-tasarim/TASARIM-RAPORU.md` §3.1 + §4 B1/B2.

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
