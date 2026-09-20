# Tur-18 RAPOR — B3 tipografi 6-adım + B4 HermesCard + radius 4-adım

Tarih: 2026-09-20 · Uzman: @android · Kart: t_efe1f3f4
Dal: `feat/tur18-tipografi-kart` (taban 41755c3 = feat/tur17-tasarim-b1b2 HEAD) — MAIN'E MERGE YOK, PUSH YOK.
Tasarım kaynağı: `/Users/gokhanuzman/007-HERMES/04-ARASTIRMA/hermes-mobil-tasarim/TASARIM-RAPORU.md` §3.2/§3.3/§3.4/§3.5 + devir notu §5.

## Bulunan
- Ekranlarda 360 `fontSize = N.sp` literal (13 ayrı punto: 9..30sp) — fontScale yalnız 3
  Material tipografisine işliyor (B3: ~%90 metin sistem font ölçeğine kapalı).
- 47 manuel `lineHeight = N.sp` — rol ölçeklense bile bu override'lar sabit kalıp
  büyük yazıda kırpılma üretecekti (B3'ün ikinci yüzü; sınıf hatası).
- 117 `RoundedCornerShape(N.dp)` literal + 10 ham kalıntı (15 ayrı değer, B4).
- HermesCard zaten 22 yerde kullanımda (tur15-17 kalıbı) ama 12dp radius / 14dp iç boşluk /
  48dp tabanı yoktu — token'a bağlandı.
- Markdown.kt L202: kod bloğu vurgu çizgisi Midground (=aksan) — tur17 finding #5 burada
  çizgiydi, dolgu L193'te Background'du; ikisi de B2 kuralına göre ele alındı (FR-009).
- MonoTextStyle (16 dosya) sabit 12.sp idi — fontScale dışıydı.

## Yapılan
1. **FR-001 6-rollü tipografi (Theme.kt):** `hermesTypography(fontScale)` — micro 11 /
   caption 12 / body 14 / prose 15 / title 17 / display 22 sp; ağırlıklar 400/500
   (title/display Medium); satır aralığı tight 1.2 / body 1.43 / prose 1.45 → 15sp→21.75sp
   (tur3 22sp bandı KORUNDU — testte assertion). Map: micro→labelSmall, caption→bodySmall,
   body→bodyMedium, prose→bodyLarge (prose = bodyLarge override), title→titleMedium,
   display→headlineSmall. HermesTheme artık 6 rollü ölçeği + HermesShapes'i sarar.
2. **LocalFontScale + MonoTextStyle (FR-001):** settings.fontScale CompositionLocal olarak
   provide edilir; MonoTextStyle 12×fontScale + 1.2 satır aralığı taşır. Sabit-stil sınıfı
   hata kapanır (fontScale 0.85–1.4, kullanıcı Ayarlar→Görünüm'den).
3. **FR-002 ekran geçişi (30 dosya, ~360 literal):** tüm `fontSize = N.sp` →
   `style = MaterialTheme.typography.<rol>`; eşlenik **47 manuel `lineHeight = N.sp`
   override'ı silindi** (rol kendi lineHeight'ını taşır; silinmezse fontScale'da kırpılma —
   kök-neden sınıfı, semptom yaması değil). Mono+rol çakışan 6 blok
   `MonoTextStyle.copy(fontSize=rol.fontSize, lineHeight=rol.lineHeight)` ile birleştirildi.
   MarkdownText imzası: `fontSize` varsayılanı artık `bodyLarge.fontSize` (prose 15×scale).
4. **FR-003 radius 4 adım:** `HermesShapes` override — Material3 slot haritası:
   extraSmall=4, small=4, **medium=10**, **large=16**, extraLarge=16 (Material3'ün 5 slotu
   4 adıma eritildi); `HermesRadius` nesnesi sm4/md10/lg16/**full=999** dp sabitlerini verir
   (pill). 117+10 literal `MaterialTheme.shapes.*`'a map'lendi.
   Eski→yeni harita (RAPOR tablosu): 1dp→(kaldı: 1dp ızgara çizgisi, token-dışı küçük
   dekoratif ayraç — ChatScreen L1369 shapes.extraSmall), 2dp→sm, 4dp→sm, 6dp→sm,
   8dp→md (10), 9dp→md, 10dp→md, 11dp→md, 12dp→md, 13dp→md, 14dp→lg, 16dp→lg, 18dp→lg,
   20dp→lg, 22dp→lg, 24dp→lg, 28dp→lg; RoundedCornerShape(50) %50→CircleShape;
   bottomStart/End=8dp→10dp (md'ye tam); koşullu `if (prominent) 10 else 8`→md (8↔10
   kardeş değer, md=10 ikisini de kapsar).
5. **FR-004 HermesCard (B4, Common.kt):** SurfaceCard + shapes.medium + 1dp Border +
   **iç boşluk 12dp** + `defaultMinSize(48.dp)` dokunma tabanı; kartlar arası **8dp**
   (HomeScreen 10→8, SettingsScreen 6→8; ekran kenarı 16dp zaten sabitti); Sunucu satırı
   (HomeScreen ServerRow) **min 56dp** liste satırı. Tüketici durumu: 22+ HermesCard çağrısı
   (Sunucular / Ayarlar bölüm satırları / Pano-Durum / Connect) — FR-007 kapanışı gerçek.
6. **FR-005:** UserBubble balon içi 12/9 → **12/10**; prose satır aralığı 15→22 bandı
   korundu (ThemeTur18Test `tur3 prose bandi` testi KIRMIZI'ya dönerse RED).
7. **FR-007 tur17 LOW kapatması:** ThemeTur17Test.kt 325:29 `String?/String` uyarısı
   temizlendi (`userDir: String` non-null yerel) — final derleme logunda ThemeTur17Test
   uyarısı YOK (grep 0).
8. **FR-008 testler:** ThemeTur18Test 9 test — 6 rol sp + ağırlık + lineHeight oran
   assertion'ları; HermesRadius 4/10/16/999 + HermesShapes slot haritası (CornerSize.toPx
   density=1 okuması); fontScale ×1.15 ve ×0.85 çarpan testleri (saf fonksiyon,
   15×1.15=17.25sp hedefi assertion'lı); tur3 prose bandı kilidi.
9. **FR-009:** Markdown kod bloğu dolgusu Background→**Skeleton** (nötr kart tonu;
   tur17 bulgusunun "SurfaceCard/Skeleton tonu" önerisi), L202 vurgu çizgisi
   Midground→**BorderStrong** (bulgu 5 kapandı); kod bloğu dili etiketi 10→labelSmall,
   kod gövdesi 12→bodySmall rolüne (mono ailesi) geçti. Kopyala-çubuğu radius→md (10).

## Ölçülen sayaçlar (grep, app/src/main, /theme/ hariç)
| Ölçüm | Önce (41755c3) | Sonra |
|---|---|---|
| `fontSize = N.sp` literal | 360 | **0** |
| `lineHeight = N.sp` literal | 47 | **0** |
| `RoundedCornerShape(N.dp)` | 117+10 | 0 (yalnız Theme.kt tanımları + 1 bilinçli bottom-köşe 10dp/md) |
| `MaterialTheme.typography` satırı | 2 | 370+ |
| `MaterialTheme.shapes` satırı | 0 | 120+ |

## Kanıt (komut + çıktı + yol)
- **Test:** `gradle testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**;
  XML sayımı: **tests=652 failures=0 errors=0 skipped=0** (51 XML; 643 taban + 9 ThemeTur18Test).
  Log: `denetim/tur18/build-final.log` (derleme) + 23 satır `w:` = tamamı tur-öncesi
  icon-deprecation uyarıları (grep: ThemeTur17Test/ThemeTur18Test satırı 0).
  İlk üç dereme turu 8+1+1 derleme hatası verdi ve giderildi (build1/build2/build3 log'ları;
  MonoTextStyle çift-`style` sınıfı, SessionDrawer CircleShape importu, CornerSize API).
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` 24.395.271 B,
  **md5 df744514d127620ac0b0c972ad885c53** (Common.kt SurfaceCard+12dp yaması sonrası
  build-final; bu yamadan önceki 86d3c07b… sürümü geçersiz); `adb install -r` → `Success`
  + uygulama açıldı (emülatör hermes-v2, paket com.hermes.mobile.v2).
  Ek kanıt: `13c-son-apk-giris.png` (md5 802a07d7…) son APK ile açılış ekranı,
  `13d-b4-hermescard-son-apk.png` (md5 ba8b9588…) Pano bölüm satırları HermesCard
  (SurfaceCard + 12dp iç + shapes.medium + 1dp border) — 12dp yaması SONRASI sürümden.
- **APK kopyası (FR-008 yolu):** `/Volumes/EX/007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur18-20260920.apk`
  (md5 aynı: df744514d127620ac0b0c972ad885c53).
- **FR-006 fontScale kanıtı (uiautomator bounds, 1080×2400 @420dpi):**
  Ayarlar→Görünüm→"Yazı boyutu" slider %99→%135 (%115'ten sonra);
  "Yazı boyutu" bodyMedium etiketi bounds **43px → 58px** yükseklik,
  58/43 = **1.35** ≈ 1.15/0.99 oran × (135/99 = 1.364 hedef) — **±1sp ±5px bandında
  doğru** (43×1.364=58.6 ≈ 58 ✓). Aynı ekranda 6 ayrı metnin (Görünüm bölüm etiketi,
  tema çipleri, buton metinleri) bounds'ları aynı oranla büyüdü — B3 hedefi: TÜM roller
  ölüçekleniyor (eskiden sadece 3'ü).
- **Ekran kanıtları (denetim/tur18/kanit/):** md5 tamamı FARKLI + içerik-farkı
  piksel-probe (tur17 dersi; PIL ImageChops, ≥30 eşik):

| PNG | İçerik | md5 |
|---|---|---|
| 11-b3-yazi-olcegi-normal.png | Görünüm ekranı %99 taban | 81417f31… |
| 12-b3-yazi-olcegi-buyuk.png | Aynı ekran %135 — 11'e göre 196.485 ≥30px fark | f87d5483… |
| 13-b4-hermescard-sunucular.png | Sunucular ekranı HermesCard md+border | aa5f4f2c… |
| 13b-ek-pano-durum.png | Pano/Durum kart (ek kanıt) | 90580c22… |
| 15-b4-radius-ornekleri.png | Sunucu-ekle formu (input radius-md) | b3249d0a… |

  11↔12 494.306 px fark (%19.1), 13↔15 2.276.428 px (%87.8) — kopya görüntü YOK.
- Cihaz durum notu: kanıt çekiminde uygulama fontScale %135'te bıraktım (11 ve 12 aynı
  kompozisyonun %99 tabanı).

## ATLANDI / YAPILAMADI (nedenli)
1. **14-b4-balon-prose (FR-010 listesi):** sohbet balonu için bağlı gateway + ws
   gerekiyor (mesajlar gateway ws üzerinden akar; 13'teki 1.7sn gecikme bunu doğrular).
   Yerel 4 uçlu HTTP mock (status/sessions/messages/stats, scripts/tur18_mock_api.py +
   adb reverse) kuruldu ve 127.0.0.1:9151'de 200 döndü, ancak profil kaydetme UI
   otomasyonu 4 denemede IME/Gboard odak çakışmasına takıldı (D-02 sınıfı emülatör
   girdi-tıkanıklığı; uiautomator koordinatları IME kapanınca kaydı). Balon prose 12/10
   kod düzeyinde doğrulandı (FR-005 diff + MessageViews.kt:74) — GÖRSEL KANIT YOK,
   tur19'da gerçek sunucu ile çekilecek.
2. **Diğer HermesCard dışı kart kalıpları (tur19+ listesi):** MessageViews ToolResultCard,
   CollapsedBlock, PanelScreen kart yüzeyleri (durum kartları md/lg token'larına geçti ama
   HermesCard'a geçirmediler — iç yapıları özel), SessionRail hücre yüzeyi, CommandPalette,
   InterventionDialog yüzeyleri. Radius'ları token'da; Bileşen geçişi kapsamı aşardı —
   FR-004 "en az 2 ekran" şartı Sunucular + Ayarlar(+Pano/Durum) ile sağlandı.
3. **1dp ızgara çizgisi (ChatScreen L1369, streaming progress):** 1dp radius 4'lü
   ölçekte yok; shapes.extraSmall (4dp) kullanıldı — 1dp'lik ince şeritte 4dp yarıçap
   1dp'ye yakın görünür (2px yarıçap), piksel düzeyinde görsel fark ihmal edilebilir;
   rapora bilinen sapma olarak işlendi.
4. **Skin uyumu:** kullanıcı skin'leri 13 rol hex'ini taşır; 6 yeni rol zaten tur17'de
   varsayılanlı türetilmişti (Skeleton/SurfaceCard preset-bağımsız türevler) — yeni
   hex alanı açılmadı, skin şeması değişmedi.

## Kalan riskler
- 6dp→sm (4dp) kararı (chip ailesi, 40dp altı yoğunluk): tasarım "6 buraya (chip, iç
  eleman, kod satırı) → sm4" dedi; 6→4 küçük sıkışma üretirse tur19'da 6=md/alt-geçiş
  olarak geri alınabilir (tek satırlık ters-yama).
- 8dp→md(10dp) geçişi 2px'lik kart genişlemesi yapar (tasarım §3.7 bunu öngörüyor:
  8/9/10→md). Kod bloğu ve 22 kart yüzeyi etkilendi — görsel tur onayı bekler.
- 15x kontrol tablosu bu turda 5 kanıtlı; 14 (balon) tur19'un ilk işi.

## Denetmene not
- Denetim hedefleri: FR-002 sayaçları (`git grep -cE 'fontSize = [0-9]+\.sp' HEAD -- app/src/main`
  = 0 /theme/ dışı), 652 test XML sayımı (0 fail), 11/12 PNG bounds oranı (58px/43px),
  13'teki kart köşesi 10dp = shapes.medium (MaterialTheme.shapes.medium), ve
  ThemeTur17Test.kt'nin uyarısız derlenmesi (build-final.log grep 'ThemeTur17' = 0 satır).
- 86d3c07b413024e12633f8b6267f6475 md5'li APK emulator'da kurulu (Success satırı yukarıda).
