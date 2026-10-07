# Hermes Mobil V3 P02 — Kaynak Envanteri (salt-okunur)

Tarih: 2026-10-07 · Üreten: cd-android · Görev: t_2498b8ea / czip v5fzxp
Kök: /Users/gokhanuzman/hermes-workspace/Hermes-Mobil
Bu dosya dışında hiçbir kaynak dosya değiştirilmedi; türetilmiş dosyalara (.gradle, build/, APK) dokunulmadı.

## 1. Git durumu (komut çıktısı alıntısı)

```
$ git rev-parse HEAD
36f895bdeb65703f30a0c2bbd9cc2e8f04b4443a

$ git status -sb (özet)
## feat/tur29a-ui-streaming
 M 21 kaynak dosya (RAPOR.md, cage-engine.js, cage.html, cage.js, ArenaViewModel.kt,
    ChatViewModel.kt, MainActivity.kt, JarvisBrain.kt, JarvisEngine.kt, JarvisVoice.kt,
    HermesCarService.kt, ArtemisClient.kt, ArtemisLogic.kt, GatewayWsClient.kt,
    LocalModelClient.kt, ReplyService.kt, ArenaSceneModel.kt, ChatMenu.kt, ChatScreen.kt,
    ChatMenuTest.kt, LiveModelChoiceTest.kt)
?? SessionBinding.kt, SessionContinuationTest.kt, denetim/*, docs/oturum-inceleme-tur27.md
```

```
$ git branch -vv / git branch -a (tam liste, çıktıdan kopyalanmıştır)
+ feat/ana-toplama            efe53b2 (wt-ana)
  feat/android-uzman-devralma 53e4e08
+ feat/bakim-ecrani           c731bd7 (wt-bakim)
+ feat/bot-arena              2f43ab5 (wt-arena)
+ feat/canli-dusunme          b0f0aab (wt-canli-dusunme)
+ feat/oturum-eylemleri       a313035 (wt-oturum-menu)
+ feat/paylasim-hedefi        691b0bc (wt-paylasim)
+ feat/prompt-bot-atama       d36341a (wt-bot-atama)
  feat/tur15-outrun           a33864e
  feat/tur16-oturum-cekmece   0e2174d
  feat/tur17-tasarim-b1b2     e5952d5
+ feat/tur18-tipografi-kart   d9e840d (wt-android-uzman)
+ feat/tur28-webapp-kalip     9c2fd21 (wt-t28, EX biriminde)
* feat/tur29a-ui-streaming    36f895b  <- mevcut dal
  main                        aeb6f8a [origin/main: ahead 141, behind 9]
+ merge/v3-hat                0fd5ec7 (wt-v3-merge)
+ wt/t19-genel-akis           280e2e7 (wt-t19)
+ wt/t20-arena                d65f9bf (wt-t20)
+ wt/t21-ses-jev              2ca5434 (wt-t21)
+ wt/t22-parlaklik            328c572 (wt-t22)
+ wt/t23-jarvis               382719f (wt-t23)
+ wt/t24-fix                  7ee497b (wt-t24-fix)
+ wt/t24-jarvis-loop          ec5149e (wt-t24)
+ wt/t25-wall                 16b01d8 (wt-t25)
  remotes/origin/HEAD -> origin/main
  remotes/origin/apk
  remotes/origin/claude/hermes-mobil-review-develop-bba82i
  remotes/origin/gelistirme
  remotes/origin/main
  remotes/origin/tur22-review
  remotes/origin/wt/t19-genel-akis
```

