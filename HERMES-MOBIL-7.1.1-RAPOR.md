# Hermes Mobil 7.1.1 — son kontrol ve APK teslimi

3 Kasım 2026. Sürüm `7.1.1-preview`, kod **20**, paket `com.hermes.mobile.v3`. ARM64, Android 8+.

[APK](HermesMobil-v7.1.1.apk) · [Kaynak kod](HermesMobil-v7.1.1-kaynak.zip) · [SHA-256](HermesMobil-v7.1.1.apk.sha256).

APK SHA-256: `01425103008d7b37e7d403c7d4759c19eed17b4d1e197c5a7322969225d97f16`. Boyut: 63,159,527 bayt.

## Son incelemede düzeltilenler

- Yarım kalan makro yazımında yalnız `.bak` kalırsa AtomicFile kurtarması atlanıyordu. Okuma ve kaydetme öncesinde yedek kurtarılır; bozuk kayıtların üstüne yazılmaz.
- Yeni çoklu MacroDroid/OpenTasker içe aktarmalarında her kaydın kendi kaynak nesnesi ve üst verileri korunur. İlk kaydı silmek diğerlerinin kaynak metnini kaybettirmez. Uyumsuz grafikler duraklatılmış inceleme kayıtlarıdır.
- Kullanıcı yalnız makro bildirim kanalını kapattığında sessiz başarı gösteriliyordu. Kanal denetimi tüm eylemlerden önce yapılır.
- Otomatik olayların kamera/ses/dosya işlemleri tek arka plan çalışanına taşındı. En çok 16 olay bekler; taşma geçmişe yazılır. İzleyici durdurulunca bekleyen işler bırakılır, çalışan makro sonraki adımdan önce durur.
- EMA indirmesi iptal edilirken yeni bir indirme başlatma yarışı önlendi; iptal edilen ağ isteği yedek indirmeyi başlatmaz. Geçici Piper deneme motoru kapatılır. Model kaldırma hataları arayüzde gösterilir.

Önceki temaya uyumlu konuşma çerçevesi, sohbet metni seçimi/bağlantılar, bot çalışma alanı ve Makrolar modülü korunur.

## Test kanıtı

| Kontrol | Sonuç |
|---|---|
| Windows Android testleri | 1.504 test; hata/atlama 0 |
| Mac Android testleri | 1.504 test; hata/atlama 0 |
| Backend | 74/74 |
| Lint | 0 hata; 44 uyarı, 7 bilgi |
| Makro regresyonları | Eski kodda hatalar üretildi; son odaklı test 18/18 |
| Gerçek Dashboard | 12 sağlayıcı / 57 model anlık görüntüsü; gizli yapılandırma alanları dışarı çıkmıyor |
| EMA sunucusu | Dört dosyanın boyut/hashleri, Range 206, kimliksiz 401 doğrulandı |
| Emülatörde yükseltme | Aynı Mac imzalı 19 → 20; makro değişmeden korundu |
| Gerçek Android yedek kurtarma | Yalnız `.bak` bırakılan kayıt geri geldi; elle çalıştırma tamamlandı |
| İmza ve manifest | Önceki sertifika aynı; 17 test etkinliği dışarı kapalı |
| Mac kaynak karşılaştırması | Değişen 8 dosya hash ile eşleşti |

Emülatör x86_64 derlemesi aynı kaynak, sürüm ve Mac sertifikasını kullanır. Teslim APK'sı ARM64'tür. Medya sesi emülatörde 5/15 kaldığı için bu tur sayısal ses geçişi kanıtı ileri sürülmez; test eylemin tamamlanması ve kayıt kurtarmayı kanıtlar. Ses değişimi JVM regresyonunda ayrıca sınandı.

Fiziksel telefonda yükseltme, mikrofon/fener ve Android Auto araç kabulü yapılmadı. Mola'nın önceki kablosuz yardımcı/kilitli telefon eksikleri sürüyor. MacroDroid/OpenTasker'ın tam motorları taşınmış değildir. Önceki EMA gerçek çevrimdışı çıkarım/oynatma kanıtları [7.1.0 raporundadır](../v7.1.0/OKU.md); bu tur yeniden indirme/çıkarım kabulü olarak sunulmaz.

## Kurulum ve geri dönüş

APK'yı telefona aktararak mevcut **Hermes V3** üzerine güncelle. Paket ve imza aynı; uygulamayı kaldırmak gerekmez. Makrolar: **Modüller → Makrolar**. Otomatik izleme hem genel hem kural anahtarıyla açılır.

Mac kanonik APK: `/Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration/HermesMobil-v7.1.1.apk`.
Sekiz kaynak dosyasının önceki sürümü aynı projede `kanit/v7.1.1-20261103/source-before.tar.gz` içinde korunur. Önceki APK ve teslimler silinmedi. Bu tur sunucu kaynakları veya çalışan Hermes servisleri değiştirilmedi. Eski Dashboard katalog koruması sürer; mobil alternatif envanter yolu yeniden doğrulandı.

Kaynak ZIP'i 516 dosya içerir; yerel anahtar/token, kullanıcı veritabanı ve model ağırlıkları dahil değildir. `source-update` yalnız 7.1.0 tabanına uygulanacak farktır; güncel Mac'e tekrar uygulanmaz. Önceki debug sertifikasıyla imzalı preview teslimidir.

Oturum devam kaydı `DEVAM.md`, HKP1 kısa harita ve ham kaynak anlık kopyası `oturum/` altındadır. Paket dosyası oluşturmak çalışan sohbetin bağlamını kendiliğinden sıfırlamaz; kaynak Codex kaydı korunur.
