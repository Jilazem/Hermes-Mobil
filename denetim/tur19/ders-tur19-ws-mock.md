# Ders — tur19 WS mock blocker'ı (Sec-WebSocket-Accept mismatch + OkHttp havuz kirliliği)

**Nerede:** denetim/tur19/mock_gateway.py — sahte gateway'in WS el sıkışması
(app: com.hermes.mobile.v2, OkHttp WebSocket; mock 127.0.0.1:8198, emülatörden
10.0.2.2:8198).

**Semptom (run31–36):** 101 el sıkışma başarılı görünür ama app tarafında
`Sunucu WS el sıkışmasını reddetti (HTTP 101) — Expected 'Sec-WebSocket-Accept'
value 'GTbdik…' but was 'AkKMwm…'` → hiçbir RPC (session.active_list /
prompt.submit / session.steer) mock'a ulaşmaz; her bağlantı 6–15 sn'de aynı
kalıba düşer (sonsuz reconnect), drawer'da canlı oturum 0 → 'Yanıtla' satırı
üretilemez.

**Kök neden (ikili):**
1. **Aynı sokete 2. HTTP request pipelining'i.** İlk 101 reject edilince OkHttp
   soketi "broken" saymayıp HTTP keep-alive havuzuna döndürüyor; ikinci WS
   isteği AYNI TCP soketine ham `GET /api/ws …` olarak yazılıyor. Mock ikinci
   GET'i okuyup aynı sokete 2. 101 gönderince accept-hash ikinci key'e göre
   hesaplanıyor ama OkHttp ilk request'in beklediği hash'i eşleştiriyor →
   mismatch sonsuza kadar tekrarlanıyor.
2. **Head parsing'i ham soket buffer'ına göre yapılıyordu.** OkHttp'in
   önceden yazdığı baytlar accept-hash hesabına karışabiliyordu.

**Nasıl düzeltildi (mock_gateway.py, 05:59 yaması — 3. ve doğrulanmış):**
1. HTTP head satır satır bölünür (`GET <path> HTTP/1.1` satırı ayrık);
   accept-hash YALNIZ o request'in Sec-WebSocket-Key'inden hesaplanır — soket
   buffer'ındaki yabancı baytlar hash'e GİRMEZ.
2. Aynı sokete 2. ham `GET` gelirse 2. 101 GÖNDERİLMEZ; soket kapatılır
   ("ayni-sokette 2. GET — soket kapatiliyor (temiz reconnect icin)").
   OkHttp yeni temiz soket açmak zorunda kalır; her WS bağlantısı kendi
   soketinde temiz el sıkışır.
3. Doğrulama: `ws_probe.py` (bağımsız istemci) 101 + session.active_list +
   prompt.submit ACK + message.delta event'lerini temiz alır;
   /tmp/tur19-mock.log'da `rpc session.* {` satırları akar;
   app 06:28:55/06:32:00'de `session.steer {live88bb, …}` gönderdi.

**Uzman için kural:**
- Android'de OkHttp WebSocket testi yapan sahte sunucu yazıyorsan: her 101'den
  sonra aynı sokette ikinci HTTP request BEKLEMEZSEN ya soketi 2. GET'te
  kapat ya da Connection: close de. 101 reject → soket havuza döner → 2. WS
  aynı sokete pipeline olur; bu OkHttp'in gerçek davranışıdır, "kullanıcı
  hatası" değildir.
- El sıkışma doğrulamasını İKİ taraflı yap: (a) bağımsız minimalist WS
  istemciyle probe (ws_probe.py kalıbı — socket+base64+struct, 60 satır),
  (b) app trafiği. App 0-RPC gösterirken mock log'unu düzeltmekle uğraşma;
  önce probe'un geçmesi gerekir (fail-closed sıralama).
- Accept-hash yalnız ilgili request'in başlık kümesinden hesaplanır; soket
  buffer'ındaki yabancı baytlar hash'i bozar.
- Kanıt turlarında kalıntı UI durumu bir sonraki adımın girdisini kirletir
  (run39/40: steer sonrası mini-composer 'Vazgeç' bekletilince arama alanı
  erişilemez). Her kesit sonrası bir sonraki adımın hedefini dump'tan ara,
  yoksa bilinen temizlik düğmesiyle (Vazgeç/Kapat) sıfırla.
