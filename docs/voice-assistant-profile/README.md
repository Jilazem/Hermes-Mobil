# Sesli Asistan — Hermes EMA 1.5

`sesli-asistan` profili canlı Hermes sunucusunda kuruldu; görünen adı **Sesli Asistan**, modeli **Evren / gemma-4-31b**. Ana profilin modeli değiştirilmedi. Evren anahtarı yalnız yeni profilin izinleri `0600` olan `.env` dosyasındadır; başka botların kanal anahtarları, geçmişleri ve becerileri kopyalanmadı.

Telefon asistanı, sürekli mikrofon sohbeti, otomatik gönderilen bas-konuş metni ve araç bildirimindeki sesli yanıt bu profili kullanır. Telefon komutları cihazda yürütülür. Kullanıcının seçtiği yerel beyin yolu korunur. Normal yazılı sohbet yeni oturum açarken kayıtlı model ve profil seçimini kullanmaya devam eder. Sesli uzmana geçiş yazılı model tercihini değiştirmez.

Her sunucu ve kaynak sohbet için bir sesli oturum saklanır. İlk geçişte kaynak sohbetin son altı kullanıcı/asistan mesajından en fazla 500'er karakter aktarılır; araç ve sistem çıktıları aktarılmaz. Alıntı geçmiş veri olarak işaretlenir, belirgin anahtar/parola satırları ayıklanır. Aktarım bağlamı başarılı gönderime kadar saklanır. Sohbet yeniden açılınca kullanıcı balonunda yalnız sorunun metni görünür. Eksik geçmiş veya başarısız geri yükleme sessizce boş sohbet oluşturmaz. Uzman bulunmayan eski sunucular mevcut sesli yolu kullanır.

## Model ve hız

Aynı kısa sorunun doğrudan Evren ölçümünde GLM-5.3 ilk metni 10,526 saniyede, Gemma-4-31B 1,281 saniyede verdi. Gemma sentetik `phone_status` araç çağrısını da doğru oluşturdu. Tam Hermes yolunda ayrı denemelerde ilk metin 0,813–2,841 saniyede geldi. Telefon ölçümleri ayrıca raporlanır; bu süreler sesin başlaması veya her soru için hız garantisi değildir.

Bu Evren uç noktası `reasoning_effort` parametresini reddediyor. `config.example.yaml`, Hermes'in otomatik düşünme parametresini bu modele eklemesini engelleyen desteklenen model meta veri ayarını gösterir. Model kendi iç düşünme metnini yine üretebilir; EMA yalnız yanıt metnini okur.

## Uzun işler

`delegate_task`, ayrı bir görev ajanı başlatır. Üst düzey aktarım arka planda çalışır; başlangıç teyidinden sonra tamamlanan sonuç aynı sesli sohbete döner. Mevcut adlandırılmış `cd-android` veya `ceo` profilini seçen bir aktarım değildir. Kullanıcı aynı sohbeti Takip sekmesine ekleyebilir. Profil en fazla iki eşzamanlı görev ajanına izin verir.

Canlı sunucuda sekiz saniyelik sentetik görev başlatıldı; ana yanıt görev bitmeden döndü, ardından sonuç `4` aynı sohbetin mesaj olaylarında alındı. Telefon ve EMA kabul sonuçları teslim raporundadır.

## Kurulum ve sınırlar

Telefonundaki kurulum bir uygulama güncellemesidir; indirilen EMA modeli ve sunucu ayarları korunur. EMA ses üretimi çevrimdışı çalışabilir; Hermes/Evren yanıtı için ağ bağlantısı gerekir. Yeni uzman kablosuz Android Auto bağlantı sorununu veya Bluetooth arama kaydını tek başına çözmez; bunların gerçek araç kabulü ayrıca yapılmalıdır.

Başka sunucuya kurarken Hermes'in `create_profile` yordamıyla temiz, becerisiz bir `sesli-asistan` profili oluştur; bu SOUL ve yapılandırma örneğini o profile uygula. Evren uç noktasını mevcut ayarlardan al, anahtarı `.env` içine koy. Ana profilin SOUL veya aktif oturumunu değiştirme.
