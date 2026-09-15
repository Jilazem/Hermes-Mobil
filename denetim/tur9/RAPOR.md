# Tur-9 — Arena 3D çalışma sahnesi (three.js, WebView + yerel asset)

Tarih: 2026-09-15 · Bot: android uzmanı · Dal: `feat/android-uzman-devralma`
Proje: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman` · Başlangıç HEAD: `4a9a8d3` (tur-8)
Push: **YOK** · Model/config değişimi: **YOK** · Canlı relay (9180/9181): **dokunulmadı** · Telefon: **dokunulmadı**
Emülatör: `emulator-5554` (1080×2400, 420dpi = 2.625 px/dp), paket `com.hermes.mobile.v2`.

İstek (birebir): *"Arena kısmında alt uzmanların çalışmasını üstlerinde sessin adı ile animasyon
yaparmısın threejs tasarimi kullan"*.

---

## 1. Ne yapıldı

| Katman | Dosya | İş |
|---|---|---|
| Sahne (asset) | `app/src/main/assets/arena/three.min.js` | three.js **r147** (MIT, 607.784 bayt, lisans başlığı dosyada korunur) — Mac'ten indirildi, **çalışma anında indirme YOK** |
| Sahne (asset) | `assets/arena/LICENSE-three.txt` | MIT lisans metni (1081 bayt) |
| Sahne (asset) | `assets/arena/arena3d.html` + `arena3d.js` (24.200 bayt) | Düşük poligonlu gladyatör-bot figürleri, billboard ad etiketleri, durum animasyonları |
| Saf çekirdek | `ui/ArenaSceneModel.kt` | `ArenaState`(+canlı oturumlar) → JSON / faz-durum-rozet eşlemesi / zarif geri düşme kararı — **tamamı yan etkisiz, testli** |
| Android köprü | `ui/ArenaSceneView.kt` | WebView host (güvenlik ayarları) + JS→Kotlin olayları + statik kart fallback'i |
| Durum takibi | `ArenaViewModel.kt` | Her bot bir figür: `waiting → working → done/error`, tur 2 ve sentez kimlikleri |
| Ekran | `ui/ArenaScreen.kt` | Sahne Arena'nın üstünde (~%35 hedef); kurulum/çipler/kipler/sonuç kartları/Durdur **aynen korundu** |
| Bağlayıcı | `MainActivity.kt` | Arena'ya canlı oturum listesi geçildi (boşta sahne için) |

### 1.1 Figürler ve etiketler
- Figür: gövde + omuz kuşağı + baş/vizör + miğfer tepesi + kollar (ayrı grup, animasyonlu) + bacaklar +
  gladyatör kalkanı + enerji çekirdeği; koyu tema (mavi-gri/cyan), düşük poligon.
- **Etiket her figürün ÜSTÜNDE**: billboard düzlem (kameraya döner), CanvasTexture — ad + rozet
  (`Tur 1` / `Tur 2` / `Sentez` / `Canlı`) + durum noktası. `depthTest:false` + `renderOrder:30`:
  hiçbir fazda öndeki figür etiketi örtmez. Ad kutuya sığmazsa punto otomatik küçülür (ölçüm: 62 → 36/40).

### 1.2 Durum animasyonları
- **bekliyor**: yavaş nefes (gövde ölçeği + hafif salınım), çekirdek nabzı, sönük çerçeve.
- **çalışıyor**: hızlı gövde salınımı, **klavye/titreşim kolu hareketi**, parlak çekirdek, dönen enerji
  halkası, yükselen kıvılcım parçacıkları, cyan çerçeve.
- **bitti**: kollar yukarı (zafer), yeşil onay parlaması + **iki genişleyen halka**.
- **hata**: kırmızı kesinti — titreme, kırmızı halka, hızlı vizör parlaması, kart sönümlenmesi.
- **Durdur sonrası**: figürler silinmez, sakin beklemeye döner (animasyon donmaz, idle nefesine iner) — `ArenaPhase.Stopped`.

### 1.3 Veri köprüsü
- Kotlin → JS: `window.arenaScene.setData(<json>)` ve `setActive(<bool>)`.
  JSON: `{phase, theme, figures:[{id,name,state,badge}]}` — `ArenaSceneJson.encode()` ile elle üretilir
  (birim testte `org.json` saptır; kaçış kuralları testlidir).
- JS → Kotlin: `__ArenaBridge.onSceneEvent(type, detail)` → `ready` / `nowebgl` / `error` / `data`.
- WebView ayarları: `javaScriptEnabled=true`, **`allowFileAccess=false`**, `allowContentAccess=false`,
  **`blockNetworkLoads=true`**, `cacheMode=LOAD_NO_CACHE`; `shouldInterceptRequest` **yalnız
  `file:///android_asset/arena/`** yolunu geçirir, diğer her isteği boş gövdeyle keser; konsol satırları
  `ArenaScene` etiketiyle logcat'e düşer. Ölçüm: sahne açıkken **hiç "engellendi" satırı yok** — sayfa
  tamamen yerel, ağa çıkmıyor.
