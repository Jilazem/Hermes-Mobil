# Hermes Mobil — tur-4 "İstenen hale getirme" raporu

Tarih: 2026-09-14 · Profil: android · Dal: feat/android-uzman-devralma
Başlangıç HEAD: 3672b6c · Emülatör: emulator-5554 (Android 34) · Paket: com.hermes.mobile.v2
Sunucu: http://192.168.1.101:9150 (gerçek gateway, gerçek oturumlar)

Kanıt klasörü: `/Users/gokhanuzman/007-HERMES/000-TEMP/android-tur4/`
- `before/` = ÖNCE (eski APK, 3672b6c)
- `son/`    = SONRA (yeni APK, TR arayüz, gerçek akış)
- `en/`     = SONRA (EN arayüz turu)

---

## 1. Bulgu → Düzeltme → Kanıt

| # | Kusur | Düzeltme (kod) | Kanıt |
|---|---|---|---|
| **A** | Kart önizlemesi ham JSON/tool çıktısı: `{"status": "success", "output": "=== ESBLESME: … len 11050…"}` | Yeni saf katman `ui/SessionText.kt`: `isMachineNoise()` + `meaningfulPreview()`. JSON nesne/dizi, `"status"/"output"/"result"/"error"` gibi tool gövdesi, markdown çiti ATILIR; anlamlı satır yoksa önizleme BOŞ kalır ve `liveFeed` bir önceki anlamlı kaynağa (REST ilk mesajı) düşer | ÖNCE `before/t02-oturumlar.png`, `before/t06-tumu.png` → SONRA `son/s02-canli.png`, `son/s03-tumu.png` (kart önizlemesi: "Bilirkişi Yılmaz Altın ve şahsım, 10/02/2024 tarihli taslak…") |
| **B** | Cron oturumu önizlemesi ham sistem mesajı: `[IMPORTANT: You are running as a scheduled cron job. DELIVER…` | Aynı katman: cron/sistem promptu işaretleri (`[important:`, `you are running as a scheduled cron job`, `deliver this`, …) önizlemeden atılır | ÖNCE `before/t06-tumu.png` (3. kart) → SONRA `son/s03-tumu.png` (aynı kart: "Pavo Saglik Takibi · Sep 14 19:40" + "Zamanlanmış görev" rozeti, ham sistem metni YOK) |
| **C** | Telegram kaynaklı oturum başlığı kişi adı ("Gökhan Uzman") kalıyordu | `readableTitle` zinciri tur-4 sırasına çevrildi: rename → anlamlı sunucu başlığı → anlamlı gateway başlığı → ilk anlamlı kullanıcı cümlesi (~40 krkt) → cron iş adı + saat → "Sohbet · gg.AA ss:dd". `isGenericIdentityTitle()` kişi/kaynak adlarını (Gökhan Uzman, telegram, desktop telegram, default…) ve harf içermeyen kırıntıları (".", ". #2") reddeder. Ayrıca kaynak adı ("Masaüstü", "Telegram") artık BAŞLIK değil, kart rozeti | Yeni testler `PreviewSanitizeTest`, `SessionReadableTitleTest`; cihazda `son/s03-tumu.png`, `son/s04-gecmis.png` (tüm kartlar konu; ". #2" → "Sohbet · 14.09 21:09") |
| **D** | Kartta "39 mesaj", detayda "0 mesaj" | Detay sayacı artık oturumun GERÇEK sayısını gösterir: REST kaydı varsa `message_count` (kartla birebir aynı), yoksa yüklenen mesaj sayısı; yüklenirken/hata varsa hiç sayı yazılmaz. Ayrıca Canlı→Döküm artık REST kaydını geçiriyor (`MainActivity.onOpenLive`) ve dipnot "N mesaj yüklendi" kaldırıldı (ikinci farklı sayı güven kırığıydı) | ÖNCE `before/00-mevcut.png` ("0 mesaj") → SONRA `son/s05-detay-ust.png` ("39 mesaj" — kartla aynı), test `TranscriptFoldTest` |
| **E** | Buton seti tutarsız: "Konuşmaya devam et │ Müdahale │ Döküm │ Dur" vs "Devam │ Müdahale │ Dur" | Tek kural (`cardActions()`): liste kartında buton YIĞINI yok; satırın tamamı sohbeti açar; "Döküm" SABİT konumda sağdaki ikon; çalışan oturumda tek birincil eylem "Dur" (+ ikincil müdahale ikonu). Geçmiş listesinde Dur kaldırıldı (durdurma Canlı sekmesinin/uğun-bas menüsünün işi) | ÖNCE `before/t02-oturumlar.png`, `before/t06-tumu.png` → SONRA `son/s02-canli.png`, `son/s03-tumu.png`, `son/s04-gecmis.png`; test `SessionCardAnatomyTest` |
| **F** | Kart altı uzun kılavuz ("Müdahale/durdurma yalnız ajan çalışırken anlamlı…") sürekli görünüyor | Metin SİLİNDİ; eylem kümesi kendini anlatır (buton yoksa kılavuz da yok) | ÖNCE `before/t02-oturumlar.png` (alt iki satır) → SONRA `son/s02-canli.png` |
| **G** | Üst şeritte iç terminoloji: model adı + "bağlı" + `default/ac/android` profil çipleri | `ChatHeader` yeniden yazıldı: tek satır KONU başlığı + ⋯ (model/profil) ikonu; "bağlı" rozeti kaldırıldı, bağlantı YALNIZ kopunca kırmızı "bağlantı yok". `visibleProfileChips()`: varsayılan çip ve insan adı olmayan iç adlar ("default", "ac", "android") çizilmez | ÖNCE `before/t01-sohbet.png` (qwen3.8-flash-next / bağlı / Yönlendirici / default / ac / android) → SONRA `son/s12-sohbet-tr.png` (tek satır konu + ⋯); test `ProfileChipLogicTest` (yeni 4 test) |
| **H** | Mesaj listesi ajan günlüğü gibi: "Düşünme" + araç satırları asistan yanıtını bastırıyor | `foldTranscript()` + `foldToolRuns()`: asistanın nihai METNİ öne çıkar; düşünme + araç çağrı/sonuçları + sistem istemi TEK katlanır "Ayrıntı" satırına iner (dokununca açılır). Canlı akan düşünme bloğu katlanmaz | ÖNCE `before/00-mevcut.png` (alt alta "Düşünme"/"2 araç") → SONRA `son/s05-detay-ust.png` ve `son/s01-sohbet.png` ("✓ Ayrıntı  Düşünme ×17, execute_code ×17"); testler `TranscriptFoldTest`, `SessionCardAnatomyTest` |
| **P0-2** | Başlık = KONU | C kusuruyla aynı iş (zincir + kimlik filtresi + cron iş adı + saat) | `son/s03-tumu.png`, `son/s04-gecmis.png` |
| **P0-1** | Çökme 0 | Hiçbir akışta çökme yok; `adb logcat -b crash` BOŞ (tur başı ve sonu) | `final-check.sh` çıktısı; `son/s01..s11`, `en/en10..en17` |
| **P2-7** | Model etiketi + "bağlı" + profil çipleri üst şeritten çıkar | G kusuruyla aynı | `son/s12-sohbet-tr.png` |
| **P3-8** | Üst sıkışma: chrome incelsin, tek satır konu | Header 56 → 52 dp, alt satır yalnız kopuk bağlantıda | `son/s12-sohbet-tr.png` |
| **Durum etiketleri** | Boşta/bitmiş ETİKETSİZ, "Canlı" kelimesi yok | `statusPill()`: working → "yazıyor…", waiting → "onay bekliyor", starting → "başlıyor", idle/done → ETİKETSİZ. Eski "boşta"/"bitti"/"canlı" etiketleri kaldırıldı | `son/s02-canli.png` (durum etiketi yok, yalnız saat), `son/s03-tumu.png`; test `SessionCardAnatomyTest` |

