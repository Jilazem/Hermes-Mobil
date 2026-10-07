# Oturum incelemesi — tur27 (Hermes-Mobil)

Tarih: 03.10.2026 · İnceleyen: cd-android · Son commit aeb6f8a

## 1) Mobil (Hermes-Mobil)

### Bulgu 1 — Yerel model (node1) hattında geçmiş YOK (kök neden, ŞİKÂYET 1)
- `data/LocalModelClient.kt:120-123` (`LocalModelLogic.chatBody`): istek gövdesi
  yalnız `system` + TEK `user` mesajı üretir — önceki turlar hiçbir zaman istekle
  gitmez. `data/LocalModelClient.kt:235-241` (`LocalModelClient.chat`): `text`
  parametresi tek metin, geçmiş parametresi yok.
- `ChatViewModel.kt:835` (`sendLocalAssistant`): `client.chat(label, ...)` —
  sohbetteki `items`'tan geçmiş kurulmuyor. Ayarlar'da "Yerel (node1)" seçiliyken
  HER mesaj ilk mesaj gibi gider — patronun "konuyu bilmiyor, devam edemiyor"
  şikâyetinin bu hatta kök nedeni.
- DÜZELTİLDİ: `chatBody(model, system, history, userText)` geçmişli gövde +
  `chat(..., history)` parametresi + `sendLocalAssistant` içinde sohbet
  balonlarından son 20 turu (LOCAL_HISTORY_LIMIT, ChatViewModel.kt:174) kesip
  gönderme. `/new` akışı temizlediği için geçmiş de sıfırlanır (runSlash
  `new/clear` ChatState sıfırlar — ChatViewModel.kt:1231).

### Bulgu 2 — Yerel yanıt SESLENDİRİLMİYOR (ŞİKÂYET 2'nin yerel-hat kökü)
- `ChatViewModel.kt:sendLocalAssistant` onSuccess: yanıt yalnız ekrana yazılır +
  bildirim; `voiceMsg.speak` / `voice.speak` / `jarvisLoop.onAgentReply`
  ÇAĞRILMIYOR. Gateway yolundaki okuma kararı (`handleEvent` →
  message.complete, ChatViewModel.kt:2289-2307) bu yolda kopyasız — asistan
  modu, hands-free ve Jarvis döngüsü yerel beyinle sessiz kalıyordu.
- DÜZELTİLDİ: onSuccess içinde gateway yoluyla AYNI karar makinesi:
  Jarvis `WaitReply` → onAgentReply + altyazı; `handsFree` → voice.speak;
  asistan modu → AssistantModeLogic.shouldAutoRead → voiceMsg.speak.

### Bulgu 3 — Gateway hattında süreklilik ZATEN VAR (rapor: değişiklik gerekmedi)
- `ChatViewModel.kt:1162-1179` (`send`): `_state.value.sessionId` saklanır,
  yoksa `createSessionWithProfile` açılır ve HER `prompt.submit` o `sid` ile
  yapılır (`GatewayWsClient.kt:184-192` `prompt.submit{session_id,text}`).
  Geçmiş gateway belleğinde durur; Telegram ile aynı model. `runSlash`/`/new`
  `ChatState` sıfırlar → yeni oturum (ChatViewModel.kt:1226-1241).
- `onReconnected` → `reattach` (ChatViewModel.kt:1019-1021, 1830-1845):
  kopmada `session.activate`/`session.resume` + outbox tekrar — oturum kimliği
  soket yenilense de taşınıyor.

## 2) Gateway (Mac'teki Hermes)

- `GatewayWsClient` dashboard'ın JSON-RPC lehçesini kullanır
  (`session.create`, `prompt.submit(session_id)`, `session.activate`,
  `session.resume`, `session.history`) — mobil, gateway'den oturum kimliğini
  TAŞIYOR; gateway tarafında mobil için ekstra eksik GÖRÜLMEDİ. Telegram
  eşlemesine dokunulmadı (kapsam dışı).

## 3) Sesli asistan hattı (uçtan uca)

- Jarvis döngüsü: mikrofon → VAD kesimi → `/transcribe` (STT,
  JarvisLoopController.kt:184-223) → `onSend(text)` → `ChatViewModel.send()`
  → YEREL ise `sendLocalAssistant` (Bulgu 1-2 düzeltildi), gateway ise
  oturumlu prompt (Bulgu 3). TTS: `synthesize` döngü içinde çalınır
  (JarvisLoopController.kt:331-337); bas-konuş `VoiceMessageController`
  üzerinden aynı taşıyıcıyı paylaşır (ChatViewModel.kt:515-522).
- Ses hattının BEYİNİ tek yerden geçer (send) — geçmiş ve seslendirme
  düzeltmeleri hattın tamamına (bas-konuş, hands-free, Jarvis döngüsü)
  etki eder. Mikrofon izni/recorder yaşam döngüsünde yeni kusur
  GÖRÜLMEDİ (tur26'da AA kanal/izin düzeltmeleri mevcut).

## Yapılan değişiklikler (dosya:satır)

1. `app/src/main/java/com/hermes/mobile/data/LocalModelClient.kt`
   - `LocalModelLogic.chatBody` geçmişli overload (tur-27 gövdesi; eski 3-arg
     imza korundu — geriye uyum).
   - `LocalModelClient.chat(..., history)` parametresi.
2. `app/src/main/java/com/hermes/mobile/ChatViewModel.kt`
   - `LOCAL_HISTORY_LIMIT = 20` sabiti.
   - `sendLocalAssistant`: sohbet balonlarından son 20 turu kesip geçmiş
     gönderme + yanıtı seslendirme kararı (Jarvis/handsFree/asistan modu).
3. `app/src/test/java/com/hermes/mobile/LiveModelChoiceTest.kt` — geçmişli
   gövde testi (sıra: system → user → assistant → son kullanıcı turu).

## Kapsam dışı / notlar

- Gateway davranışı DEĞİŞTİRİLMEDİ; Telegram'a dokunulmadı.
- Sohbet içerikleri bu rapora kopyalanmadı (KVKK) — yalnız kod yolu kanıtı.
- Commit ATILMADI: değişiklik çalışma ağacında, denetim CEO'da.
