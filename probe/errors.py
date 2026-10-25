#!/usr/bin/env python3
"""errors.py LOG OUT : condenses javac / gradle output into a grouped list (file -> line: message)."""
import sys, re, collections

log = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
head = re.compile(r"^(/\S+?\.java):(\d+): (error|warning): (.*)$")
errs = []
seen = set()
i = 0
while i < len(log):
    m = head.match(log[i])
    if not m:
        i += 1
        continue
    path, line, kind, msg = m.groups()
    extra = []
    j = i + 1
    while j < len(log) and not head.match(log[j]) and (log[j].startswith(" ") or log[j].startswith("\t") or (j == i + 1)):
        extra.append(log[j])
        j += 1
        if len(extra) > 12:
            break
    # drop the caret line; the line before it is the echoed source line
    src, info = "", []
    for k, e in enumerate(extra):
        if re.fullmatch(r"\s*\^\s*", e):
            if k > 0:
                src = extra[k - 1].strip()
            continue
    for k, e in enumerate(extra):
        if re.fullmatch(r"\s*\^\s*", e) or (e.strip() == src and src):
            continue
        info.append(e.strip())
    rel = path.split("/src/", 1)[-1]
    if (rel, line, msg) not in seen:  # gradle prints javac output twice
        seen.add((rel, line, msg))
        errs.append((rel, int(line), kind, msg, info, src))
    i = j

out = []
E = [e for e in errs if e[2] == "error"]
W = [e for e in errs if e[2] == "warning" and "deprecat" in e[3] or e[2] == "warning" and "removal" in e[3]]
out.append(f"TOTAL javac errors: {len(E)} in {len(set(e[0] for e in E))} files; deprecation/removal warnings: {len(W)}")
sym = collections.Counter()
for rel, line, kind, msg, info, src in E:
    s = next((x for x in info if x.startswith("symbol:")), None)
    loc = next((x for x in info if x.startswith("location:")), "")
    if s:
        sym[f"{s}  @{loc.replace('location: ', '')}"] += 1
    elif msg.startswith("package") or "does not exist" in msg or "cannot" in msg or "does not override" in msg:
        sym[msg] += 1
out.append("== unique missing symbols / packages (count) ==")
for k, n in sym.most_common():
    out.append(f"{n}x {k}")
out.append("== errors by file ==")
byfile = collections.OrderedDict()
for e in E + W:
    byfile.setdefault(e[0], []).append(e)
for f, es in byfile.items():
    out.append(f"## {f} ({len(es)})")
    for rel, line, kind, msg, info, src in es:
        bits = " | ".join(x for x in info if x)
        w = "WARN " if kind == "warning" else ""
        out.append(f"  {w}L{line}: {msg}" + (f" [{bits}]" if bits else "") + (f"  `{src[:140]}`" if src else ""))
# gradle failures that are not javac errors
if not E:
    ww = [k for k, l in enumerate(log) if "What went wrong" in l]
    for k in ww:
        out.append("== gradle: What went wrong ==")
        out.extend(log[k:k + 25])
    if not ww:
        out.append("(no javac errors and no gradle failure block found)")
open(sys.argv[2], "w").write("\n".join(out) + "\n")
print("\n".join(out))
