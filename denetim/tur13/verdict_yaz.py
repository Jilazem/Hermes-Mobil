#!/usr/bin/env python3
"""Tur-13 kapı denetimi — verdict yazma + dogrulama (denetmen koşumu).

SOUL.md semasi: verdict/gorev/tarih/test_sonucu/findings/artifacts/lesson.
CRITICAL/HIGH yok -> pass. Artefakt sha'leri canli hesaplanir (bütünlük kanıtı).
"""
import hashlib
import json
import os

ROOT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman"


def sha(path):
    """Dosyanin sha256'sini hex olarak dondurur."""
    h = hashlib.sha256()
    with open(os.path.join(ROOT, path), "rb") as fh:
        for blok in iter(lambda: fh.read(65536), b""):
            h.update(blok)
    return h.hexdigest()


artifacts = {}
for yol in [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/hermes/mobile/data/AssistantMode.kt",
    "app/src/main/java/com/hermes/mobile/data/AssistantRole.kt",
    "app/src/main/java/com/hermes/mobile/ChatViewModel.kt",
    "app/src/main/java/com/hermes/mobile/MainActivity.kt",
    "app/src/main/java/com/hermes/mobile/data/AppSettings.kt",
    "app/src/test/java/com/hermes/mobile/AssistantModeTest.kt",
    "app/src/debug/java/com/hermes/mobile/ui/AssistantSelfTestActivity.kt",
    "app/build/outputs/apk/debug/app-debug.apk",
    "denetim/tur13/RAPOR.md",
    "denetim/tur13/denetmen-gradle.log",
    "denetim/tur13/denetmen-synth-probe.log",
    "denetim/tur13/live-synth.json",
    "denetim/tur13/live-synth-probe.ogg",
    "denetim/tur13/denetmen-ozet.txt",
    "denetim/tur13/denetmen_kontrol.py",
]:
    artifacts[yol] = sha(yol)

verdict = {
    "verdict": "pass",
    "gorev": "tur13",
    "tarih": "2026-09-16T01:04:59+03:00",
    "test_sonucu": (
        "Kendi koşum: gradle-8.9/JDK-17, testDebugUnitTest assembleDebug --rerun-tasks "
        "-> BUILD SUCCESSFUL (52 sn, 41 task, exit 0). XML sayımı (46 dosya, kendi "
        "count_tests.py): tests=598 failures=0 errors=0 skipped=0 — 574+24 iddiası "
        "doğrulandı (AssistantModeTest'te 24 @Test). APK sha256=aafd07c26732f9363ab"
        "037b8d0bb3e427d6f6056ce7a526b64a915c433aefcf3. Canlı /synthesize yeniden "
        "testi (denetmen probe, salt-çağrı): /health 200 kahya=hazir -> POST "
        "/synthesize 200, 57.6 sn, 11678 bayt OggS (sha256_8=e7ba15c2) -> /health "
        "200. Tur-13'teki sunucu arızası giderilmiş; uzun süreli ilk yanıt (~58 sn) "
        "motor yükleme maliyeti, uygulama kusuru değil — kanıtlar ayrı tutuldu."
    ),
    "findings": [
        {
            "sev": "LOW",
            "id": "genel",
            "bulgu": "app/build/outputs/apk/debug/app-debug.apk:1 — Aynı HEAD "
                "(28e897a) için coder kanıtı 0a1077da…, denetmen rebuild aafd07c2…; "
                "zip-mtime farkı, kusur değil. Tek sha beklentisi için tekrarlanabilir "
                "derleme (reproducible build) kurulumu önerilir.",
        },
        {
            "sev": "LOW",
            "id": "SC-13-5",
            "bulgu": "app/src/test/java/com/hermes/mobile/VoiceSpeakLogicTest.kt:1 — "
                "Ses zinciri birim testleri ağ yolunu mock'lar (tasarım geregi); canlı "
                "uc davranışı denetmen probe koşumuyla ayrıca kanıtlandı (200/57.6 sn/"
                "OggS). Birim ve canlı kanıt ayrı katmanlarda tutuldu.",
        },
    ],
    "artifacts": artifacts,
    "lesson": (
        "Sunucu arızası ile uygulama kusurunu ayırt etmenin en kısa yolu canlı uç "
        "probe'u: /health + /synthesize yeniden koşumu tek kanıtla 'arıza giderilmiş, "
        "kod sağlıklı' hükmini verdi; kod okuması ve mock testler bu ayrımı veremez."
    ),
}

hedef = os.path.join(ROOT, "denetim", "verdict-tur13.json")
with open(hedef, "w", encoding="utf-8") as fh:
    json.dump(verdict, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

# --- kendi dogrulamam: sema + tutarlılik ---
with open(hedef, encoding="utf-8") as fh:
    v = json.load(fh)
assert v["verdict"] in ("pass", "fail")
assert v["gorev"] and v["tarih"] and v["test_sonucu"] and v["lesson"]
assert v["findings"] is not None and v["artifacts"]
for b in v["findings"]:
    assert b["sev"] in ("CRITICAL", "HIGH", "MEDIUM", "LOW") and b["id"] and b["bulgu"]
sev_sayaç = {}
for b in v["findings"]:
    sev_sayaç[b["sev"]] = sev_sayaç.get(b["sev"], 0) + 1
if v["verdict"] == "pass":
    assert sev_sayaç.get("CRITICAL", 0) == 0 and sev_sayaç.get("HIGH", 0) == 0
assert len(v["artifacts"]) >= 10, "artefakt kanıtı yetersiz"
print("VERDICT-YAZILDI:", hedef)
print("verdict =", v["verdict"], "| findings =", sev_sayaç, "| artifacts =", len(v["artifacts"]))
