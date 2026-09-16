#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TUR-14 verdict dogrulayici: SOUL semasi + artefakt sha yeniden hesabi.

Kurallar:
 1) verdict in {pass, fail}; pass ise CRITICAL+HIGH sayisi 0, fail ise >0.
 2) Her findings kaydinda sev CRITICAL/HIGH/MEDIUM/LOW ve id/bulgu dolu.
 3) artifacts: her yol repo kokune gore var ve sha256 diskten yeniden
    hesaplanip verdict'teki degerle birebir eslesmeli.
Cikis: sayim ozeti; hata varsa exit 1.
"""
import hashlib
import json
import os
import sys

REPO = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman"
VFILE = os.path.join(REPO, "denetim", "verdict-tur14.json")

with open(VFILE, encoding="utf-8") as f:
    v = json.load(f)

hatalar = []

# 1) verdict alanlari
if v.get("verdict") not in ("pass", "fail"):
    hatalar.append("verdict gecersiz: %r" % v.get("verdict"))
for alan in ("gorev", "tarih", "test_sonucu", "lesson"):
    if not v.get(alan):
        hatalar.append("eksik alan: %s" % alan)

# 2) bulgular
sev_say = {"CRITICAL": 0, "HIGH": 0, "MEDIUM": 0, "LOW": 0}
for b in v.get("findings", []):
    sev = b.get("sev")
    if sev not in sev_say:
        hatalar.append("gecersiz sev: %r" % sev)
    else:
        sev_say[sev] += 1
    if not b.get("id") or not b.get("bulgu"):
        hatalar.append("bulgu id/bulgu eksik: %r" % (b.get("id"),))

# 3) pass/fail tutarlilik (SOUL kurali)
top = lambda s: sev_say[s]
if v["verdict"] == "pass" and (top("CRITICAL") + top("HIGH")) > 0:
    hatalar.append("pass ama CRITICAL/HIGH > 0")
if v["verdict"] == "fail" and (top("CRITICAL") + top("HIGH")) == 0:
    hatalar.append("fail ama CRITICAL/HIGH = 0")

# 4) artefakt sha dogrulama (diskten yeniden hesap)
sha_ok = sha_hata = 0
for yol, beklenen in v.get("artifacts", {}).items():
    tam = os.path.join(REPO, yol)
    if not os.path.isfile(tam):
        hatalar.append("artefakt yok: %s" % yol)
        sha_hata += 1
        continue
    h = hashlib.sha256(open(tam, "rb").read()).hexdigest()
    if h != beklenen:
        hatalar.append("sha eslesmedi: %s" % yol)
        sha_hata += 1
    else:
        sha_ok += 1

print("JSON-PARSE: OK")
print("verdict =", v["verdict"])
print("bulgular =", json.dumps(sev_say))
print("sha dogrulandi: %d ok / %d hata (toplam %d artefakt)" % (sha_ok, sha_hata, len(v.get("artifacts", {}))))
if hatalar:
    print("HATALAR:")
    for x in hatalar:
        print(" -", x)
    sys.exit(1)
print("assert'ler: TUMU GECTI")
