# PLAN — Mobil v6.4 WS kendini iptal döngüsü onarımı

Veren: CEO · Uzman: cd-android · Tür: ORTA (tek dosya + sürüm + test + APK)
Görev: 2026-10-23 Mobil v6.4 — WS kendini iptal döngüsü onarımı [GÖREV HATTI #1023-466290]
Teşhis kanıtı: 05-CIKTILAR/teshis/20261023-mobil-ws-reconnect-teshis.md
(plan_kapisi.py 03-SCRIPTS altında bulunamadı — plan adımları elle yürütülüyor)

## Amaç
Mobil uygulamanın WS soketini ~1 sn'lik kendini besleyen döngüyle kapatıp yeniden
açmasını durdurmak. Sunucu kanıtı: 22.433 aç/kapa çifti/gün, medyan ömür 0,97 sn, kodu 1006.

## Ölçülebilir Kabul ölçütleri
1. `./gradlew :app:testDebugUnitTest` PASS + yeni test: ardışık 3 iptal edilmiş soketin
   gecikmeli `onFailure`'ı yalnız 1 reconnect planlar (fake listener).
2. `versionCode = 12`, `versionName = "6.4.0"`; APK:
   `/Volumes/EX/007-HERMES-M4-LIVE/05-CIKTILAR/hermes-mobil-v5/hermes-mobil-6.4.0.apk` + sha256.
3. Emülatörde 60 sn: logcat'te tek WS nesili (gen), aç/kapa ≤2.
   Sunucu gui.log: 5 dk pencerede `ws accepted` ≤10 (öncesi ~150/saat-ölçekli döngü).
   (Sunucu metriği yalnızca döngünün kaynağı sabit APK'yla bağlanan istemciyse anlamlı;
   başka kaynak varsa kanıt raporunda açıkça kaynak atfı yazılır.)
4. Kanıt raporu: `05-CIKTILAR/hermes-mobil-v5/kanit-v6.4/B1-WS-DONGU-ONARIM.md`
   (diff özeti, test çıktısı, logcat, sha256).

## Kaynaklar
- Repo: /Volumes/EX/007-HERMES-M4-LIVE/12-PROJELER/Hermes-Mobil-v5-integration
- Hedef dosya: app/src/main/java/com/hermes/mobile/data/GatewayWsClient.kt
- Test: app/src/test/java/com/hermes/mobile/GatewayWsClientTest.kt (+ yeni reconnect testi)
- Sunucu log: /Volumes/EX/007-HERMES-M4-LIVE/20-HERMES/logs/gui.log
- Emülatör: AVD hermes-v2 / hermes-v63 (önceki kanıtta emülatör-5554 arm64 kullanıldı)

## Adımlar
1. GatewayWsClient.kt'e nesil (gen) sayacı + tek reconnectJob + 30 sn stabilite
   sıfırlaması + canlı soketi koruma + tanılama satırları (kapsam maddeleri A–H).
   Doğrulama: kod okuması + derleme.
2. Yeni JVM testi: 3 iptal edilmiş soketin gecikmeli onFailure'ı → 1 reconnect;
   canlı sokette ikinci connect() cancel etmez; backoff onOpen'da sıfırlanmaz.
   Doğrulama: `./gradlew :app:testDebugUnitTest` PASS.
3. Sürüm 6.4.0 (code 12), THIRD-PARTY-NOTICES künyesi, assembleDebug.
   Doğrulama: APK yolu + sha256 + APK içi sürüm kaydı.
4. Emülatörde 60 sn çalışma + logcat kanıtı (tek gen, aç/kapa ≤2).
   Doğrulama: logcat sayımları; gui.log 5 dk penceresi.
5. Kanıt raporu yazımı. Doğrulama: rapor dosyası yolu + içinde sha256/test/logcat var.

## Riskler
- Nesil koruması yanlışrsa ilk soket hiç açılmaz → awaitOpen timeout; test şart.
- 30 sn stabil eşiği gerçek kopmada yeniden bağlanmayı 2-4 sn geciktirebilir; kabul edilebilir.
- Sunucudaki 1/sn aç/kapanın başka yerel istemciden gelen payı olabilir; atfı kanıtta ayrılır.
- Patron kararı gerekmez; geri alma: v6.3 APK kanıt klasöründe duruyor.