# Hermes Mobil 7.1.0 — Makrolar ve ses düzeltmeleri

3 Kasım 2026. APK `7.1.0-preview`, kod 19, paket `com.hermes.mobile.v3`, ARM64, Android 8 ve üzeri. [APK](HermesMobil-v7.1.0.apk) · [Kaynak](HermesMobil-v7.1.0-kaynak.zip).

SHA-256: `cb69e15f5784b037b031288629aed2afe1ec857195eaff49f4b4536fb2f4c7e7`.

## Ekran görüntülerindeki hatalar

EMA indirmesindeki 404, uygulamanın gösterdiği kaynakların model dosyalarını sunmamasından kaynaklanıyordu. Mac'teki kurtarma arşivinden dört dosya bulundu ve APK'daki sabit boyut/SHA-256 değerleriyle eşleşti. Dosyalar izole mobil adapter altında `/mobile-api/ema/model/{dosya}` yolunda sunulur. Yeni uygulama önce seçili Dashboard'u kullanır. Token yalnız başlıklarda taşınır; diğer profillere gönderilmez, yönlendirme izlenmez. Boyut/hash kontrolü kaldırılmadı.

EMA ve Piper indirme aşamaları ayrıldı; yedek ses indirilirken EMA yüzdesi gösterilmez. Tamamlanan/iptal edilen işin aktif görünmesi giderildi. Uzun teknik hata dökümü ayrıntı düğmesine taşındı. EMA yokken hazır Piper sesini deneme düğmesi eklendi. Android emülatöründe gerçek EMA dosyalarının indirilmesi, kesme/devam, iki farklı metinden sessiz olmayan WAV üretimi, üretim ses fabrikasıyla oynatma ve iptal geçti.

Model listesindeki 503, çalışan Dashboard kodunun diskteki checkout'tan eski kalmasına karşı sunucunun bilinçli korumasıdır. Bu ana süreç yeniden başlatılmadı. Mobil istemci 404/503 durumunda `/mobile-api/models` üzerinden mevcut yapılandırmadan ayıklanmış model adlarını okur. Anahtarlar, sağlayıcı URL'leri ve diğer yapılandırma alanları istemciye gönderilmez. Yalnız loopback model uçları, kendi sağlayıcı kimliğiyle ve yönlendirme kapalı olarak keşfedilir. Dış sağlayıcılara keşif isteği veya Dashboard tokenı gönderilmez.

Bu kontrolde `yerel` takma adının sunucu envanterinde olmadığı görüldü; listeden çıkarıldı. Gerçek `DeepSeek-V4.1-Flash-1017` kimliğinin seçimi ayrı test sohbetinde, `--global` olmadan, bilinmeyen model uyarısı gelmeden doğrulandı. Bu testte üretim/model çağrısı yapılmadı. Genel model ayarları değiştirilmedi. Kayıtlı diğer sağlayıcıların listelenmesi onların tümünün canlı yanıt üretme kabulü değildir. Ana Dashboard'un eski kod koruması yerinde kalır; mobil liste bu süreçten bağımsız okunur.

## Sesli modun yeni görünümü

Kırmızı kayan tarayıcı şeritleri kaldırıldı. Asistan katmanı ve tam ekran sesli modda seçili temanın Focus/Midground renkleriyle yuvarlatılmış ekran çerçevesi çizilir. Mikrofon/oynatma genliği çerçevenin ışık şiddeti ve kalınlığını değiştirir; sesli kartın renkleri de temaya uyar. Azaltılmış hareket ayarında efekt sabitlenir. Çerçeve dokunma yakalamaz. Bu efekt uygulama/asistan penceresi içindedir, fiziksel telefon donanım çerçevesini aydınlatmaz.

## Modüller > Makrolar

Yeni hazır modülün adı **Makrolar**. Ayrı bir uygulama kurulmadan çalışan yerel tarifler:

- Şarj arkadaşı: şarja bağlanınca yerel bildirim.
- Odak zamanı: medya sesini kapat ve kısa titreşim.
- Medya sesi %35.
- Feneri aç / kapat.

Makro adı, tetikleyici, eylem sırası/değerleri ve çalıştırmalar arasındaki bekleme süresi düzenlenebilir. Elle çalıştırma; şarja bağlanma/ayrılma, kablolu kulaklık ve kilit açma olayları desteklenir. Otomatik iş için hem makro hem genel izleyici etkin olmalıdır. Kayıtlar ve içe aktarmalar duraklatılmış başlar. İzleyici kullanıcı tarafından açılır, kalıcı bildirimindeki düğmeden durdurulabilir. Telefon yeniden başlatıldığında Makrolar ekranını açarak izleme sürdürülür; otomatik açılış alıcısı eklenmedi.

