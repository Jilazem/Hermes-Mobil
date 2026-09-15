# Verdict JSON'unu json.load ile geri oku ve doğrula
import json
p = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/verdict-tur6.json"
d = json.load(open(p))
assert d["verdict"] in ("pass", "fail")
assert set(d["sayilar"]) >= {"critical", "high", "medium", "low"}
print("JSON_OK", d["verdict"], d["sayilar"], "bulgu=", len(d["bulgular"]))
