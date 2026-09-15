# Tur-9 probe: pencere dokumundaki WebView dugumunu ve Arena rozetlerini bul
import re
x = open('/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur9/emulator-pencere-dokumu.xml').read()
for m in re.finditer(r'<node[^>]*?/?>', x):
    s = m.group(0)
    if 'WebView' in s or 'Arena 3D' in s or 'Canlı' in s:
        print(s[:320])
        print('---')
