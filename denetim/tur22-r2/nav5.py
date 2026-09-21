#!/usr/bin/env python3
# r2 denetçi: bounds-string parse + tap; Vazgeç bas -> durum
import sys, re, time
sys.path.insert(0, '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22')
import p5lib as P

def xy(bstr):
    m = re.findall(r'\d+', bstr)
    x1, y1, x2, y2 = map(int, m)
    return (x1 + x2) // 2, (y1 + y2) // 2

def tapat(want, s=1.8, force=True):
    for n in P.nodes():
        if (n['t'] or n['cd']) == want and (n['clk'] or force):
            P.tap(*xy(n['b'])); time.sleep(s); return True
    return False

print('vazgec:', tapat('Vazgeç', 2.0))
time.sleep(1.5)
for n in P.nodes():
    t = n['t'] or n['cd']
    if t:
        print(repr(t[:60]), n['b'], 'clk' if n['clk'] else '')
