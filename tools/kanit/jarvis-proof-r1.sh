#!/bin/bash
# Tur-23 JARVIS-1 — JarvisVisualizer 4-faz kanıtı + Speaking 2-kare diff.
# r1: JarvisVisualTestActivity artık faz/seviye sabitleyebiliyor (--es/--ef);
#     her faz AYRI açılır (gerçek 4 faz görüntüsü), Speaking'te animate=1 ile
#     120ms arayla 2 kare alınır (level sürücülü animasyon canlı kanıtı).
# Kullanım: bash tools/kanit/jarvis-proof-r1.sh <kanıt-klasörü>
# Ön koşul: emulator açık, debug APK kurulu.
set -u
ADB=${ADB:-/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb}
DEV=${DEV:-emulator-5554}
# r1 fix: debug applicationIdSuffix=.v2 (build.gradle.kts) — ayrı paket kimliği.
ACT=com.hermes.mobile.v2/com.hermes.mobile.ui.JarvisVisualTestActivity
OUT=${1:?kanıt klasörü verin}
mkdir -p "$OUT"
adb() { $ADB -s "$DEV" "$@"; }
adb root >/dev/null 2>&1 || true; sleep 1

shot() { local f=$1; adb exec-out screencap -p > "$f"; }

phase_shot() { # phase_shot <faz> <ek dosya> [extra...]
  local phase=$1 file=$2; shift 2
  adb shell "am force-stop com.hermes.mobile.v2" >/dev/null 2>&1
  adb logcat -c >/dev/null 2>&1
  adb shell "am start -n $ACT --es phase $phase $*" >/dev/null
  sleep 3
  shot "$file"
  grep -a "JARVIS-VISUAL-TEST" <(adb logcat -d) | tail -1 >> "$OUT/log.txt"
}

phase_shot idle      "$OUT/faz1_idle.png"      --ef level 0.0
phase_shot listening "$OUT/faz2_listening.png" --ef level 0.7
phase_shot thinking  "$OUT/faz3_thinking.png"
# Speaking: animate=1 — level 120ms'de bir 0..1 dolaşır; 2 ardışık kare FARKLI olmalı.
phase_shot speaking  "$OUT/faz4a_speaking.png" --es animate 1
sleep 1
shot "$OUT/faz4b_speaking.png"
adb shell "am force-stop com.hermes.mobile.v2" >/dev/null 2>&1

python3 - "$OUT" <<'PY'
import sys, zlib, struct, os
d = sys.argv[1]
def raw(path):
    b = open(path,'rb').read()
    pos = 8; idat = b''; w = h = 0
    while pos < len(b):
        ln = struct.unpack('>I', b[pos:pos+4])[0]; typ = b[pos+4:pos+8]
        if typ == b'IHDR': w,h,bd,ct = struct.unpack('>IIBB', b[pos+8:pos+18])
        if typ == b'IDAT': idat += b[pos+8:pos+8+ln]
        pos += 12 + ln
    data = zlib.decompress(idat)
    stride = w*4
    rows = [data[i*(stride+1)+1:(i+1)*(stride+1)] for i in range(h)]
    prev = bytearray(stride); out=[]
    for r in rows:
        cur = bytearray(stride)
        for x in range(stride):
            f = r[x]; a = cur[x-3] if x>=3 else 0
            if f==1: cur[x]=(r[x]+a)&255
            elif f==2: cur[x]=(r[x]+prev[x])&255
            elif f==3: cur[x]=(r[x]+(a+prev[x])//2)&255
            elif f==4:
                b_=prev[x-3] if x>=3 else 0; c=prev[x-1] if x>0 else 0
                p=a+b_-c; pa,pb,pc=abs(p-a),abs(p-b_),abs(p-c)
                pr=a if (pa<=pb and pa<=pc) else (b_ if pb<=pc else c)
                cur[x]=(r[x]+pr)&255
            else: cur[x]=r[x]
        out.append(cur); prev=cur
    return w,h,bytes(b''.join(out))
pairs = [('faz1_idle.png','faz2_listening.png'),
         ('faz2_listening.png','faz3_thinking.png'),
         ('faz3_thinking.png','faz4a_speaking.png'),
         ('faz4a_speaking.png','faz4b_speaking.png')]
for a_f,b_f in pairs:
    w1,h1,a = raw(os.path.join(d,a_f)); w2,h2,b = raw(os.path.join(d,b_f))
    n = min(len(a),len(b))
    diff = sum(1 for i in range(0, n, 4*7) if a[i:i+4]!=b[i:i+4])
    print('DIFF %s ↔ %s: %d örnekte farklı (adım=7px, boyut %dx%d)' % (a_f,b_f,diff,w1,h1))
PY
echo "KANIT: $OUT  (4+1 ekran + log.txt + diff)"
