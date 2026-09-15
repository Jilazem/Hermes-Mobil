#!/usr/bin/env python3
"""Tur-13 denetmen kanit taramasi (salt okunur).

Coder kanit dosyalarindaki iddialari makine-okur dogrular:
- asistan seridi metinleri (f1-soguk/f2-sicak/f3-voice-command)
- rol satiri (e3-rol-hermes) + vekil bilesen kaniti
- selftest mock/gercek son satirlari
- yonlendirme (b4-yonlendirme: DefaultAppListActivity)
Cikti: denetim/tur13/denetmen-kanit.json
"""
import json
import re
from pathlib import Path

DIR = Path(__file__).resolve().parent
OUT = DIR / "denetmen-kanit.log"


def oku(ad: str) -> str:
    p = DIR / ad
    return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""


satirlar = []


def kanit(etiket: str, kosul: bool, ayrinti: str = "") -> None:
    satirlar.append(f"{'OK ' if kosul else 'EKSIK'} {etiket} {ayrinti}".rstrip())


# 1) Asistan seridi: soğuk açılışta hazır satırı + yerel hat ibaresi
f1 = oku("f1-soguk.xml")
kanit("f1 asistan-hazir", "Asistan hazır" in f1)
kanit("f1 yerel-hat", "Yerel hat" in f1)
f2 = oku("f2-sicak.xml")
kanit("f2 asistan-hazir (ilik)", "Asistan hazır" in f2)
f3 = oku("f3-voice-command.xml")
kanit("f3 voice-command asistan", "Asistan hazır" in f3)

# 2) Rol: Hermes atandıktan sonra satır + öncesi Google
e3 = oku("e3-rol-hermes.xml")
kanit("rol-hermes satiri", "varsayılan asistan" in e3)
kanit("rol-hermes dosya", "hermes" in oku("rol-hermes.txt").lower())
kanit("rol-once google", "Google" in oku("rol-once.txt") or "google" in oku("rol-once.txt").lower())

# 3) Vekil bileşen: AssistantAlias çözümlemesi
vekil = oku("assist-vekil-sicak.txt") + oku("assist-vekil-soguk.txt")
kanit("vekil AssistantAlias", "AssistantAlias" in vekil)
qa = oku("query-assist.txt")
kanit("query-assist alias", "AssistantAlias" in qa and "com.hermes.mobile.v2" in qa)
qv = oku("query-voice-command.txt")
kanit("query-voice alias", "AssistantAlias" in qv)

# 4) Yönlendirme: rol diyaloğu kapandıktan sonra DefaultAppListActivity
b4 = oku("b4-yonlendirme.xml")
kanit("yonlendirme dijital-asistan", ("asistan" in b4.lower() and "uygulama" in b4.lower()))
yon = oku("secure-assistant-once.txt")
kanit("yonlendirme oncesi secure=assistant", len(yon.strip()) > 0 and "google" in yon.lower())

# 5) Selftest sonuçları
mock = oku("selftest-mock.txt")
kanit("selftest-mock BASARILI", "BASARILI" in mock)
kanit("selftest-mock oto-okuma", "oto-okuma karari=true" in mock)
gercek = oku("selftest-gercek.txt")
kanit("selftest-gercek health", "ok=true" in gercek or "ok&#8221;:true" in gercek or '"ok": true' in gercek or "ok=true" in gercek)
kanit("selftest-gercek oto-okuma karari", "karari=true" in gercek or "karar=true" in gercek)

# 6) Mock sunucu günlüğünde synth 200
mv = oku("mock-voice.log")
kanit("mock synth 200", "/synthesize 200" in mv or "synthesize" in mv and "200" in mv)

# 7) APK sha coder iddiası
apk_sh = oku("apk-sha256.txt").strip()
kanit("apk-sha256 dosya dolu", len(apk_sh) >= 64, apk_sh[:72])

rapor = "\n".join(satirlar)
(OUT).write_text(rapor + "\n", encoding="utf-8")
eksik = [s for s in satirlar if s.startswith("EKSIK")]
print(rapor)
print(f"OZET: {len(satirlar) - len(eksik)}/{len(satirlar)} kanit OK")
raise SystemExit(1 if eksik else 0)
