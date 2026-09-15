# GÖREV (tur-4): Hermes Mobil — "İstenen hale getirme" turu

**Kime:** android (uzman) — zorunlu kapı: kod-denetmen (VERDICT=GEÇTİ olmadan kabul yok)
**Repo:** /Users/gokhanuzman/hermes-workspace/wt-android-uzman (dal: feat/android-uzman-devralma, HEAD: 3672b6c)
**Emülatör:** emulator-5554, paket com.hermes.mobile.v2 (activity: com.hermes.mobile.MainActivity)
**Sunucu:** http://192.168.1.101:9150 (token: /tmp/gw_token.txt dosyasında — varsa; kontrol et)

## Bağlam (kullanıcı birebir)
"Olmamış tüm ekibi topla büyüklerinize danışın ve bu işi istendiği hale getirin."
Kullanıcının şikâyetleri: (1) sohbet yukarıda sıkışmış, (2) oturum adlarından KONU anlaşılmıyor, (3) sık kapanıyor/göçüyor, (4) tasarım Telegram kalitesinde olmalı.

## Emülatörde bugün tespit edilen GERÇEK kusurlar (düzeltilecek)
- A) Oturum kartı önizlemesi HAM JSON/tool çıktısı gösteriyor: `{"status": "success", "output": "=== ESBLESME: ... len 11050..."` → son ÖNİZLEME insan-okur metin olmalı; JSON/tool çıktısı özetlenmeli veya atlanmalı (bir önceki anlamlı mesaja düşülmeli).
- B) Cron oturumu önizlemesi ham sistem mesajı: `[IMPORTANT: You are running as a scheduled cron job. DELIVER...` → sistem/cron mesajları önizlemede gösterilmemeli; anlamlı ilk satır seçilmeli.
- C) Telegram kaynaklı oturumların başlığı kullanıcı adı ("Gökhan Uzman") kalıyor → konu-bazlı başlık türetimi (oturum başlığı anlamsızsa ilk kullanıcı mesajından konu).
- D) Oturum detayı başlık altı "0 mesaj" yazarken kartta "39 mesaj" → sayaç tutarlılığı (detayda gerçek mesaj sayısı; yüklenirken belirsizse "—" veya gizle).
- E) Buton seti tutarsız: "Konuşmaya devam et | Müdahale | Döküm | Dur" vs "Devam | Müdahale | Dur" → tek kural: boşta için [Devam et][Müdahale]? çalışırken [Durdur]; "Döküm" konumu sabit.
- F) Kart altı uzun ipucu ("Müdahale/durdurma yalnız ajan çalışırken anlamlı...") sürekli görünüyor → yalnız gerektiğinde (disabled buton ipucu/tooltip) veya tek satır kısalt.
- G) Üst şerit: "default/ac/android" profil çipleri iç terminoloji → görünen ada çevir (ör. profil display adı) veya net etiket.
- H) Mesaj listesi: asistanın nihai metinleri öne çıkmalı; "Düşünme" + araç satırları ikincil (katlanır/dim). Son mesaj akışı okunur olmalı.

## Kabul kriterleri (kanıt zorunlu — sahte kanıt yok)
1. Her kusur için ÖNCE/SONRA ekran görüntüsü (emülatör, GERÇEK sunucu + gerçek oturum).
2. Gerçek akış: uygulama aç → Oturumlar (Canlı/Tümü) → bir oturum detayı → sohbet kaydırma → geri → Ayarlar. Crash buffer 0.
3. Render testi: markdown yanıt (kalın/başlık/liste/kod paneli) gerçek yanıtla.
4. Önizleme kuralı birim/UI testi + JSON-skip kuralı testi.
5. TR/EN turunda etiket sızıntısı 0.
6. Tüm mevcut testler yeşil (en az 211 test) + yeni testler; rapora taze sayaç yaz ("şu an X test").

