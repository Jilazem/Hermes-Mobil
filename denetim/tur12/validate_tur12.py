#!/usr/bin/env python3
"""Doğrulama: verdict-tur12.json — validate_verdict.py şemasıyla aynı assert'ler."""
import json
import sys

p = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/verdict-tur12.json"
with open(p, encoding="utf-8") as f:
    v = json.load(f)

assert v["verdict"] in ("pass", "fail"), "verdict alanı geçersiz"
n = v["sayilar"]
tot = {}
for b in v["bulgular"]:
    tot[b["sev"]] = tot.get(b["sev"], 0) + 1
for k in ("critical", "high", "medium", "low"):
    assert n[k] == tot.get(k.upper(), 0), f"sayı uyuşmazlığı: {k}"
if v["verdict"] == "pass":
    assert n["critical"] == 0 and n["high"] == 0, "pass ama CRITICAL/HIGH var"
else:
    assert n["critical"] + n["high"] > 0, "fail ama CRITICAL/HIGH yok"
for b in v["bulgular"]:
    for field in ("id", "sev", "baslik", "dosya", "satir", "neden"):
        assert b.get(field), f"bulgu {b.get('id')} eksik alan: {field}"
assert v["artifacts"], "artefakt sha'ları yok"
print("JSON-PARSE: OK")
print("verdict =", v["verdict"])
print("sayilar =", json.dumps(n))
print("test_sonucu =", v["test_sonucu"][:80], "...")
print("artifacts =", len(v["artifacts"]))
print("assert'ler: TUMU GECTI")
sys.exit(0)
