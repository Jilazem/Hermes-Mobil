#!/usr/bin/env python3
# Acik duzenleme formunu EditText y-sirasiyla duzelt:
# y~514 Adres -> http://10.0.2.2:9171 | y~703 Token -> mocktoken | y~892 Uzak -> bosalt
import time

import p5lib as P


def xy(n):
    return [int(v) for v in n['b'].strip('[]').replace('][', ',').split(',')]


def tapat(want, s=1.6, tries=2):
    for _ in range(tries):
        for n in P.nodes():
            if (n['t'] or n['cd']) == want:
                x1, y1, x2, y2 = xy(n)
                P.sh(P.ADB + ['shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)])
                time.sleep(s)
                return True
    return False


def clear_field():
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    time.sleep(0.2)
    for _ in range(60):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(0.4)


def type_text(t):
    P.sh(P.ADB + ['shell', 'input', 'text', t.replace(' ', '%s')])
    time.sleep(0.4)


def edit_by_y(target_y, tol=60):
    for n in P.nodes():
        if n['cls'].endswith('EditText'):
            x1, y1, x2, y2 = xy(n)
            cy = (y1 + y2) // 2
            if abs(cy - target_y) <= tol:
                P.sh(P.ADB + ['shell', 'input', 'tap', str((x1+x2)//2), str(cy)])
                time.sleep(1.0)
                return True
    return False


ok = edit_by_y(598)
print('adres hucresi:', ok)
if ok:
    clear_field(); type_text('http://10.0.2.2:9171')
ok = edit_by_y(785)
print('token hucresi:', ok)
if ok:
    clear_field(); type_text('mocktoken')
ok = edit_by_y(976)
print('uzak hucresi:', ok)
if ok:
    clear_field()
P.sh(P.ADB + ['shell', 'input', 'keyevent', '111']); time.sleep(0.8)
for n in P.nodes():
    t = n['t'] or n['cd']
    if t:
        print(repr(t[:60]), n['b'], n['cls'].split('.')[-1])
print('kaydet:', tapat('Kaydet', s=2.5))
for n in P.nodes():
    t = n['t'] or n['cd']
    if t:
        print(repr(t[:60]), n['b'])