(`+` işareti git'in worktree checkout göstergesidir; `*` mevcut dal.)

## 2. Özellik → dosya matrisi

| # | Özellik | Dosya yolu (köke göre) | Durum | Dal kanıtı |
|---|---------|------------------------|-------|------------|
| 1 | VoiceController | app/src/main/java/com/hermes/mobile/data/VoiceController.kt | VAR (9.847 B, 27.09.2026) | Tüm 23 yerel dalda `git cat-file -e <dal>:<yol>` = ok |
| 2 | LiveVoiceClient | app/src/main/java/com/hermes/mobile/data/LiveVoiceClient.kt (`class LiveVoiceClient` :56) | VAR | Tüm 23 dalda ok |
| 3 | HermesCarService | app/src/main/java/com/hermes/mobile/car/HermesCarService.kt (`class HermesCarService : CarAppService()` :39) | VAR | Tüm 23 dalda ok |
| 4 | startVoiceOnPhone | car/HermesCarService.kt:194 (`private fun startVoiceOnPhone()`) | VAR | Aynı dosya; tüm 23 dalda dosya ok |
| 5 | phone_speak (ajan aracı) | data/PhoneTools.kt:116 (kayıt listesi `"phone_speak"`), :165 (`"phone_speak" -> speak(arg("text"))`); yorum: VoiceController.kt:33 | VAR | Çalışma kopyası (feat/tur29a-ui-streaming) |
| 6 | PhoneBridge | data/PhoneBridgeService.kt:61 (`class PhoneBridgeService : Service()`) | VAR | Tüm 23 dalda ok |
| 7 | LIVE_VOICES | data/AppSettings.kt:77 (`val LIVE_VOICES = listOf("Puck", ... "Zephyr")`); kullanım: ui/SettingsScreen.kt:61,324 | VAR | AppSettings tüm 23 dalda dolaylı; tanım çalışma kopyasında doğrulandı |
| 8 | TextToSpeech (TTS) | data/VoiceController.kt:42,85; assistant/JarvisVoice.kt:98; ui/SettingsScreen.kt:2418 (`android.speech.tts.TextToSpeech`) | VAR | JarvisVoice yalnız main, merge/v3-hat, tur28-webapp-kalip dallarında; diğerlerinde VoiceController TTS'i var |
| 9 | TTS_SERVICE (manifest) | app/src/main/AndroidManifest.xml:41 (`<action android:name="android.intent.action.TTS_SERVICE" />`) | VAR | Tüm 23 dalda manifest ok |
| 10 | ACTION_ASSIST | MainActivity.kt:387,407; data/AssistantRole.kt:76 (`Intent(Intent.ACTION_ASSIST)`) | VAR | Çalışma kopyası |
| 11 | Quick Settings tile | data/VoiceTileService.kt:18 (`class VoiceTileService : TileService()`) | VAR | Tüm 23 dalda ok (ana-toplama grubunda ve JarvisVoice'suz dallarda da mevcut) |
| 12 | AndroidManifest car category | AndroidManifest.xml:281-287: `.car.HermesCarService` service + `<category android:name="androidx.car.app.category.NAVIGATION" />`; ayrıca :15-17 car izinleri, :274 `com.google.android.gms.car.application` meta-data | VAR (`androidx.car.app.category.*` — `android.car.category` değildir) | Tüm 23 dalda manifest ok |
| 13 | CrashGuard | data/CrashGuard.kt + test app/src/test/java/com/hermes/mobile/CrashGuardTest.kt:14 | VAR | CrashGuard.kt: 18 dalda ok; YOK: feat/ana-toplama, feat/bakim-ecrani, feat/bot-arena, feat/canli-dusunme, feat/oturum-eylemleri, feat/paylasim-hedefi, feat/prompt-bot-atama (7 dal — main'in eski atası grubu) |

Kaynak ağaç büyüklüğü: app/src altında 231 .kt, 18 .xml.

## 3. Dal birleşme durumu (`git merge-base main <dal>`)

Ortak ata YOK olan dal: **yok** — 23 yerel dalın tümünün main ile ortak atası var. Dosya bazlı taşıma zorunluluğu DOĞMADI; üç durum var:

a) **Tamamlanmış (merge-base == dal ucu, dal main'in altında):**
feat/ana-toplama, feat/android-uzman-devralma, feat/bakim-ecrani, feat/bot-arena, feat/canli-dusunme, feat/oturum-eylemleri, feat/paylasim-hedefi, feat/prompt-bot-atama, feat/tur15-outrun, feat/tur16-oturum-cekmece, feat/tur17-tasarim-b1b2, feat/tur18-tipografi-kart, merge/v3-hat, wt/t19-genel-akis, wt/t20-arena, wt/t21-ses-jev, wt/t23-jarvis, wt/t24-fix, wt/t24-jarvis-loop.

b) **Main ileride, dal main'den yeni (merge-base == main HEAD aeb6f8a):**
feat/tur28-webapp-kalip (9c2fd21) — main'e mergelenmemiş commitleri var.

c) **Gerçek ayrışma (merge-base her iki ucundan farklı):**
- wt/t22-parlaklik: ortak ata 75414b8, dal ucu 328c572
- wt/t25-wall: ortak ata 0c86b5eb, dal ucu 16b01d8

Mevcut çalışma dalı feat/tur29a-ui-streaming (36f895b) main'e göre ileride, 21 modified + 10 untracked çalışma ağacı ile.

## 4. Win11 kopyası

[OKUNAMADI] /Volumes altında yalnız `EX` ve `Macintosh HD` var; G:\ (SMB mount) bağlı değil, smb://192.168.1.100 yolu bu turda erişilemedi. Uydurma yok.

## 5. Türetilmiş dosya envanteri (sayıldı, dokunulmadı)

- .gradle/ : 14M
- app/build/ : 602M (APK çıktıları dahil: app-x86_64-debug.apk, app-arm64-v8a-debug.apk)
- Kök build/ : yok
- Diğer APK: 00-DENETIM-KANITLAR/tur22-merge-tur19-t2-kanit/merge-tur19-t2-kanit-APK-debug.apk

## 6. Kabul ölçütleri kontrolü

- Çıktı dosyası boş değil: OK (bu dosya).
- Her özellik satırında gerçek dosya yolu: OK (13/13; yok olan durum sadece dal kırımlarında ve aranan dizin `app/src/main/java/com/hermes/mobile/data/` belirtilerek).
- Dal/commit uydurma yok: tüm adlar ve sha'lar yukarıdaki komut çıktılarından kopyalandı.
- Kaynak dosya değişmedi: aşağıdaki son `git status -sb` özetinde yalnız `?? denetim/v3-p02-envanter.md` eklenmiştir; M listesi ilk okumayla aynıdır (21 dosya).

---
*Kanıt komutları: git status -sb; git branch -vv; git branch -a; git rev-parse HEAD; git cat-file -e <dal>:<yol> (8 temel yol x 23 dal); grep -rn sembol taramaları; git merge-base main <dal> x 23; ls /Volumes; find/du sayımları.*
