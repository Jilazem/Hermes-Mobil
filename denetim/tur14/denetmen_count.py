#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TUR-14 denetmen sayim betigi: JUnit XML'lerinden tests/failures/errors/skipped
toplamini uretir (coder iddiasindan bagimsiz kendi sayim), yeni tur14 siniflarini
listeler ve APK sha256 hesaplar. Salt-okunur; yalniz denetim cikti uretir."""
import glob
import hashlib
import json
import os
import re

REPO = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman"

tests = failures = errors = skipped = 0
files = 0
per_class = {}
for p in sorted(glob.glob(REPO + "/app/build/test-results/testDebugUnitTest/TEST-*.xml")):
    head = open(p, encoding="utf-8", errors="replace").read(2000)
    t2 = re.search(
        r'tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"', head)
    if not t2:
        continue
    n, s_, f_, e_ = (int(t2.group(i)) for i in (1, 2, 3, 4))
    tests += n
    failures += f_
    errors += e_
    skipped += s_
    files += 1
    cls = re.search(r"TEST-(.+)\.xml$", os.path.basename(p)).group(1)
    per_class[cls] = [n, f_, e_]

new3 = {k: v for k, v in per_class.items() if "Tur14" in k or "DaySeparator" in k}

apk = REPO + "/app/build/outputs/apk/debug/app-debug.apk"
h = hashlib.sha256(open(apk, "rb").read()).hexdigest()

print(json.dumps({
    "xml_dosya": files,
    "tests": tests,
    "failures": failures,
    "errors": errors,
    "skipped": skipped,
    "yeni_siniflar": new3,
    "apk_sha256": h,
    "apk_bayt": os.path.getsize(apk),
}, ensure_ascii=False, indent=1))
