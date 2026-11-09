# K2-SES-KANIT — Ses/EMA kök neden, onarım ve birim test kanıtı

İş: GÖREV HATTI #1021-4b97c7 (K2)
Proje: `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration`
Tarih: 21.10.2026
Kapsam: yalnız kaynak + birim test. **APK derlenmedi/kurulmadı** (istek gereği).

---

## 1) Girdiler ve kaynaklar

- Proje kökü: `12-PROJELER/Hermes-Mobil-v5-integration` (Gradle, Kotlin/Compose).
- İncelenen dosyalar: `assistant/EmaVoice.kt`, `assistant/JarvisVoice.kt`,
  `data/EmaOfflineEngine.kt`, `data/EmaVoiceTransport.kt`, `data/AppSettings.kt`,
  `data/JarvisLogic.kt` (`SpeakLevelLogic.parseWavPcm`), `ui/EmaSettingsCard.kt`,
  `ui/SettingsScreen.kt`, `ui/HomeFeedLogic.kt`.
- JDK: `/opt/homebrew/opt/openjdk@17` (makinede varsayılan `java` yok; `JAVA_HOME`
  bu yola verilerek Gradle çalıştırıldı).
- Test çerçevesi: JUnit4 (`app/src/test/java/com/hermes/mobile`).

## 2) Kök nedenler (kanıt: dosya:satır)

**N1 — Yedek WAV gövdesi sabit 44. ofsetten okunuyordu.**
`EmaOfflineEngine.kt` yedek ses akışında `wav.copyOfRange(44, wav.size)` kullanılıyordu.
Piper/`piper-tts` çıktısında `fmt ` bloğundan sonra `LIST/INFO` gibi ek bloklar
bulunabildiğinden gerçek `data` gövdesi 44'ten sonra başlar; sonuç: metadata baytları
PCM sanılıp hoparlöre yazılıyor → çatlak/bozuk ses, kaymış örnek hizası.

**N2 — 16-bit PCM tek bayt sınırında parçalanıyordu.**
Parça boyu 4096 sabit olmasına karşın son parça tek bayt kalabiliyor, ayrıca
akıştan gelen parçalar hizasız birleştirilebiliyordu → örnek baytları kayıyor
(cızırtı). Çözüm: her parça çift bayt sınırında; tek kalan yarım örnek atılır.

**N3 — EMA hattı hatasında tam sessizlik.**
`EmaVoice` sınıfı `allowAndroidFallback = false` ile kuruluyordu; EMA sunucusu
yanıt vermediğinde (ağ yok, model yüklü değil, profil boş) hiçbir ses çıkmıyor,
kullanıcı "ses yok" ile kalıyordu. Ayrıca arayüzde "EMA / Sistem" seçimi yoktu;
kullanıcı EMA bozukken telefon TTS'ine geçemiyordu.

**N4 — Örnek hızı değişince çıkış hattı yeniden kurulmuyordu.**
EMA 48000 Hz, yedek Piper 22050 Hz üretir. `AudioTrack` hızı ilk kurulumda
sabitleniyor, hız değişince hat yeniden kurulmadığından ses hızı/uzunluğu bozuk
çıkıyor, boşaltma (drain) süresi sabit varsayıldığından kuyruk kesiliyordu.

## 3) Uygulanan onarımlar

