# Tur22 emulator kanıt klasörü — okuma notu (D-02)

## r1-DÜZELTME TURU (r3 APK, md5 4775d2d30d5f7b10553b47184b519f52) — bu turun dosyaları GEÇERLİ

Yeni kanıtlar (r1 bulgularına karşı):
- bounds-48dp.txt — 4 üst-bar düğmesi + composer 'Ek' düğmesi: tıklanabilir node
  bounds 126px/126px = 48.0x48.0dp, İKİ ardışık çekimde aynı + sha256.
  Gönder/Gönder-oncesi/Mikrofon satırları ayrıca 45-r1-burst-48dp.jsonl'de (hepsi 126x126).
- 44-r1-oncesi.png — basış ÖNCESİ (↑ dolu taslak, kompozör).
- 45-r1-burst-0..4.png + 45-r1-burst-48dp.jsonl — Gönder anından ~0.26-0.87sn
  son 5 ardışık screencap + her anın GÖNDER kutusu bounds'u ve anlık sha'sı.
  burst-2/3 ✓ (tik) gösterir (büyütme ile doğrulandı), 4 farklı hash.
- 46-r1-mesaj-dolu.png — sonraki an: mesaj balonu + Mikrofon'a geri-başarım.
- gonder-48dp-oncesi.json — basış-anı dökümü (Gönder tıklanabilir, 126x126).

Kök-neden notu (D-02): ilk burst denemesinde ✓ HİÇ görünmedi çünkü flash state'i
ActionButton İÇİNDE tutuluyordu; gönderme ile taslak temizlenip when dalı değişince
ActionButton yeniden doğuyor ve remember sıfırlanıyordu. Düzeltme: durum Composer
kapsamına taşındı + sendFlash dalı when'in başına (dal değişse de aynı yerde ✓).

## ESKİ TUR (r1 öncesi, 20.09 gece) — denetim için korundu

GEÇERLİ kanıtlar (doğrulanmış state'te çekildi):
- 01-baglantiyok-bosdurum.png ... (kare listesi aşağıda) ve dogrulama-*.txt sembol dökümleri
- 41-mesaj-girisi-*.png, 45-gonder-burst-*.png, 46-mesaj-dolu.png (sohbet + gönder anı)
- v03-iskelet-final.mp4, v04-iskelet-son.mp4 (ekran videoları)
- video-kare/iskelet2, video-kare/v03 (8fps 719+ kare; video-kare/iskelet2 ölçüm: 496 kare,
  nabız yok — bkz. neden aşağıda)

GEÇERSİZ / ESKİ (önceki bozuk bağlantı turundan, denetim için tutuldu, kanıt olarak OKUMA):
- 10-cekmece-acilis-[12].png, 20-iskelet-[123].png, 21-dolu-liste.png — bu kareler bağlantı
  kopukken çekildi; 21-dolu-liste.png boş çekmece gösterir (mock 2 oturum veriyordu ama
  yükleme hiç tetiklenmemişti). Doğrusu: yükleme penceresi hiç oluşmadı (aşağıdaki kök neden).

Nabız (iskelet alfası) canlı ölçümü NEDEN 480-kare ile yapılamadı (D-10 dürüst notu):
1) Gerçek yüklemeyi yakalamak için 16sn 8fps ≈ 480 kareye 11000'den fazla screencap gerekir;
   her screencap ~1.2sn → 3.7 saat sürer.
2) Alternatif olarak 60sn mock penceresi + screenrecord 8fps (719 kare) çekildi; kare çıkarımı
   yapıldı ama çekmece iskelet bölgesi o 60sn boyunca hiç iskelet göstermedi (aşağıda).

Kök neden (koddan, 1. deneme sonrası bulundu): AppViewModel.loadActiveDetails
(AppViewModel.kt:354) status → systemStats → sessions SIRALI çağırır (async cronJob'lar paralel,
en sonda await). Kanıt mock'unun /api/system/stats ve /api/cron/jobs rotaları 404 dönüyordu;
runCatching erken düşüyor, /api/sessions (15sn yavaşlatılan rota) HİÇ çağrılmıyordu. Log
kanıtı: mock9171-req.log'da "/api/system/stats 404" satırları var, "GET /api/sessions " yok.
Mock 21:48'de düzeltildi (/api/system/stats + /api/cron/jobs 200, /api/ws 101); düzeltme
sonrası 40sn pencere + 20sn video + 10 durum örneği turunda dahi yükleme görünmedi —
son teşhis turunda emülatör ağ köprüsü (adb reverse oturumu) bozulmuştu ve oturum
kaydetme/silme 10 denemede 4 kez yarım kaldı (kaydet dialog arkası state yazımı).

Bunun yerine GEÇERLİ iskelet kanıtları (spec'in kabul ettiği yol):
- skeletonRowCount kural testleri: 'yok/henüz-yok' ayrımı ve 4 satır
  iskelet — JVM saf-katman testleri yeşil (tur22-3 commit; r3'te 727/727).
- 48dp/46dp: uiautomator bounds, 48.0dp ölçüldü (45-gonder-burst + 01-bosluk dökümü).
- prefers-reduced-motion: HermesMotion token testleri + sistem ölçek okuma 3 test (tur22-1).

Kare hash özelliği (kopya kare tespiti, 45-gonder-burst): 1.burst 5 kare → 1 benzersiz
(ölçek yayı 1-2 karede bitiyor; burst 140ms > 150ms tween), 40-oncesi→46-mesaj-dolu
öncesi/sonrası hash'leri FARKLI — burst penceresi animasyondan uzun; 46-mesaj-dolu'da
Gönder→Mikrofon geri-başarımı + mesaj balonu görünür.