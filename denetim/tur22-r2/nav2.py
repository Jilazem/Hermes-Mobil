#!/usr/bin/env python3
# r2 denetçi: Geri ile formu kapat, sohbet ekranına geç, durum raporu + bounds dökümü
import sys, time
sys.path.insert(0, '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22')
import p5lib as P

P.tap('Geri', 1.5)
time.sleep(1)
for n in P.nodes():
    t = n['t'] or n['cd']
    if t:
        print(repr(t[:60]), n['b'], 'clk' if n['clk'] else '')
