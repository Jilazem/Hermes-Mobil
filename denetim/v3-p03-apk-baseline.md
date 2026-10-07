# Hermes Mobil V3 P03 — kirli APK baseline

Tarih: 2026-10-07
Rol: cd-android
Kök: /Users/gokhanuzman/hermes-workspace/Hermes-Mobil
Görev: t_686df888 (promt 53yz9p)
Ham istek: Commit/stash/push yok. Mevcut kirli agacta assembleDebug.

Git mutasyonu yok (commit, add, stash, reset, checkout, clean, push, dal açma yok). Kaynak .kt/.xml silinmedi ve değiştirilmedi. Win11'e bağlanılmadı. Çevrimiçi ikinci deneme kullanılmadı. APK uydurulmadı, kurulu sayılmadı, telefona yüklenmedi.

## Dal ve HEAD

- Dal: feat/tur29a-ui-streaming
- HEAD: 36f895bdeb65703f30a0c2bbd9cc2e8f04b4443a
- Kısa: 36f895b
- Mesaj: TUR-29A kanit + RAPOR: taze test 78/897/0, APK md5, emülator kurulum/am start/crash-bos

## Başlangıç git status -sb

Porcelain 34 satır (21 M + 13 ??). Bu kirlilik baseline'ın parçasıdır; temizlenmedi.

```
 M RAPOR.md
 M app/src/main/assets/arena/cage-engine.js
 M app/src/main/assets/arena/cage.html
 M app/src/main/assets/arena/cage.js
 M app/src/main/java/com/hermes/mobile/ArenaViewModel.kt
 M app/src/main/java/com/hermes/mobile/ChatViewModel.kt
 M app/src/main/java/com/hermes/mobile/MainActivity.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisBrain.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisEngine.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisVoice.kt
 M app/src/main/java/com/hermes/mobile/car/HermesCarService.kt
 M app/src/main/java/com/hermes/mobile/data/ArtemisClient.kt
 M app/src/main/java/com/hermes/mobile/data/ArtemisLogic.kt
 M app/src/main/java/com/hermes/mobile/data/GatewayWsClient.kt
 M app/src/main/java/com/hermes/mobile/data/LocalModelClient.kt
 M app/src/main/java/com/hermes/mobile/data/ReplyService.kt
 M app/src/main/java/com/hermes/mobile/ui/ArenaSceneModel.kt
 M app/src/main/java/com/hermes/mobile/ui/ChatMenu.kt
 M app/src/main/java/com/hermes/mobile/ui/ChatScreen.kt
 M app/src/test/java/com/hermes/mobile/ChatMenuTest.kt
 M app/src/test/java/com/hermes/mobile/LiveModelChoiceTest.kt
?? app/src/main/java/com/hermes/mobile/data/SessionBinding.kt
?? app/src/test/java/com/hermes/mobile/SessionContinuationTest.kt
?? denetim/deneme-tur28-webapp-kalip.json
?? denetim/deneme-v3-p02-envanter-qc.json
?? denetim/session-arena-20261003-build.log
?? denetim/tur27/
?? denetim/tur28-build.log
?? denetim/tur28-denetmen-test.log
?? denetim/tur28/
?? denetim/v3-p02-envanter.md
?? denetim/verdict-tur28.json
?? denetim/verdict-v3-p02-envanter-qc.json
?? docs/oturum-inceleme-tur27.md
```

Kaynak: scratch `v3-p03-git-porcelain-before.txt` (1642 bayt, 34 satır). Derleme sonrası `v3-p03-git-porcelain-after.txt` ve `v3-p03-git-porcelain-now.txt` ile `diff -u` DIFF_EXIT=0 ve NOW_DIFF_EXIT=0. Kaynak listesi derleme yüzünden büyümedi.

## Derleme

Komut (birebir, log satır 5):

```
./gradlew :app:assembleDebug --offline -Pkotlin.compiler.execution.strategy=in-process
```

Prompttaki çıplak `./gradlew :app:assembleDebug --offline` komutuna ek olarak `-Pkotlin.compiler.execution.strategy=in-process` verildi. Çevrimiçi ikinci deneme yok: logda FAILED yok, bağımlılık eksikliği yok.

JDK:

```
JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
openjdk version "17.0.20.1" 2026-08-18
OpenJDK Runtime Environment Temurin-17.0.20.1+1 (build 17.0.20.1+1)
```

Log: `/Volumes/EX/007-HERMES-M4-LIVE/20-HERMES/profiles/cd-android/cache/scratch/v3-p03-assembleDebug-offline.log` (53 satır, 2483 bayt).

Gradle sonucu (log satır 51, birebir; üçüncü karakter U+0130, ASCII BUILD değil):

```
BUİLD SUCCESSFUL in 7s
35 actionable tasks: 35 up-to-date
```

`:app:assembleDebug` ve `:app:packageDebug` UP-TO-DATE. NO-SOURCE: mergeDebugNativeDebugMetadata, compileDebugJavaWithJavac, compileDebugShaders. SKIPPED: checkKotlinGradlePluginConfigurationErrors. Logda FAILED yok.

### Derleme exit code

Sayısal exit code YAKALANMADI. 0 yazılmadı.

