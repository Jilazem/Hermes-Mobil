#!/usr/bin/env python3
"""Denetim: JUnit XML'lerinden test sayımı (üretici iddiasına güvenmeden).

Kullanım: python3 denetim/count_tests.py [xml_dizini]
Çıktı: dosya bazında tests/failures/errors/skipped + GENEL TOPLAM.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

d = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"
tot = fail = err = skip = 0
files = sorted(glob.glob(os.path.join(d, "TEST-*.xml")))
print(f"XML dosya sayisi: {len(files)}")
for f in files:
    try:
        r = ET.parse(f).getroot()
    except Exception as e:  # bozuk/yarım XML = kanıt sayılmaz
        print(f"  !! PARSE HATASI: {f}: {e}")
        fail += 1
        continue
    t = int(r.get("tests", 0)); fl = int(r.get("failures", 0))
    e = int(r.get("errors", 0)); sk = int(r.get("skipped", 0))
    tot += t; fail += fl; err += e; skip += sk
    name = r.get("name", os.path.basename(f))
    if fl or e or sk:
        print(f"  {name}: tests={t} failures={fl} errors={e} skipped={sk}")
print(f"GENEL: tests={tot} failures={fail} errors={err} skipped={skip}")
if fail or err:
    sys.exit(1)
