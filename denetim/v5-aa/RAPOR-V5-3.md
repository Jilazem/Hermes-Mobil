# V5-3 ANDROID AUTO — araç ekranında mesaj akışı (2. tur)

Tarih: 20.10.2026 · Uzman: cd-android · Dal: feat/v5-aa

## 1. Bulunan (kök neden)

Araç ekranında Hermes'in tek erişilebilir yüzeyi **bildirim tabanlı mesajlaşma**
yüzeyiydi. Android Auto bu yüzeyi yalnız `category=MESSAGE` + `MessagingStyle`
bildirimlerden kurar. Eski akışta:

1. Araç konuşması **tek seferlik** bir bildirimdi ("Araç sohbeti hazır…") ve sohbet
   ilerledikçe **hiç güncellenmiyordu** → AA'da konuşma boş kalıyor, ekran
   "yeni mesajınız yok" gösteriyordu.
2. Her ajan yanıtı **yeni bir bildirim** açıyordu (`tag=null`, artan id) → aynı
   oturumun mesajları tek konuşmada birikmiyordu.
3. Templated (Car App Library) ekran ise yalnız durum + oturum listesi çiziyordu:
   oturum satırları **tıklanamıyordu**, hiçbir yerde **mesaj içeriği yoktu**.

REA ile APK beyanı doğrulandı (`denetim/v5-aa/rea-decode-v53.json`): pakette
`CarAppService` + `category.NAVIGATION` var, `category.MESSAGING` yok; yani AA
mesajlaşma yüzeyi tamamen bildirim sözleşmesine bağlı.

## 2. Yapılan

- `data/CarConversation.kt` — araç konuşması durumu (saf, Android'siz): oturum
  anahtarı, 25 mesaj sınırı, 4000 karakter kırpma, geçmişle başlatma.
- `data/Notifier.kt` — `carConversationStart` / `carFlow` / `postCarConversation`:
  tek `tag=car:<profil>/<oturum>`, id 4870, `MessagingStyle` + `setConversationTitle`,
  `Yanıtla` RemoteInput (`SEMANTIC_ACTION_REPLY`), görünmez `Okundu` eylemi.
  Konuşma her mesajda yeniden bildirilir.
- `ChatViewModel` (kullanıcı mesajı), `MainActivity` (oturum açılışında geçmişle
  başlatma), `ReplyService` (bildirimden gelen yanıt akışı) kancaları.
- `debug/CarMessagingProofActivity` — gerçek gateway'e bağlanıp gerçek bildirimi
  ve gerçek RemoteInput eylemini sınayan kanıt etkinliği.
- `car/CarChatLogic.kt` + `car/CarChatScreen.kt` — templated ekranda **mesaj akışı**:
  oturuma dokun → son 30 mesaj (Sen/Hermes, tek satır özet) → mesaja dokun → tam
  metin (`LongMessageTemplate`); "Konuş" araç mikrofonuyla aynı oturuma yazar.
- `car/HermesCarService.kt` — oturum satırları tıklanabilir, başlık/önizleme/mesaj
  sayısı gösterilir (5 etkin oturum).

## 3. Kanıt

| Kanıt | Sonuç | Dosya |
|---|---|---|
| Birim test (yeni) | CarChatLogic 11/11, CarConversation 10/10 | `app/build/test-results/...` |
| Tam birim test paketi | **1127 test, 0 failure, 0 error** | Gradle `testDebugUnitTest` |
| Cihaz kanıtı (emülatör, gerçek gateway) | `RESULT PASS`; aynı konuşmada 5 mesaj birikti | `denetim/v5-aa/car-messaging-proof.txt` |
| Bildirim dökümü | `category=msg`, `tag=car:<profil>/<oturum>`, `[0] "Yanıtla" -> PendingIntent startService` | `denetim/v5-aa/notification-car-dump.txt` |
| REA (apktool 3.0.3) | `CarAppService` ✓, `NAVIGATION` ✓, `MESSAGING` ✗ | `denetim/v5-aa/rea-decode-v53.json` |
| APK | arm64 debug, md5 `f84e739f44d6446546b6124f27c828be` | `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` |

## 4. Atlanan / DOĞRULANAMADI

- **DHU ekran kanıtı: DOĞRULANAMADI.** Bu makinede DHU yok; SDK deposunda
  `extras;google;auto` paketi listelenmiyor (yalnız build-tools/platform/emulator/
  system-images kurulu) ve emülatördeki Android Auto sürümü
  `1.2.531830-stub` (gerçek AA bileşeni yok). DHU, gerçek AA uygulaması + telefon
  tarafında "head unit server" ister; stub ile mümkün değil.
- **AAOS emülatörü (android-34-ext9 automotive-playstore):** APK kuruldu, uygulama
  uygulama ızgarasında "Hermes EMA" olarak göründü; ancak ızgara girişi
  `MainActivity`'yi açıyor, `CarAppService` host girişi (templates host) sunulmuyor.
  Bu yüzden templated mesaj akışının araç ekranı görüntüsü alınamadı.
  Templated akışın doğruluğu şimdilik birim testleriyle sınırlıdır.
- Bildirim yolu, AA'nın resmî sözleşmesine (aktif oturum sırasında gelen
  MessagingStyle bildirimi) uygundur; gerçek araç başlığında son kabul patron
  tarafından yapılmalıdır.

## 5. Açık riskler

- Konuşma geçmişi süreç belleğinde; süreç ölürse araçtaki konuşma sıfırlanır
  (bildirim görünür kalır). Kalıcılık istenirse `ActivityInbox`'tan yeniden kurulabilir.
- `setAutoCancel(false)` — konuşma bildirimi okundu eylemiyle temizlenir; kullanıcı
  bildirimi elle silmezse durum çubuğunda kalır (mesajlaşma uygulamalarının olağan
  davranışı).

## 6. Denetmene not

Diff'te `Notifier.agentReply` davranışı değişmedi; yeni yol ayrı fonksiyonlarda.
`ChatViewModel`/`MainActivity`/`ReplyService` değişiklikleri yalnız kanca ekliyor.
