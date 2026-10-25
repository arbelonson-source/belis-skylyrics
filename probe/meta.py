#!/usr/bin/env python3
"""Throwaway probe: looks up the Fabric / Mojang / Modrinth versions for Minecraft 26.x.

  meta.py report OUT.txt      -> writes a human-readable report
  meta.py resolve MC          -> prints KEY=VALUE lines (for $GITHUB_ENV) for one game version
"""
import json, sys, re, urllib.request, urllib.parse, xml.etree.ElementTree as ET, zipfile, io

UA = {"User-Agent": "arbelonson-source/belis-skylyrics probe (github actions)"}
NAMED = ["26.1", "26.1.1", "26.1.2", "26.2", "26.3"]


def get(url, raw=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    return data if raw else data.decode("utf-8")


def getj(url):
    return json.loads(get(url))


def maven_versions(url):
    try:
        root = ET.fromstring(get(url))
    except Exception as e:
        return None, None, None, str(e)
    v = root.find("versioning")
    return ([x.text for x in v.find("versions")], v.findtext("latest"), v.findtext("release"), None)


def modrinth(project, mc):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    try:
        vs = getj(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    except Exception as e:
        return [], str(e)
    return vs, None


def pick(vs):
    rel = [v for v in vs if v.get("version_type") == "release"]
    return (rel or vs or [None])[0]


def resolve(mc):
    out = {"MC": mc}
    loaders = getj("https://meta.fabricmc.net/v2/versions/loader")
    out["LOADER"] = next(l["version"] for l in loaders if l.get("stable"))
    for key, proj in [("FABRIC_API", "fabric-api"), ("MODMENU", "modmenu"), ("SODIUM", "sodium"), ("IRIS", "iris")]:
        vs, _ = modrinth(proj, mc)
        p = pick(vs)
        out[key] = p["version_number"] if p else ""
    if not out["FABRIC_API"]:
        vers, *_ = maven_versions("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
        m = [v for v in (vers or []) if v.endswith("+" + mc)]
        out["FABRIC_API"] = m[-1] if m else ""
    return out


def report(path):
    lines = []
    P = lines.append

    games = getj("https://meta.fabricmc.net/v2/versions/game")
    g26 = [g for g in games if g["version"].startswith("26")]
    P("== meta.fabricmc.net game versions starting with 26 (newest first) ==")
    P(", ".join(f"{g['version']}{'' if g['stable'] else '(snap)'}" for g in g26))
    stable26 = [g["version"] for g in g26 if g["stable"]]
    P("stable 26.x: " + ", ".join(stable26))
    P("newest 1.21.x: " + ", ".join(g["version"] for g in games if g["version"].startswith("1.21") and g["stable"])[:200])

    loaders = getj("https://meta.fabricmc.net/v2/versions/loader")
    P("== fabric loader ==")
    P("newest 8: " + ", ".join(f"{l['version']}{'' if l.get('stable') else '(unstable)'}" for l in loaders[:8]))
    for mc in NAMED[:1] + NAMED[-1:]:
        try:
            lv = getj(f"https://meta.fabricmc.net/v2/versions/loader/{mc}")
            P(f"loader/{mc}: {len(lv)} entries; first: {json.dumps(lv[0]) [:600] if lv else 'none'}")
        except Exception as e:
            P(f"loader/{mc}: {e}")
        for kind in ["intermediary", "yarn"]:
            try:
                x = getj(f"https://meta.fabricmc.net/v2/versions/{kind}/{mc}")
                P(f"{kind}/{mc}: {[i.get('version') for i in x][:5]}")
            except Exception as e:
                P(f"{kind}/{mc}: {e}")

    P("== fabric loom (maven.fabricmc.net) ==")
    for name, url in [
        ("net.fabricmc:fabric-loom", "https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml"),
        ("plugin marker net.fabricmc.fabric-loom", "https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml"),
        ("plugin marker net.fabricmc.fabric-loom-remap", "https://maven.fabricmc.net/net/fabricmc/fabric-loom-remap/net.fabricmc.fabric-loom-remap.gradle.plugin/maven-metadata.xml"),
        ("plugin marker fabric-loom (legacy id)", "https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/maven-metadata.xml"),
    ]:
        vers, latest, rel, err = maven_versions(url)
        if err:
            P(f"{name}: ERROR {err}")
        else:
            P(f"{name}: latest={latest} release={rel} last15={vers[-15:]}")

    P("== fabric-api maven versions per 26.x ==")
    vers, latest, rel, err = maven_versions("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
    suffixes = {}
    for v in vers or []:
        if "+26" in v:
            suffixes.setdefault(v.split("+", 1)[1], []).append(v)
    for s, vs in suffixes.items():
        P(f"+{s}: {len(vs)} versions, newest {vs[-1]}")

    P("== modmenu (maven.terraformersmc.com) ==")
    vers, latest, rel, err = maven_versions("https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/maven-metadata.xml")
    P(f"latest={latest} release={rel} last12={(vers or [])[-12:]} {err or ''}")

    P("== Modrinth (loader=fabric) per game version: newest release (all newest 4) ==")
    for mc in NAMED + [g for g in stable26 if g not in NAMED]:
        for proj in ["fabric-api", "modmenu", "sodium", "iris"]:
            vs, err = modrinth(proj, mc)
            p = pick(vs)
            P(f"{mc} {proj}: {p['version_number'] if p else 'NONE'} [{', '.join(v['version_number'] + '/' + v['version_type'][0] for v in vs[:4])}] {err or ''}")
        p = pick(modrinth("sodium", mc)[0])
        if p:
            P(f"   sodium deps: {[(d.get('project_id'), d.get('version_id'), d['dependency_type']) for d in p.get('dependencies', [])]} game_versions={p.get('game_versions')}")

    P("== piston-meta (Mojang) ==")
    man = getj("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    P(f"latest: {man['latest']}")
    ids = [v for v in man["versions"] if v["id"].startswith("26")]
    P("26.x ids: " + ", ".join(f"{v['id']}({v['type'][0]})" for v in ids))
    want = set(NAMED) | {v["id"] for v in ids[:3]} | {"1.21.11"}
    for v in [v for v in man["versions"] if v["id"] in want]:
        j = getj(v["url"])
        dl = j.get("downloads", {})
        P(f"{v['id']}: java={j.get('javaVersion')} downloads={sorted(dl)} mainClass={j.get('mainClass')} released={j.get('releaseTime')}")
    for mc in ["26.1", "26.3"]:
        v = next((v for v in man["versions"] if v["id"] == mc), None)
        if not v:
            P(f"{mc}: not in manifest")
            continue
        j = getj(v["url"])
        z = zipfile.ZipFile(io.BytesIO(get(j["downloads"]["client"]["url"], raw=True)))
        names = [n for n in z.namelist() if n.endswith(".class")]
        named = [n for n in names if n.startswith("net/minecraft/client/Minecraft.class") or n == "com/mojang/blaze3d/vertex/PoseStack.class"]
        short = [n for n in names if re.fullmatch(r"[a-z]{1,3}\.class", n)]
        ver = z.read("net/minecraft/client/Minecraft.class")[6:8] if "net/minecraft/client/Minecraft.class" in names else b"\0\0"
        P(f"{mc} client jar: {len(names)} classes, named hits={named}, obfuscated-looking top-level={len(short)}, Minecraft.class major={int.from_bytes(ver,'big')}, sample={names[:5]}")

    open(path, "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    if sys.argv[1] == "report":
        report(sys.argv[2])
    else:
        for k, v in resolve(sys.argv[2]).items():
            print(f"{k}={v}")