- Performans: `pixelRatio ≤ 2`; ekran/sahne görünmezken (`ON_PAUSE` + `visibilitychange`) render döngüsü durur.

---

## 2. Test / derleme / kurulum (kanıt)

| Kontrol | Sonuç |
|---|---|
| `gradle testDebugUnitTest assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** (41 görev yürütüldü) |
| Birim test (JUnit XML'den sayıldı) | **378 test, 0 hata, 0 atlanan** (tur-8'de 344 → **+34**) |
| Yeni testler | `ArenaSceneTest` (34): faz eşlemesi, figür/durum/rozet, kimlik kararlılığı, canlı oturum figürleri, JSON kaçışları, JS çağrısı ASCII güvenliği, geri düşme kararı |
| APK | `app/build/outputs/apk/debug/app-debug.apk` · **24.156.558 bayt** · md5 **6f3ea001936da1f06b2ddddc8ff7cdc8** |
| Asset yerleşimi | `unzip -l`: `assets/arena/{three.min.js 607784, arena3d.js 24200, arena3d.html, LICENSE-three.txt}` |
| Kurulum | `adb install -r` → **Success** |
| Çökme tamponu | `adb logcat -d -b crash` → **boş (0 satır)** |

---

## 3. Uçtan uca kanıt

### 3.1 Emülatörde köprü (gerçek Arena koşusu, 2 bot — "Beyin fırtınası")
`denetim/tur9/logcat-sahne-kopru.txt` (logcat, kırpılmadan):

```
arena3d etiket "default" rozet=Tur 1 durum=working cerceve=#5ec8ff punto=62
arena3d etiket "ac"      rozet=Tur 1 durum=working cerceve=#5ec8ff punto=62
arena3d data 2|running
...
arena3d etiket "default" rozet=Tur 1 durum=error cerceve=#ff4d5e punto=62
arena3d etiket "ac"      rozet=Tur 1 durum=error cerceve=#ff4d5e punto=62
arena3d data 2|done
```

- **bekliyor → çalışıyor**: Arena başlar başlamaz iki figür `working` (cyan) oldu; ekranda 2 figür
  çizildi (ölçüm: `tri=2604 cizim=50 figur=2`, ~40 fps, `glerr=0`).
- **hata (kırmızı kesinti)**: sunucu bu koşuda `AI hatası` döndürdü → iki figür `error`/`#ff4d5e` oldu.
  (Not: "bitti" hâli bu koşuda sunucu hatası nedeniyle oluşmadı; onay parlaması 3.2'de doğrulandı.)
- Boşta (Idle): sunucunun çalışan oturumları figür oldu — `data 3|idle`, etiketler **"Yaz WhatsApp gelen
  mesajları"**, **"Bot Arena beyin fırtınası t…"**, **"6f3e9429"**, rozet **Canlı** (Türkçe karakterler
  köprüden bozulmadan geçti; uzun ad kırpıldı).
- Sayfa yükü: `sahne yukleniyor: file:///android_asset/arena/arena3d.html` → `arena3d boot` →
  `arena3d ready figur:0 yazilimGL=1` → `sayfa yuklendi:` → veri basıldı. **JS hatası/konsol kirliliği yok.**
- WebView düğümü `uiautomator dump`ta görünür: `[32,309][1053,1081] WebView: 'Arena 3D'` → 772px = **294dp**
  (914dp ekranın ~%32'si; hedef %35-40'ın alt sınırı, pencere insetleri düşüyor).

### 3.2 Sahnenin görsel doğrulaması (Chromium — **aynı asset baytları**)
Emülatörün yazılım GL'i sahneyi sunamadığı için (bkz. 4.1) sahne, **birebir aynı dosyalarla**
(`arena3d.js`, `three.min.js`, aynı JSON şeması) masaüstü Chromium'da koşturuldu; 4 durum + boşta:

| Görüntü | Sahne | Görsel modelin okuduğu (kırpma + 2-4x) |
|---|---|---|
| `sahne-1-bekliyor.png` | `phase=ready`, 2 figür `waiting` | 2 figür, etiketler okunur, sakin duruş |
| `sahne-2-calisiyor.png` | alfa `done`, beta+`gama` `working` | **"alfa / Tur 1", "beta / Tur 1", "gama / Tur 2"** — adlar ve rozetler okunur, gama rozeti turuncu |
| `sahne-3-bitti.png` | 3 figür `done` | **"alfa / Tur 1", "beta / Tur 2", "gama / Sentez"**, durum noktaları yeşil (onay) |
| `sahne-4-durduruldu.png` | `phase=stopped` | figürler silinmemiş, beklemeye dönmüş (animasyon durmuyor) |
| `sahne-5-canli-bosta.png` | `phase=idle` + canlı oturumlar | **"hermes-ustasi-devriye / Canlı", "Greeting / Canlı"** |

