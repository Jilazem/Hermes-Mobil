# RAPOR — Hermes-Mobil phone-harness benimseme K2 (orta paket)

Kart: kanban t_384678f7 · Tarih: 2026-10-17 · Uzman: cd-android
Proje: Hermes-Mobil, dal feat/ema-unified-20261007 (taban 55f362b)

## Bulunan
- GAP raporu (t_a22e09e0) kapsamındaki 5 ORTA iş: doctor kartı, screen.wait,
  screen.scroll_step, Canlı envanter, Artemis bitiş bildirimi.
- İlk test turunda 1 gerçek hata: DrawerLiveRow'daki "bekleyen oturum" rengi
  (yellow) drawerStatusDot semantiğiyle birebir eşleşmiyordu (sessionActive
  yalnız "running"e bakıyordu). Kök neden: renk semantiğinin tek yerde
  (SessionDrawerLogic.drawerStatusDot) tanımlı olması ama kullanıcı kodunun
  kendi koşulunu yeniden yazması. Düzeltme: semantiğe birebir uyum + gri dal
  testle kilitlendi (Ders defteri D-05 sınıfı: aynı veriyi gösteren tüm
  bileşenler).
- Kabul ölçütü "5 alan", görev gövdesi 6 alan sayıyor (Android Auto dahil) →
  6 alan implement edildi, iki sayım da raporlandı (PLAN.md riskler).

## Yapılan (ADIM 1/8 → 8/8)
1. **PLAN.md** yazıldı, plan_kapisi.py "PLAN KAPISI: GEÇTİ" (exit 0).
2. **Doctor kartı**: `DoctorLogic.kt` (saf, 6 alan ✓/✗/? + fix aksiyonu) +
   `DoctorCard.kt` (Tara + "şimdi yap" derin bağlantıları + DiagLog kaydı) +
   SettingsScreen'e ekleme + `DoctorLogicTest.kt` (12 test).
3. **screen.wait**: `FullControl.parseWait/waitSatisfied` (deadline tavanı 30 sn,
   sonsuz bekleme yok) + `HermesAccessibilityService.screenWait` (250 ms
   polling) + FullControlTools dispatch (salt-okunur) + `ScreenWaitTest.kt` (9).
4. **screen.scroll_step**: `FullControl.parseScrollStep` (içerik yönü ≠ parmak
   yönü, sabit adım 0.1–0.9) + `HermesAccessibilityService.scrollStep`
   (etiket farkıyla bitiş algılama) + WRITE_TOOLS kaydı + `ScrollStepTest.kt` (9).
5. **Canlı envanter**: `ArtemisTaskTracker` (StateFlow) + `LiveInventoryLogic`
   (köprü → oturumlar → Artemis; drawerStatusDot rengi yeniden) +
   `LiveInventoryCard.kt` (Canlı sekmesi üstü) + `LiveInventoryTest.kt` (7).
6. **Artemis bitiş bildirimi**: PhoneBridgeService.runArtemisTask arka plan
   takibi (30 dk tavan, 2 sn yoklama) → bitince `Notifier.agentMessage`
   (phone_notify ile aynı hat) + `ArtemisTaskTrackerTest.kt` (5).
7. **CREDITS.md** (ShawnPana/phone-harness, MIT — fikir kaynağı, kopya yok) +
   `docs/PHONE-TOOLS.md` (screen.wait / screen.scroll_step belgeleri).
8. **Doğrulama merdiveni**: tam test → assembleDebug → adb install → cihazda
   doctor taraması + Canlı sekmesi ekran görüntüsü + DiagLog dump.

