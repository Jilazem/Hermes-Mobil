#!/usr/bin/env python3
"""Tur-20: emülatör screencap'tan sahne bölgesini kırpar PNG üretir + iki sahne karesinin
ortalama piksel deltasını ölçer (kabul kapısı: delta > 0, istatistiksek hareket).

Kullanım:
  crop:  python3 denetim/tur20/scene_shot.py crop  <girdi.png> <çıktı.png>
  diff:  python3 denetim/tur20/scene_shot.py diff  <A.png> <B.png>

Sahne bbox: CDP sayfa tanımı (screenX=32, screenY=419, w=1016, h=924 — 1080x2400 AVD).
PIL YOK — stdlib (zlib/struct) PNG codec'i denetim/tur20/frame_diff.py'den.
"""
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from frame_diff import decode_png  # noqa: E402
import struct  # noqa: E402
import zlib  # noqa: E402

X, Y, W, H = 32, 419, 1016, 924  # sahne bölgesi (emülatör 1080x2400)


def encode_png(path, w, h, rgb):
    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        return c + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    raw = bytearray()
    stride = w * 3
    for y in range(h):
        raw.append(0)
        raw += rgb[y * stride:(y + 1) * stride]
    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
    out += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(out)


def main():
    mode = sys.argv[1]
    a_w, a_h, a = decode_png(sys.argv[2])
    if mode == "crop":
        out = bytearray(W * H * 3)
        for row in range(H):
            src = ((Y + row) * a_w + X) * 3
            out[row * W * 3:(row + 1) * W * 3] = a[src:src + W * 3]
        encode_png(sys.argv[3], W, H, out)
        print(sys.argv[3])
    elif mode == "diff":
        b_w, b_h, b = decode_png(sys.argv[3])
        assert (a_w, a_h) == (b_w, b_h)
        # sahne bölgesini kırparak diff
        total = changed = 0
        for row in range(H):
            ra = ((Y + row) * a_w + X) * 3
            rb = ((Y + row) * b_w + X) * 3
            ea = a[ra:ra + W * 3]
            eb = b[rb:rb + W * 3]
            for i in range(0, len(ea), 3):
                d = abs(ea[i] - eb[i]) + abs(ea[i + 1] - eb[i + 1]) + abs(ea[i + 2] - eb[i + 2])
                total += d
                if d > 24:
                    changed += 1
        n = W * H
        print(f"sahne {W}x{H}: ortalama delta/kanal = {total / n / 3:.3f}, degisen piksel = {100.0 * changed / n:.2f}%")
        return 0 if total > 0 else 1


sys.exit(main() or 0)
