#!/usr/bin/env python3
"""Tur-12: emülatördeki voice_api alanını BOŞALT (tur-12 koşumunda yazılan mock
adresi kalmasın; uygulama yeniden profil adresinden türetsin)."""
import subprocess
import sys
import time

sys.path.insert(0, "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12")
from ui_warm import PKG, _voice_alani, anahtar, dump, nodes, report, sh, shot, tap_node  # noqa: E402


def main():
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh("shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
    time.sleep(9)
    ns = nodes(dump("t-ana"))
    a = [n for n in ns if (n["text"] or "") == "Ayarlar"]
    if a:
        tap_node(a[-1])
        time.sleep(2.5)
    ns = nodes(dump("t-liste"))
    c = [n for n in ns if "Canlı ses" in (n["text"] or "")]
    if c:
        tap_node(c[0])
        time.sleep(2.5)
    for _ in range(8):
        xml = dump("t-scroll")
        if _voice_alani(xml):
            break
        sh("shell", "input", "swipe", "540", "1800", "540", "900", "300")
        time.sleep(1.0)
    alan = _voice_alani(xml)
    if not alan:
        print("HATA: alan bulunamadı")
        return 1
    tap_node(alan)
    time.sleep(1.2)
    for _ in range(30):
        sh("shell", "input", "keyevent", "67")   # backspace
    time.sleep(1.5)
    out = sh("shell", "dumpsys", "input_method").stdout
    if "mInputShown=true" in out:
        sh("shell", "input", "keyevent", "4")
        time.sleep(1.5)
    xml = report("t-temiz")
    anahtar(xml)
    ns = nodes(xml)
    et = [n for n in ns if n["cls"].endswith("EditText")]
    print("alan metni:", repr(et[0]["text"]) if et else "?")
    shot("emulator-alan-temiz")
    return 0


if __name__ == "__main__":
    sys.exit(main())
