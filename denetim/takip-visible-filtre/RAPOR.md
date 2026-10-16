# Takip: matchLabel/matchId/editableTarget visible filtresi + parseTap yarım koordinat

Kaynak: cd-denetmen ikinci göz — `denetim/verdict-k1-k2-phoneharness-merge.json` conditions.
Tarih: 17.10.2026 · Dal: `feat/ema-unified-20261007` (merge 50bdedf üstü) · Uzman: cd-android

## Bulunan
1. `FullControl.matchLabel` / `matchId` / `editableTarget` aday havuzunu yalnız
   `bounds.width/height > 0` ile süzüyordu; `visible` kontrolü yoktu. Ekran dışı
   (offscreen) düğüm — bounds'u geçerli ama `visible=false` — `tap "Sil"` çağrısında
   hedef seçilebiliyordu: yanlış koordinata gerçek jest riski.
2. `FullControl.parseTap`: x/y'den yalnız biri parse edilebildiğinde (veya biri
   bozuk geldiğinde) akış sessizce id/metin aramasına düşüyordu —
   `parseTap("100", null, "Sil", null)` Label("Sil") dönüyordu; niyet edilen
   nokta yerine başka etikete dokunulabilirdi.

## Yapılan (en küçük diff — yalnız FullControl.kt + FullControlTest.kt)
1. Üç arama fonksiyonunun aday filtresine `it.visible` eklendi. Görünür eşleşme
   yoksa zaten mevcut `tapNotFound` yolu çalışır (null → ekranda bulunamadı).
2. `parseTap`'e tek satır: tam Point çifti kurulamıyorsa ve x/y'den herhangi biri
   boş olmayan değer taşımıyorsa değil — **taşıyorsa** → `TapTarget.Invalid`
   ("reddet, tahmin etme"). Boş/whitespace koordinat "verilmemiş" sayılır
   (mevcut `parseTap("", " ", …)` davranışı korunur).
3. Kardeş yol taraması: `grep -rn "width > 0" app/src/main` — kusur sınıfı
   yalnız FullControl.kt'de; başka dosyada aynı kalıp yok.

## Test (saf katman, FullControlTest.kt +2)
- `yarim koordinat aramaya dusmez - gecersiz hedef`: yarım/bozuk x veya y →
  Invalid; tam çift Point'i bozmaz.
- `ekran disi satir eslesmez`: `visible=false` düğüm matchLabel/matchId/
  editableTarget'ta seçilmez (null); görünür kardeş varsa o kazanır.

## Kanıt (gerçek koşum, Mac M4 — JDK 17 Temurin `20-ARACLAR/jdk/jdk-17`)
- `./gradlew :app:testDebugUnitTest --tests "…FullControlTest"` → BUILD SUCCESSFUL (5s)
- `./gradlew :app:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL (23s)
- XML sayımı (93 dosya): **996 test / 0 fail / 0 error / 0 skipped**
  (önceki birleşik taban 994 + bu tur 2 yeni; FullControlTest.xml'de yeni
  test adları teyitli: `ekran disi satir eslesmez`, `yarim koordinat aramaya dusmez - gecersiz hedef`)
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL;
  app-arm64-v8a-debug.apk 62,829,166 B md5 `0d85301e2f764a2a314ba9a2d027ae1b`;
  app-x86_64-debug.apk 68,837,416 B md5 `f2acc30fb5cbaa5f6250e35b8f52976f`

## Atlanan / kapsam dışı
- Cihaz/emülatörde canlı doğrulama: bu tur saf-katman kural değişikliği; KABUL
  birim test + fail=0 istedi. Emülatör koşusu kapsam dışı bırakıldı (K1/K2
  kartlarında da saf katman kabul yeterli sayıldı).
- Geniş refactor (kart kapsamı dışı): yapılmadı.

## Açık riskler
- `visible` bayrağı servis ağacı dönüşümünün doğruluğuna bağlıdır
  (HermesAccessibilityService); dönüşüm zaten mevcut testlerle kilitli
  (`ekran disi ogeler ozete ve kaydirma farkina girmez`, `ekran disi isaretlenir dokumde`).