## 2. Kabul kriterleri

| # | Kriter | Durum | Kanıt |
|---|---|---|---|
| 1 | Her kusur için ÖNCE/SONRA ekran görüntüsü (gerçek sunucu + gerçek oturum) | ✅ | `before/` ve `son/` klasörleri (yukarıdaki tablo satır satır eşler) |
| 2 | Gerçek akış: aç → Oturumlar (Canlı/Tümü) → detay → kaydırma → geri → Ayarlar; crash buffer 0 | ✅ | `son/s01-sohbet.png` … `son/s11-arena.png` (`final-tour.sh`, tek koşumda); `adb logcat -b crash` boş; süreç ayakta (pid 22611) |
| 3 | Render testi: markdown yanıt (kalın/başlık/liste/kod) GERÇEK yanıtla | ✅ | `son/m03-md-kaydir1.png`, `son/m04-md-kaydir2.png` — 193 mesajlık gerçek oturumun ("Hermes Agent web-search özelliklerini incele") asistan yanıtı: `###`/`####` başlıklar, kalın diziler, sıralı liste, inline kod, bağlantılar, yatay ayırıcı. Debug-Activity KULLANILMADI |
| 4 | Önizleme kuralı birim testi + JSON-skip kuralı testi | ✅ | `PreviewSanitizeTest` (14 test: JSON/tool/cron-sistem/markdown/kesme/kimlik), `SessionCardAnatomyTest` (5) |
| 5 | TR/EN turunda etiket sızıntısı 0 | ✅ | `en/en10..en17` + `en-scan.py`: kalan 10 eşleşmenin TAMAMI oturum VERİSİ (kullanıcı mesajı/başlık/profil adı), 0 arayüz etiketi. Turda bulunan gerçek sızıntılar düzeltildi: "Masaüstü"/"Zamanlanmış görev" kaynak rozetleri ve "Sohbet · …" başlık yedeği artık dile göre. Regresyon: `SessionSourceLabelLocalizationTest` |
| 6 | Tüm testler yeşil (≥211) + yeni testler, taze sayaç | ✅ | **şu an 252 test** (211 → 252, +41), failures=0, errors=0, skipped=0; `--rerun-tasks` ile taze XML sayımı; 27 XML dosyası |

