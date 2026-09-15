#!/usr/bin/env python3
"""Denetim aracı: uiautomator dump XML'ini yasaklı metin açısından tarar ve
düğüm metinlerini listeler. Salt-okunur; yalnız stdout'a yazar.

Kullanım: python3 denetim/scan_dump.py denetim/d1-sohbet.xml [d2.xml ...]
Çıkış kodu: yasaklı bulgu varsa 1, yoksa 0.
"""
import sys
import re
import xml.etree.ElementTree as ET

# Yasaklı desenler (tur-4 kabul kapısı + geniş sızıntı taraması).
FORBIDDEN = [
    ("json_status_success", re.compile(r'"status"\s*:\s*"success"', re.I)),
    ("json_output", re.compile(r'"output"\s*:', re.I)),
    ("IMPORTANT", re.compile(r"IMPORTANT", re.I)),
    ("gokhan_uzman", re.compile(r"Gökhan\s+Uzman", re.I)),
    ("scheduled_cron_prompt", re.compile(r"scheduled cron job", re.I)),
    ("internal_default_chip", re.compile(r"^\s*default\s*$", re.I)),
    ("internal_ac", re.compile(r"^\s*ac\s*$", re.I)),
    ("internal_android", re.compile(r"^\s*android\s*$", re.I)),
    ("status_idle_label", re.compile(r"^\s*(boşta|idle)\s*$", re.I)),
    ("markdown_fence", re.compile(r"^```", re.I)),
    ("tool_call_id", re.compile(r"tool_call_id", re.I)),
]


def texts_of(path: str):
    tree = ET.parse(path)
    root = tree.getroot()
    out = []
    for node in root.iter():
        for attr in ("text", "content-desc"):
            v = node.get(attr)
            if v:
                out.append((attr, v))
    return out


def main() -> int:
    exit_code = 0
    for path in sys.argv[1:]:
        try:
            items = texts_of(path)
        except Exception as exc:  # bozuk dump = kanıt yok → fail sinyali
            print(f"[{path}] PARSE HATASI: {exc}")
            exit_code = 1
            continue
        hits = []
        for attr, v in items:
            for name, rx in FORBIDDEN:
                if rx.search(v):
                    hits.append((name, attr, v[:120]))
        print(f"=== {path}: {len(items)} metin düğümü, {len(hits)} yasaklı eşleşme ===")
        for name, attr, v in hits:
            print(f"  YASAKLI[{name}] {attr}={v!r}")
            exit_code = 1
        # Görünür tüm metinler (sızıntı göz taraması için)
        print("--- görünen metinler ---")
        seen = set()
        for attr, v in items:
            key = (attr, v)
            if key in seen or not v.strip():
                continue
            seen.add(key)
            print(f"  {v}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
