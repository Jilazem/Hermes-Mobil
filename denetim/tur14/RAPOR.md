# TUR-14 RAPOR — Sohbet Arayüzü v2 (Claude/ChatGPT/Grok tarzı)

Tarih: 2026-09-16 · Dal: feat/android-uzman-devralma · Push yok
Önceki tur: tur-13 commit'leri doğrulandı (b15f231 feat, 28e897a docs, 61e9d63 verdict) — aynı worktree boştu, koşu yok.

## F1 — Oturumlar listesi

Yapılanlar:
1. **Göreli zaman insan-okur oldu** (`Common.kt formatRelative`): "13 sa" → "13 sa önce",
   "5 dk önce", "2 g önce"; 7 günden eski oturumda göreli zaman yerine tarih
   ("12.08", yıl farklıysa ".2026") — bulanık "40 g önce" yerine net tarih.
   Emülatör kanıtı: liste "az önce" / "3 sa önce" gösteriyor (03-oturumlar-sonra.png).
2. **Boş/kırık kartlar ele** (`SessionsScreen.kt`): yeni saf `isPlaceholderSession` —
   ne gerçek başlığı (ham id / generic ad sayılmaz) ne anlamlı önizlemesi ne mesajı
   olan ve canlı olmayan kart"gizlenir (`visibleSessionsFiltered` liste süzgeci).
   "yalnız TUI çipi" kartı bu filtrenin hedefi; içerik ortaya çıkınca kart geri gelir.
3. **Kaynak chip'i** (`sourceChipLabel`): kart alt satırında insan-okur kaynak adı
   (tui/cli→"TUI", telegram→"Telegram", api_server→"API", cron→"Zamanlanmış").
   İkon + ad birlikte; tanınmayan ad kısaltılıp gösterilir.
4. **Dokun→sohbet**: zaten mevcuttu (`onContinue` tüm kart; uzun bas → eylem sheet).
   Emülatörde kanıtlandı: ICRA kartına dokunuş sohbet ekranını açtı
   (04-karta-dokun-sohbet.png), geri dönüş sekme üzerinden.

"Example description for a demo entry." kaynağı: **uygulamanın kendi
`DemoMask.description()` sabiti** (`data/DemoMask.kt:105`) — Ayarlar→Geliştirici'deki
tanıtım-maskeleme kipi açıkken beceri/MCP açıklamaları bu sabitle değiştirilir.
Uygulama placeholder'ıdır ve yalnız demo kipinde görünür; normal listede çıkmaz.
Karar: dokunulmadı (bilinçli tasarım; kipi kapatınca gerçek açıklama döner).

## F2 — Sohbet ekranı

Mevcut hâl Claude/ChatGPT/Grok düzenine zaten yakındı (kullanıcı sağda, asistan
solda; Markdown; canlı düşünce; streaming; alt giriş çubuğu + mikrofon bas-konuş
tur-11/tur-13; SpeedRow "yazıyor/düşünüyor" göstergesi; akışta dibe otomatik iniş +
klavye yaslaması tur-2/tur-8). Bu turda eklenen:
1. **Tarih-saat ayraçları**: `ChatItem.User/Assistant`a `ts` alanı eklendi; geçmiş
   yüklemesi `SessionMessage.timestamp`ı taşıyor. Saf `needsDaySeparator` gün
   değişiminde ortalanmış ayraç basar ("Bugün"/"Dün"/"12.09.2026"). Canlı akışta
   damga yok — geçmiş restorasyonunda belirir (sunucu damgası gerçek).
2. Ayraç harici balon/markdown/streaming koduna dokunulmadı (regresyon riski 0'a yakın).

## F3 — Test / kanıt

- Test: **617 tests, 0 fail, 0 error** (baz 598 + 19 yeni; `--rerun-tasks` ile sayaç
  XML'den parse). Yeni: `ChatDaySeparatorTest` (7), `SessionCardTur14Test` (8),
  `RelativeTimeTur14Test` (5). Mevcut 574+ kümesi korunuyor.
- APK: `app/build/outputs/apk/debug/app-debug.apk` 24.353 kB,
  md5 `4f246715b82dd922c1d5495738d4eab4`. Emülatör kurulum: `Success`.
- Emülatör kanıtı (`denetim/tur14/kanit/`): 01 launcher, 02 uygulama açılış,
  03 oturumlar listesi (yeni zaman formatı + anlamlı başlıklar),
  04 karta dokunuş→sohbet açılışı, 05 dönüş sonrası liste.
  `logcat -b crash` = 0 satır ( tüm adımlarda).
- Canlı sekme dökümü: "ICRA raporları V2 kuralları güncelleme / 54 mesaj /
  3 sa önce" — başlık+mesaj+anlamlı zaman birlikte.

## Kısıtlara uyum

relay/gateway/caddy/voice_api dokunulmadı; push yapılmadı. Commit'ler ayrık:
feat (kod+test) ve docs (rapor+kanıt).

## Dürüst kalanlar

- Geçmiş sekmesi (Canlı/Tümü/Geçmiş) emülatör kanıtında yalnız "Canlı" tab dökümde
  göründü; Geçmiş listesi kanıtı çekilemedi (tab düğümleri uiautomator'da görünmez —
  tur-8'de bilinen çökme-şeridi tuzağı). Davranış ortak `SessionRow` olduğu için
  risk düşük.
- Balon altı saat damgası (`clockLabel`) saf katmanda testli; UI'ye saat başına
  damga basmak eklemedi — gün ayırıcı yeterli bulundu, istenirse tek satır ek.
- "13 sa" şikâyetinin göründüğü ekran oturum kartıydı; `LiveSessionsScreen` ayrı
  `formatRelative` kullanıyor — aynı düzeltme her iki ekranı da kapsıyor (ortak fonksiyon).
