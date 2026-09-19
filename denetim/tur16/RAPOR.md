# Tur-16 — Oturum çekmecesi (tek ekran, Claude/Grok/Gemini tarzı)

Tarih: 2026-09-19 · Dal: feat/tur16-oturum-cekmece (a33864e tabanı)

## Bulunan
- Önceki denemeler altyapı dalgalanmasıyla düştü; kod yarım kalmıştı: MainActivity,
  çekmece state'lerini (drawerQuery/drawerArchived) drawerRowList'ten SONRA tanımlıyordu
  (ileri referans → 30+ derleme hatası).
- SessionDrawer, material3 1.4 API'sine göre yazılmıştı (DismissedToEnd / background= /
  DismissValue); proje BOM 2024.10.01 → material3 1.3.1: StartToEnd / backgroundContent.
- InterventionDialog hem LiveSessionsScreen'de hem yeni InterventionDialog.kt'de
  aynı imzayla (public) duruyordu → conflicting overload.
- ChatScreen'e yeni eklenen ☰ ikonu, MainActivity'de onOpenDrawer geçirilmediği için
  sessiz no-op'tu (emülatörde kanıtlandı: dokunuş hiçbir ekran açmıyordu).

## Yapılan
- SessionDrawer.kt + SessionDrawerLogic.kt (saf, testli): zaman grupları (Sabitlenmiş/
  Bugün/Dün/Son 7 gün/Daha eski), arama (başlık+önizleme), canlı durum noktası,
  ham-id filtresi (displayLabel, FR-001 koruması), sabitle, satır içi yeniden
  adlandır, ModalBottomSheet (Döküm/Sabitle/Yeniden adlandır/Arşivle/Sil-onaylı),
  sağa kaydır=arşivle + 5 sn geri-al satırı, boş-durum + boş-arama metinleri,
  Canlı sekmesi (bağlan/Müdahale/Dur) — ayrı sayfa YOK.
- ModalNavigationDrawer sohbetin etrafında; seçim YERİNDE continueSession (rota/back
  stack yok). Geri tuşu açık çekmeciyi kapatır (BackHandler mevcut).
- FR-006: son açık oturum settings.lastSession + lastSessionToRestore ile geri gelir.
- FR-007 ölü kod silme: WorkScreen.kt, LiveSessionsScreen.kt, LiveFeedScreen.kt,
  SessionRail.kt, SessionRailLogic.kt (+ SessionRailTur8Test 18, SessionRailRawIdTest 3),
  ChatViewModel ray durumları (recentRail/railDismissed/noteRecentRail/observeLiveRail),
  SessionsScreen.kt yalnız saf mantığı koruyacak şekilde kırpıldı (Composable yüzeyi
  ve özel helper'ları kaldırıldı), SpeedMeterTest railLabel testleri kaldırıldı.

## Atlanan / LOW
- LOW: "Geçmiş" (arşiv) ayrı görünümü artık çekmecede showArchived anahtarı YOK —
  arşivlenen satır geri-al satırı/kaldırılabilir; kalıcı "Arşiv" filtresi istenirse
  ayrı tur işi (kapsam büyütmeme kuralı).
- LOW: geri-al, Snackbar yerine çekmece içi inline satır (ModalBottomSheet/drawer
  gölgelenmesi riski); 5 sn sonra kendiliğinden kalkar.
- ATLANDI: fiziksel cihaz doğrulaması (yalnız emülatör hermes-v2).

## Kanıt
- testDebugUnitTest XML: 622 test, 0 fail, 0 err (--rerun-tasks, /tmp/tur16-final.log).
  Yeni: SessionDrawerTur16Test 10 test (gruplama, arama, sabitleme sırası,
  arşiv/geri-al, son oturum kalıcılığı, ham-id yasağı, görünmezlik, durum noktası).
- assembleDebug + apksigner verify OK.
  Teslim APK: /Volumes/EX/007-HERMES-M4-LIVE/000-TEMP/hermes-mobile-tur16-oturum-20260918.apk
  md5 d9ef6cf08cbccac14da655ad44e1bf3b (build-dir md5'i rebuild ile değişir — skill kuralı).
- Emülatör (hermes-v2, kurulum Success, crash buffer 0 — grep 'com.hermes.mobile' = 0):
  denetim/tur16/kanit/01-cekmece-acik.png (çekmece açık: Oturumlar/Canlı sekmeleri,
  arama, Yeni sohbet, 'Bugün' grubu, göreli saatler)
  02-arama.png ('pavo' → 2 satır: Pavo Saglik Takibi, App.pavo-ai.cn...)
  03-uzun-basma-alt-sayfa.png (Döküm/Sabitle/Yeniden adlandır/Arşivle/Sil/İptal)
  04-secim-sonrasi-sohbet.png (satır seçildi → sohbet YERİNDE 'Pavo Sağlık Raporu',
  çekmece kapandı)
  05-back-cekmece-kapatir.png + topResumedActivity hâlâ
  com.hermes.mobile.v2/MainActivity (uygulama kapanmadı).

## Commit
- bb6d519 feat(tur16): tek ekran oturum çekmecesi + ölü kod silme
- 24a2183 fix(tur16): onOpenDrawer bağlantısı (menü ikonu no-op'tu)

## Denetmene not
- Emülatör kanıtındaki 01-deneme.png ve 03-uzun-basma.png, onOpenDrawer düzeltmesi
  ÖNCESİ başarısız denemelerdir (hata kanıtı olarak tutuldu).
- drawerListItems liveOnly parametresi var ama UI'dan çekmece canlı listesi ayrı
  LazyColumn olarak çiziliyor (tab==1); liveOnly yalnız mantık testi yolunda.