Kanıt scriptleri: `/tmp/tur9/kanit-hazirla.py` (senaryo üretimi), `/tmp/tur9/kanit-cek.sh`
(Chrome headless ekran görüntüsü), `/tmp/tur9/pngfind.py` + `/tmp/tur9/pngtop.py` (piksel ölçümü),
`/tmp/tur9/vision2.py` (yerel multimodal ile okuma; `qwen3.8-flash-next-S`).

---

## 4. Dürüst kalanlar

1. **Emülatör (yazılım GL) WebGL karesini tam sunmuyor — sahne emülatör görüntüsünde koyu/kısmi.**
   Ölçüm: renderer gerçekten çiziyor (`tri=816…2604`, `cizim=25…50`, ~40 fps, `glerr=0`),
   etiket tuvali dolu (`doluluk=95958/128000`, punto 36-62), ama `screencap` karesi bayat/kısmi:
   `readPixels` ile kare içeriği (örn. `fb_orta=214,66,0`) ekran görüntüsünde yok; sahne bölgesi bazı
   karelerde tek renk. GL dizesi: `Android Emulator OpenGL ES Translator (ANGLE … SwiftShader)`.
   Uygulanan dayanıklılık: yazılım GL sezilirse `antialias:false` + `preserveDrawingBuffer:true`
   (gerçek cihazda AA açık, `preserveDrawingBuffer` kapalı). Sahne durumlarının görsel kanıtı bu yüzden
   Chromium'dan alındı; Android tarafı köprü loglarıyla kanıtlandı. **Gerçek telefonda donanım GL yolu
   standarttır** (kullanıcı telefonu bu turda ellenmedi).
2. **"bitti" hâli emülatörde oluşmadı**: koşuda sunucu `AI hatası` döndürdü → sahne doğru biçimde
   `error` (kırmızı) oldu. Onay parlaması (yeşil halka + zafer kolları) Chromium'da doğrulandı.
3. **Sahne yüksekliği 294dp ≈ %32** (hedef %35-40). `screenHeightDp*0.35` istendi; ölçülen WebView
   kutusu pencere insetleri nedeniyle 294dp. İstenirse 0.40'a çekilir.
4. **Aynı bot kapışmada iki figür** (tur 1 + tur 2) olarak görünür — bilinçli: tur ayrımı rozetle
   gösteriliyor; 4 botlu kapışmada sahne 8 figüre kadar kalabalıklaşabilir.
5. **Çökme şeridi (tur-8 KALAN-4)** Arena başlığını kapatmaya devam ediyor (banner y 152-393,
   WebView y 309'dan başlıyor, ~84px örtüşme). Bu turun kapsamı dışı; şerit kapatılınca sorun yok.
6. **Süre hedefi aşıldı**: hedef 40-60 dk, gerçekleşen ~140 dk. Aşımın nedeni emülatörde sahnenin
   görünmemesinin kök nedenini ölçmekle geçen tur (WebView çift bağlanma, canvas 0 yükseklik,
   yazılım GL sunumu) — bkz. 4.1. Kapsam tamamlandı, iş yarım bırakılmadı.

---

## 5. Değişen / eklenen dosyalar

- **YENİ** `app/src/main/assets/arena/three.min.js` (three.js r147, MIT, 607.784 B)
- **YENİ** `app/src/main/assets/arena/LICENSE-three.txt`
- **YENİ** `app/src/main/assets/arena/arena3d.html`, `arena3d.js`
- **YENİ** `app/src/main/java/com/hermes/mobile/ui/ArenaSceneModel.kt` (saf model + JSON + fallback kararı)
- **YENİ** `app/src/main/java/com/hermes/mobile/ui/ArenaSceneView.kt` (WebView host + fallback kartları)
- **YENİ** `app/src/test/java/com/hermes/mobile/ArenaSceneTest.kt` (34 test)
- `ArenaViewModel.kt` — `ArenaState.figures/stopped`, figür takibi, Durdur'da sakinleşme, hata işaretleme
- `ui/ArenaScreen.kt` — 3D sahne kutusu + sahne durumu + fallback yerleşimi (mevcut işlev aynen)
- `MainActivity.kt` — `ArenaScreen(liveSessions = live.sessions)`
