#!/usr/bin/env python3
"""Tur-15 denetim: verdict-tur15.json şema + CRITICAL/HIGH↔fail tutarlılığı + artefakt sha256'larını diskten yeniden hesaplar.
Kullanım: python3 denetim/tur15/verdict_dogrula.py  (repo kökünden)
"""
import hashlib, json, os, sys
v = json.load(open("denetim/verdict-tur15.json", encoding="utf-8"))
for k in ("verdict", "gorev", "tarih", "test_sonucu", "findings", "artifacts", "lesson", "uzman", "deneme_no", "devir"):
    assert k in v, f"eksik alan: {k}"
assert v["verdict"] in ("pass", "fail")
agir = [f for f in v["findings"] if f["sev"] in ("CRITICAL", "HIGH")]
assert (v["verdict"] == "fail") == bool(agir), "CRITICAL/HIGH ile verdict tutarsız"
kok = os.environ.get("DENETIM_KOK", ".")
for yol, sha in v["artifacts"].items():
    p = yol if os.path.isabs(yol) else os.path.join(kok, yol)
    h = hashlib.sha256(open(p, "rb").read()).hexdigest()
    assert h == sha, f"sha uyusmaz: {yol}"
print(f"OK verdict={v['verdict']} bulgu={len(v['findings'])} artefakt={len(v['artifacts'])}")
