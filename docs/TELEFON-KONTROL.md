# Hermes'in telefonu tam kontrolü (V3)

Hedef: "WhatsApp'tan gelen mesajı oku", "Ali'ye geliyorum yaz", "Ayarlar'dan
pil yüzdesine bak" gibi düz cümleleri Hermes'e söylemek. Bu cümleler
uygulamaya yazılabilir, Telegram'dan gönderilebilir ya da arabada
söylenebilir; Hermes de işi yapar.

## Üç katman

| Katman | Ne yapar | Nerede çalışır | Gereken |
|---|---|---|---|
| **1. Mesaj kutusu** | WhatsApp/Telegram/SMS okur, **bildirimden yanıtlar** | Telefonda | Bildirim erişimi |
| **2. Tam kontrol** | Ekranı okur, dokunur, yazar, kaydırır | Telefonda (erişilebilirlik) | Tek anahtar + erişilebilirlik izni |
| **3. Artemis** | Ekranı görerek çok adımlı işler (uygulamalar arası) | Sunucuda (ADB) | docs/ARTEMIS.md |

Mesajlar için 1. katman en iyisidir. Android Auto da aynı yolu kullanır:
uygulamayı açmadan, ekran kilitliyken bile yanıt gönderir.

## Uygulamada söyleyebileceklerin (sunucuya gitmez, anında)

- `WhatsApp mesajlarımı oku` · `telegramda ne var` · `gelen mesajları oku`
- `Ayşe ne yazmış`
- `Ali'ye WhatsApp'tan geliyorum yaz`
- `whatsapp'tan Ayşe'ye "5 dk'ya oradayım" diye cevap ver`
- `Mehmet'e tamam yaz` (kesme işaretiyle kişi adı)

Aynı adla birden çok sohbet eşleşirse **mesaj gönderilmez**; hangisi olduğu
sorulur. Yalnız **bildirimi hâlâ duran** sohbete yanıt verilebilir.
Sohbeti telefonda açıp okuduysan bildirim kalkmıştır.

## Ajan (Telegram / cron / araç) de yapsın

Ayarlar'da:
1. **Ajan mesajlara yanıt verebilsin**: `phone_reply` aracını açar. Varsayılan
   olarak kapalıdır, çünkü gönderilen mesaj geri alınamaz.
2. **Tam kontrol**: ekran araçlarını açar. Artemis adresi girildiyse
   `phone_task` (Artemis) aracını da açar.

Telefon bu araçların **tanımlarını** köprüye kendisi gönderiyor (`hello.specs`).
Sunucudaki phone MCP bu alanı okumuyorsa, Hermes'e aşağıdaki talimatı
**olduğu gibi yapıştır**:

> Telefon köprüsünün (phone_bridge / phone MCP) araç listesine üç yeni araç
> ekle. Telefon "hello" karesinde `tools` listesinin yanında `specs` nesnesini
> gönderiyor: her aracın `description` ve `parameters` (JSON Schema) alanı
> var. phone MCP, `specs` içindeki her aracı bu şemayla MCP aracı olarak
> kaydetsin. Çağrıyı mevcut araçlar gibi `{"id","tool","args"}` karesiyle
> telefona iletsin. Yeni araçlar: `phone_messages` (okuma), `phone_reply`
> (sohbete yanıt; yalnız kullanıcı açıkça isteyince), `phone_task` (Google
> Artemis ile çok adımlı ekran görevi; 4 dakikaya kadar sürebilir, zaman
> aşımını buna göre ayarla). Değişiklikten sonra köprüyü yeniden başlat.

Şemaların kendisi: `app/src/main/java/com/hermes/mobile/data/PhoneToolSpecs.kt`.

## Android Auto

1. Telefonda Android Auto → **Ayarlar** → en alttaki *Sürüm*'e 10 kez dokun
   (geliştirici modu) → sağ üst ⋮ → **Geliştirici ayarları** →
   **Bilinmeyen kaynaklar**'ı aç. Uygulama Play Store'dan gelmediği için bu şart.
2. Araçta Hermes'in yanıtları **mesaj** olarak görünür ve sesli okunur.
   "Yanıtla" deyip sesle cevap verebilirsin. Cevap Hermes'e gider:
   - `WhatsApp mesajlarımı oku` ya da `Ali'ye geliyorum yaz` gibi komutlar
     **telefonda anında** yapılır ve sonuç yine sesli okunur.
   - Diğer her şey Hermes ajanına gider. Ajan gerekirse telefon araçlarını
     (yukarıdakiler) kullanır.
3. Araç ekranındaki Hermes girişi telefon ekranının yansımasıyla açılır
   (aşağıda). Sunucu durumu "Durum" düğmesinde.

> Not: Android Auto kendi başına bir sohbeti *başlatmaya* yalnız Google
> Asistan'a izin veriyor. Hermes'e yeni bir konu açmak için telefondaki sürüş
> kipi ya da Hızlı Ayarlar'daki "Hermes'e konuş" döşemesi kullanılır. Gelen
> bir Hermes mesajına ise araçtan doğrudan yanıt verilebilir.

## Android Auto'ya telefon ekranını yansıtma

1. Telefonda: Hermes V3 → Ayarlar → **Android Auto'ya ekran yansıtma** →
   **"İzin ver ve başlat"** → açılan pencerede **"Tüm ekran"**ı seç.
   Kalıcı bir bildirim çıkar; yansıtmayı buradan durdurabilirsin.
2. Araçta **Hermes**'i aç. Telefonun ekranı araç ekranında görünür. Dikey
   telefon ortalanır, iki yanında siyah bant kalır.
3. Dokunmak için telefonda **Tam kontrol** açık olmalı. Araç ekranında:
   - **dokunma** telefona dokunur;
   - **kaydırma** için sağdaki ✥ (kaydır) düğmesine bas, sonra sürükle ya da savur;
   - üst düğmeler: **Geri**, **Ana** (ana ekran), **Hermes** (uygulamayı öne
     getirir), **Durum** (sunucu durumu).
4. İzin verilmediyse araçta "İzni iste"ye bas. Telefona bir bildirim gelir;
   ona dokunup izin ver.

**Güvenlik:** "Sürerken yansıtmayı durdur" varsayılan olarak açık. Araç
~5 km/sa'yı geçince görüntü kesilir, durunca geri gelir. Araç hız bilgisini
vermiyorsa bu koruma çalışmaz.

Teknik not: Android Auto serbest çizimi yalnız navigasyon uygulamalarına
açıyor. Bu yüzden Hermes araçta navigasyon uygulaması olarak görünür.
Telefon ekranı kilitlenirse yansıma da kilit ekranını gösterir. Yansıtma
sürerken ekranın kararmaması için bir ekran-açık kilidi tutulur.
