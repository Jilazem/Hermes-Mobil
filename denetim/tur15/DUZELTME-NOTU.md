# TUR-15 DÜZELTME NOTU — çökme-kurtarma bandı iddiası

Tarih: 2026-09-18 · Uzman: android · Dal: feat/tur15-outrun
İlgili denetim kaydı: `denetim/verdict-tur15.json` (verdict=pass, MEDIUM `genel/kanit`)
Not: `verdict-tur15.json`'a DOKUNULMADI (denetmenin kaydı).

## Bulgu (kod-denetmen, MEDIUM `genel/kanit`)

RAPOR.md "Dürüst kalanlar" bölümü, `ForegroundServiceDidNotStartInTime` bandını
"önceki oturumdan" deyip 01-acilis.png'de kapatıldığını ve "crash buffer boş"
yazıyordu. Ancak `kanit/09-yeniden-acilis-kip-kalici.png` (17:17, force-stop
SONRASI) aynı "Önceki açılış bir çökmeyle kapandı" bandını YENİDEN gösteriyor ve
mod çiplerini örtüyor. Bu görsel "crash buffer boş" iddiasıyla çelişiyor gibi
görünüyordu; iddia düzeltilmeliydi.

## Teşhis (emülatörde canlı, 2026-09-18, emulator-5554 / hermes-v2)

Kurulu APK md5 = `ef970d93c617c0d531c7b109b7fabd76` (RAPOR ile birebir).

1. `adb logcat -b crash -d` = **0 satır** (force-stop öncesi ve sonrası). Android
   sistem crash tamponu gerçekten boş; native/tombstone çökme YOK.
2. Band metni sistem crash tamponundan gelmiyor. Kaynağı, uygulamanın kendi
   kalıcı günlüğü `files/diag.log`'un son `C [crash]` satırı:
   - `CrashGuard.recoverFromDiagLog(filesDir)` — `HermesApp.onCreate` içinde her
     soğuk açılışta çağrılır (`HermesApp.kt:22`).
   - Fonksiyon diag.log'daki son `" C [crash]"` satırını okur ve
     `CrashGuard.lastCrash`'e yazar; `CrashRecoveryBanner` bunu gösterir
     (`MainActivity.kt:1292`).
3. `force-stop`, uygulamanın `files/` dizinini SİLMEZ → diag.log kalıcıdır →
   her yeniden açılışta aynı satır tekrar okunur → band tekrar görünür. 09'daki
   yeniden görünme bu davranıştır; **çelişki değil, beklenen sonuç.**
4. Bandı dokunarak kapatma (01) yalnızca bellekteki `CrashGuard.lastCrash = null`
   yapar (`MainActivity.kt:1301`). Force-stop süreci öldürünce sonraki açılışta
   disk'ten yeniden okunur. Yani "01'de kapatıldı, bir daha görünmez" ifadesi
   hatalıydı; kapatma kalıcı değildir.
5. `dumpsys activity exit-info com.hermes.mobile.v2`: tüm uygulama çıkışları
   `reason=10 (USER REQUESTED) / subreason=21 (FORCE STOP)` — çökme kaydı yok.
   Bandı doğuran çökme önceki bir oturumdandır ve tur-15 diff'inde servis kodu
   bulunmadığından bu turla ilgisizdir.
6. Kesin ayrım kanıtı: `files/diag.log`'a bu düzeltme oturumunda kontrollü
   eklenen tek `C [crash]` satırı ("tur15 duzeltme teshis satiri"), `logcat -b
   crash` = 0 iken, yeniden açılışta band olarak göründü → bandın kaynağı kesin
   olarak diag.log'dur, sistem crash tamponu değil.

## Ne değişti

- `denetim/tur15/RAPOR.md`:
  - "Dürüst kalanlar" bandı maddesi düzeltildi (kaynak = diag.log; force-stop'ta
    band tekrar görünür; dokunarak kapatma bellekte geçerli; "crash buffer boş"
    ifadesi doğru bağlamına oturtuldu).
  - Kanıt tablosuna yeni "Çökme-kurtarma bandı ve crash tamponu" notu ve 09b + 11
    satırları eklendi; 09 satırı bandın çipleri örttüğü belirtilerek güncellendi.
- Kanıt eklendi:
  - `kanit/09b-yeniden-acilis-band-kapali-kip-okunur.png` — band kapatıldıktan
    sonra Outrun kipi kalıcılığı çipler örtülmeden okunur (band, kip seçim
    kanıtını bozmuyor; yalnız üstünü geçici örtüyor).
  - `kanit/11a-sentetik-kayit-bandi.png` — sentetik `C [crash]` satırının soğuk
    açılışta (12:50) band olarak ilk görünüşü.
  - `kanit/11-banner-diaglog-kaynakli-logcat-crash-bos.png` — diag.log kaynaklı
    band, `logcat -b crash` boşken.
  - `kanit/11-crash-kaynak-teshisi.txt` — ham komut çıktıları (logcat, diag.log
    grep, exit-info) ve sonuç.

## Kanıt geçerliliği (09'un durumu)

Bandın 09'da çipleri örtmesi, Outrun kipi kalıcılığı iddiasını çürütmez: band
yalnızca görsel bir katmandır, altındaki durumu değiştirmez. 09b'de band
kapatıldığında "Outrun yarış" çipinin seçili olduğu ve WebView'in OUTRUN
sahnesini yüklediği açıkça okunur; kalıcılık örtüşmeden doğrulanmıştır.

## Kısıt uyumu

Kod/test değişmedi (yalnız denetim/ altı + yeni kanıt). 617+18 test dokunulmadı.
APK yeniden derlenmedi (gerekmiyor). `verdict-tur15.json`'a dokunulmadı.
Başka oturum devralınmadı.

TUR15_DUZELTME_BITTI