## 3. Ortam notu (dürüstlük)

`vision_analyze` bu profilde ÖLÜ bir model adına yönlendiği için (`qwen3.8-flash-next` — yerel uç :8888'de yüklü değil; profilde `auxiliary.vision` yok) ekran görüntüsü incelemesi Hermes config'i DEĞİŞTİRİLMEDEN yapıldı: `000-TEMP/android-tur4/vision.py`, Gemini'ye (`gemini-2.5-flash`, profildeki `GOOGLE_API_KEY`) gerçek PNG'yi gönderir. Bu bir kanıt üretme yolu değil, görüntü OKUMA yoludur; bulgular bu araçla cihaz ekranlarından okundu. Yapılandırma dosyasına dokunulmadı (araç da izin vermiyor).

Emülatör ağı 20:49 civarında kısa süre HTTP timeout verdi (gateway yoğun); uygulama WS ile toparlandı ve tur tekrarlandı — nihai kanıt seti bu toparlanmadan SONRA üretildi.

## 4. KALAN (bu turda yapılmayanlar — bilinçli)

- **İskelet (skeleton) yükleme ekranları**: boş durum ekranları var (ör. arama sonucu yok: `son/m01-arama.png`), iskelet animasyonu yok. P2/P3 kapsamı.
- **Açık sohbetin taşma menüsünde "Müdahale/Dur"**: müdahale ikonu kartta kaldı; sohbet ekranına taşma menüsü eklenmedi.
- **Diğer sekmeler (Pano/Arena/Ayarlar) derin gezisi**: gezildi (`son/s09-ayarlar.png`, `s10-pano.png`, `s11-arena.png`) ama bariz olmayan kusurlar düzeltilmedi; Pano/Arena bu turun kapsamı dışında bırakıldı.
- **`default/ac/android` profil seçimi**: çipler gizlendi; profil seçimi ⋯ / Ayarlar → Profiller yolundan yapılır (Ayarlar'a yeni bir "Profiller" girişi EKLENMEDİ — mevcut profil sayfası kullanılıyor).
- **Kaynak ikonu sadeleştirmesi**: kaynak hâlâ küçük metin rozeti (10 sp); Grok'un önerdiği "en fazla kaynak ikonu" adımı yapılmadı.
- **Kart sayacı `14 açık kayıt · 26 toplam`** (FR-004 sayaç üçgeni) etiketli bırakıldı, birleştirilmedi.

## 5. Değişen dosyalar

Yeni: `ui/SessionText.kt` (saf önizleme/başlık/durum katmanı) + 4 yeni test dosyası.
UI: `SessionsScreen.kt`, `LiveFeed.kt`, `LiveFeedScreen.kt`, `LiveSessionsScreen.kt`, `SessionDetailScreen.kt`, `MessageViews.kt`, `ToolActivity.kt`, `ChatScreen.kt`, `ProfileChips.kt`, `ChatViewModel.kt`, `AppViewModel.kt`, `MainActivity.kt`, `data/Models.kt` (`display_name`).
Test: `SessionReadableTitleTest`, `SessionCardTopicTest`, `ProfileChipLogicTest` güncellendi (tur-4 sözleşmesi), yeni: `PreviewSanitizeTest`, `TranscriptFoldTest`, `SessionCardAnatomyTest`, `SessionSourceLabelLocalizationTest`.

## 6. Teslimat

- Dal: `feat/android-uzman-devralma` (main'e merge YOK, push YOK)
- Commit'ler: `30a32a0` (ana tur-4 düzeltmeleri) + `0b3dc92` (harf içermeyen kırıntı başlıklar — ". #2")
- HEAD: **0b3dc92**
- APK: `/Users/gokhanuzman/007-HERMES/000-TEMP/hermes-tur4-0b3dc92.apk` (24.578.893 bayt)
- MD5: **f225c65f1aeb74c1428d2323f7b894e7** (`hermes-tur4-0b3dc92.apk.md5`)
- APK doğrulaması: `apksigner verify` → OK; build-dizini md5 == teslim md5; TESLİM EDİLEN baytlar emülatöre kuruldu (`adb install -r` → Success), uygulama açıldı, crash buffer BOŞ.

## 7. Teslim sonrası ek doğrulama (teslim edilen APK ile)

- Kurulum + açılış: Success, `mCurrentFocus=com.hermes.mobile.v2/com.hermes.mobile.MainActivity`
- Canlı ekran dökümünde yasaklı metin taraması: `"status": "success"` → **0**, `IMPORTANT` → **0**, `Gökhan Uzman` → **0**
- "Sohbet · 14.09 21:09" (sunucunun `derived` ürettiği ". #2" kırıntı başlığı yerine)
- `teslim/` klasöründe yeni tur (`teslim/s01..s11`): gerçek akış uçtan uca, crash 0

