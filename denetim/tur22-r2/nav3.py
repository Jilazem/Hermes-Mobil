#!/usr/bin/env python3
# r2 denetçi: formu kapat -> Sohbet'e geç -> üst bar bounds + bağlantı durumu
import sys, time
sys.path.insert(0, '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22')
import p5lib as P

def tapat(want, s=1.8):
    for n in P.nodes():
        if (n['t'] or n['cd']) == want and n['clk']:
            b = n['b']
            P.tap((b[0]+b[2])//2, (b[1]+b[3])//2); time.sleep(s); return True
    return False

print('vazgec:', tapat('Vazgeç'))
time.sleep(1)
print('sohbet:', tapat('Sohbet'))
time.sleep(3)
for n in P.nodes():
    t = n['t'] or n['cd']
    if t:
        print(repr(t[:60]), n['b'], 'clk' if n['clk'] else '')