- Log satır 53 birebir: `GRADLE_EXIT=` (eşitten sonra karakter yok).
- `v3-p03-offline.exit` hex: `00000000: 0a` (yalnız satır sonu, sayı yok).
- zsh'te boş `exit` de 0 döner (`ZSH_EXIT_EMPTY=0`). Bu yüzden çıkış dosyası ve doğrulama kabuğunun exit 0 değeri Gradle exit sayılmadı.

Sonuç cümlesi logdan: `BUİLD SUCCESSFUL in 7s`. Sayısal kod raporda yok çünkü yakalanmadı.

## APK

`find app/build/outputs/apk -name '*.apk'` iki dosya verdi. Bu koşum APK'yı yeniden paketlemedi: mtime 2026-10-04 21:42:40, Gradle UP-TO-DATE. Hash'ler 2026-10-07 disk `shasum -a 256` çıktısı (arşivdeki shasum satırlarıyla aynı).

```
be42732965607bff44e9134e4e47d71ce17b1679a4f542e4e78cfa9372b80fd6  app/build/outputs/apk/debug/app-x86_64-debug.apk
2a36a6a7f4ead9c55a6c9545b4f36d90032e5dc23ca6b4f20c3dfb2132ec7603  app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

| APK | boyut (bayt) | mtime | sha256 |
| --- | --- | --- | --- |
| /Users/gokhanuzman/hermes-workspace/Hermes-Mobil/app/build/outputs/apk/debug/app-x86_64-debug.apk | 65455220 | 2026-10-04 21:42:40 | be42732965607bff44e9134e4e47d71ce17b1679a4f542e4e78cfa9372b80fd6 |
| /Users/gokhanuzman/hermes-workspace/Hermes-Mobil/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk | 60086127 | 2026-10-04 21:42:40 | 2a36a6a7f4ead9c55a6c9545b4f36d90032e5dc23ca6b4f20c3dfb2132ec7603 |

`stat -f '%N %z %Sm'` (2026-10-07):

```
app/build/outputs/apk/debug/app-x86_64-debug.apk 65455220 2026-10-04 21:42:40
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk 60086127 2026-10-04 21:42:40
```

APK yok değil. Hata alıntısı yok (log FAILED içermiyor).

## Kaynak diff

Derleme kaynak listesini büyütmedi. porcelain-before / after / now üçü de 34 satır, 1642 bayt, `diff -u` çıkışı boş. DUR koşulu tetiklenmedi. Geri alma yok.

Bu rapor dosyası (`denetim/v3-p03-apk-baseline.md`) istenen çıktıdır; derlemenin ürettiği kaynak değişikliği değildir.

## Sonda git status -sb (2026-10-07 13:15)

HEAD hâlâ `36f895bdeb65703f30a0c2bbd9cc2e8f04b4443a`. Dal `feat/tur29a-ui-streaming`. porcelain-before ile tek fark istenen çıktı dosyası:

```
+?? denetim/v3-p03-apk-baseline.md
```

Kaynak .kt/.xml/.js listesi değişmedi. DUR koşulu tetiklenmedi.

```
## feat/tur29a-ui-streaming
 M RAPOR.md
 M app/src/main/assets/arena/cage-engine.js
 M app/src/main/assets/arena/cage.html
 M app/src/main/assets/arena/cage.js
 M app/src/main/java/com/hermes/mobile/ArenaViewModel.kt
 M app/src/main/java/com/hermes/mobile/ChatViewModel.kt
 M app/src/main/java/com/hermes/mobile/MainActivity.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisBrain.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisEngine.kt
 M app/src/main/java/com/hermes/mobile/assistant/JarvisVoice.kt
 M app/src/main/java/com/hermes/mobile/car/HermesCarService.kt
 M app/src/main/java/com/hermes/mobile/data/ArtemisClient.kt
 M app/src/main/java/com/hermes/mobile/data/ArtemisLogic.kt
 M app/src/main/java/com/hermes/mobile/data/GatewayWsClient.kt
 M app/src/main/java/com/hermes/mobile/data/LocalModelClient.kt
 M app/src/main/java/com/hermes/mobile/data/ReplyService.kt
 M app/src/main/java/com/hermes/mobile/ui/ArenaSceneModel.kt
 M app/src/main/java/com/hermes/mobile/ui/ChatMenu.kt
 M app/src/main/java/com/hermes/mobile/ui/ChatScreen.kt
 M app/src/test/java/com/hermes/mobile/ChatMenuTest.kt
 M app/src/test/java/com/hermes/mobile/LiveModelChoiceTest.kt
?? app/src/main/java/com/hermes/mobile/data/SessionBinding.kt
?? app/src/test/java/com/hermes/mobile/SessionContinuationTest.kt
?? denetim/deneme-tur28-webapp-kalip.json
?? denetim/deneme-v3-p02-envanter-qc.json
?? denetim/session-arena-20261003-build.log
?? denetim/tur27/
?? denetim/tur28-build.log
?? denetim/tur28-denetmen-test.log
?? denetim/tur28/
?? denetim/v3-p02-envanter.md
?? denetim/v3-p03-apk-baseline.md
?? denetim/verdict-tur28.json
?? denetim/verdict-v3-p02-envanter-qc.json
?? docs/oturum-inceleme-tur27.md
```

## Kapsam dışı (yapılmadı)

EMA, TTS silme, Android Auto mikrofon, refactor, test yazma, emülatör açma.
