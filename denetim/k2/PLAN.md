# PLAN — Hermes-Mobil phone-harness benimseme K2 (orta paket)

Katman: orta

## Amaç
Hermes-Mobil'e phone-harness'ın ORTA büyüklükteki 5 yeteneğini taşımak: (1) birleşik
**Doctor kartı** — telefon-tarafı tarama (erişilebilirlik, bildirim erişimi, Shizuku, köprü WS,
sunucu erişimi, Android Auto geliştirici modu) ✓/✗/? + "şimdi yap" aksiyonu + DiagLog kaydı;
(2) **screen.wait** aracı — text/app/stable koşulu, deadline'lı, sonsuz bekleme yok;
(3) **screen.scroll_step** — momentum'suz içerik kaydırma + bitiş algılama (swipe'dan ayrı
semantik); (4) **Canlı envanter kartı** — sohbet oturumları + köprü durumu + Artemis görevleri
tek görünümde (drawerStatusDot rengi yeniden); (5) Artemis görevi arka planda bitince
**phone_notify** bildirimi. Sonuç: derlenen, testleri yeşil, cihazda doğrulanmış kod +
CREDITS.md girişi.

## Kabul ölçütleri
- `./gradlew :app:testDebugUnitTest` fail=0 (mevcut 926 test + yeni en az 8 test:
  wait/scroll/doctor/inventory); sayı XML çıktısından raporlanır.
- Doctor kartı cihazda (emulator-5554) 6 alanın tamamını ✓/✗/? ile raporluyor; ekran
  görüntüsü + DiagLog dump kanıt dosyaları `denetim/k2/` altına yazılır.
- screen.wait zaman aşımı testi geçer: koşul doğrulanmazsa süre sonunda ok=false döner,
  sonsuz bekleme yok (en fazla 30 sn tavan test edilir).
- scroll_step bitiş testi geçer: iki dökümde görünen etiketler aynıysa atEnd=true döner.
- `./gradlew :app:assembleDebug` exit 0; `adb install -r` çıktısında "Success" yazısı
  rapor dosyasında kanıtlanır.
- PLAN.md + her adımda "ADIM i/8 bitti" notu; CREDITS.md ShawnPana/phone-harness (MIT)
  girdisini içerir.

