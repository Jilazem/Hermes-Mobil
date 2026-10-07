# Hermes EMA birleşik asistan — geliştirme sürümü

Bu değişiklik `gelistirme` dalının 5bd04e9 tabanını genişletir. EMA adresi yapılandırıldığında sohbet okuma, sesli asistan ve Android Auto yanıtları EMA üzerinden seslendirilir. EMA hatasında başka bir ses motoruna sessiz geçiş yapılmaz.

## Sunucu

Python ortamına `ema-lightning` kurulmalıdır. İlk istek model dosyalarını indirir; çalışma aygıtı EMA kütüphanesinin seçtiği aygıttır. Test edilen Mac hattı CPU üzerinde çalışmıştır.

```sh
python3 -m venv .venv-ema
.venv-ema/bin/pip install ema-lightning
# EMA_TOKEN değerini özel ortam dosyasından yükleyin; kaynak koduna eklemeyin.
EMA_HOST=127.0.0.1 EMA_PORT=8176 .venv-ema/bin/python relay/ema_tts_service.py
```

`EMA_TOKEN` zorunludur. `/health`, `/speak`, `/stream` ve `/cancel` çağrıları `X-Hermes-Session-Token` başlığını kullanır. `/stream` 48 kHz, tek kanallı PCM16LE döndürür; Android tarafı AudioTrack ile çalar. `/speak` WAV döndürür. İptal kimliği tek isteğe aittir. Aynı model bir kez yüklenir; eşzamanlı üretim sayısı ve kuyruk sınırlandırılır. Metin ve ses kalıcı kayda yazılmaz.

Telefon için adresin erişilebilir olması gerekir. USB geliştirme testinde `adb reverse tcp:8176 tcp:8176` ve `http://127.0.0.1:8176` kullanılabilir. Windows'a bağlı telefonda Windows → Mac SSH tüneli de gerekir. Bu geçici test bağlantısı USB çıkarılınca çalışmaz. Kalıcı LAN kullanımında servisi Mac'in LAN IP'sine bağlayıp uygulamaya o adresi girin; uzak ağ erişimi ayrıca kurulmalıdır.

## Mobil uygulama

1. Hermes sunucu adresini ve erişim anahtarını sunucu profiline girin.
2. Ayarlar → Ses bölümünde EMA adresini ve ayrı EMA anahtarını girin. Anahtar boş bırakılırsa profil anahtarı kullanılır.
3. Mikrofon iznini verin. Normal sohbet ekranındaki sesli düğme aynı pencere üzerinden devam eder.
4. Android Auto'da Hermes uygulamasını açıp Konuş'a dokunun. Doğrudan araç mikrofonu, mevcut Whisper STT, Hermes ve EMA kullanılır. İptal kaydı, ağ isteğini ve oynatmayı durdurur.

EMA ile Hermes sesli asistanı ve Android Auto, seçili profilin `lastSession` kaydını kullanır. Saklanan kimlik yeniden etkinleştirilir veya sürdürülür; geri yükleme başarısızlığı yeni boş sohbetle gizlenmez. Ayrı Gemini çok modlu canlı modu kendi oturumunu sürdürür; henüz Hermes sohbet geçmişi ile birleşmiş değildir.

## Telefon kontrolü

Mevcut ekran okuma, tıklama, yazma, uygulama açma ve sistem araçları korunur. Otomatik araç çalıştırma, salt okunur mod, tam kontrol ve erişilebilirlik izinleri ayrı kapılardır. Shizuku gerektiren işlemler ek izin ister. Android'in güvenli sistem ekranları ve uygulamaların kendi kısıtları nedeniyle her işlem desteklenmez. Bir testin tıklama/yazma başarısı tüm telefon işlemlerinin doğrulanması anlamına gelmez.

## Android Auto kapsamı

Bu, yan yüklenen geliştirme sürümüdür. HostValidator geliştirme için tüm host'ları kabul eder. Mevcut NAVIGATION kategorisi korunmuştur. Play Store kategorisi, host imza izin listesi ve gerçek araç üretim kabulü ayrıca ele alınmalıdır. Araç sesi, odak kaybında ve ekran yaşam döngüsü sonunda iptal edilir. Kayıt süresi ve sessizlik eşiği sınırlıdır.

## Doğrulama

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug -PhermesDebugSuffix=.ema20261007
python3 -m unittest discover -s relay -p test_ema_tts_service.py
```

`EmaSelfTestActivity`: gerçek servis, WAV, akışlı iki cümle, tamamlanma, iptal ve servis kesintisi. `FullControlSelfTestActivity`: yalnız kendi geçici formunda ekran okuma, tıklama, yazma ve izin kapıları. `UnifiedSessionSelfTestActivity`: gerçek sunucuda iki tur ve asistan yeniden oluşturulduğunda aynı geçmişin korunması. Bu etkinlikler yalnız debug manifestinde bulunur; üretim uygulamasına dahil edilmez.

## Mevcut Hermes ses API'si

`relay/install_ema_voice_api.py /path/to/voice_api.py` mevcut v2 ses API'sine denetimli bir hook kurar; kaynak şablonu farklıysa yazmadan hata verir, özgün dosyayı `voice_api.pre-ema.py` olarak saklar. Yanındaki `hermes_ema_adapter.py` EMA'ya anahtarlı `/speak` çağrısı yapar ve mevcut istemcilerin beklediği OGG/Opus biçimine geçici dosyalarla dönüştürür. EMA çağrısı disk ses önbelleğine yazılmaz. Varsayılan motor EMA olur; eski motorlar ancak açıkça seçilirse kullanılır.

`EMA_URL` varsayılanı `http://127.0.0.1:8176`; `EMA_TOKEN_FILE` ile özel anahtar dosyasını belirtin. Mac kurulumunda varsayılan dosya `~/Library/Application Support/HermesEMA/ema-service-token` olur. Doğrudan `EMA_TOKEN` ortam değişkeni de desteklenir. Mevcut STT yönlendirmesi korunur.