## Kurallar
- Yalnız Gerçek akış; Debug-Activity ekranı KANIT DEĞİL.
- Yorumlar Türkçe. Commit mesajı Türkçe. Dokunulacak yer: UI katmanı + önizleme türetimi.
- Bitince: commit + `000-TEMP/android-tur4-rapor.md` (bulgu→düzeltme→kanıt tablosu) + APK derle (000-TEMP/hermes-tur4-<sha>.apk) + md5.
- main'e merge YOK; push YOK. Dal üstünde çalış. Denetmen ayrı turda çağrılacak.

## Bilinen tuzaklar (önceki turlardan)
- "http://" ön-dolgusu üstüne yazınca `httphttp://` çökmesi — regresyon testini koru.
- Emülatör locale tr-TR; EN sızıntılarını ekran turuyla tara.
- WebSocket şema temizliği (ws:// wss://) korunacak.

## BÜYÜK MODEL DANIŞMASI SONUÇLARI (BAĞLAYICI YÖN — 14.09)
Büyüklerin (Grok 4.6 + Gemini) tam metni: 000-TEMP/buyuk-konsult-sonuc.txt. Öz:

**P0 — aynı sprint, ikisi birden:**
1. Çökme/stabilite: her akışta crash 0 (kanıt logcat).
2. Başlık = KONU: sıra → anlamlı oturum başlığı → ilk anlamlı kullanıcı cümlesi (~40 karakter) → cron iş adı + saat → "Sohbet · 14.09 18:22". "Gökhan Uzman" / "desktop telegram" / ham cron_ KALDIR.
**P1:**
3. Önizleme = son kullanıcı VEYA son NİHAİ asistan metni; JSON/tool/[IMPORTANT]/sistem promptu/markdown çiti atılır; çalışırken "çalışıyor…". Tek satır, soluk.
4. Sohbet = konuşma: varsayılan görünür = kullanıcı + asistanın bitmiş yanıtı. Düşünme/araç/sistem tek "Ayrıntı" satırına katlanır (açılır-kapanır). Canlıyken "yazıyor…".
5. Kart anatomisi: sol durum noktası · başlık · sağ üstte saat; altında 1 satır önizleme. Liste kartında buton YIĞINI olmaz; satırın tamamı sohbeti açar; çalışan oturumda tek birincil eylem "Dur" (taşma menüsü/alt bar). Kart altı 2 satırlık kılavuz SİLİNİR.
**P2:**
6. Sayaç tutarlılığı: "39 vs 0 mesaj" — sayaç yüklenen KONUŞMA mesajını sayar; yüklenemezse "0" yazma.
7. Üst şeritten model etiketi + "bağlı" + default/ac/android çipleri çıkar → Ayarlar/⋯ içine; bağlantı yalnız KOPUNCA kırmızı "bağlantı yok".
**P3:**
8. Üst sıkışma: chrome incelir (tek satır konu başlığı; model orada durmaz).
**Durum etiketleri:** boşta → ETİKETSİZ (gri nokta/sadece saat); working → "yazıyor…"; onay → "onay bekliyor"; bitti/geçmiş → etiketsiz; "Canlı" kelimesi kullanılmaz. Sekme alt metni "N çalışıyor · M sohbet" gibi.
**Yerelleştirme:** iç ad sızmaz: default → gizle/"Varsayılan"; ac/android → insan adı yoksa gizle; telegram/desktop/cron → en fazla küçük kaynak ikonu.
**Ek:** boş durum ekranı (1 cümle + 1 eylem) + yüklenirken iskelet. Diğer sekmeler (Pano/Arena/Ayarlar) da aynı standartla gezilir; bariz kusur düzeltilir veya rapora yazılır.
**Tek cümle hedef (Grok):** "Her kart bir bakışta KONUYU söyler; açık ekran ajan günlüğü değil KONUŞMA olur."

Kapsam geniş — öncelik sırasıyla ilerle; yetişmeyeni rapora "KALAN" olarak yaz. UI detay kararları mühendisliğinde; kabul kriterleri bağlayıcı.
