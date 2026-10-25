#!/usr/bin/env python3
"""port.py DIR : rewrites gradle.properties + build.gradle in a COPY of the repo for an unobfuscated 26.x game.
Reads MC, LOADER, FABRIC_API, MODMENU, SODIUM, IRIS, LOOM_ID, LOOM from the environment."""
import os, re, sys

d = sys.argv[1]
env = os.environ
props = open(f"{d}/gradle.properties").read()
for key, val in [("minecraft_version", env["MC"]), ("loader_version", env["LOADER"]),
                 ("fabric_api_version", env["FABRIC_API"]), ("modmenu_version", env.get("MODMENU", "")),
                 ("sodium_version", env.get("SODIUM", "")), ("iris_version", env.get("IRIS", "")),
                 ("loom_version", env.get("LOOM") or re.search(r"loom_version=(.*)", props).group(1))]:
    props = re.sub(rf"(?m)^{key}=.*$", f"{key}={val}", props)
props = props.replace("org.gradle.configuration-cache=true", "org.gradle.configuration-cache=false")
open(f"{d}/gradle.properties", "w").write(props)

b = open(f"{d}/build.gradle").read()
b = b.replace("id 'net.fabricmc.fabric-loom-remap'", f"id '{env.get('LOOM_ID', 'net.fabricmc.fabric-loom')}'")
if env.get("LOOM_ID", "net.fabricmc.fabric-loom") == "net.fabricmc.fabric-loom":
    b = re.sub(r"(?m)^\s*mappings loom\.officialMojangMappings\(\)\s*$", "", b)
    b = b.replace("modImplementation ", "implementation ").replace("modCompileOnly ", "compileOnly ")
    b = b.replace("modLocalRuntime ", "localRuntime ").replace("from(remapJar)", "from(jar)")
drop = []
if not env.get("MODMENU"):
    drop.append("modmenu_version")
if not env.get("SODIUM"):
    drop.append("sodium_version")
if not env.get("IRIS"):
    drop += ["iris_version", "jcpp", "glsl-transformer", "antlr4"]
b = "\n".join(l for l in b.splitlines() if not any(x in l for x in drop))
b = b.replace("it.options.release = 21", "it.options.release = 25\n\tit.options.compilerArgs += ['-Xmaxerrs', '5000', '-Xmaxwarns', '5000', '-Xlint:deprecation,removal']")
b = b.replace("JavaVersion.VERSION_21", "JavaVersion.VERSION_25")
b += """

// probe: write the client compile classpath so later steps can javap the 26.x classes
tasks.register("probeClasspath") {
	def cp = sourceSets.client.compileClasspath
	def out = layout.buildDirectory.file("probe-cp.txt")
	doLast { out.get().asFile.text = cp.files.join("\\n") }
}
"""
open(f"{d}/build.gradle", "w").write(b)
print(b)
print(props)
if drop:
    print("dropped dependency lines for:", drop)
