# Hermes EMA 1.5 — Sesli Asistan doğrulaması

Tarih: 8 Ekim 2026. Canlı profil: `sesli-asistan`, Evren / `gemma-4-31b`. Uygulama: `com.hermes.mobile.ema20261007`, sürüm kodu 8. Test telefonu: Samsung SM-S918B, Android 16.

## Otomatik kontrol

`:app:testDebugUnitTest` ve `:app:assembleDebug` geçti: **937 test, 0 hata, 0 başarısız test**. ARM64 debug APK imza doğrulaması geçti. Yeni testler sunucu/sohbet ayrımını, devam sorularını, sınırlı bağlamı, gizli bilgi ayıklamayı ve geçmiş yüklenirken temiz kullanıcı balonunu doğrular. Var olan bildirim zaman damgası testi, yeniden yüklemede zamanın değişmesine yol açan serileştirme hatasını da yakaladı; zaman artık açıkça kaydedilir.

## Canlı sunucu ve telefon

- Yeni profil ana profilin modelini ve etkin oturumlarının talimatlarını değiştirmeden kuruldu. Evren anahtarı yalnız profilin özel `.env` dosyasına yazıldı.
- Canlı Hermes üzerinden kısa sorular cevaplandı. Sekiz saniyelik sentetik görev gerçek `delegate_task` ile ayrı ajana aktarıldı; ana yanıt görev tamamlanmadan geldi, sonuç aynı sohbetin olay akışına döndü.
- Telefonda kaynak sohbetin sentetik “menekşe” bağlamı ayrı sesli oturuma aktarıldı. Asistan yeniden oluşturulduğunda aynı sesli oturum devam etti.
- `ChatViewModel.sendVoice` üzerinden gönderilen yeni matematik sorusu aynı uzmanda cevaplandı. Kayıtlı yazılı model tercihleri değişmedi.
- Telefonun sesli gönderim yolundan gerçek görev aktarımı ve sonucun görünür sohbete dönmesi doğrulandı.
- Araç sesli beyninin doğrudan girişinden “Pil ne kadar?” sorusu cihazdaki `phone_status` aracıyla cevaplandı; Hermes'e zorunlu bağlanma seçeneğinde de bu komut yerel kaldı.
- Araç konuşma bildiriminin gerçek `RemoteInput` yanıtı ayrı sesli uzman oturumuna gitti. İki cümlelik cevap tamamlandı ve telefondaki indirilen EMA modeliyle sonuna kadar okundu. Bu test araç donanımında yapılmadı.

## Hız ölçümünün anlamı

Doğrudan Evren karşılaştırmasında aynı kısa soruda GLM-5.3 ilk metni 10,526 saniyede, Gemma-4-31B 1,281 saniyede verdi. Tam Hermes sunucu yolunda ayrı kısa denemeler 0,813–2,841 saniye sürdü. Son APK ile telefon kabul denemesinde kaynak bağlam sorusunun ilk metni 2,456 saniyede, devam sorusunun ilk metni 1,297 saniyede geldi. Bunlar ilk **metin** süreleridir; mikrofon, konuşma tanıma ve sesin başlaması dahil değildir. Ağ ve soru karmaşıklığı sonucu değiştirebilir.

Bir tekrarın görev sonucu denetimi “eşittir 4” ifadesini tanımadığı için yanlış zaman aşımı bildirdi. Yalnız sentetik oturumun geçmişi okunarak gerçek tamamlanma doğrulandı, test ifadesi düzeltildi ve son APK ile bütün telefon adımları `RESULT PASS` verdi.

## Açık sınırlar

EMA ses üretimi çevrimdışı çalışabilir; Hermes/Evren yanıtı ağ gerektirir. Telefon testleri ev ağı üzerinden yapıldı; ev dışından telefon bağlantısı kabulü tamamlanmadı. Gerçek araçtaki kablosuz Android Auto bağlantısı ve Bluetooth görüşmelerinin iki taraflı kaydı doğrulanmış değildir. Bildirim testi bu sorunların çözüldüğü anlamına gelmez. Mevcut adlandırılmış Hermes profiline görev aktarımı veya otomatik Takip kaydı eklenmedi; kullanıcı sohbeti Takip sekmesine ekleyebilir.
