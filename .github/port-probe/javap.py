#!/usr/bin/env python3
"""THROWAWAY (port-26x): javap the 26.x classes the mixins and the screen layer touch. javap.py MC OUT"""
import io, json, re, subprocess, sys, tempfile, urllib.request, zipfile, os

mc, out_path = sys.argv[1], sys.argv[2]
UA = {"User-Agent": "arbelonson-source/belis-skylyrics port probe"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()


man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
ver = json.loads(get(next(v["url"] for v in man["versions"] if v["id"] == mc)))
tmp = tempfile.mkdtemp()
client = os.path.join(tmp, "client.jar")
open(client, "wb").write(get(ver["downloads"]["client"]["url"]))
names = zipfile.ZipFile(client).namelist()
lines = []


def javap(jar, cls, pattern=None):
    r = subprocess.run(["javap", "-p", "-cp", jar, cls], capture_output=True, text=True)
    txt = r.stdout or r.stderr
    for l in txt.splitlines():
        if pattern is None or re.search(pattern, l):
            lines.append("  " + l.strip()[:400])


def find(simple):
    return [n[:-6].replace("/", ".") for n in names if n.endswith("/" + simple + ".class")]


lines.append(f"== {mc} LevelRenderer render*")
javap(client, "net.minecraft.client.renderer.LevelRenderer", r"render|Matrix|Camera")
for simple in ["CameraRenderState", "LevelRenderState"]:
    for c in find(simple):
        lines.append(f"== {c}")
        javap(client, c, r"Matrix|Vec|Quat|projection|Camera|Frustum|pos|public")
lines.append("== GameRenderer matrices / camera")
javap(client, "net.minecraft.client.renderer.GameRenderer", r"Matrix|[Pp]rojection|Camera|render|mainRenderTarget")
lines.append("== FontTexture / AbstractTexture")
javap(client, "net.minecraft.client.gui.font.FontTexture", r"FontTexture\(")
javap(client, "net.minecraft.client.renderer.texture.AbstractTexture", r"sampler|Sampler")
lines.append("== BossHealthOverlay")
javap(client, "net.minecraft.client.gui.components.BossHealthOverlay", r"Map|events")
for c in find("RenderSystem") + find("SamplerCache"):
    lines.append(f"== {c}")
    javap(client, c, r"[Ss]ampler")
lines.append("== PoseStack")
javap(client, "com.mojang.blaze3d.vertex.PoseStack", r"public")

# Fabric API: the level render context
fabric = {"26.1": "0.155.3+26.1.2", "26.2": "0.161.0+26.2", "26.3": "0.165.0+26.3"}.get(mc)
if fabric:
    bundle = zipfile.ZipFile(io.BytesIO(get(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{fabric}/fabric-api-{fabric}.jar")))
    for n in bundle.namelist():
        if "fabric-rendering-v1" in n and n.endswith(".jar"):
            p = os.path.join(tmp, "rendering.jar")
            open(p, "wb").write(bundle.read(n))
            inner = zipfile.ZipFile(p).namelist()
            for c in [x[:-6].replace("/", ".") for x in inner if re.search(r"/(LevelRenderContext|LevelRenderEvents|WorldRenderContext)\.class$", x)]:
                lines.append(f"== {c}")
                javap(p, c)
            fmj = json.loads(zipfile.ZipFile(p).read("fabric.mod.json"))
            lines.append(f"rendering depends: {fmj.get('depends')}")
    fmj = json.loads(bundle.read("fabric.mod.json"))
    lines.append(f"fabric-api depends: {fmj.get('depends')}")

if mc == "26.1":
    pom = get("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.pom").decode()
    m = re.search(r"<artifactId>sponge-mixin</artifactId>\s*<version>([^<]+)</version>", pom)
    lines.append(f"sponge-mixin for loader 0.20.1: {m.group(1) if m else pom[:600]}")
    if m:
        mj = os.path.join(tmp, "mixin.jar")
        open(mj, "wb").write(get(f"https://maven.fabricmc.net/net/fabricmc/sponge-mixin/{m.group(1)}/sponge-mixin-{m.group(1)}.jar"))
        javap(mj, "org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel", r"JAVA_")

open(out_path, "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
