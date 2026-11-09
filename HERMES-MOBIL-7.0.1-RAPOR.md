# Hermes Mobil 7.0.1 — teslim ve kullanım

3 Kasım 2026. Teslim sürümü `7.0.1-preview`, kod 18, paket `com.hermes.mobile.v3`, ARM64, Android 8 ve üzeri. Bu rapor önceki v7.0.0 belgelerindeki bağlantı ve Mola durumunun yerini alır.

APK: [HermesMobil-v7.0.1.apk](HermesMobil-v7.0.1.apk). Kaynak: [HermesMobil-v7.0.1-kaynak.zip](HermesMobil-v7.0.1-kaynak.zip).

SHA-256: `d4aa2820651f3dea2dff253cddb76eaf5581c954b2ce899f5ffcf8642b387401`.

## Kullanım

Hermes ayarlarında mevcut Dashboard kaydını seçin. Botlar aynı Dashboard adresi ve tokenıyla bağlanır; ikinci bir bot sunucusu, Mika/Phongent APK'sı veya Mola sunucusu girilmez. Telefon ajanı için uygulama içindeki Telefon bölümünde Android erişilebilirlik izni ve “Ajan telefonu kullanabilsin” tercihi gerekir. Yazma kontrolü, salt okunur kip ve acil durdurma cihazda uygulanır.

Bot atölyesinde mevcut düzenleyici, sohbet, geçmiş, görev kuyruğu ve içe/dışa aktarma korundu. İsteğe bağlı Kıvılcım, Meraklı Baykuş ve Telefon Ajanı şablonları eklendi. Şablon seçmek kendiliğinden telefonda işlem başlatmaz; görev taslağı gönderim kuyruğuna hazırlanır.

