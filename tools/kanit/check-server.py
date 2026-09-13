#!/usr/bin/env python3
# Tur-3 ADB kanıtı: 9120 dashboard'u X-Hermes-Session-Token ile doğrula.
# Token ~/.hermes/.env'den okunur, ASLA yazdırılmaz — yalnız HTTP durumu.
import re, sys, urllib.request

def token():
    for line in open('/Users/gokhanuzman/.hermes/.env'):
        m = re.match(r'HERMES_DASHBOARD_SESSION_TOKEN=(.+)', line.strip())
        if m:
            return m.group(1)
    sys.exit('TOKEN_NOT_FOUND')

def req(url, tok):
    r = urllib.request.Request(url, headers={'X-Hermes-Session-Token': tok})
    try:
        with urllib.request.urlopen(r, timeout=6) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:
        return f'ERR {e}'

tok = token()
print('status with token  :', req('http://127.0.0.1:9120/api/status', tok))
print('status without     :', req('http://127.0.0.1:9120/api/status', ''))
