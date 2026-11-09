# Hermes Mobil 7.2.0

[APK](HermesMobil-v7.2.0.apk) · [Kaynak kod](HermesMobil-v7.2.0-kaynak.zip) · [SHA-256](HermesMobil-v7.2.0.apk.sha256)

3 Kasım 2026. Sürüm `7.2.0-preview`, kod21, paket `com.hermes.mobile.v3`, ARM64, Android8+.
APK SHA-256: `a3a7484f50ac33f0add6d4a65564208c9bc1ba42eb84a88263b64742080b1af0`. Boyut: 63159527 bayt.

## Makrolar

Yerel makroların telefon ajanı duraklatma ayarına ve şifreli telefon kimliği deposuna bağımlılığı kaldırıldı. Makrolar için sunucu/telefon ajanı yetkisi gerekmez. İzleyicinin kendi aç/kapat anahtarı ve servis durunca işi iptal etme davranışı korunur.

Hazır tarife dokununca düzenleyici açmak yerine tarif doğrudan duraklatılmış olarak eklenir. İzinleri uygulama içinden isteme düğmesi ve kısa kullanım adımları eklendi. Otomatik çalıştırma izin/bekleme süresine takılırsa neden geçmişe yazılır. Mevcut kayıtlar silinmez; elle çalıştırma önce eylemleri gösterir.

Kullanım:
1. Modüller → Makrolar → örneğin **+ Şarj arkadaşı**.
2. **İzinleri hazırla** düğmesinden Android bildirim iznini ver. Fener için Kamera izni gerekir. Android'de kanal daha önce kapatıldıysa Uygulama ayarlarından kanalı da aç.
3. Kayıtta **Elle dene → Çalıştır** ile sınayabilirsin.
4. Otomatik çalışması için kayıttaki **Etkin** ve üstteki **Otomatik kurallar** açık olmalı. Aynı makronun varsayılan bekleme süresi30 saniyedir; hemen ardışık tetikleme bekleme kaydı oluşturur.

MacroDroid/OpenTasker özgün dosyaları inceleme kaydı olarak korunur; desteklenmeyen dış grafikler yürütülmez. Çalıştırılabilir yerel uyarlamalar ayrı tariflerdir. Tüm üçüncü taraf motorları eklenmiş değildir.

## Bot arkadaşları

Bot kartlarında temanın renkleriyle çizilen özgün küçük karakterler var. Kartın tamamına dokunmak doğrudan sohbeti açar; ayrıntılar üç nokta menüsündedir. Sohbet ve görev verme düğmeleri durur. Botlar ve geçmişleri aynı Dashboard bağlantısı/tokenıyla kullanılmaya devam eder.

Ana bot ekranındaki büyük acil durdur satırı ve telefon kartındaki büyük kırmızı düğme kaldırıldı. Önceden duraklatılmış telefon erişimi için yalnız açık bir sürdürme işlemi kalır; yerel makroları engellemez. Telefonun mevcut erişilebilirlik ve yetki denetimleri geçerlidir. Grok Bot/dots esinli sade arayüz, bu hizmetlere hesap/API bağlantısı yapmaz; kullanıcıya yeni bir bulut bilgisayar tahsis etmez.

Nefes entegrasyonu kullanıcı isteğiyle iptal edildi. Model kurulmadı, çalışan GPU modelleri kapatılmadı. Mevcut EMA/sistem/Piper ses yolları bu sürümde değiştirilmedi.

## Kontroller

| Kontrol | Sonuç |
|---|---|
| Mac Android tam test | 1.505/1.505; hata/atlama0 |
| Windows Android tam test | 1.505/1.505; hata/atlama0 |
| Lint | 0 hata,44 uyarı,7 bilgi |
| Odaklı makro regresyonu | 19/19 |
| Aynı imzayla güncelleme | Emülatörde20→21; kayıt kimliği/eylemi korundu |
| Android izin akışı | Bildirim izni uygulama düğmesinden verildi |
| Android elle çalıştırma | Gerçek yerel bildirim eylemi tamamlandı |
| Android otomatik çalıştırma | Şarj AC off→on olayı; ayrı ikinci Tamamlandı kaydı |
| İmza | Önceki Mac sertifikası aynı, apksigner doğrulandı |
| Test etkinlikleri | Teslim APK'sındaki17 test etkinliği dışarı kapalı |
| Kaynak | Mac'te değişen8 dosya hash ile eşleşti; ZIP 517 dosya |

Backend değişmedi;74 testlik önceki7.1.1 kontrol kaydı kanıtlarda tarihsel olarak korunur, bu sürümde yeniden koşulduğu iddia edilmez. Ana Dashboard/gateway/telefon köprüsü yeniden başlatılmadı.

Bildirim eylemleri kontrollü Android emülatöründe doğrulandı. Fiziksel telefonda bildirilen iki hatanın aynı cihazdaki kabulü, fener donanımı ve Android Auto araç testi yapılmadı. Makrolar artık bağımsız çalışır; izin/kanal/kural anahtarları yine Android'de açık olmalıdır. Önceki Mola kablosuz yardımcı/kilitli telefon eksikleri devam eder.

## Teslim ve yedek

Mevcut Hermes V3 üzerine APK güncellemesi olarak kurulabilir; paket ve imza aynı. Uygulamayı kaldırarak verileri silmek gerekmez. Preview/debug sertifikalı teslimdir.

Mac kanonik proje: `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration`.
Kaynak yedeği: `kanit/v7.2.0-20261103/source-before.tar.gz`. Önceki APK'lar korunur.
`source-update`7.1.1 tabanından farktır; güncel Mac'e tekrar uygulanmamalıdır. Kaynak ZIP'i anahtar/token, kullanıcı veritabanı veya model ağırlıkları içermez.

Bot arayüzü ayrıca ayrı test paketinde, yalnız sahte yerel Dashboard ile görsel olarak incelendi; kart tıklaması sohbeti açtı. Gerçek modele veya telefona görev gönderilmedi. Son iki görsel dosya değişikliğinden sonra Mac tam testleri ve Windows arayüz derleme/lint tekrar geçti. Ek kaynak yedeği aynı klasörde ui-before.tar.gz dosyasındadır.

Mevcut Codex kaydı czip-session-pack ile paketlendi; kısa harita doğrulandı, ham kaynak korundu. Paket dosyası çalışan sohbetin bağlamını kendiliğinden sıfırlamaz.
