#!/usr/bin/env python3
"""Denetim: verdict-tur7.json + verdict-tur8.json tutarlılık doğrulaması.
Kural (tur-6 şeması): sayilar adetleri bulgular listesiyle BİREBİR; pass ⇒
critical=0 ve high=0; fail ⇒ critical+high > 0; her bulguda zorunlu alanlar.
"""
import json
import sys

for p in ("denetim/verdict-tur7.json", "denetim/verdict-tur8.json"):
    with open(p, encoding="utf-8") as f:
        v = json.load(f)
    assert v["verdict"] in ("pass", "fail"), f"{p}: verdict alanı geçersiz"
    tot = {}
    for b in v["bulgular"]:
        tot[b["sev"]] = tot.get(b["sev"], 0) + 1
    n = v["sayilar"]
    for k in ("critical", "high", "medium", "low"):
        assert n[k] == tot.get(k.upper(), 0), f"{p}: sayı uyuşmazlığı {k}: {n[k]} != {tot.get(k.upper(), 0)}"
    if v["verdict"] == "pass":
        assert n["critical"] == 0 and n["high"] == 0, f"{p}: pass ama CRITICAL/HIGH var"
    else:
        assert n["critical"] + n["high"] > 0, f"{p}: fail ama CRITICAL/HIGH yok"
    for b in v["bulgular"]:
        for field in ("id", "sev", "baslik", "dosya", "satir", "neden"):
            assert b.get(field), f"{p}: bulgu {b.get('id')} eksik alan: {field}"
    assert v["artifacts"], f"{p}: artifacts sha'ları yok"
    assert 3 <= len(v["artifacts"]), f"{p}: artifacts çok az"
    assert v.get("test_sonucu") and v.get("lesson"), f"{p}: test_sonucu/lesson yok"
    print(f"{p}: JSON-PARSE OK | verdict={v['verdict']} | sayilar={json.dumps(n)} | findings={len(v['bulgular'])} | artifacts={len(v['artifacts'])} | assert'ler TUMU GECTI")
sys.exit(0)
