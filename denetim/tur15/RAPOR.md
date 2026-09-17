# TUR-15 RAPOR — Arena'da Outrun yarış modu (three.js sahnesinin gömülmesi)

Tarih: 2026-09-17 · Dal: feat/tur15-outrun (taban 53e4e08) · Push yok

## Yapılanlar

1. **Varlıklar** — `app/src/main/assets/arena/outrun.html` + `outrun.js` kaynaktan
   (`hermes-workspace/arena-outrun/`) birebir kopyalandı (`cmp` farksız).
   `three.min.js` TEKRAR KOPYALANMADI: arena/ altındaki mevcut dosya ile kaynak aynı
   (sha1 `912bc7c150055cd324dfee0ff243cab74e7c6606`, r147) → iki sahne paylaşıyor.
   `LICENSE-three.txt` zaten mevcut. APK içinde 6 arena varlığı doğrulandı (`unzip -l`).
2. **Mod seçici** (`ArenaScreen.kt`) — "İş sahnesi" (varsayılan) | "Outrun yarış" çipleri.
   Seçim `AppSettings.arenaSceneMode` ("work"/"outrun") ile kalıcı; bilinmeyen/eski kayıt
   iş sahnesine düşer (`ArenaSceneKind.fromId`). Outrun'da sahne yüksekliği ekranın ~%50'si.
3. **WebView yükleyici** (`ArenaSceneView.kt`) — tek fabrika `createArenaWebView`: iki sahne
   aynı güvenlik sözleşmesiyle doğar (JS açık, dosya/içerik erişimi kapalı, ağ yükü kapalı,
   yalnız `file:///android_asset/arena/` izinli, `..`/`%2e` kaçışı reddedilir,
   `__ArenaBridge` köprüsü). Tek fark: Outrun en iyi skoru `localStorage`'da tuttuğu için
   DOM depolaması açık.
   - (a) Yükleme + köprü: mevcut arena3d deseni; JS olayları (`ready/start/pause/resume/
     gameover/nowebgl/error`) saf durum makinesine akar.
   - (b) Yaşam döngüsü: `ArenaOutrunHolder` WebView'i sekme kompozisyonunun DIŞINDA
     (HermesApp kapsamı) tutar. Sekme gizlenince/uygulama arka plana düşünce:
     `outrun.pause()` → `setActive(false)` → `WebView.onPause()`. Dönüşte `onResume()` +
     `setActive(true)`; yarış duraklatma ekranında bekler (Devam kullanıcının dokunuşu).
     İş sahnesine geçilince WebView `destroy` edilir (GPU bağlamı tutulmaz).
   - (c) Geri tuşu: yarış KOŞARKEN `BackHandler` önce duraklatır; duraklatılmışken sahne
     geri tuşunu tüketmez → kabuğun mevcut "Çıkılsın mı?" akışı çalışır.
4. **Dokunmatik** — WebView dokunuşları doğrudan sahneye gider (outrun.js pointer/touch
   işleyicileri); ek jest kodu yazılmadı. Sahne kutusu alt gezinme çubuğu/sistem jest
   kenarlarından uzakta.
5. **Performans** — `android:hardwareAccelerated="true"` manifest `<application>`
   seviyesine açıkça yazıldı; renderer sekme değişiminde `onPause/onResume` ile duraklıyor.
