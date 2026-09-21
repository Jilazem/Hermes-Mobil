#!/usr/bin/env python3
# Tur18 toplu donusum: fontSize literallerini MaterialTheme.typography rollerine,
# RoundedCornerShape(N) literallerini MaterialTheme.shapes tokenlarina cevirir.
# FR-002/FR-003 — tema dizini (theme/) KAPSAM DISI (oradaki tanim satirlaridir).
import re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/hermes/mobile"

# FR-001/FR-002 — punto -> tipografi rolu
FONT = {
    9:  "labelSmall",
    10: "labelSmall",
    11: "labelSmall",
    12: "bodySmall",    # caption
    13: "bodyMedium",   # body
    14: "bodyMedium",
    15: "bodyLarge",    # prose
    16: "bodyLarge",
    17: "titleMedium",  # title
    18: "titleMedium",
    20: "titleMedium",
    30: "headlineSmall",  # display
}
# FR-003 — radius -> Material Shapes (HermesShapes: extraSmall=sm4, medium=md10, large=lg16)
RADIUS = {
    2: "extraSmall", 4: "extraSmall", 6: "extraSmall",
    8: "medium", 9: "medium", 10: "medium", 11: "medium", 12: "medium", 13: "medium",
    14: "large", 16: "large", 18: "large", 20: "large", 22: "large", 24: "large", 28: "large",
}

MT_IMPORT = "import androidx.compose.material3.MaterialTheme"

def ensure_mt_import(text: str) -> str:
    lines = text.split("\n")
    if any(l.strip() == MT_IMPORT for l in lines):
        return text
    idxs = [i for i, l in enumerate(lines) if l.startswith("import androidx.compose.material3.")]
    if idxs:
        lines.insert(idxs[-1] + 1, MT_IMPORT)
    else:
        pkg = next(i for i, l in enumerate(lines) if l.startswith("package "))
        imps = [i for i, l in enumerate(lines) if l.startswith("import ")]
        lines.insert((imps[-1] + 1) if imps else pkg + 2, MT_IMPORT)
    return "\n".join(lines)

def main():
    counts = {"f": 0, "r": 0}
    changed = []
    for p in sorted(ROOT.rglob("*.kt")):
        if "/theme/" in str(p):
            continue
        text = p.read_text()
        orig = text

        def fsub(m):
            n = int(m.group(1))
            role = FONT.get(n)
            if role is None:
                return m.group(0)
            counts["f"] += 1
            return "style = MaterialTheme.typography.%s" % role

        def rsub(m):
            n = int(m.group(1))
            tok = RADIUS.get(n)
            if tok is None:
                return m.group(0)
            counts["r"] += 1
            return "MaterialTheme.shapes.%s" % tok

        text = re.sub(r"fontSize = (\d+)\.sp", fsub, text)
        text = re.sub(r"RoundedCornerShape\((\d+)\.dp\)", rsub, text)
        if text != orig:
            text = ensure_mt_import(text)
            p.write_text(text)
            changed.append(p.name)
    print("fontSize donusumu:", counts["f"], "| radius donusumu:", counts["r"], "| dosya:", len(changed))
    for c in changed:
        print(" ", c)

if __name__ == "__main__":
    main()
