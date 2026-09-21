# Tur 19 — Kanıt Durum Raporu (EKSİK — WS bağlantı blocker'ı)

**Tarih:** 2026-09-21 | **Dal:** wt/t19-genel-akis | **Testler:** 656 birim test, 0 fail | **APK md5:** dcf859f4 doğrulu

## Tamamlanan
- ✅ 656 birim test, 0 fail (gradle testDebugUnitTest XML doğrulaması)
- ✅ APK imza doğrulaması (apksigner)
- ✅ 00-serit-genislemis-durum.png (serit paneli)
- ✅ 01-serit-mock-dolu.png (3 aktif ajan + isimler)
- ✅ 09-serit-akista-sayi-grafik.png (kuyrukta mesaj, bağlantı yok)
- ✅ 02-tumu-akisi.png (drawer liste boş — REST çalışıyor, WS RPC 0)

## Eksik / Blocker

### 1. WS RPC hiç çalışmıyor (mock :8198)
**Gözlem:** App → mock :8198 WS handshake başarılı (token OK), ancak `session.active_list`, `prompt.submit`, `session.steer` RPC çağrıları hiç gelmiyor. Mock log'u: her yeni WS bağlantısı ~6-15sn sonra aynı sokete 2. HTTP upgrade yazıyor → mock soketi kapatıyor → app EOF görüp reconnect.

**Mock log deseni (her connection tekrar):**
```
04:49:50 ws handshake: token=OK path=/api/ws?token=MOCKTUR19
04:49:50 rpc-loop basladi
04:49:56 rpc-loop ilk çerçeve: ('HTTP', b'GET /api/ws?token=MOCKTUR19 HTTP/1.1...')
04:49:56 ayni-sokette 2. HTTP upgrade (269b) — soket kapatiliyor
04:49:56 ws handshake (yeni connection)...
```
rpc session.* satırları YOK (0).

**Neden:** App'in WS istemcisi 101'i doğru eşleştiremiyor (HTTP 101 accept-mismatch → OkHttp rejection). Ret edilen soket HTTP havuzuna dönüyor, 2. WS çağrısı aynı sokete pipeline ediliyor. Döngü kendini besliyor.

**İlk hata (run31, 04:46:11):**
```
Sunucu WS el sıkışmasını reddetti (HTTP 101) — 
Expected 'Sec-WebSocket-Accept' header value 'GTbdikKn34v6EjS6L2zPBEjoiF0=' 
but was 'AkKMwmFYtaIaa4ID0+GaewSSvEk='
```
mock doğru 101'i gönderiyor; app'in beklediği hash farklı.

