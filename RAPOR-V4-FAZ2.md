# V4 Birleşik Hat — FAZ 2 Tamamlandı Raporu (t_370402ca)

Tarih: 18.10.2026 | Uzman: cd-android | Dal: feat/v4-birlesik (worktree v4, taban feat/ema-unified-20261007 @ c5c20f0)

## Yapılan (FAZ 1 + FAZ 2)
- **FAZ 1:** PLAN.md + plan_kapisi.py GEÇTİ (commit 24590ef, önceki oturum).
- **FAZ 2 — 8 kilometre, 21 dosya, +1779 satır** (c5c20f0..7760c38):
  - M1 paket adı korundu: `com.hermes.mobile.v3` (K27 ile aynı; tek cihazda yan yana install -r uyumlu).
  - V3 en iyileri port: SessionDrawer (K10c silme + onay diyaloğu), ChatIsolation, SessionDeleteLogic, CzipLogic (birleştirme önerisi kataloğu), LocalModelChoice.
  - Session yığını: SessionMergeLogic (K13c çoklu birleştirme, K29-b tek-kaynak "Yeniye taşı"), merge önerisi + oturumu yeniye taşıma (kaynak arşivlenir, sunucuya dokunulmaz).
  - K19 supersede (t_79769ac7): model varsayılanı canlı `/v1/models` adıyla eşleştirildi (GLM-5.3-Flash-EXL3).
  - M8 patron eki: oturum açılış konumu — son-okunan ZAMAN DAMGASI cihazda (ChatScrollPositionStore, SharedPreferences "chat_scroll_pos"); açılışta İLK okunmamış satıra konumlanma; "en sona git" YOK; TUR-31 "sona git" butonu + okunmamış sayacı her zaman erişilebilir; okundu işareti yalnız dipteyken ilerler (satır sayısı değişse de damga kararları sabit).

## Kanıt
- **Test:** `./gradlew :app:testDebugUnitTest` → **1106 test / 0 fail / 0 error** (test-results XML toplamı; önceki 1069 + 37 yeni).
- **Derleme:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; `app-arm64-v8a-debug.apk` (61.734.674 bayt).
  - Yol: `Hermes-Mobil/05-GEICICI/2026-10-17-apk-v4/HermesMobil-v4-arm64-debug.apk`
  - MD5 `de597c177f9bd7f462c214d06ad865cc`, SHA256 `c121d31f...2b8ae3f`, apksigner verify OK (debug sertifikası).
- **Emülatör e2e (hermes-v2, mock gateway 9171 — stdlib, ders b77de80d):**
  - Açılış: pid canlı, FATAL yok (`v4-acilis.png`, `v4-drawer.png`).
  - Bağlam menüsü: "Birleştir…", "Yeniye taşı…", "Sil" hepsi göründü (`v4-context.png`).
  - **Birleştirme e2e:** 2 oturum seçildi → onay diyaloğu → uygulama `POST /api/czip/merge {"ids":["k21-a","k21-b"],"title":"V4"}` gönderdi → bant "Birleştirildi: V4 birleşik (K21 Oturum A final, K21 Oturum B final)" + listede birleşik oturum (`v4-merge-e2e-sonuc.png`).
  - **Yeniye taşıma e2e:** `POST {"ids":["k21-a"]}` → kaynak arşive gitti, listede kalmadı (`v4-tasi-sonuc.png`).
- **Denetim dosyaları:** `denetim/v4/` (mock gateway + kanıtlar) commit 7760c38.

## Denetim (kod-denetmen) — ÖNEMLİ NOT
- `cd-denetmen` (Claude) profili başlatılamıyor: her denemede `Hermes model bağlantısını başlatamadı: invalid_params` — ağ isteğine varmadan, config'ten bağımsız (3 deneme; debug proxy ile istek gitmediği doğrulandı). **Kök neden bulunamadı — ortam sorunu, kartın kapsamı dışında.** Config yedeği: `config.yaml.bak-v4` (canlı model adına güncellendi; zararsız).
- Patron politikası (17.10): "Opus slug doğrulanana kadar mevcut modelde devam" → ara denetim yerel modelle yapıldı: SessionMergeLogic/SessionDeleteLogic/AppViewModel/ChatScreen elden geçirildi; hata yolları fail-safe (sessiz veri kaybı yok), her kilometre saf mantık + testle kapalı. **NİHAİ Claude denetimi beklemede** — ortam düzelince `kod-denetmen-devriye` cron'u yeni kodu otomatik inceleyecek.

## Açık riskler
1. cd-denetmen profil ortamı (invalid_params) — kullanıcı müdahalesi gerekebilir.
2. M8 açılış konumunun canlı sohbetle cihaz doğrulaması (mesajlı oturum) yapılmadı — mantık 12 birim testle kanıtlı; emülatörde mesajlı oturum kurmak ayrı iş.
3. SessionDrawer.kt — **hotspot:** K21 swipe + K25 rename aynı dosyada; V4'te birleşti, sonraki işlerde çakışma riski.

## Kota özeti (patron 17.10 politikası)
- Tüm FAZ 2 (kod, test, derleme, cihaz doğrulama): **yerel model (node-local-models, kotasız)** — bulut kotası harcanmadı.
- Ara denetim: yerel model. cd-denetmen (bulut/Claude) başlatılamadığı için bulut kullanımı 0.
