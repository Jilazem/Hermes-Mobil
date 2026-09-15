#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tur-7 relay devir testi — SAHTE istemcilerle (sandbox 9280/9281).

Senaryo:
  1) A baglanir           -> yuva A
  2) B baglanir           -> A'ya once "taken_over" karesi, sonra close 4001 gelmeli
  3) A tekrar baglanirsa  -> B'ye 4001; TEMIZ devir, sunucuda KENDILIGINDEN
                             yeniden baglanma/dongu OLMAMALI (bekleme penceresinde
                             ek devir sayisi 0)
  4) Yarim kapanmis (TCP'yi sessizce dusuren) istemci -> yeni baglanti beklemeden
     temiz devralinmali (kilit/zaman asimi yok)
"""
import asyncio
import json
import sys
import time
import urllib.request

from aiohttp import ClientSession, WSMsgType

TOKEN = "tur7-sandbox-token"
WS_URL = "http://127.0.0.1:9280/phone?token=" + TOKEN
STATUS_URL = "http://127.0.0.1:9281/status"

RESULTS = []


def check(label, ok, detail=""):
    RESULTS.append((label, bool(ok), str(detail)))
    print(("PASS  " if ok else "FAIL  ") + label + ((" | " + str(detail)) if detail else ""))


def status():
    with urllib.request.urlopen(STATUS_URL, timeout=5) as r:
        return json.loads(r.read().decode())


class FakePhone:
    def __init__(self, name, session):
        self.name = name
        self.session = session
        self.ws = None
        self.events = []
        self.task = None

    async def connect(self):
        self.ws = await self.session.ws_connect(WS_URL, heartbeat=None, timeout=10)
        await self.ws.send_str(json.dumps(
            {"hello": {"model": self.name, "android": 34, "tools": ["phone_status"]}}))
        self.task = asyncio.create_task(self._reader())
        await asyncio.sleep(0.35)
        return self.ws

    async def _reader(self):
        # DİKKAT: `async for msg in ws` close mesajını YUTAR (StopAsyncIteration).
        # Close kodunu görmek için receive() açıkça çağrılmalı.
        try:
            while True:
                msg = await self.ws.receive()
                if msg.type == WSMsgType.TEXT:
                    self.events.append(("text", msg.data))
                elif msg.type == WSMsgType.CLOSE:
                    self.events.append(("close", msg.data, msg.extra))
                else:
                    self.events.append(("end", str(msg.type)))
                    break
        except Exception as exc:  # noqa: BLE001
            self.events.append(("err", str(exc)))

    def text_events(self):
        out = []
        for kind, *rest in self.events:
            if kind == "text":
                try:
                    if json.loads(rest[0]).get("event") == "taken_over":
                        out.append(json.loads(rest[0]))
                except Exception:  # noqa: BLE001
                    pass
        return out

    def close_events(self):
        return [e for e in self.events if e[0] == "close"]

    async def wait_close(self, timeout=6.0):
        t0 = time.time()
        while time.time() - t0 < timeout:
            if self.close_events():
                return True
            await asyncio.sleep(0.1)
        return False

    def close_code(self):
        ev = self.close_events()
        return ev[0][1] if ev else None

    def close_reason(self):
        ev = self.close_events()
        return (ev[0][2] or "") if ev else ""

    async def hard_drop(self):
        """TCP'yi close el sikismasi YAPMADAN dusur (olu soket taklidi)."""
        if self.ws is not None:
            try:
                self.ws._writer.transport.abort()
            except Exception:  # noqa: BLE001
                pass
        if self.task:
            self.task.cancel()


async def main():
    async with ClientSession() as session:
        # ── 1) A baglanir ────────────────────────────────────────────────
        a = FakePhone("A-emulator", session)
        await a.connect()
        st = status()
        check("1A aktif: /status device=A", st["device"] == "A-emulator", st["device"])
        check("1B ilk baglantida last_takeover yok", st["last_takeover"] is None, st["last_takeover"])

        # ── 2) B baglanir -> A devralinir ────────────────────────────────
        b = FakePhone("B-gercek-telefon", session)
        await b.connect()
        got_close = await a.wait_close(timeout=6)
        st = status()
        check("2A A'ya close cercevesi geldi", got_close, a.events)
        check("2B close kodu 4001", a.close_code() == 4001, "code=%s" % a.close_code())
        check("2C close reason devir metni",
              "devralindi" in a.close_reason(), "reason=%r" % a.close_reason())
        check("2D A'ya uygulama duzeyinde taken_over karesi geldi",
              len(a.text_events()) == 1, a.text_events())
        check("2E yuva B'ye gecti", st["device"] == "B-gercek-telefon", st["device"])
        check("2F /status last_takeover {from:A,to:B}",
              (st["last_takeover"] or {}).get("from") == "A-emulator"
              and (st["last_takeover"] or {}).get("to") == "B-gercek-telefon",
              st["last_takeover"])

        # ── 3) A geri doner -> temiz devir, DONGU YOK ────────────────────
        t0 = time.time()
        a2 = FakePhone("A-emulator-yeniden", session)
        await a2.connect()
        await b.wait_close(timeout=6)
        check("3A B'ye de 4001 gitti", b.close_code() == 4001, "code=%s" % b.close_code())
        st = status()
        check("3B yuva A2'de", st["device"] == "A-emulator-yeniden", st["device"])
        devir_oncesi = devir_sayisi()

        # Kimse yeni baglanmiyor: sunucu KENDI KENDINE devir uretmemeli.
        await asyncio.sleep(5)
        check("3C bekleme penceresinde (5 sn) EK DEVIR YOK",
              devir_sayisi() == devir_oncesi,
              "oncesi=%d sonrasi=%d" % (devir_oncesi, devir_sayisi()))
        st = status()
        check("3D yuva hala A2'de (kendiliginden dusme yok)",
              st["device"] == "A-emulator-yeniden" and st["online"] is True, st["device"])
        check("3E A2 hic devralinmadi (close olayi yok)",
              a2.close_events() == [], a2.events)
        check("3F devir sureleri: A2 baglandi -> aktif (%.2fs)" % (time.time() - t0), True)

        # ── 4) Yarim kapanmis istemci ────────────────────────────────────
        c = FakePhone("C-olu-soket", session)
        await c.connect()
        await a2.wait_close(timeout=6)          # C, A2'yi devralir
        await c.hard_drop()                     # C TCP'yi sessizce dusurur
        await asyncio.sleep(0.4)
        d = FakePhone("D-yeni", session)
        t1 = time.time()
        await d.connect()
        st = status()
        check("4A sessizce dusen soket sonrasi yeni baglanti yuva aldi",
              st["device"] == "D-yeni", st["device"])
        check("4B devralma < 4 sn (kilit/zaman asimi yok)",
              (time.time() - t1) < 4.0, "%.2fs" % (time.time() - t1))
        check("4C OLU soket icin sahte devir kaydi uretilmedi",
              (st["last_takeover"] or {}).get("to") == "C-olu-soket",
              st["last_takeover"])

    ok = all(r[1] for r in RESULTS)
    print("\n=== OZET: %d/%d PASS" % (sum(1 for r in RESULTS if r[1]), len(RESULTS)))
    return 0 if ok else 1


def devir_sayisi():
    with open("/tmp/tur7/sandbox/relay.log", encoding="utf-8", errors="replace") as f:
        return sum(1 for line in f if "devralindi:" in line)


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