Sohbette metin seçme, bağlantıları açma, kopyalama, paylaşma, sabitleme, arama ve Markdown kaydetme v7 akışları korunur. Bot ve telefon bölümlerindeki bağlantı alanları tek seçili Dashboard kaydına bağlandı. Mika/Phongent araştırmasından görev kuyruğu ve cihaz denetimi yaklaşımı kullanıldı; iki projenin tüm motorlarının eksiksiz taşındığı iddia edilmez. Araştırma ve lisans notları kaynak paketinin `third_party` ve `docs/integration` klasörlerindedir. Kaynak projeler: [Mika](https://github.com/wangzan101/mika), [Phongent](https://github.com/mylea1/phongent).

## Düzeltilen somut hatalar

- Bot API ile Dashboard'un aynı `/api/sessions` yolunu farklı anlamda kullanması giderildi. Yeni adapter `/mobile-api/bot/api/...` alanını kullanır.
- Bot yanıtları demo yanıtlayıcısı yerine mevcut Dashboard WebSocket oturumundan alınır. Sonraki mesaj aynı saklı gateway görüşmesine devam eder. Sonucu belirsiz gönderimler kendiliğinden tekrarlanmaz.
- Bot ekranının seçili Dashboard değişikliğini izlememesi düzeltildi. Telefon köprüsü profil/token/adres değiştiğinde eski kanalı kapatır; eski profile ait komutları reddeder.
- Telefon köprüsünün varsayılan adresi ayrı 9180 portu yerine seçili Dashboard'un `/phone-bridge/phone` yoluna bağlandı. Önceden açıkça girilmiş ileri düzey köprü adresi korunur.
- Mola menüsünün telefon kullanımı yerine yalnız Hermes sohbet panelini açması düzeltildi. “Telefonu Kullan” ve “Hermes sohbet paneli” ayrı girişler oldu.
- Telefonda ekran paylaşımı düğmesinin bildirim izni yokken kendi izin penceresinin önüne ana ekranı açması düzeltildi.
- Mola dokunma/kaydırma ve uygulama açma eylemlerinde güncel cihaz izinleri, salt okunur kip ve durdurma kontrolü uygulanır.
- Araç servisindeki herkesi kabul eden host doğrulaması kaldırıldı; AndroidX host doğrulayıcısı eklendi. Bilinmeyen host için ret testi geçti. Gerekçe: [HostValidator resmi belgesi](https://developer.android.com/reference/androidx/car/app/validation/HostValidator).
- Ses tanıma servisine bağlanmak için mikrofon izni zorunlu kılındı. Teslim APK'sındaki 17 test/inceleme etkinliği dışarıya kapalıdır.
- Telefon köprüsünün ağdaki `/call` ve `/status` HTTP yollarında eksik token denetimi giderildi. Yerel MCP 9181 sözleşmesi korunur; komut içeriği logları yerine yalnız işlem metadatası tutulur.

## Mola: mevcut işlev ve kalan eksik

[Paylaşılan video](https://www.youtube.com/watch?v=8URObluFUHk) konuşma dökümü üzerinden incelendi; görüntüler kare kare incelenmedi. Videodaki dahili yardımcı, kablosuz hata ayıklama eşleştirmesi ve kilitli telefonda uygulama çalıştırma mekanizması bu sürüme taşınmadı. Dolayısıyla videodaki Mola ile tam eşdeğerlik yoktur.

Bu APK'da Android MediaProjection ekran paylaşımı, erişilebilirlik üzerinden izinli dokunma, araç için yüklü uygulama listesi ve son kullanılan altı uygulama kaydı bulunur. Rehberden Android ekran paylaşım izni alınır; araçta “Telefonu Kullan · Mola” açılır. Uygulama listesi araç host'unun satır sınırına göre sayfalanır. Hermes sohbet paneli ayrıca açılır.

Emülatörde native Android izin penceresi görüldü, tüm ekran paylaşımı kabul edildi ve `MirrorProjectionService` foreground olarak başladı. Rehber hazır durumuna geçti. Araç yüzeyi bağlı olmadığı için araçta görüntü/dokunma, uygulama başlatma ve yeniden bağlantı kabulü yapılmadı. API 36.1 emülatörünün `dumpsys media_projection` çıktısında sistem taraflı NPE görüldü; servis kanıtı `dumpsys activity services` ile alındı. Bu sonuç gerçek araç kabulü değildir. Resmi test ayrımı: [Android for Cars test araçları](https://developer.android.com/training/cars/testing).

## Doğrulama kanıtları

| Kontrol | Sonuç |
|---|---|
| Mac son kaynak, test ve APK derlemesi | 1.486/1.486; hata, atlama yok |
| Windows tam Android testi | 1.486/1.486; hata, atlama yok |
| Backend | 68/68 |
| Telefon köprüsü HTTP yetki regresyonu | 3/3 |
| Android lint | 0 hata, 43 uyarı, 7 bilgi; toplu bağımlılık yükseltmesi yapılmadı |
| Gerçek Dashboard ve model | Kontrollü botta 2+3 → 5; aynı görüşmede önceki sonuç+2 → 7 |
| Kimliksiz erişim | Adapter ve ağ telefon köprüsünde reddedildi |
| Mac/teslim kaynak karşılaştırması | Değişen 29 dosyanın normalize SHA-256 değerleri eşleşti |
| APK imzası | v2 doğrulandı; önceki Mac APK'larıyla aynı sertifika |
| Fiziksel telefonda güncelleme/kayıt koruma | Yapılmadı |
| Gerçek telefon ajanıyla uçtan uca eylem | Yapılmadı; telefon köprüsü dağıtım sırasında çevrimdışıydı |
| Fiziksel Android Auto / kilitli telefon | Yapılmadı |

Gerçek model kanıtı `kanit/dashboard-real-model-proof.json` içindedir. Geçici test botu silindi; test öncesi mevcut bot sayısı sıfırdı ve test sonrası yine sıfır olduğu doğrulandı. Bu, dolu bir kullanıcının eski telefon kayıtlarının yükseltme testinin yapıldığı anlamına gelmez. Test görüşmesi gateway geçmişinde kalabilir. Ekran görüntülerindeki botlar kontrollü yerel UI verisidir; canlı kullanıcı verisi değildir.

APK boyutu 64.640.343 bayt. Sertifika SHA-256: `c22b26996797c11316c81fd292a83764ce2879adce9eb424bad586ab054ed34f`. APK hâlâ debug imzalı preview'dur. İmza eşleşmesi doğrulandı; telefonda kurulum/yükseltme ayrıca sınanmalıdır. Uygulamayı kaldırarak veri kaybettiren bir geçiş yapılmadı.

## Mac dağıtımı ve geri dönüş kaydı

Kanonik proje: `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration`. Yeni APK aynı klasördedir. Eski v6.6/v6.7/v7.0.0 teslimleri üzerine yazılmadı.

Yeni adapter servisi `com.hermes.mobile-workspace`, yerel çalışma dizini `/Users/gokhanuzman/hermes-local/mobile-workspace`, yalnız loopback 19760. Caddy'de `/mobile-api/*` yolu eklendi; mevcut 12 rota korundu. Dashboard ve ana gateway yeniden başlatılmadı. Saf Python websocket bağımlılığı adapter'a ayrı kopyalandı; Hermes'in yönetilen Python ortamına paket kurulmadı.

Adapter bot persona metnini ilk iletiye rol bağlamı olarak ekler. Bu bir sunucu sistem istemi veya bot başına araç sandbox'ı değildir. Gerçek araç yetkileri mevcut Hermes profiline; telefon yetkileri ayrıca cihazdaki izinlere bağlıdır. Ortak sağlayıcı/model ayarları değiştirilmedi.

Telefon köprüsü yalnız bağlı telefon yokken hedefli olarak yeniden başlatıldı. Kaynağı `/Users/gokhanuzman/007-HERMES/20-ARACLAR/phone_bridge.py`. HTTP yetki patch'i ve önce/sonra hashleri bu pakettedir. Android kaynak yedekleri kanonik projede `kanit/v7.0.1-20261103/source-before*.tar.gz`; adapter/Caddy özel yedekleri yerel adapter'ın `backups/20261103-v71` klasöründedir. Bu özel yapılandırma yedekleri teslim ZIP'ine eklenmedi. Geri dönüş bu yedeklerle yalnız değişen dosya/rota/servise uygulanmalıdır; genel Hermes kurulumu yeniden çalıştırılmamalıdır.

`source-update.tar.gz` v7.0.0 Android teslimi ve backend HEAD tabanından değişen dosyaları içerir. Ek `corrections`, `binding-corrections`, `host-corrections` paketleri bu oturumdaki aşamalı Mac uygulamasının denetim kayıtlarıdır; güncel projeye sırayla yeniden uygulanmamalıdır. Yeni bir checkout için tam kaynak ZIP'i esas alınır. Derleme ayarındaki Mac Java yolu Windows'ta `-Dorg.gradle.java.home=<yerel-JDK>` ile geçersiz kılınmalıdır. Anahtar ve yerel veritabanları kaynak teslimine dahil değildir.
