#!/usr/bin/env python3
"""V5-5 bot çerçevesi sunucusu.

Kullanım:
    python3 backend/bot_framework/server.py --port 8765 --db ~/.hermes/bots.db
    python3 backend/bot_framework/server.py --host 0.0.0.0 --token "$HERMES_BOT_API_TOKEN"

Varsayılan bağ: 127.0.0.1 (genel paylaşım yok). Token yalnız ortamdan/CLI'dan
gelir; bot tanımlarına ve dışa aktarma paketlerine yazılmaz.
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bot_framework.api import serve  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Hermes bot çerçevesi yerel API")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--db", default=os.path.expanduser("~/.hermes/bots.db"), help="SQLite dosyası"
    )
    parser.add_argument(
        "--token", default=os.environ.get("HERMES_BOT_API_TOKEN", ""), help="Bearer token (isteğe bağlı)"
    )
    args = parser.parse_args()

    if args.host not in ("127.0.0.1", "localhost", "::1") and not args.token:
        print("HATA: LAN'a açarken --token zorunlu (genel paylaşım yok).", file=sys.stderr)
        return 2

    serve(args.db, host=args.host, port=args.port, token=args.token or None)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
