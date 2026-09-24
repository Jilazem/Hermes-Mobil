# Google Artemis ile tam telefon kontrolü

[Google Artemis](https://github.com/google/artemis), telefonu bir insan gibi
kullanan bir yapay zekâ ajanıdır: ekranı görür, dokunur, yazar, uygulamalar
arasında geçiş yapar (AndroidWorld testinde %99+ başarı).

**Önemli:** Artemis telefonda değil, **sunucuda** (ör. `192.168.1.101`)
çalışır ve telefona **ADB** (kablosuz hata ayıklama) ile bağlanır.
Hermes Mobil V3 ona `/telefon <görev>` komutuyla görev gönderir.

```
Telefon (Hermes V3)  ──/telefon görev──►  Artemis (sunucu :8000)
       ▲                                        │
       └──────── ADB (kablosuz hata ayıklama) ──┘
```

## 1. Sunucuya kurulum (192.168.1.101)

```bash
git clone https://github.com/google/artemis.git && cd artemis
./start.sh            # adb, scrcpy, ffmpeg ve Python bağımlılıklarını kurar
cp .env.example .env  # sonra .env içine en az bir model anahtarı yaz
```

`.env` içinde bir **görsel (multimodal)** model gerekir, çünkü Artemis ekranı
görerek çalışır. En kolayı: `GEMINI_API_KEY=...`

Kişisel telefonda şu iki satırı da ekle:

```
ARTEMIS_KEEP_DEVICE_AWAKE=false   # ekran kendi kapansın
ARTEMIS_HELPER_AUTO_INSTALL=true  # küçük erişilebilirlik yardımcısını kursun
```

## 2. Telefonu sunucuya bağla (bir kez)

1. Telefonda: **Ayarlar → Telefon hakkında → Yapım numarası**'na 7 kez dokun
   (Geliştirici seçenekleri açılır).
2. Hermes V3 → Ayarlar → Artemis kartı → **"Kablosuz hata ayıklama"** düğmesi
   → *Kablosuz hata ayıklama*'yı aç → **"Eşleme kodu ile cihaz eşle"**.
3. Sunucuda, ekrandaki IP:port ve kodla:

```bash
adb pair 192.168.1.50:37xxx     # eşleme portu + 6 haneli kod
adb connect 192.168.1.50:4xxxx  # "IP adresi ve bağlantı noktası" satırındaki port
adb devices                      # telefon "device" olarak görünmeli
```

> Kablosuz hata ayıklama telefon yeniden başlayınca kapanır; bağlantı portu da
> değişir. Açıp `adb connect` komutunu yeniden çalıştırman gerekir.

## 3. Artemis'i ev ağına aç

```bash
uv run artemis ui --host 0.0.0.0 --port 8000
```

Artemis'in kendi parola koruması **yok**. Bu yüzden 8000 portunu modemden
**internete açma**. Yalnız ev ağında (ya da Tailscale üzerinden) kullan.

## 4. Uygulamada

- Ayarlar → **Artemis** kartı → adres `http://192.168.1.101:8000` →
  **"Bağlantıyı dene"**. Telefonun seri numarasını (`192.168.1.50:4xxxx`)
  görmelisin.
- Sohbette:
  - `/telefon Ayarlar'ı aç, pil yüzdesini söyle`
  - `/telefon Google Haritalar'da eve yol tarifi başlat`
  - `/telefon YouTube'da Coldplay aç`
- **Hızlı** kip adım başına 3-5 sn sürer. **Planlı (Pro)** kip uzun, çok
  adımlı işleri planlar ve her adımı doğrular.
- Composer'daki **Durdur** düğmesi süren Artemis görevini iptal eder.

## 5. (İsteğe bağlı) Hermes ajanı da Artemis'i kullansın

Artemis bir MCP sunucusu da sunar. Hermes'e eklenirse Telegram'dan, cron'dan
ya da sohbetten çalışan ajan telefonu kendisi kullanabilir. Hermes
panelinde **MCP → sunucu ekle** ile ekle:

- komut: `/path/to/artemis/.venv/bin/python`
- argümanlar: `-m mcp_server`
- çalışma dizini: `/path/to/artemis`

Araçlar: `mobile_run_task`, `mobile_manage_task`, `mobile_get_device_state`,
`mobile_inspect_trace`, `mobile_diagnose`.

## Uygulamanın kendi "Tam kontrol"ü ile farkı

| | Tam kontrol (uygulama içi) | Artemis |
|---|---|---|
| Nerede çalışır | Telefonda (erişilebilirlik servisi) | Sunucuda (ADB) |
| Kurulum | Tek anahtar + erişilebilirlik izni | Sunucu kurulumu + kablosuz hata ayıklama |
| Güçlü yanı | Evden uzakta da çalışır | Çok adımlı işleri planlar ve doğrular, ekranı görerek çalışır |
| Kısıtı | Android 13+'da "Kısıtlanmış ayar" izni gerekir | Telefonla sunucu aynı ağda olmalı |
