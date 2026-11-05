# Dashboard ortak bağlantısı

Üretim modülü `bot_framework.dashboard_hub`'dır. Eski `server.py` bağımsız demo sunucusudur; Dashboard bağlantısı için kullanılmaz.

Android istemci seçili `ServerProfile` kaydını kullanır. Bot uçları aynı adresin `/mobile-api/bot/api/...` yolundadır. `X-Hermes-Session-Token` başlığı zorunludur. Bot sohbetleriyle Dashboard sohbetlerinin `/api/sessions` adları böylece çakışmaz. Telefon WebSocket bağlantısı da aynı adresin `/phone-bridge/phone` yoluna gider; mevcut Bearer kimlik doğrulaması korunur.

Adapter yalnız `127.0.0.1:19760` dinler; Caddy'nin mevcut Dashboard girişine dar bir `/mobile-api/*` yolu eklenir. Dashboard veya ana gateway yeniden başlatılmaz. Ortak token, Dashboard'un korumalı oturum yoluna HEAD isteğiyle doğrulanır. HEAD, pahalı sohbet listesi sorgusunu çalıştırmaz. Kimliksiz HEAD isteğinin reddedilmesi de doğrulanır; doğrulama başarısızsa erişim açılmaz. Adapter tokenı ayrı bir sağlayıcı ayarına kaydetmez.

Gerçek yanıtlar mevcut Dashboard WebSocket oturumundan gelir. Her bot sohbeti için saklı gateway oturum kimliği SQLite tablosuna yazılır; sonraki tur aynı görüşmeye devam eder. Sonucu belirsiz bir tur otomatik yeniden gönderilmez. Botun persona metni ilk kullanıcı iletisine rol bağlamı olarak eklenir; bu bir sunucu sistem-prompt veya araç sandbox sınırı değildir. Gerçek araç yetkileri mevcut Hermes profiline, telefon eylemleri ayrıca cihazdaki izinlere ve acil durdur durumuna bağlıdır. Model/sağlayıcı genel ayarları değiştirilmez.

Yeni ortamda çalıştırmak için bu modüle ait ayrı bir Python ortamı ve `requirements-dashboard.txt` kullanın. Mevcut Hermes üretim Python'una kurulum veya güncelleme yapmayın. Bu bilgisayardaki dağıtım mevcut saf Python websocket istemcisini yalnız adapter dizinine kopyalayarak yalıtılmıştır; lisans dosyası aynı dizinde tutulur.

```text
python -m bot_framework.dashboard_hub --db /yerel/adapter/bots.sqlite --dashboard-http http://127.0.0.1:9120
```

Gerçek Dashboard WebSocket kimliği, mevcut sunucunun sözleşmesi gereği loopback bağlantısının token sorgu parametresindedir. Adapter bu URL'yi veya başlıkları loglamaz. Dış HTTP istemcilerinde token yalnız başlıktadır.

Telefon köprüsünün ağ dinleyicisindeki `/call` ve `/status` uçlarına da aynı token denetimi eklenmiştir. Yerel MCP için `127.0.0.1:9181` sözleşmesi korunur. İlgili patch ve hashler teslim paketinde ayrıdır.
