#!/usr/bin/env python3
"""Tur-15 denetim: WebView DevTools (CDP) ile sayfada JS ifadesi değerlendirir.
Kullanım: python3 cdp_eval.py <port> "<js ifadesi>"  (adb forward tcp:<port> localabstract:webview_devtools_remote_<pid>)
"""
import json, sys, urllib.request, websocket
port, expr = sys.argv[1], sys.argv[2]
pages = json.load(urllib.request.urlopen(f"http://localhost:{port}/json"))
ws = websocket.create_connection(pages[0]["webSocketDebuggerUrl"], timeout=15, suppress_origin=True)
ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                    "params": {"expression": expr, "returnByValue": True, "awaitPromise": True}}))
while True:
    r = json.loads(ws.recv())
    if r.get("id") == 1:
        print(json.dumps(r.get("result", {}).get("result", {}).get("value", r), ensure_ascii=False))
        break