## Kaynaklar
- GAP raporu: `/Volumes/EX/007-HERMES-M4-LIVE/20-HERMES/kanban/attachments/t_a22e09e0/GAP-ANALIZI-phone-harness-Hermes-Mobil_1.md` (md5 2c5ecbd59ead90e12acf7b863806926f, doğrulandı).
- CZİP promt `8pa3sv` (phone-harness benimseme) — doctor alan listesi kaynağı. Not:
  phone-harness doctor main@68d883f (PR #138), yerel 0.3.2'de YOK → upstream kontrol
  listesi DOĞRULANAMADI, raporda işaretlenecek.
- Kod: `app/.../data/FullControl.kt`, `FullControlTools.kt`, `HermesAccessibilityService.kt`
  (sleep(250) L204/232/238), `PhoneBridgeService.kt` (runArtemisTask L480), `PhoneTools.kt`
  (notifyUser L425), `Notifier.kt` (agentMessage L94), `ShizukuBridge.kt` (State enum),
  `ui/SettingsScreen.kt` (ReadAccessRows L827, ShizukuRow L2015, ArtemisCard L2101),
  `ui/SessionDrawer.kt` (Canlı sekmesi tab=1), `ui/SessionDrawerLogic.kt` (drawerStatusDot L115),
  `data/DiagLog.kt`.
- Test altyapısı: `app/src/test/java/com/hermes/mobile/` (JUnit4, cihazsız).
- Araçlar: `plan_kapisi.py`, `./gradlew`, `adb` (emulator-5554), phone-harness SKILL.md
  (wait_*/scroll semantiği).

## Adımlar
1. PLAN.md yaz ve plan_kapisi'nden geçir.
   Doğrulama: `python3 /Volumes/EX/007-HERMES-M4-LIVE/00-SISTEM/super-uzman/plan_kapisi.py PLAN.md`
   çıktısı "PLAN KAPISI: GEÇTİ", exit 0.
2. Doctor: saf `DoctorLogic.kt` (6 alan, DoctorCheck ✓/✗/? + fix aksiyonu) + `DoctorCard`
   Compose + SettingsScreen'e ekleme + tarama sonucunun DiagLog'a yazılması + DoctorLogicTest.
   Doğrulama: `./gradlew :app:testDebugUnitTest --tests '*Doctor*'` fail=0; derleme exit 0.
3. screen.wait: `FullControl.parseWait/waitSatisfied` (saf, deadline tavanı 30 sn) +
   `HermesAccessibilityService.screenWait` (250 ms polling, deadline'lı) + FullControlTools
   dispatch + READ_TOOLS'a kayıt + ScreenWaitTest.
   Doğrulama: ScreenWaitTest fail=0 (koşul bulundu / zaman aşımı / hatalı argüman senaryoları).
4. screen.scroll_step: `FullControl.parseScrollStep` (içerik yönü ≠ swipe parmak yönü, sabit
   adım) + `HermesAccessibilityService.scrollStep` (yavaş jest, etiket farkıyla bitiş) +
   WRITE_TOOLS'a kayıt (jest = eylem) + ScrollStepTest.
   Doğrulama: ScrollStepTest fail=0 (yön eşlemesi, adım sınırı, atEnd tespiti).
5. Canlı envanter: `ArtemisTaskTracker` (StateFlow) + saf `LiveInventoryLogic` (köprü + oturum
   + görev satırları, drawerStatusDot renk semantiği) + `LiveInventoryCard` (Canlı sekmesi
   üstü) + LiveInventoryTest.
   Doğrulama: LiveInventoryTest fail=0; derleme exit 0; kartın Canlı sekmesinde olduğu
   ADIM 8 cihaz ekran görüntüsünde görünür.
6. Artemis bitiş bildirimi: runArtemisTask "still running" yolunda arka plan takibi (30 dk
   tavanlı, 2 sn yoklama) → görev bitince `Notifier.agentMessage` (phone_notify ile aynı hat)
   + ArtemisTaskTracker güncellemesi.
   Doğrulama: derleme exit 0; ADIM 8'de bildirim satırı + DiagLog kaydı kanıtlanır
   (Artemis sunucusu yoksa DOĞRULANAMADI olarak raporlanır).
7. CREDITS.md (ShawnPana/phone-harness, MIT — fikir kaynağı) + docs/PHONE-TOOLS.md'ye
   screen.wait / screen.scroll_step belgeleri.
   Doğrulama: `git show --stat` bu iki dosyayı listeler; içerikleri rapor md5 ile verilir.
8. Doğrulama merdiveni: tam test koşusu (fail=0, sayı raporlanır) → assembleDebug exit 0 →
   `adb install -r` Success → Doctor kartı cihazda tarama (ekran görüntüsü + DiagLog dump) →
   sonuç raporu.
   Doğrulama: `denetim/k2/` altındaki test XML özetleri, kurulum çıktısı ve ekran
   görüntüleri raporla yollarıyla listeleniyor.

## Riskler / Patron kararı
- phone-harness doctor içeriği yerel 0.3.2'de yok; alan listesi CZİP 8pa3sv + GAP raporundan
  alındı → raporda "DOĞRULANAMADI: upstream doctor kontrol listesi" işaretli.
- Android Auto geliştirici modu üçüncü taraf uygulamadan okunamaz (AA kendi tercihinde
  tutar) → satır "?" (bilgi yok) gösterir, "şimdi yap" AA ayarlarını açar; DOĞRULANAMADI.
- Kabul "5 alan" diyor, görev gövdesi 6 alan sayıyor → 6 alan implement edilir, ikisi de
  raporlanır (fark not edilir).
- Emülatör (emulator-5554) D-02 tuzakları: kanıt çift kaynaklı (ekran görüntüsü + DiagLog),
  kör tıklama yok.
- Artemis arka plan takibi 30 dk tavanlı, yalnız 2 sn'lik yoklamalarla — pil/WS etkisi düşük.
  Patron kararı gerektirmez.
- Yeni araçlar `advertise()`/`guardReason()` kapısından geçer; scroll_step WRITE_TOOLS
  (jest = eylem, salt-okunur kipte reddedilir). Tasarım kararı, patron onayı gerektirmez.