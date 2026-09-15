# Tur-9 verdict dogrulama: parse + sayilar<->bulgular + pass kapisi
import json
p = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/verdict-tur9.json"
with open(p, encoding="utf-8") as f:
    v = json.load(f)
assert v["verdict"] in ("pass", "fail")
tot = {}
for b in v["bulgular"]:
    tot[b["sev"]] = tot.get(b["sev"], 0) + 1
n = v["sayilar"]
for k in ("critical", "high", "medium", "low"):
    assert n[k] == tot.get(k.upper(), 0), f"sayi uyusmazligi: {k}"
if v["verdict"] == "pass":
    assert n["critical"] == 0 and n["high"] == 0
else:
    assert n["critical"] + n["high"] > 0
for b in v["bulgular"]:
    for field in ("id", "sev", "baslik", "dosya", "satir", "neden"):
        assert b.get(field), f"eksik alan {field} @ {b.get('id')}"
assert v["artifacts"] and v["test_sonucu"]
print("JSON-PARSE: OK | verdict =", v["verdict"], "| sayilar =", json.dumps(n))
