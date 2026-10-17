# PLAN — K18B-G1: Kalıcı bot kimlikleri/ekipler (bot kartı roster'ı)

Katman: büyük

## Amaç

Hermes-Mobil'e kalıcı bot kimlikleri katmanı (roster) eklenecek: kullanıcı bot kartı
(ad, kişilik, model/sağlayıcı, pin/duplicate) oluşturup kaydedecek; "bu botla devam et"
aynı Hermes profili + model/sağlayıcı seçimini geri yükleyecek. Yeni katman, Arena'daki
geçici (koşu-bazlı) bot seçimine dokunmadan yanına eklenecek. Teslim: PLAN.md (bu dosya),
kod + birim testleri, emülatörde ekran görüntülü akış kanıtı, CREDITS girişi, RAPOR.md.

Çalışma kopyası (CEO notu): ANA HAT
`/Users/gokhanuzman/Documents/Codex/2026-10-07/files-mentioned-by-the-user-g/work/Hermes-Mobil`
(dal feat/ema-unified-20261007). Kod dalı `feat/k18b-g1-bot-kimlikleri`, taban = G3
denetim+merge kartı (t_0f6814f1) tamamlandıktan SONRA ana hattın HEAD'i.

## Kabul ölçütleri

- PLAN.md plan_kapisi.py'den exit 0 (GEÇTİ) alır; bu olmadan kod satırı yazılmaz (test: plan kapısı komut çıktısı).
- Bot kartı alanları: id, ad, emoji, kişilik, profil adı, sağlayıcı, model, pin, oluşturulma/güncellenme zamanı — JSON dosyada kalıcı kaydedilir; yeniden başlatmada liste geri gelir (birim testi: kaydet→yükle turu 0 kayıp).
- Pin/duplicate: sabitlenen kart listede önce gelir (test: sıralama fonksiyonu); çoğaltma "ad (kopya)" benzersiz adla yeni kayıt açar (test: duplicate adlandırma).
- "Bu botla devam et" aynı profil+model'i geri seçer: seçilen profil `createSession(profile)` yoluna, model "sağlayıcı|model" olarak son modele yazılır (birim testi: apply mantığı; emülatör kanıtı: devam et sonrası sohbet başlığı/model afişi bot kartıyla aynı).
- En az 5 yeni birim testi yazılır ve testDebugUnitTest tamamen yeşil: fail=0, err=0 (komut: ./gradlew testDebugUnitTest, sayısal çıktı raporda).
- assembleDebug BUILD SUCCESSFUL; APK md5 rapora yazılır (komut çıktısı + dosya yolu).
- Emülatörde (emulator-5554, com.hermes.mobile.v3) uçtan uca akış: bot oluştur → listede gör → sabitle/çoğalt → "devam et" → sohbet aynı profil+model; en az 4 ekran görüntüsü + mock gateway log kanıt olarak raporlanır.
- CREDITS.md'ye MausBot (Apache-2.0) + Holt (AGPL-3.0) + AI-Buddy (MIT) yalnız-fikir girişleri yazılır (grep ile doğrulanır).
- Gizlilik: bot kartı serileştirmesinde token/anahtar alanı YOKTUR (birim testi: JSON'da "token"/"geminiApiKey" geçmez); kişilik metni yalnız kullanıcı girdisi olarak cihazda kalır.
- Arena kırılmaz: ArenaEngine/ArenaViewModel/ArenaScreen'e dokunulmaz; mevcut Arena testleri (BotArenaTest, ArenaCageTest, ArenaSceneTest, ArenaSceneModeTest) yeşil kalır (test çıktısı).

## Kaynaklar

- K18B raporu: /Volumes/EX/007-HERMES-M4-LIVE/20-HERMES/kanban/attachments/t_7669090c/K18B-bots-rakip-inceleme.md (G1 satırı + K18B-B/C taslakları + CREDITS taslağı).
- Ana hat depo: /Users/gokhanuzman/Documents/Codex/2026-10-07/files-mentioned-by-the-user-g/work/Hermes-Mobil — Models.kt (HermesProfile/ModelProvider/ModelSetRequest), ServerProfile.kt, ChatViewModel.kt (selectedProfileValue, selectModel, preferredModel), MainActivity.kt (settings.selectedProfile/lastModel kalıcılığı), SavedPromptsStore.kt + SessionFlagsStore.kt (JSON dosya deposu deseni), ArenaScreen.kt/ArenaViewModel.kt (geçici bot seçimi — dokunulmayacak), ModelPickerSheet.kt.
- 2. kopya (yalnız okuma, G3'ün çalışma yeri — dokunulmayacak): /Volumes/EX/80-YEREL/hermes-workspace/wt-k18b-g3/data/LocalBot.kt (K13d bot şablon deseni), tools/k18b-g3/mock_gateway.py (emülatör kanıt altyapısı fikri).
- Araçlar: plan_kapisi.py (/Volumes/EX/007-HERMES-M4-LIVE/00-SISTEM/super-uzman/), gradlew (testDebugUnitTest, assembleDebug), adb/emülatör (mac-android-build-emulator bilgisi), python3 mock gateway.
- Ders defteri: DERSLER-CLAUDE.md (D-05 ham id sızıntısı, D-07 kanıtsız iddia, D-08 teşhis kancaları, D-13 timeout disiplini).

## Adımlar

1. Ön şart kontrolü: G3 denetim+merge kartı t_0f6814f1 tamamlanmış mı (kanban_show) — tamamlanmadıysa KOD YOK, kart yorumla not düşülür ve beklenir. Ayrıca ana hattın HEAD kaydı alınır (git log --oneline -1).
   Doğrulama: kanban_show t_0f6814f1 status=done çıktısı + git log satırı rapora yazılır.

2. Dal açma: ana kopyada `git worktree add /Volumes/EX/80-YEREL/hermes-workspace/wt-k18b-g1 -b feat/k18b-g1-bot-kimlikleri <birleşik HEAD>`; PLAN.md çalışma ağacına kopyalanır.
   Doğrulama: `git log --oneline -1` + `git merge-base` çıktısı taban commit'i rapora yazar.

3. Veri katmanı: `data/BotRoster.kt` (BotCard @Serializable + BotRosterLogic: doğrulama, pin-önce sıralama, duplicate adlandırma, apply→(profil, sağlayıcı|model)) + `data/BotRosterStore.kt` (SavedPromptsStore deseni: dosya+kilit+fail-soft JSON; Neden Room değil: depo kararı — bkz. Riskler R1).
   Doğrulama: yeni birim testleri (kaydet/yükle, bozuk dosya fail-soft, sıralama, duplicate, doğrulama) yeşil.

4. UI: `ui/BotRosterScreen.kt` (bot kartları: ad/emoji/kişilik afişi, profil+model afişi, pin/duplicate/düzenle/sil/"bu botla devam et") + `ui/BotEditorSheet.kt` (oluştur/düzenle formu: ad, emoji, kişilik, profil seçimi, model seçimi mevcut ModelPicker mantığından) + Settings'te "Botlarım" satırı girişi (MainActivity/SettingsScreen bağlantısı).
   Doğrulama: ./gradlew assembleDebug derlenir; yeni UI'ın saf parçaları için birim testler yeşil.

5. "Bu botla devam et" akışı: roster ekranı → ChatViewModel.selectedProfileValue = bot.profil, selectModel(bot.sağlayıcı, bot.model) + settings.selectedProfile/lastModel kalıcılığı + yeni oturum → Sohbet. Yeni katman; ArenaViewModel'a dokunulmaz.
   Doğrulama: birim test (apply mantığı profil+model eşleşmesi) + assembleDebug + emülatör akışı (adım 7).

6. Test turu: `./gradlew testDebugUnitTest` tam koşum (env -u HERMES_SINGLE_QUERY_SESSION); toplam/fail/err sayıları XML çıktısından rapora yazılır; D-05 testi (bot kartı görünümünde ham UUID yok) eklenir.
   Doğrulama: fail=0 err=0 sayısal çıktı + rapor satırı.

7. Emülatör doğrulama: mock gateway (G3'ün mock_gateway.py deseni, tools/k18b-g1/) ile (a) bot oluştur, (b) listele/pin/duplicate, (c) "devam et" → sohbet aynı profil+model; ekran görüntüleri (en az 4 PNG) + mock log kaydedilir.
   Doğrulama: PNG dosya yolları + log grep satırları rapora yazılır (D-07: kanıtsız iddia yok).

8. Paket + belge: assembleDebug APK md5, CREDITS.md (MausBot/Holt/AI-Buddy fikir girişleri), RAPOR.md (şablon: Bulunan/Yapılan/Atlanan/Kanıt/Commit/Test sayısı/Açık riskler/Denetmene not).
   Doğrulama: md5 komut çıktısı, `grep -c "MausBot\|Holt\|AI-Buddy" CREDITS.md` ≥ 3, RAPOR.md dosya yolu.

9. Commit + kart kapatma: dalda tek/az commit, mesajı ne-değişti-nasıl-doğrulandı biçiminde; kanban_complete ile eldeki metadata (commit, test sayısı, APK md5, artifacts) teslim.
   Doğrulama: `git show --stat` çıktısı + kanban_complete dönüşü.

## Riskler / Patron kararı

- R1 (PATRON KARARI GEREKİR): Kart tasarım notu "Room+SQLCipher mevcut altyapıyı kullan (depoda var)" diyor; ÖLÇÜM bunu DOĞRULAMADI — ana hat ve 2. kopyada room/sqlcipher/zetetic bağımlılığı 0 eşleşme; depo kendi dokümanlı kararıyla JSON dosya deposu kullanıyor (SavedPromptsStore: "Neden Room değil: tek bir JSON yığını, sorgu yok, ilişki yok"; Secret'lar EncryptedSharedPreferences'te). Karar: yeni altyapı (Room+SQLCipher) keyfi EKLENMEZ, aynı JSON dosya deposu deseni kullanılır. İtiraz: patron "SQLCipher zorunlu" derse adım 3'te depo soyutlanır (BotRosterStore arayüzü), arka uç değiştirilir.
- R2: ArenaScreen/ArenaViewModel ana hatta çağrılmıyor (orphan; sekme listesinde Arena yok — MainActivity Tab enum'u Chat/Bildirimler/Takip/Pano/Ayarlar). G1 kapsamı: Arena'ya DOKUNMA, mevcut Arena testleri yeşil kalsın. Arena'nın ana hatta erişilebilir kılması ayrı kart konusudur (kapsam dışı).
- R3: Kişiliğin çalışma zamanında sohbete enjeksiyonu (sistem bağlamı) kapsam dışı — gateway protokolünde per-oturum sistem promptu yok; G1'de kişilik kartta kayıtlı + afişli. K18B-C kartına yazılı (madde 10: kapsam büyütme yok).
- R4: Eşzamanlılık — t_0f6814f1 sürerken kod fazı kapalı; adım 1'de ölçülür, geçmediyse kart bloke/yorumlanır (sessizce başlama yok).
- R5: Emülatörde WebView/dokunma gecikmesi (D-02): kanıt çift kaynaklı olur (PNG + mock gateway log/logcat); adb komutları timeout'lu (D-13).
- R6: Pin/duplicate listesi çok büyürse (50+ kart) JSON tarama maliyeti — bu fazda kabul (liste insan ölçeği), indeks gerekirse sonraki kart.