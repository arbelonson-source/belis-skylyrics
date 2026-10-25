#!/usr/bin/env python3
"""emit.py FILE TITLE FIRST COUNT : prints chunks FIRST..FIRST+COUNT-1 of FILE as ::notice annotations.
GitHub keeps only ~10 notices per step, so the workflow calls this from several steps."""
import sys, os

path, title, first, count = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
if not os.path.exists(path):
    sys.exit(0)
text = open(path, encoding="utf-8", errors="replace").read()
chunks, cur = [], ""
for line in text.splitlines():
    line = line[:900]
    if len(cur) + len(line) + 1 > 3600:
        chunks.append(cur)
        cur = ""
    cur += line + "\n"
if cur.strip():
    chunks.append(cur)
esc = lambda s: s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
for i in range(first, min(first + count, len(chunks))):
    t = f"{title} {i + 1}/{len(chunks)}".replace(":", "-").replace(",", " ")
    print(f"::notice title={t}::{esc(chunks[i])}")