## Atlanan
- Yok. Kapsamın tamamı işlendi.
- DOĞRULANAMADI (bilinçli işaretleme):
  - **Android Auto geliştirici modu**: üçüncü taraf uygulamadan okunamaz (AA
    kendi tercihinde tutar) → kartta "?" satırı + "şimdi yap" AA ayarlarını
    açar. Upstream doctor kontrol listesi de doğrulanamaz (yerel phone-harness
    0.3.2'de doctor yok; alan listesi CZİP 8pa3sv + GAP raporundan).
  - **Artemis bitiş bildirimi canlı uçtan-uca**: Artemis sunucusu emülatör
    oturumunda yok; hattın tamamı birim testle (ArtemisTaskTrackerTest) + kod
    yolu (Notifier.agentMessage) ile kanıtlı, canlı bildirim gözlenmedi.
  - **Canlı envanter Artemis satırı cihazda**: emülatörde aktif Artemis görevi
    olmadığı için satır görünmedi (boş durum doğru render edildi; satır
    üretimi LiveInventoryTest ile kilitli).

## Kanıt (komut + çıktı + yol) — hepsi `denetim/k2/` altında
| Adım | Kanıt |
|---|---|
| Test (tam) | `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, EXIT=0 (`adim8-test-full.log`); JUnit XML: **979 tests, 0 failures, 0 errors, 0 skipped** (88 XML, `app/build/test-results/testDebugUnitTest/`) — taban 926 + 53 yeni |
| Yeni testler | DoctorLogicTest 12, ScreenWaitTest 9, ScrollStepTest 9, LiveInventoryTest 7, ArtemisTaskTrackerTest 5 (+ ilgili mevcut paketler) |
| APK | `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (`adim8-assemble.log`); `app-arm64-v8a-debug.apk` **MD5 3d73fcbb02ac86eb60fc7f894bdd5e3f** (`apk-md5.txt`) |
| Kurulum | `adb install -r` → "Performing Streamed Install / **Success**" (`adb-install.txt`) |
| Doctor kartı (cihaz) | `doctor-karti-cihaz.png` — 6 alan ✓/✗/? satırı + "Tara" + 6× "Şimdi yap" düğmesi görselde doğrulandı (vision analizi); özet "0/6 tamam · 5 eksik · 1 bilinmiyor" |
| Doctor DiagLog | `diaglog-doctor.txt` — 6 alanın tamamı ayrı satırda raporlanmış (a11y, notification, shizuku, bridge, server: ✗ eksik; android_auto: ? DOĞRULANAMADI) |
| Canlı envanter (cihaz) | `canli-envanter-cihaz.png` — "Canlı" sekmesi aktif, "Canlı — ne çalışıyor" başlığı, Köprü WS satırı gri dot + "kapalı" (drawerStatusDot semantiği: aktif değil = gri) |
| Emülatör | `emulator-5554` (adb devices), `emulator.log` |
| K1 kalıntısı | `grep HANDOFF|topLabels|screen_bounds|phone_handoff FullControl.kt` → boş (hotspot notundaki K1 geri-alma doğrulandı) |
| Debug kanca (D-08) | diff'te teşhis kancası yok (grep eşleşmeleri meşru `scroll_step` tanımları) |

## Commit listesi
- Tek commit: bu paketin tüm değişiklikleri (6 değişik dosya + 10 yeni dosya +
  denetim/k2/ + CREDITS.md + docs/PHONE-TOOLS.md), `git add -A` ile; K1 kalıntısı
  içermez. Commit SHA rapor ekinde/kanban metadata'da.

## Test sayısı
979/0/0 (failures/errors/skipped), taze XML (bu koşu, 88 dosya).

## Açık riskler
- Android Auto geliştirici modu satırı kalıcı olarak "?" (platform sınırı).
- Artemis canlı bildirimi sunucusuz ortamda uca kadar denenemedi (yukarıda
  DOĞRULANAMADI).
- K1 kartı (t_35ec8257) bu kartla aynı 3 dosyaya dokunuyor (FullControl.kt,
  FullControlTools.kt, HermesAccessibilityService.kt); K1 dalı bu commit'ten
  sonra orkestratörce birleştirilecek (hotspot yorumu kartta kayıtlı).

## Denetmene not
- İlk test turundaki tek hata kök nedeniyle düzeltildi ve gri dal testle
  kilitlendi; semantiğin sahibi drawerStatusDot'tur.
- Rapor içindeki her sayı araç çıktısından alındı (XML parse, md5, vision).
- "5 alan" (kabul) vs 6 alan (gövde) farkı bilinçli: 6 implement edildi.