# 1020-f94a99 SES AA onarımı
Katman: orta
## Amaç
VOICE-AA.md bulgu 1–5 için terminal hata, sahipli FGS, bildirim iptali, focus ve wake-word yaşam döngüsünü düzelt; kaynak, regresyon, Gradle logu ve commit teslim et.
## Kabul ölçütleri
- 5 bulgu uygulama kodunda giderilir, model ve ses seçimi korunur.
- compileDebugKotlin ve testDebugUnitTest --rerun-tasks exit 0; XML test sayımı raporlanır.
- VOICE-AA-FIX.md commit hash ve fiziksel kabul sınırını içerir.
## Kaynaklar
VOICE-AA.md; JarvisEngine, JarvisVoice, WakeWord, CarVoiceSession, LiveVoiceViewModel, DrivingModeService; mevcut JUnit altyapısı. Kod kütüphanesi arandı, uygun sahiplik örneği bulunmadı. Ders araması yapıldı; doğrudan ses sahipliği dersi yok. Önceki kusur: sahipsiz stop ve hata sonrası callback; neden: yaşam döngüsü kimliği yok. Önlem: sahip kimliği ve terminal token geçersizleştirme; somut kabul kanıtı: yürütülmüş regresyon XML ve Gradle exit.
## Adımlar
1. EMA terminal hata ve callback kimliği düzelt. Doğrulama: hata/iptal regresyonları.
2. FGS sahipliği ve bildirim iptali uygula. Doğrulama: örtüşen sahip ve stop testleri.
3. Focus gate ve wake-word sahipliği uygula. Doğrulama: reddetme/kayıp ve üst üste sahip testleri.
4. Gradle compile+test çalıştır, XML say, kapsam commit oluştur. Doğrulama: gerçek log, XML ve git diff/status.
## Riskler / Patron kararı
Telefon/emülatör/relay/üretim restart ve kanban yasak. Fiziksel ses, AA rota ve kilit ekranı kabulü yapılmaz; bağımsız kabul ayrıca gerekir. Önceden değişik PLAN.md ve untracked dosyalara dokunulmaz.

## Gerçek sonuç
- Bulgu 1–5 uygulandı; Robolectric API 34 ile gerçek JarvisEngine/DrivingModeService ve ortak sahiplik üzerinde 10 yaşam döngüsü regresyonu geçti.
- JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ile `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`: exit 0.
- XML programatik sayım: 113 suite, 1209 test, 0 failure/error/skipped. Çıktı: ../../05-CIKTILAR/hermes-mobil-v5/VOICE-AA-test-counts.json.
- Log: ../../05-CIKTILAR/hermes-mobil-v5/VOICE-AA-gradle-final.log. Rapor: ../../05-CIKTILAR/hermes-mobil-v5/VOICE-AA-FIX.md.
- Gözlem/teslim: yerel kaynak ve JVM doğrulaması tamam. Bağımsız denetim ve fiziksel ses kabulü yapılmadı.

