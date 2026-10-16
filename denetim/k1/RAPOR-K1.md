# K1 — phone-harness benimseme K1 (tap labels, screen_bounds, phone_handoff, DiagLog adım metni, B4)

**Kart:** t_35ec8257 (cd-android) · **Tarih:** 16.10.2026
**Proje:** Hermes-Mobil · **Dal:** `cd-android/k1-phoneharness-t35ec8257` (taban `feat/ema-unified-20261007` @ `55f362b`)
**Kaynak:** t_a22e09e0 GAP raporu (md5 2c5ecbd59ead90e12acf7b863806926f ✓ doğrulandı)

## Bulunan

- **ÇAKIŞMA (hotspot):** K2 kartı (t_384678f7 — screen.wait/scroll_step/Doctor) aynı ana
  çalışma ağacında (`…/work/Hermes-Mobil`) paralel koşuyordu; `FullControl.kt`,
  `FullControlTools.kt`, `HermesAccessibilityService.kt` aynı anda iki işçi tarafından
  yazılıyordu (mtime 23:31-23:34 ölçüldü). Yönetim: K1 işi **izole git worktree**'de
  yapıldı; K2 ağacına düşen 2 geçici K1 yaması geri alındı (grep 0 kalıntı); her iki
  karta `hotspot:` yorumu yazıldı.
- GAP raporundaki B4 "sessiz oturum fallback"i kodda README'deki anlatımıyla birebir
  **bulunamadı** (Tur-16'da `lastSessionToRestore` artık sessizce `null` dönüyor;
  "en günceline düşme" README'de kalmış). → **DOĞRULANAMADI** (rapordaki 2. maddeyle
  aynı). Uygulama, kartın CEO kararındaki davranış tarifine göre yapıldı (aşağıda).

## Yapılan (madde madde, kanıtlı)

1. **`tap` başarısızlık mesajına görünen etiketler** — `FullControl.visibleSuffix()` +
   `FullControl.tapNotFound()` (saf, testli); `HermesAccessibilityService.tap`'te hem
   "hedef bulunamadı" hem "jest reddedildi" yollarında `topLabels(snap)` çıktısı artık
   mesaja giriyor. Kanıt: `TapLabelsTest` (3/0), diff `FullControl.kt` "Araç mesajları"
   bölümü, `HermesAccessibilityService.kt` tap().
2. **`screenshot` sonucuna `screen_bounds` + dönüşüm kuralı** — sonuç JSON'una
   `screen_bounds: [0, 0, shotW, shotH]` (tam ekran, tap ile aynı nokta uzayı) eklendi;
   PNG pikseli→ekran noktası kuralı `PhoneToolSpecs.SPECS["screenshot"]` tanımında
   (`x = px * (screen_bounds[2]-screen_bounds[0]) / width`, eşdeğeri `px/scale`) —
   köprü `hello.specs` ile sunucuya otomatik gidiyor. docs/TELEFON-KONTROL.md'ye de
   yazıldı. Kanıt: `ScreenBoundsSpecTest` (1/0), `HermesAccessibilityService.kt`
   screenshot(), `PhoneToolSpecs.kt`.
3. **`phone_handoff` aracı** — `FullControl.HANDOFF`; `FullControlTools.handOff()`:
   (a) `Notifier.agentMessage` ile "ekranda sen yap" bildirimi, (b) `agentReadOnly=true`
   (salt-okunur kipe dönüş), (c) DiagLog kaydı, (d) araç sonucunda kip değişiminin açık
   yazımı. `guardReason` deseni korunur: HANDOFF `WRITE_TOOLS`'ta değil → salt-okunur
   kipte reddedilmez (yalnızca kısıtlayıcı), diğer tüm yazma araçları `WHY_READ_ONLY`
   ile reddedilir; kapalı kanal/tam kontrolde aynı tek-sebep sırası. Erişilebilirlik
   gerektirmez (bildirim + ayar). Kanıt: `PhoneHandoffTest` (3/0).
4. **DiagLog ERROR → "kullanıcı adımı"** — `DiagUserStep` (saf): 9 sınıf
   (agent-access/full-control/read-only/accessibility/server-setup/token/server-error/
   network/share-log), `Entry.userStep` yalnız ERROR/CRASH'te dolar; `DiagView` satırın
   altında "Yapılacak: …" olarak gösterir; eşleşmeyen hata genel adımla cevapsız kalmaz.
   Kanıt: `DiagUserStepTest` (3/0), `DiagLog.kt`, `DiagView.kt`.
