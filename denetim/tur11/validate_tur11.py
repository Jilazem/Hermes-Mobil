# Tur-11 verdict dogrulamasi — validate_verdict.py mantiginin tur11 kopyasi.
import json
import sys

P = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/verdict-tur11.json"
with open(P, encoding="utf-8") as f:
    v = json.load(f)

assert v["verdict"] in ("pass", "fail"), "verdict alani gecersiz"
tot = {}
for b in v["bulgular"]:
    tot[b["sev"]] = tot.get(b["sev"], 0) + 1
n = v["sayilar"]
for k in ("critical", "high", "medium", "low"):
    assert n[k] == tot.get(k.upper(), 0), f"sayi uyusmazligi: {k}"
if v["verdict"] == "pass":
    assert n["critical"] == 0 and n["high"] == 0, "pass ama CRITICAL/HIGH var"
else:
    assert n["critical"] + n["high"] > 0, "fail ama CRITICAL/HIGH yok"
for b in v["bulgular"]:
    for field in ("id", "sev", "baslik", "dosya", "satir", "neden"):
        assert b.get(field), f"bulgu {b.get('id')} eksik alan: {field}"
assert v["artifacts"], "artefakt sha'lari yok"
import re
for k, s in v["artifacts"].items():
    assert re.fullmatch(r"[0-9a-f]{64}", s), f"sha256 bicim degil: {k}"
print("JSON-PARSE: OK")
print("verdict =", v["verdict"])
print("sayilar =", json.dumps(n))
print("artifacts =", len(v["artifacts"]))
print("assert'ler: TUMU GECTI")
sys.exit(0)
