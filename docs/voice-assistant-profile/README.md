# Sesli Asistan uzmanı — hazırlanmış taslak

Bu SOUL, canlı Hermes'e kurulmuş bir profil değildir. Telefona yüklenen 1.4 sürümü mevcut sohbet ve sunucuyu kullanır; araç mesajlarına kısa konuşma dili yönergesi ekler. Yeni uzman için önerilen profil adı `sesli-asistan`, görünen ad “Sesli Asistan”dır.

Kurulu Hermes kaynakları `session.create(profile=...)` ile profil seçimini ve ayrı SOUL/config/kimlik alanlarını destekliyor. Profiller bağımsızdır. Ana sohbet geçmişi yeni uzmana otomatik taşınmaz; kaynak oturumla bağlı bir özet aktarımı ve sonuç bildirimi tasarlanmalıdır. Ana sohbetin sistem yönergelerini her sesli turda değiştirerek profil taklidi yapılmamalıdır.

Model, mevcut Evren modelleri arasından aynı kısa sorularla ölçülerek seçilmeli. İlk kullanılabilir yanıt gecikmesi, tam yanıt süresi, araç seçiminin doğruluğu ve EMA'nın ilk sesine kadar geçen süre ayrı ölçülmeli. Düşük düşünme ayarı yalnız seçilen model/provider destekliyorsa uygulanmalı. Hız hedefi henüz ölçülmüş bir garanti değildir.

Kurulumda mevcut telefon kontrolü ve görev aktarımı için gereken araçlar seçilmeli. Başka botların kanal anahtarları ve geçmişleri klonlanmamalı. Ana profil veya onun aktif sohbetleri değiştirilmemeli. Mobil uygulama sesli görüşme için uzmanı seçmeli; yazılı sohbetin mevcut profil tercihi korunmalı. Uzun görevlerin sonucu Bildirimler ve Takip üzerinden kaynak oturuma bağlanmalı.
