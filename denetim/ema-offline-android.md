# EMA Android çevrimdışı ses — 7 Ekim 2026

EMA Lightning'in gerçek ağırlıkları telefona bir kez indirilir; sonraki ses üretimi Android CPU üzerinde, sunucu veya ağ çağrısı olmadan yapılır. Sohbet, telefon araçları, sesli asistan ve araç yanıtları ortak EmaSpeech sözleşmesini kullanır. Hermes model yanıtları ve Whisper konuşma çözümlemesi halen sunucu bağlantısı ister. Bu sürüm entegrasyon betadır.

## Kullanım ve kalıcılık

Ayarlar → Ses · EMA ve mikrofon → EMA modelini indir. Yaklaşık 35 MB indirme, durdurma/devam etme, boyut ve SHA-256 doğrulaması, yerel ses denemesi ve kaldırma sunulur. Başarılı indirme ve model yükleme sonrası çevrimdışı mod seçilir. Model özel uygulama alanında kalır; normal APK güncellemeleri silmez. Uygulama verilerini silmek veya uygulamayı kaldırmak yeniden indirmeyi gerektirir. Sunucu modu ayrıca seçilebilir; EMA hatasında başka sese sessiz geçiş yapılmaz.

Sohbet açılış ekranı sadeleştirildi. Oturumlar ☰ menüsünde; oyun/Arena, profil çipleri ve teknik istatistik şeritleri ana akıştan çıkarıldı. Model seçimi profil bazında kaydedilir. Evren'in sohbet modelleri gösterilir; OCR, embedding, reranker ve ASR modelleri sohbet listesinden ayrılır. Model seçimi mevcut sohbete otomatik deneme mesajı göndermez. Boş sohbette seçim yeni ajan oluşturmaz; ilk mesajda sunucudaki seçim uygulanır. Başarı onayı alınmadan mevcut oturum modeli değiştirilmiş gösterilmez.

Ev ve dış bağlantı adresleri aynı Hermes profilinde saklanır. EMA sunucu modu profil adreslerinden /ema-api yolunu türetir; özel LAN EMA adresi için kayıtlı uzak adres alternatif olabilir. Yalnız bağlantı kurulamadığında diğer adres denenir; yetki hatası veya başlamış ses üretimi tekrar edilmez. Sunucu Caddy örneği relay/ema_caddy_snippet.txt içindedir. Kişisel adresler ve erişim anahtarları kaynak/APK içine konmaz.

## Model ve native köprü

- Kaynak EMA Lightning 1.0.1, Apache-2.0. Ağırlık kaynağı: https://huggingface.co/canberkkkkkk/ema-lightning ; kaynak revizyonu 7a6ba1ad216bb2f1da9863f80ac8770a6a807632.
- İndirilen ONNX paketi models/ema-android-v1 dalında, değişmez commit 2d5c71c64446069c72404471d71ef451fbaff69d. Dört dosya toplam 34.984.583 bayt. Lisans ve dönüştürme kanıtı model paketiyle birlikte yayınlanır.
- Text, dört adımlı acoustic ve decoder aşamaları relay/export_ema_android.py ile ONNX opset 18'e çevrildi. İki farklı uzunlukta Türkçe cümlede PyTorch/ONNX en büyük latent farkı 0,000398; aynı latent girdisindeki decoder farkı 0,000000624 altındadır.
- ONNX Runtime 1.28.0 Java/JNI köprüsü, mevcut sherpa-onnx ORT 1.28.2 C API 28'e bağlanır. İkinci ORT native runtime eklenmez. Kaynak ve lisans: app/libs/EMA-ORT-NOTICE.md; yeniden üretim: relay/build_ort_java_bridge.py.
- Android pipeline 48 kHz mono PCM16 üretir; kısa parçalar ve seri, yeniden kullanılan model oturumları bellek tüketimini sınırlar. İptal, native RunOptions ve oynatma kuyruğuna iletilir.

## Gerçek doğrulama