| Dosya | Değişiklik |
|---|---|
| `data/EmaAudioLogic.kt` (yeni) | Saf Kotlin ses mantığı: `pcmFromWav` (RIFF bloklarını yürüyerek gerçek `data` gövdesi + `fmt ` örnek hızı, çift kanalı mono'ya indirir), `evenChunks` (çift bayt hizalı parçalama, tek yarım örneği atar), `frames`, `drainDeadlineMs` (hıza göre süre). |
| `data/JarvisLogic.kt` | `SpeakLevelLogic.parseWavPcm` yeniden kullanıldı; sabit 44 ofseti kaldırıldı. |
| `data/EmaOfflineEngine.kt` | Yedek akış artık `EmaAudioLogic.pcmFromWav` + `evenChunks` kullanıyor; hız WAV başlığından geliyor (22050 varsayımı yok). |
| `assistant/EmaVoice.kt` | `allowAndroidFallback = true` — EMA başarısızsa son çare telefon TTS'i; sessizlik yok. |
| `data/EmaSpeechChoice.kt` (yeni) | `route(emaPlayback, emaConfigured)` + `label`/`describe`: "ema" → EMA, "sistem" → telefon TTS; EMA yapılandırılmamışsa sessizlik yerine sistem. |
| `data/AppSettings.kt` | `emaPlayback` alanı (varsayılan `"ema"`) + `toVoicePrefs()` taşıması. |
| `assistant/JarvisVoice.kt` | Ses yolu seçime göre kurulur (`route(...)`); EMA yoksa `SystemTtsVoice`. |
| `ui/EmaSettingsCard.kt`, `ui/SettingsScreen.kt` | "Ses: EMA" / "Ses: Sistem (telefon)" seçim çipleri; seçim anında ses nesnesi yeniden kurulur. |
| `app/src/test/.../K2VoiceRepairTest.kt` (yeni) | 12 birim testi: WAV gövde/hız, LIST bloklu WAV, çözülemeyen WAV, çift bayt hizalama, kare/boşaltma süresi, seçim yönlendirmesi, ayar taşıma. |

## 4) Gerçekte çalıştırılan testler (komut + çıktı)

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
cd 12-PROJELER/Hermes-Mobil-v5-integration
./gradlew :app:compileDebugKotlin --offline        → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --tests "com.hermes.mobile.K2VoiceRepairTest" --offline
                                                    → BUILD SUCCESSFUL (12 test)
./gradlew :app:testDebugUnitTest --offline          → BUILD SUCCESSFUL
```

Tüm birim test paketi (JUnit XML sayımı):

```
tests 1318  failures 0  errors 0  skipped 0
```

K2 testlerinin kapsadığı hatalar:
- `liste bloklu wavda metadata ses diye calinmaz` — sabit 44 ofsetinin ürettiği
  bozuk gövdeyi doğrudan çürütür (naif dilim ile çözümlenen gövde eşit değil).
- `parcalar cift bayt sinirinda kesilir`, `cift bayt girişte kayip yok` — hizalama.
- `kare ve bosaltma suresi hiza gore hesaplanir` — 48000 vs 22050 farkı.
- `sistem secimi emayi devre disi birakir`, `ema yapilandirilmadiysa sessizlik yok sistem sesi`.
- `emaPlayback varsayilani ema ve VoicePrefs tasir`.

## 5) Bağımsız kontrol durumu

- Derleme + 1318 birim testi: **gerçekten çalıştırıldı, geçti** (yukarıdaki komutlar).
- Cihaz/emülatör testi: **YAPILMADI** — istek "APK derleme YOK" olduğu için APK
  üretilmedi, kurulum ve gerçek cihaz ses testi yapılmadı.
- Bu nedenle "ses artık cihazda düzgün çalışıyor" iddiası **kurulmamıştır**;
  kanıtlanan şey mantığın birim testlerle doğruluğudur.
- Bağımsız denetim (cd-denetmen) yapılmadı.

## 6) Açık riskler

1. `AudioTrack` yeniden kurulumu yalnız hız değişiminde tetikleniyor; kanal
   sayısı 2 çıkarsa mono'ya indirme `parseWavPcm` içinde — bu yol birim testte
   yalnız mono ile sınandı, çift kanal WAV için test eklenmedi.
2. Telefon TTS'ine düşüş (`allowAndroidFallback = true`) yalnız kod düzeyinde;
   gerçek cihazda TTS motoru yoksa hâlâ sessizlik olabilir (cihaz testi gerekir).
3. Kalan `22050` geçişleri yalnız açıklama/yorum satırlarıdır (kaynak kodda sabit
   varsayım kalmadı: `grep -rn 22050 app/src/main/java` → 5 yorum).
4. Çalışma ağacında bu işle ilgisiz başka değişiklikler var (`MainActivity.kt`,
   `GatewayWsClient.kt`, `HomeFeed*` vb.); bunlar bu işin kapsamı dışındadır.

## 7) Üretilen/değişen dosyalar (tam yollar)

Üretilen:
- `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration/app/src/test/java/com/hermes/mobile/K2VoiceRepairTest.kt`
- `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration/app/src/main/java/com/hermes/mobile/data/EmaAudioLogic.kt`
- `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration/app/src/main/java/com/hermes/mobile/data/EmaSpeechChoice.kt`
- `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration/K2-SES-KANIT.md`

Değişen:
- `.../app/src/main/java/com/hermes/mobile/assistant/EmaVoice.kt`
- `.../app/src/main/java/com/hermes/mobile/assistant/JarvisVoice.kt`
- `.../app/src/main/java/com/hermes/mobile/data/AppSettings.kt`
- `.../app/src/main/java/com/hermes/mobile/data/EmaOfflineEngine.kt`
- `.../app/src/main/java/com/hermes/mobile/data/JarvisLogic.kt`
- `.../app/src/main/java/com/hermes/mobile/ui/EmaSettingsCard.kt`
- `.../app/src/main/java/com/hermes/mobile/ui/SettingsScreen.kt`

SHA-256:
```
bd665866c7ea5763f4b587a4a015a3ac3e7f64fd5e0085c1ff484bd96e760d89  app/src/test/java/com/hermes/mobile/K2VoiceRepairTest.kt
d5a0424765298f13ff3a096ce8d39ed10d87e80d29eb8474a9a5f55bbd09ae54  app/src/main/java/com/hermes/mobile/data/EmaAudioLogic.kt
00dc9ab5dac3b4591536d33f8fdc189a3483f8773cf99d158f80bf49aed9f965  app/src/main/java/com/hermes/mobile/data/EmaSpeechChoice.kt
29630f4b190d20cc35e0f8203e89b84bad28b5791f7f8c94949e7b49539b860f  app/src/main/java/com/hermes/mobile/assistant/EmaVoice.kt
```
