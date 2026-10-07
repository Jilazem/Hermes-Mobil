#!/usr/bin/env python3
"""Local EMA voice service. No text/audio is logged or retained.

EMA_TOKEN is required; EMA_HOST defaults to loopback, EMA_PORT to 8176.
POST /speak -> WAV, POST /stream -> chunked mono PCM16LE, POST /cancel.
The installed ema_lightning model is loaded once, on the first request.
"""
import hmac
import io
import json
import os
import queue
import threading
import uuid
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RATES = (8000, 16000, 24000, 48000)
MAX_TEXT = 3000


class RequestError(ValueError):
    pass


def validate(payload):
    if not isinstance(payload, dict):
        raise RequestError("JSON object required")
    text = payload.get("text")
    rate = payload.get("sample_rate", 48000)
    speed = payload.get("speed", 1.0)
    seed = payload.get("seed", 0)
    rid = payload.get("request_id", str(uuid.uuid4()))
    if not isinstance(text, str) or not text.strip() or len(text) > MAX_TEXT:
        raise RequestError("text must contain 1..3000 characters")
    if isinstance(rate, bool) or rate not in RATES:
        raise RequestError("unsupported sample_rate")
    if isinstance(speed, bool) or not isinstance(speed, (int, float)) or not 0.25 <= speed <= 4:
        raise RequestError("speed must be 0.25..4")
    if isinstance(seed, bool) or not isinstance(seed, int) or not 0 <= seed < 2**63:
        raise RequestError("seed must be a non-negative 63-bit integer")
    try:
        rid = str(uuid.UUID(rid))
    except (ValueError, TypeError, AttributeError):
        raise RequestError("invalid request_id") from None
    return text.strip(), rate, speed, seed, rid


class Synthesis:
    def __init__(self, rid):
        self.id = rid
        self.cancelled = threading.Event()
        self.producer_done = threading.Event()
        self.handler_done = threading.Event()
        self.chunks = queue.Queue(maxsize=2)

    def put(self, value):
        while not self.cancelled.is_set():
            try:
                self.chunks.put(value, timeout=0.1)
                return
            except queue.Full:
                pass


class EmaRuntime:
    def __init__(self, factory=None):
        self.factory = factory or self._load
        self.model = None
        self.lock = threading.Lock()
        self.active = {}
        self.active_lock = threading.Lock()

    @staticmethod
    def _load():
        from ema_lightning import EMA
        return EMA()

    def start(self, args):
        text, rate, speed, seed, rid = args
        req = Synthesis(rid)
        with self.active_lock:
            if rid in self.active or len(self.active) >= 4:
                raise RequestError("busy or duplicate request_id")
            self.active[rid] = req

        def produce():
            stream = None
            try:
                # Initialize once; EMA's Playhead schedules concurrent streams.
                with self.lock:
                    if req.cancelled.is_set():
                        return
                    if self.model is None:
                        self.model = self.factory()
                stream = self.model.stream(text, sample_rate=rate, speed=speed, seed=seed)
                for chunk in stream:
                    if req.cancelled.is_set():
                        break
                    pcm = (chunk.clip(-1, 1) * 32767).astype("<i2").tobytes()
                    if pcm:
                        req.put(pcm)
            except Exception:
                # Do not disclose exceptions containing paths or input text.
                req.put(RuntimeError("EMA synthesis failed"))
            finally:
                if stream is not None:
                    close = getattr(stream, "close", None)
                    if close:
                        try:
                            close()
                        except Exception:
                            pass
                req.put(None)
                req.producer_done.set()
                self._reap(req)

        threading.Thread(target=produce, daemon=True).start()
        return req

    def finish(self, req):
        req.cancelled.set()
        req.handler_done.set()
        self._reap(req)

    def _reap(self, req):
        # Cancellation cannot interrupt an in-flight model call. Retain its slot
        # until both threads finish, so repeated cancels cannot spawn unbounded work.
        if not (req.producer_done.is_set() and req.handler_done.is_set()):
            return
        with self.active_lock:
            if self.active.get(req.id) is req:
                self.active.pop(req.id)

    def cancel(self, rid):
        with self.active_lock:
            req = self.active.get(rid)
            if req is not None:
                req.cancelled.set()
            return req is not None


class EmaServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, token, runtime=None):
        if not token.strip():
            raise ValueError("EMA_TOKEN must be set")
        self.token = token
        self.runtime = runtime or EmaRuntime()
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def handle_one_request(self):
        try:
            super().handle_one_request()
        except (BrokenPipeError, ConnectionResetError):
            self.close_connection = True

    def log_message(self, *_):
        pass

    def reply(self, code, payload):
        data = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        token = self.headers.get("X-Hermes-Session-Token", "")
        if not hmac.compare_digest(token.encode(), self.server.token.encode()):
            self.close_connection = True
            self.reply(403, {"error": "unauthorized"})
            return False
        return True

    def do_GET(self):
        if not self.authorized():
            return
        if self.path != "/health":
            self.reply(404, {"error": "not found"})
            return
        self.reply(200, {"ok": True, "engine": "ema-lightning", "loaded": self.server.runtime.model is not None,
                         "sample_rates": RATES, "format": "pcm_s16le", "channels": 1,
                         "engines": {"ema": "hazir"}})

    def do_POST(self):
        if not self.authorized():
            return
        try:
            self.connection.settimeout(30)
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 24000:
                self.close_connection = True
                raise RequestError("invalid Content-Length")
            raw = self.rfile.read(size)
            if len(raw) != size:
                raise RequestError("incomplete body")
            payload = json.loads(raw)
            if self.path == "/cancel":
                rid = str(uuid.UUID(payload["request_id"]))
                self.reply(200, {"cancelled": self.server.runtime.cancel(rid)})
                return
            if self.path not in ("/speak", "/stream"):
                self.reply(404, {"error": "not found"})
                return
            args = validate(payload)
        except (ValueError, TypeError, KeyError, AttributeError, OSError):
            self.close_connection = True
            self.reply(400, {"error": "invalid request"})
            return
        try:
            req = self.server.runtime.start(args)
        except RequestError:
            self.reply(429, {"error": "EMA busy"})
            return
        sent = False
        try:
            # First failure can still return a normal HTTP error.
            first = self.next_chunk(req)
            if not isinstance(first, bytes):
                raise RuntimeError("no audio")
            rate = args[1]
            if self.path == "/speak":
                pcm = bytearray(first)
                while (chunk := self.next_chunk(req)) is not None:
                    pcm.extend(chunk)
                    if len(pcm) > 48_000 * 2 * 600:
                        raise RuntimeError("audio limit exceeded")
                out = io.BytesIO()
                with wave.open(out, "wb") as wav:
                    wav.setnchannels(1)
                    wav.setsampwidth(2)
                    wav.setframerate(rate)
                    wav.writeframes(pcm)
                data = out.getvalue()
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                sent = True
                self.wfile.write(data)
            else:
                self.send_response(200)
                self.send_header("Content-Type", "audio/pcm")
                self.send_header("Transfer-Encoding", "chunked")
                self.send_header("X-Audio-Sample-Rate", str(rate))
                self.send_header("X-Audio-Channels", "1")
                self.send_header("X-Audio-Format", "pcm_s16le")
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                sent = True
                chunk = first
                while chunk is not None:
                    self.wfile.write(f"{len(chunk):X}\r\n".encode() + chunk + b"\r\n")
                    self.wfile.flush()
                    chunk = self.next_chunk(req)
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
        except (OSError, RuntimeError):
            if not sent:
                self.reply(503, {"error": "EMA unavailable or cancelled"})
            else:
                # An incomplete chunked response is a failure at the client.
                self.close_connection = True
        finally:
            self.server.runtime.finish(req)

    @staticmethod
    def next_chunk(req):
        # Bound cold-start time; cancellation wakes a waiting HTTP handler.
        import time
        until = time.monotonic() + 120
        while not req.cancelled.is_set() and time.monotonic() < until:
            try:
                chunk = req.chunks.get(timeout=0.1)
                if isinstance(chunk, Exception):
                    raise RuntimeError("EMA synthesis failed")
                return chunk
            except queue.Empty:
                pass
        raise RuntimeError("cancelled or timed out")


if __name__ == "__main__":
    EmaServer((os.environ.get("EMA_HOST", "127.0.0.1"), int(os.environ.get("EMA_PORT", "8176"))),
              os.environ.get("EMA_TOKEN", "")).serve_forever()
