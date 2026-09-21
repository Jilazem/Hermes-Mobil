#!/usr/bin/env python3
# Tur22 kanıt DOĞRULAMA — her ekranın uiautomator metinleri + sha256.
# Ekran görüntüsü + hiyerarşi metni = çift kaynak (D-02).
import hashlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = ["adb", "-s", "emulator-5554"]
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"


def sh(args, binary=False):
    r = subprocess.run(args, capture_output=True, timeout=30)
    return r.stdout if binary else r.stdout.decode(errors="replace")


def texts():
    sh(ADB + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    x = sh(ADB + ["shell", "cat", "/sdcard/ui.xml"])
    root = ET.fromstring(x[x.index("<hierarchy"):])
    return [n.get("text") for n in root.iter("node") if n.get("text")]


def verify(name, expect_any):
    ts = texts()
    blob = " | ".join(ts)
    ok = [w for w in expect_any if w in blob]
    status = "OK " if ok else "EKSİK"
    print(f"[{status}] {name}")
    print(f"   beklenen={expect_any}")
    print(f"   bulunan={ok}")
    print(f"   ekran metinleri={ts[:12]}")
    return bool(ok)


# 1) bağlantı-yok boş durumu
verify("01 baglantiyok-bosdurum",
       ["Bağlantı yok — bir sunucu ekle ya da ağın kontrol et.", "Sunucu ekle"])