| Kontrol | Sonuç |
|---|---|
| Android unit test + debug APK derleme | 907 test, 0 hata |
| Python EMA HTTP servis testleri | 5 geçti |
| GitHub gerçek model indirmesi, yarıda durdurma/devam, tüm SHA-256 | Samsung ve emülatörde geçti |
| AOSP 35 arm64, uçak modu / Wi-Fi ve veri kapalı, aktif varsayılan ağ yok | Soğuk süreçten EMA yükleme ve iki farklı ses geçti |
| Emülatör ilk / ikinci ses | 2,73 / 2,37 sn ses; 423 / 206 ms üretim |
| Samsung SM-S918B Android 16, zorunlu yerel EMA yolu | Aynı sesler; 434 / 279 ms üretim |
| Yerel sırayla iki cümle oynatma, tek tamamlanma, iptal | Her iki cihazda geçti |
| Samsung kesintisiz normal yerel EMA konuşması | Geçti |
| Çevrimdışı üretilmiş WAV'ın mevcut Whisper ile sonradan çözümlenmesi | İki Türkçe cümle doğru çözüldü |
| Evren glm-5.3 gerçek API çağrısı | Geçti; anahtar sunucuda kaldı |
| Evren seçimi ve uygulama soğuk açılışında korunması | Emülatör gerçek UI testi geçti |
| Uzak HTTPS EMA yolu, geçerli RIFF WAV / anahtarsız istek | HTTP 200 / HTTP 403 |
| Samsung kendi geçici formunda ekran okuma, dokunma, yazma, tekrar dokunma | Geçti; salt okunur ve ajan kapalı kapıları geçti |
| Android Auto gerçek telefon + DHU: menü, Hermes ekranı, Konuş/kayıt, iptal | Geçti; iki yazılı ActionStrip düğmesinden kaynaklanan gerçek çökme düzeltildi |
| Telefon kontrol köprüsü | Bağlandı; 30 araç bildirildi |
| arm64 native ELF ve APK ZIP hizalama | 10 native kütüphane ≥16 KB; zipalign geçti |

Samsung testi sırasında ağ açık tutuldu; tamamen bağlantısız kabul emülatörde yapıldı. Samsung üretimi yerel EmaOfflineSpeech yolundaydı. Ölçümler iki kısa cümleye aittir; her telefon/metin için gecikme garantisi değildir. Türkçe frontend temel sayı/saat/kısaltmaları destekler; Python normalizer-tr'nin bütün özel gösterim kuralları henüz aktarılmadı. Java Gaussian RNG kullanımı PyTorch ile birebir ses baytı eşitliği sağlamaz.

## Açık kabul alanları

Android Auto ekranı ve kayıt/iptal akışı gerçek telefonda DHU ile doğrulandı. DHU kayıtlı mikrofon girdisi genel Android Auto sesli asistanını da etkinleştirdi; Hermes'e giden kayıtta konuşma tespit edilemedi. Araç mikrofonu → STT → Hermes → EMA tam yanıt kabulü henüz tamamlanmadı. Test komutları: https://developer.android.com/training/cars/testing/dhu . Play Store kategori uygunluğu ve host imza izin listesi bu yan yüklenen geliştirme sürümünde tamamlanmış değildir. Gerçek 16 KB sayfalı cihaz testi yapılmadı. HTTPS dış adres ev ağından doğrulandı; hücresel/başka ağ testi henüz yok. İki tur ortak sohbet testi tekrar Samsung'da denendi: 10 etkin oturum / sınır 8 hatası devam etti. Çalışan kullanıcı oturumları sonlandırılmadı. Test için geçici sohbet seçimi kullanıldı ve kullanıcıdaki önceki seçim geri yüklendi. Ayrı Gemini canlı modu kendi geçmişini kullanmaya devam eder.

Telefon kontrolü erişilebilirlik, ajan izni, salt okunur ve tam kontrol ayarlarıyla sınırlıdır; güvenli Android ekranları ve Shizuku gerektiren işlemler ayrıca izin ister. Kendi formundaki başarı bütün telefon işlemlerinin desteklendiği anlamına gelmez.
