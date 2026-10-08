# Sohbet, bildirimler ve takip — EMA önizleme 1.2

Ana gezinme üç sekmedir: Sohbet, Bildirimler, Takip. Sunucu panosu, görevler ve terminal Ayarlar → Sunucu yönetimi üzerinden açılır.

Sohbette varsayılan tam akış; mesajlar, sunucunun paylaştığı düşünme/açıklama, durum olayları ve araç giriş/sonuçları sırasıyla görünür. Menüde Sade sohbet görünümü ile ayrıntılar katlanabilir. Tercih kalıcıdır. Geçmiş artık 150 mesajla kesilmez; sunucunun verdiği tüm sohbet kayıtları LazyColumn ile çizilir. Sunucunun göndermediği veya saklamadığı olaylar istemci tarafından yeniden üretilemez. Sistem/otomatik gürültü süzmesi korunur. Taslak ve kaydırma konumu sunucu/oturum bazında sekmeler arasında korunur.

Oturum çekmecesinde bir oturuma uzun basıp Takip et seçilir. Takip, sabitlemeden bağımsız ve sunucu bazında kalıcıdır. Takip ekranında arama, yenileme, sohbete geçiş ve takibi bırakma bulunur. Arşivlenmiş takipler görünür; gizlenen oturumlar görünmez. Açık ama boşta oturum çalışan diye gösterilmez.

Bildirimler, bu uygulamanın aldığı Hermes yanıtları ve gözlemlediği takip değişiklikleridir; telefonun diğer uygulamalarının bildirimleri değildir. Okunmamış süzmesi ve tümünü okundu işaretleme bulunur. Son 200 kayıt, cihazın uygulamaya özel dosyasında saklanır. Profil sınırı korunur; bir bildirim veya hızlı yanıt başka sunucuya yönlendirilmez. Bildirimden ilgili sohbet açılır. Ekranda görülen sohbetin kayıtları okundu işaretlenir; arka plandaki sohbetin kayıtları okunmamış kalır.

Canlı takip, uygulamanın bağlı olduğu sunucudan altı saniyelik sorgularla izlenir. İlk liste eski olayları bildirim diye üretmez; başarısız sorgular boş liste veya tamamlanma sayılmaz. Canlı listeden ayrılmak başarı olarak adlandırılmaz. Uygulama süreci durduğunda kesintisiz sunucu push bildirimi garantisi yoktur; FCM eklenmemiştir.

## Doğrulama

`./gradlew :app:testDebugUnitTest :app:assembleDebug -PhermesDebugSuffix=.ema20261007 --offline`

Yeni testler: eski bayrak uyumluluğu, takip/sabitleme ayrımı, arşiv/gizleme, canlı durum değişimleri ve tekrar sorguları, sunucu izolasyonu, kayıt sınırı/okunma kalıcılığı, araç gövdeleri ve uzun geçmişin başının korunması. Ekran testi ayrıca kontrollü yerel sunucu ile yapılır; bu test gerçek Hermes modelinin yanıt kalitesini veya Android Auto araç mikrofonunu doğrulamaz.