[BespredeL/MacroDroid](https://github.com/BespredeL/MacroDroid), uygulamanın kaynak kodu değil bir makro koleksiyonudur. Commit `46bd349236f20879d2a68fa9cd57ad0c8a98de7c` içindeki 11 özgün `.macro` dosyası ve MIT lisansı APK'da korunur. Koleksiyon uygulama içinde inceleme kaydı olarak açılır; özgün JSON da saklanır. Koşulları desteklenmeyen makrolar otomatik yürütülmez. Medya sesi ve fener tarifleri açıkça ayrı yerel uyarlamalardır; sallama/ışık/kilit koşullarının aynısını uyguladıkları söylenmez.

[SysAdminDoc/OpenTasker](https://github.com/SysAdminDoc/OpenTasker), commit `a3b4fda4942eb61e9c81290d0ff390ad1f2160b8`, MIT lisanslı `CooldownReservations` kodu atomik yeniden tetikleme denetiminde kullanılır. Bekleme durumu kalıcıdır. OpenTasker JSON paketleri özgün kaynak korunarak duraklatılmış inceleme kaydına alınır. Tam OpenTasker uygulaması, 78 eylemi ve şifreli Room motoru APK'ya taşınmış değildir.

Hermes'in kendi makro JSON'ı içe/dışa aktarılır; dışa aktarılan kurallar da yeniden içe alınırken otomatik etkinleşmez. İçe aktarma en çok 1 MiB ve 100 kayıtla sınırlıdır. Genel MacroDroid/OpenTasker grafiklerinin eksiksiz dönüşümü yapılmaz. Kamera/bildirim gibi izinler yoksa eylemler başlamadan açıklama gösterilir. Acil durdurma her adım öncesinde kontrol edilir. Bir dizi yarıda hata verirse tamamlanan adım sayısı gösterilir; önceki ses/fener değişiklikleri otomatik geri alınmaz. İşlem geçmişi eylem değerlerini ve içe alınmış kaynak metnini kaydetmez.

## Doğrulama

| Kontrol | Sonuç |
|---|---|
| Android tam test, Windows | 1.498/1.498; hata/atlama yok |
| Android tam test, Mac | 1.498/1.498; hata/atlama yok |
| Backend | 74/74 |
| Lint | 0 hata, 44 uyarı, 7 bilgi |
| Gerçek Dashboard model envanteri | Kontrol anında 12 sağlayıcı / 76 model; gizli yapılandırma alanı yok |
| Gerçek oturumda model seçimi | DeepSeek kimliği doğrulandı; global seçim/üretim yok |
| EMA dosyaları | Dört hash, HTTP indirme ve 206 Range yanıtı geçti |
| Android EMA | Gerçek yerel çıkarım, oynatma ve iptal geçti |
| Android elle makro | %35 tarifi sonrası medya sesi 5/15; tamamlandı kaydı |
| Android otomatik makro | Şarja bağlanma olayı → bildirim eylemi; foreground servis ve geçmiş doğrulandı |
| Teslim kaynakları | Değişen 42 dosya Mac ile eşleşti |
| APK | İmza doğrulandı; 17 inceleme/test etkinliği dışarıya kapalı |

Kanıtlar `kanit/` klasöründedir. Ekran görüntüleri kontrollü emülatör verisidir. Fiziksel telefonda yükseltme, mikrofon/araç kabulü ve gerçek fener donanımı bu turda test edilmedi. Mola'nın önceki raporda belirtilen kablosuz yardımcı ve kilitli telefon eksikleri bu sürümde tamamlanmadı.

Ses testi öncesinde 9 düzeyine ayarlama komutu verildi; aynı komutun hemen yaptığı okuma hâlâ 5 döndürdü. Bu nedenle kesin bir 9→5 geçişi kanıtı olarak sayılmadı. Tarifin tamamlanması ve son düzeyin 5/15 hedefiyle eşleşmesi doğrulandı; JVM regresyonunda başlangıçtan farklı düzeye geçiş ayrıca sınandı.

APK 63.143.147 bayt, önceki Mac sürümüyle aynı debug sertifikasıyla imzalı preview. Sertifika SHA-256: `c22b26996797c11316c81fd292a83764ce2879adce9eb424bad586ab054ed34f`. Eski APK'lar korunur; telefondaki uygulama kaldırılmadı.

## Dağıtım kaydı

Kanonik kaynak ve yeni APK: `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration`.

Yalnız bu görevde eklenen `com.hermes.mobile-workspace` adapter'ı güncellendi ve yeniden başlatıldı. Bot turu sürerken dağıtım yapılmaması SQLite kontrolüyle sınandı. Ana Dashboard, gateway ve telefon köprüsü yeniden başlatılmadı; Hermes'in yönetilen Python ortamına kurulum yapılmadı. Caddy rotası değişmedi.

EMA'nın doğrulanmış kopyası `/Users/gokhanuzman/hermes-local/mobile-workspace/ema-model` içinde. Eski model arşivi korunur. Adapter önceki kaynak yedeği `backups/20261103-v710/backend-before.tar.gz`, envanter son düzeltmesi `inventory-before.tar.gz`; Android kaynak yedeği kanonik projede `kanit/v7.1.0-20261103/source-before.tar.gz` içindedir.

Tam kaynak ZIP'i güncel teslimdir. `source-update` v7.0.1 tabanından son farkı içerir; `inventory-delta` ise bu oturumdaki ara dağıtım kaydıdır, güncel projeye yeniden uygulanmamalıdır. Kaynak ZIP'i yerel anahtar, token, kullanıcı veritabanı veya model ağırlıkları içermez. EMA ağırlıkları doğrulanmış sunucu yolundan indirilir. Windows derlemesinde Mac Java yolu `-Dorg.gradle.java.home=<yerel-JDK>` ile geçersiz kılınmalıdır.