6. **Saf karar katmanı** (`ArenaSceneMode.kt`, yeni) — kip + kalıcılık kimliği, varlık yolu
   çözümlemesi, URL inşası (deterministik, yüzde-kodlu), istek süzgeci, JS komutları,
   yaşam döngüsü durum makinesi (`arenaSceneReduce` / `arenaSceneCommands` /
   `arenaBackConsumed`). Outrun için ilk kare zaman aşımı 12 sn (yazılım GL'de shader derlemesi).

## Test

- Komut: `./gradlew --no-daemon testDebugUnitTest assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- JUnit XML sayımı (`denetim/count_tests.py`): **635 tests, 0 failures, 0 errors, 0 skipped**
  (baz 617 + 18 yeni).
- Yeni: `ArenaSceneModeTest` (18): kalıcılık (varsayılan, fromId toleransı, sabit kimlikler,
  AppSettings JSON gidiş-dönüş, alanı olmayan eski kayıt), varlık yolu (düz ad dışı red),
  URL (iki kip tek kök, parametre sıralama/kodlama), istek süzgeci (`..`, `%2e`, dış URL),
  JS olay eşlemesi, JS komut hedefleri, kip başına zaman aşımı, durum makinesi
  (akış, sekme gizle/göster, uygulama arka plan, geri tuşu, hata terminal→RELOAD,
  iş sahnesine outrun.pause gitmemesi). Mevcut 617 test korunuyor.

## APK

`app/build/outputs/apk/debug/app-debug.apk` — 24.394.804 bayt,
md5 `ef970d93c617c0d531c7b109b7fabd76`. Emülatöre kurulum: `Success`.

## Emülatör kanıtı (`denetim/tur15/kanit/`)

AVD hermes-v2 (headless, swiftshader). Oyun durumu WebView DevTools (CDP) ile sayfadan
okundu: `denetim/tur15/cdp_eval.py`; ham kayıt `cdp-yasam-dongusu.txt`.

| Dosya | Kanıt |
|---|---|
| 01-acilis.png | Uygulama açılışı |
| 02-arena-is-sahnesi.png | Arena: seçici görünür, "İş sahnesi" seçili, arena3d sahnesi |
| 03-outrun-acildi.png | "Outrun yarış" seçildi → OUTRUN menüsü (BAŞLA) yüklendi |
| 04-yaris-kosuyor.png | Yarış koşuyor (MESAFE 580, HIZ 328); CDP `durum=running` |
| 05-sekme-sohbet.png | Sohbet sekmesine geçiş; CDP `durum=paused`, `document.hidden=true`, kare sayacı 6 sn'de 789→789 (render durdu) |
| 06-donus-duraklatilmis.png | Arena'ya dönüş: "DURAKLATILDI, Mesafe 690 m" — durum korunmuş, kaldığı yerde |
| 07-kosarken-geri-tusu-duraklatti.png | Koşarken geri tuşu → `paused` (mesafe 1048), uygulama önde kaldı, diyalog yok |
| 08-duraklatilmiskken-geri-cikis-diyalogu.png | Duraklatılmışken geri tuşu → kabuğun "Çıkılsın mı?" diyalogu (sahne tüketmedi); "Kal" seçildi |
| 09-yeniden-acilis-kip-kalici.png | force-stop + yeniden açılış → Arena doğrudan Outrun kipinde (kalıcılık) |
| 10-is-sahnesine-donus.png | "İş sahnesi"ne dönüş; DevTools sayfa listesinde outrun.html yok, yalnız arena3d.html (WebView yok edildi) |

`logcat -b crash` = 0 satır (tüm adımlar sonunda).

## Kısıtlara uyum

relay/gateway/caddy/voice_api'ye dokunulmadı; Kahya'ya dokunulmadı; push yok.
Commit'ler ayrık: feat (kod+varlık+test) ve docs (rapor+kanıt).

## Dürüst kalanlar / notlar

- **Teşhis kancaları üründe KALDI:** `outrun.js` içindeki `window.outrun._step/_render/_katman`
  (~16 satır). Sahne sahibi notuyla uyumlu; zararsız (yalnız JS'ten çağrılır). İstenirse
  tek commit'le çıkarılır — varlık kaynakla birebir tutuldu.
- Emülatör yazılım GL'de sahne ~1-3 fps; bu hızda adb dokunuşlarının bir kısmı sayfaya
  "click" üretmedi (BAŞLA/DEVAM birkaç denemede tuttu; CDP olay kaydında sonunda
  `pointerdown→touchstart→pointerup→touchend→click:resumeBtn` tam dizi görüldü).
  `cdp-yasam-dongusu.txt`teki ilk "[devam dokunusu] durum=paused" / "[geri tusu 1]"
  satırları bu tutmayan denemedir (oyun zaten duraklatılmışken). Gerçek cihazda
  donanım GL ile tekrar denenmeli — canlı telefon yok.
- Uygulama açılışında önceki bir oturumdan kalan "Önceki açılış bir çökmeyle kapandı
  (ForegroundServiceDidNotStartInTime)" bandı göründü; bu tur kodu ile ilgisiz
  (emülatör yavaşlığında ön plan servis zaman aşımı), dokunarak kapatıldı. Bu koşuda
  crash buffer boş.
- Dokunmatik direksiyon (basılı tut/sürükle) emülatörde ayrıca ölçülmedi; yalnız
  buton dokunuşları kanıtlandı.
