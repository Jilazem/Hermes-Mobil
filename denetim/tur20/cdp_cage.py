#!/usr/bin/env python3
"""Tur-20 kanıt: CDP üzerinden cage.html'e eriş (demo + advance + screenshot + frame-diff).

Kullanım:
  python3 denetim/tur20/cdp_cage.py <port> eval "<js>"
  python3 denetim/tur20/cdp_cage.py <port> shot <dosya.png>
"""
import base64
import json
import sys
import urllib.request

import websocket

port = sys.argv[1]
mode = sys.argv[2]


def pages():
    return json.load(urllib.request.urlopen(f"http://localhost:{port}/json"))


def cage_page():
    for p in pages():
        if "cage.html" in p.get("url", "") or "outrun.html" in p.get("url", ""):
            return p
    return pages()[0]


ws = websocket.create_connection(cage_page()["webSocketDebuggerUrl"], timeout=30, suppress_origin=True)


def cmd(method, **params):
    ws.send(json.dumps({"id": 2, "method": method, "params": params}))
    while True:
        r = json.loads(ws.recv())
        if r.get("id") == 2:
            return r


if mode == "eval":
    expr = sys.argv[3]
    r = cmd("Runtime.evaluate", expression=expr, returnByValue=True, awaitPromise=True)
    print(json.dumps(r.get("result", {}).get("result", {}).get("value", r), ensure_ascii=False))
elif mode == "shot":
    out = sys.argv[3]
    r = cmd("Page.captureScreenshot", format="png")
    data = r["result"]["data"]
    with open(out, "wb") as f:
        f.write(base64.b64decode(data))
    print(out)
else:
    raise SystemExit("bilinmeyen mod")
