#!/usr/bin/env python3
# Son deneme: tek (hızlı) profil + 15sn /api/sessions → refreshAll penceresi geniş.
import subprocess
import time

import p5lib as P

P.sh(P.ADB + ['shell', 'am', 'force-stop', P.PKG]); time.sleep(1.2)
subprocess.run(P.ADB + ['shell', 'rm', '/sdcard/v04.mp4'], capture_output=True, timeout=15)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity'])
# 20sn video; 2.0sn sonra çekmece aç (Compose acilis ~1.5sn)
rec = subprocess.Popen(P.ADB + ['shell', 'screenrecord', '--time-limit', '20',
                                 '/sdcard/v04.mp4'],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(2.0)
d = P.clickable_parent_of(lambda n: (n['t'] or n['cd']) == 'Oturumlar')
x, y = P.ctr(d)
P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
print("cekmece ates:", x, y)
for i in range(10):
    time.sleep(1.4)
    metin = [n['t'][:26] for n in P.nodes() if n['t']][:7]
    acik = any('Gezinme' in n['cd'] for n in P.nodes())
    print(f"t~{2.0+1.4*(i+1):.1f}sn acik={acik} metin={metin}")
rec.wait(timeout=40)
subprocess.run(P.ADB + ['pull', '/sdcard/v04.mp4', f'{P.OUT}/v04-iskelet-son.mp4'],
               capture_output=True, timeout=120)
print("video cekildi")
