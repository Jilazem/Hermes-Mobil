#!/usr/bin/env python3
"""Tur-20 kare farkı: iki CDP ekran görüntüsü arasındaki ortalama piksel deltası.

Bağımlılık YOK (stdlib: zlib+struct). Kabul kapısı: ortalama delta > 0 olmalı
— statik sprite + sayı değişikliği 'kavga' sayılmaz (tur15 MEDIUM bulgusu).

Kullanım: python3 denetim/tur20/frame_diff.py A.png B.png
"""
import struct
import sys
import zlib


def decode_png(path):
    """8-bit RGB/RGBA, non-interlaced PNG → (w, h, bytes RGB)."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "PNG degil"
    pos = 8
    idat = bytearray()
    w = h = depth = color = None
    while pos < len(data):
        (ln,) = struct.unpack(">I", data[pos:pos + 4])
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", chunk[:10])
            assert depth == 8, f"derinlik {depth} desteklenmiyor"
            assert color in (2, 6), f"renk tipi {color}"
        elif typ == b"IDAT":
            idat += chunk
        elif typ == b"IEND":
            break
        pos += 12 + ln
    raw = zlib.decompress(bytes(idat))
    ch = 3 if color == 2 else 4
    stride = w * ch
    out = bytearray(w * h * 3)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ftype == 1:  # Sub
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 0xFF
        elif ftype == 2:  # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:  # Average
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:  # Paeth
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        o = y * w * 3
        for x in range(w):
            if ch == 3:
                out[o + x * 3:o + x * 3 + 3] = line[x * 3:x * 3 + 3]
            else:
                out[o + x * 3:o + x * 3 + 3] = line[x * 4:x * 4 + 3]
        prev = line
    return w, h, out


def main():
    (wa, ha, a), (wb, hb, b) = decode_png(sys.argv[1]), decode_png(sys.argv[2])
    assert (wa, ha) == (wb, hb), f"boyut farki {wa}x{ha} vs {wb}x{hb}"
    total = 0
    changed = 0
    for i in range(0, len(a), 3):
        d = abs(a[i] - b[i]) + abs(a[i + 1] - b[i + 1]) + abs(a[i + 2] - b[i + 2])
        total += d
        if d > 24:
            changed += 1
    n = len(a) // 3
    print(f"kare {wa}x{ha}: ortalama delta/piksel = {total / n / 3:.3f}, degisen piksel orani = {100.0 * changed / n:.2f}%")
    return 0 if total > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
