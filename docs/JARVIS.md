# Jarvis — Hermes sesli asistanı

Google Asistan'ın yerine geçen, ekranı kaplamayan, Hermes ajanıyla konuşan
sesli asistan.

## Kurulum (bir kez)

1. Hermes V3 → Ayarlar → **Jarvis asistan** → **Varsayılan asistan yap**
   (önce mikrofon izni istenir, sonra sistem ekranı açılır →
   "Dijital asistan uygulaması" → **Hermes V3 Asistan**).
2. Samsung'da yan tuş: Ayarlar → Gelişmiş özellikler → Yan tuş →
   Basılı tut → **Dijital asistanı uyandır**.
3. İstersen **"Hey Jarvis"** anahtarını aç (3,7 MB model bir kez iner).

## Kullanım

- Yan tuşa basılı tut / alt köşeden çapraz kaydır / "Hey Jarvis" de →
  panel açılır, kısa bir bip sesi gelir, **hemen dinler**.
- Konuş: yazıya dökülüşünü canlı görürsün. Yanıt akarken cümle cümle okunur;
  bitince yeniden dinler. Sessiz kalırsan durur.
- **Mikrofon düğmesi** konuşmayı keser ve yeniden dinler. **"Teşekkürler"**
  ya da **"kapat"** dersen panel kapanır.
- Panel **ekranı kaplamaz**: yalnız alttaki kart dokunmaya yanıt verir,
  arkadaki uygulamayı kullanmaya devam edebilirsin. Panel açıkken ekran kararmaz.
- Üstte **KITT tarayıcısı**: düşünürken kırmızı ışık sağa sola süpürür,
  dinlerken ve konuşurken ortadan dışa ses düzeyiyle açılır.
- ↻ yeni konu (bağlam sıfırlanır), ↗ sohbette aç (uzun yanıt / onay için),
  ⌨ yazarak sor.

## Ne yapabilir

| Söyle | Ne olur |
|---|---|
| "feneri aç", "WhatsApp'ı aç", "Kadıköy'e yol tarifi" | Telefonda anında (sunucuya gitmez) |
| "WhatsApp mesajlarımı oku", "Ali'ye geliyorum yaz" | Bildirimden oku / yanıtla |
| "ekranda ne var", "bunu özetle", "bu mesaja ne yazayım" | O anki ekranın metni soruya eklenir |
| diğer her şey | Hermes ajanı (araçlar, hafıza, telefon köprüsü) |

Aynı konu 30 dakika boyunca hatırlanır ("peki yarın?" gibi devam soruları).

## Ses stüdyosu

Ayarlar → Jarvis asistan → **Ses stüdyosu**: telefonun Türkçe sesleri
(Google/Samsung) listelenir, her birinin yanında **▶ Dinle**; yerel Piper
ve sunucu sesleri; hız ve ton ayarı.

## Teknik notlar

- Hermes varsayılan asistan olunca Android sistemin ses tanıyıcısını da
  Hermes'e bağlar. Hermes'in tanıyıcısı isteği cihazdaki **gerçek
  tanıyıcıya** (Google) aktarır; klavyedeki mikrofon gibi diğer uygulamalar
  bozulmaz.
- Konuşmayı yazıya dökmek için telefonun tanıyıcısı kullanılır (Türkçede en
  hızlı ve doğru olanı). Yanıtı Hermes/yerel model verir, **Gemini değil**.
- "Hey Jarvis" sesi tamamen telefonda işlenir; ölçümler `tools/wakeword/`.