5. **B4 (CEO kararı — geri alınabilir)** — sessiz fallback YOK: `restoreFallback()`
   (saf, `SessionDrawerLogic.kt`) kayıtlı oturum listede yoksa en güncel satırı seçer;
   `MainActivity` FR-006 geri yükleme noktasında `continueSession(..., banner=…)` ile
   TEK SATIR bildirim gösterilir: "«Önceki oturum» süresi doldu, son oturum açıldı".
   Banner geçmiş yüklenirken de sonrasında da korunur (`ChatViewModel` bannerItems).
   Kural: sohbette oturum açıkken asla devralım yok; arşivli satır hedef değil; ham
   oturum kimliği gösterilmez (D-05). Kanıt: `SessionRestoreFallbackTest` (5/0).
   Not: sesli (JarvisBrain) geri yükleme yoluna bu bildirim KONMADI — o kanalda balon
   yok; kart kapsamı dışında (bkz. Atlanan).

## Atlanan

- `tap`'in **koordinat** (Point) hedefinde jest reddi mesajına etiket ekleme: o yolda
  anlık `snap` yok; ek ağaç taraması "en küçük doğru diff" ilkesine aykırı bulundu.
  Gerekçe: mesajın hedefi "aranan etiket bulunamadı" senaryosu.
- B4 bildiriminin sesli/üretilmiş konuşmaya (JarvisBrain) taşınması ve "geri alma"
  için ayar UI'ı — ayrı kart gerektirir.
- `phone_handoff` sonrası kontrolü **geri verme** akışı kullanıcı ayarı (Ayarlar →
  Telefon denetimi → Yalnız okunur) üzerinden; yeni UI yok (kapsam dışı).

## Kanıt

- **Derleme:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` →
  `BUILD SUCCESSFUL in 37s … 41 actionable tasks: 24 executed, 17 from cache`, `EXIT=0`
  (log: `denetim/k1/build-test.log`).
- **Testler (taze XML, `app/build/test-results/testDebugUnitTest/`, 88 dosya):**
  TOPLAM=**952** · FAIL=**0** · ERROR=**0** · SKIP=**0**. Yeni: TapLabelsTest 3,
  PhoneHandoffTest 3, ScreenBoundsSpecTest 1, DiagUserStepTest 3,
  SessionRestoreFallbackTest 5 → **15 yeni**; mevcut 937 hepsi yeşil.
- **APK:** `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` 61.537.066 B,
  md5 `6106dc42fbe64ee3e1c8b61f8a87a0f5` · `app-x86_64-debug.apk` 67.545.316 B,
  md5 `2e5dea4c52bdbd119ad931e303224a9b`.
- **CREDITS.md:** ShawnPana/phone-harness (MIT) — repo + lisans bağlantısı + fikir
  listesi; kod kopyalanmadı (sıfırdan uygulama).
- Diff: 10 değiştirilmiş + 7 yeni dosya, +215/−14 (kaynak) + 5 test + CREDITS.

## Commit

- `cd-android/k1-phoneharness-t35ec8257` dalı, taban 55f362b. (Aşağıdaki commit.)

## Açık riskler

1. **Dal birleşimi orkestratörde:** iş, K2 (t_384678f7) ile aynı ana ağacı paylaştığı
   için izole worktree dalında. K2 commit'inden sonra `cd-android/k1-phoneharness-t35ec8257`
   → `feat/ema-unified-20261007` birleştirilmeli (farklı bölgeler; metin çakışması
   beklenmez ama doğrulanmalı).
2. B4'te "son oturum açıldı" bildiriminin X'i bilinçli olarak **ham kimlik DEĞİL**
   "Önceki oturum" olarak yazıldı (D-05); kaybolan oturumun başlığı listede olmadığı
   için geri getirilemiyor.
3. `screen_bounds` alanı yalnız `takeScreenshot` yolunda (Android 11+) üretiliyor;
   eski cihazda zaten screenshot yok.

## Denetmene not

- Çakışma (hotspot) her iki karta yorumlandı: t_35ec8257 yorum 2372, t_384678f7 yorum 2373.
- CEO kararı (B4) uygulandı; itiraz/geri alma istenirse `restoreFallback` + `banner`
  parametresi tek commit'te geri alınabilir durumda.
- GAP raporundaki "sessiz fallback bugün görünüyor mu?" DOĞRULANAMADI maddesi bu işte
  ölçüldü: `lastSessionToRestore` null'ında sessiz kalınıyordu; artık bildirimli.