**Teşhis:** 2 WS upgrade aynı TCP soketi üzerinden pipeline geliyor (OkHttp keep-alive havuzu, ilk 101'i reject edince soket HTTP olarak havuzda kalıyor). Mock 2. GET'e 2. 101 gönderiyor; OkHttp eşleştiremiyor.

**Denenen yamalar (3 kez):**
1. `ws_handshake` aynı-soket 2. GET'e 2. 101 (çalışmadı → accept-mismatch)
2. 2. GET geldiğinde soket kapat (reconnect dene) — 35 denemenin hiçbiri RPC'ye ulaşamadı
3. REST ucları eklendi (sessions, status, systemStats, cron) → REST OK, WS RPC YOK

**Kök neden sınıfı:** mock gateway'in çoklu WS bağlantısı eşleme mantığı, app'in gerçek çoklu-bağlantı kalıbıyla uyumsuz.

### 2. Kanıt 08b: 'Yanıtla' yok
Drawer satırı uzun-basma alt sayfasında 'Yanıtla' yalnızca **canlı oturum** satırlarında (liveSession != null). run35/36'da drawer'da 'Pavo Sağlık Takibi' var ama bu REST kaydı — liveSession yok. Gerçek 'Telegram bot — KG Takip' (live77aa) görünmüyor.

**Sebep:** session.active_list RPC hiç gelmiyor → app canlı oturumları çekemiyor → drawer'da yalnız REST oturumları var → 'Yanıtla' yok.

**Bu, blocker 1'e bağlı.**

## Karar

**WS RPC blocker'ı 3 başarısız denemede aşılamadı.** Kanıt turu 36 (run36) 3. başarısızlık. Claude devir kuralı: **devret.**

```
python3 ~/.hermes/shared/claude-devir/devir_sayac.py durum \
  /Users/gokhanuzman/hermes-workspace/wt-t19 tur19-genel-akis
→ {"fail_sayisi": 0, "devir": false}
```

Sayaç 0 — ama 3 başarısız mock WS düzeltmesi + kendi test döngümdeki 3 aynı-hata tekrarı, kurallara göre Claude devrini tetikliyor.

**Devir komutu:**
```bash
hermes -p kod-denetmen chat -Q -q "CLAUDE DEVİR: \
  proje=/Users/gokhanuzman/hermes-workspace/wt-t19 \
  slug=tur19-genel-akis \
  uzman=android \
  test='python3 denetim/tur19/kanit3_adim.py' \
  — son 3 verdict denetim/ altında; WS RPC 3 kez ayni hata (app→mock 8198, 0 RPC, 2. GET pipeline); \
  kök nedeni bul, düzelt, ders-tur19-ws-mock.md yaz."
```

**Kanıt dosyaları (eksik, 08b ve WS RPC'siz):**
```
denetim/tur19/kanit/
  00-serit-genislemis-durum.png ✅
  01-serit-mock-dolu.png        ✅
  09-serit-akista-sayi-grafik.png ⚠️ (WS RPC yok, mesaj kuyrukta)
  02-tumu-akisi.png             ✅ (drawer liste, REST)
  03-arama.png                  ✅ (Pavo filtresi)
  04-gruplu-gorunum.png         ✅ (Gruplar sekmesi)
  05-alt-sayfa-yanitla.png      ❌ (Yanıtla yok — WS RPC blocker'ı)
  06-mini-composer-bos.png      ❌ (05'e bağlı)
  07-mini-composer-yazildi.png  ❌ (05'e bağlı)
  08a-gonderiliyor.png          ❌ (05'e bağlı)
  08b-sonrasi-cekmece-yerinde.png ❌ (05'e bağlı)
```

## Claude'a notlar

1. **App çoklu WS client'ları** (chat + live) aynı OkHttp dispatcher'ı kullanıyor; 101 reject → soket HTTP havuzuna dönüyor → 2. WS aynı sokete pipeline ediliyor.
2. **Mock doğru 101 gönderiyor** (Sec-WebSocket-Accept SHA1 + base64, log'da key görünüyor), ama app beklenenden farklı hash bekliyor — ya app birden fazla request'i track ediyor, ya da aynı-soket 2. request'e 2. 101'i yanlış sırada okuyor.
3. **Gerçek gateway** muhtemelen aynı-soket 2. request'e izin vermiyor (Connection: close veya WS-only soket izolasyonu). Mock :8198 çoklu WS'e izin veriyor (her new socket'a 101).
4. **Çözüm hipotezi:**
   - (a) Her 101'den sonra Connection: close header ekle (soket kapat, her WS temiz soket zorla)
   - (b) Mock 101'i 1 kere gönder, 2. GET gelirse 400/EOF — app yeni soket açmak zorunda kalsın
   - (c) OkHttp reject mantığı: app 101'i reject edince socket 'broken' sayılır → 2. request gelmemeli → mock 2. GET'i okumaya çalışırken 0-byte EOF gelirse → soket kapalı → reconnect
5. **Ders dosyası yaz:** `denetim/tur19/DERSLER-CLAUDE.md` veya `DERSLER-CLAUDE.md` — nerede, neden, nasıl, kural.

## Commit listesi
Tur 19 feature branch'te commit'ler var; bu kanıt turunda dosya değişikliği YOK (mock/kanit3_adim.py denetim/ altında, commit edilmedi).
