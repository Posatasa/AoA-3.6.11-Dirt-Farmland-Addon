"""Builds the mod jar using only what is already on this machine.

The normal way to build this mod is `gradlew build`, which needs a JDK 8, a Gradle
distribution and a pile of ForgeGradle dependencies downloaded from the internet. This script is
the offline fallback: it compiles against the ForgeGradle caches (the MCP-named Forge jar and
the SRG mapping file), remaps the result to SRG with tools/remap.py, and packages the jar. The
result is equivalent to what `gradlew build` puts in dist/.

Usage:  python tools/build.py [--aoa-jar PATH]
"""
import argparse
import glob
import io
import json
import os
import shutil
import subprocess
import sys
import time
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import remap  # noqa: E402  (local module)

GRADLE_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
MCP_JAR = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "net", "minecraftforge",
    "forge", "1.16.5-36.2.34_mapped_official_1.16.5",
    "forge-1.16.5-36.2.34_mapped_official_1.16.5.jar")
MAPPINGS = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "de", "oceanlabs", "mcp",
    "mcp_config", "1.16.5-20210115.111550", "srg_to_official_1.16.5.tsrg")
SRG_JAR = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "net", "minecraftforge",
    "forge", "1.16.5-36.2.34", "forge-1.16.5-36.2.34-srg.jar")
JDK8 = r"C:\Program Files\Java\jdk-1.8"

MOD_ID = "aoa3farmland"


def read_properties():
    """gradle.properties is the single source of truth for the version and the mod metadata,
    so the Gradle build and this script can never disagree."""
    props = {}
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


VERSION = read_properties()["mod_version"]


def find_aoa_jar(explicit):
    if explicit:
        return explicit
    patterns = [
        os.path.join(ROOT, "libs", "*.jar"),
        os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "*",
                     "mods", "AoA3*.jar"),
        os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "*",
                     "mods", "aoa3*.jar"),
        os.path.join(os.path.expanduser("~"), "AppData", "Roaming", ".minecraft", "mods",
                     "AoA3*.jar"),
    ]
    for pattern in patterns:
        for path in sorted(glob.glob(pattern)):
            return path
    return None


def javac_sources(source_dir, out_dir, classpath):
    javac = os.path.join(JDK8, "bin", "javac.exe")
    if not os.path.isfile(javac):
        javac = "javac"
    sources = []
    for root, _, files in os.walk(source_dir):
        sources += [os.path.join(root, f) for f in files if f.endswith(".java")]
    os.makedirs(out_dir, exist_ok=True)
    command = [javac, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
               "-nowarn", "-cp", classpath, "-d", out_dir] + sources
    print("compiling %d sources" % len(sources))
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        raise SystemExit("javac failed")
    if result.stderr.strip():
        print(result.stderr.strip()[:4000])
    return len(sources)


def expand_mods_toml(resources_dir):
    """Handles the ${version} style placeholders Gradle's processResources would fill in."""
    props = read_properties()
    path = os.path.join(resources_dir, "META-INF", "mods.toml")
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    for key, value in props.items():
        text = text.replace("${%s}" % key, value)
    text = text.replace("${version}", props.get("mod_version", VERSION))
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    if "${" in text:
        raise SystemExit("unexpanded placeholder remains in mods.toml")
    return props


def cached_jars():
    """Every jar in the ForgeGradle dependency cache that is not sources or javadoc.

    The MCP-named Forge jar only contains Minecraft and Forge; Forge's own compile
    dependencies (the event bus, FML's loader types, ModLauncher, jsr305, ...) live in the same
    cache as separate artifacts, so they all go on the classpath together.
    """
    root = os.path.join(GRADLE_HOME, "caches", "modules-2", "files-2.1")
    jars = []
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if not name.endswith(".jar"):
                continue
            if "sources" in name or "javadoc" in name:
                continue
            jars.append(os.path.join(dirpath, name))
    return sorted(jars)


def build(staging, out_jar):
    classpath_parts = [MCP_JAR] + cached_jars()
    aoa = find_aoa_jar(args.aoa_jar)
    if aoa:
        classpath_parts.append(aoa)
        print("using AoA jar for compile-time checks:", aoa)
    else:
        print("no AoA jar found; compiling without it (not needed, only used for verification)")
    classpath = os.pathsep.join(classpath_parts)
    print("classpath: %d jars" % len(classpath_parts))

    classes = os.path.join(staging, "classes-mcp")
    remapped = os.path.join(staging, "classes")
    javac_sources(os.path.join(ROOT, "src", "main", "java"), classes, classpath)

    mappings = remap.Mappings(MAPPINGS, MCP_JAR)
    print("remapping MCP -> SRG (%d classes, %d methods, %d class hierarchies)"
          % (len(mappings.classes), len(mappings.methods),
             len(mappings.hierarchy.supers) if mappings.hierarchy else 0))
    remap.remap_tree(classes, remapped, mappings, verbose=True)

    # A wrong mapping often shows up as a call the verifier rejects, not as a broken class file,
    # so every reference the remapped classes make is checked against the SRG jar's own members.
    import verify_refs
    checked, problems = verify_refs.check(remapped, SRG_JAR, cached_jars())
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("remapped classes reference %d member(s) that do not exist in the SRG "
                         "jar; refusing to package" % len(problems))
    print("verified %d member references against the SRG jar" % checked)

    # The mod must not contain or depend on any AoA type - that is the whole licensing story.
    for root, _, files in os.walk(remapped):
        for f in files:
            if not f.endswith(".class"):
                continue
            with open(os.path.join(root, f), "rb") as fh:
                data = fh.read()
            if b"tslat" in data or b"net/minecraft/aoa" in data:
                raise SystemExit("%s references an AoA class" % os.path.join(root, f))
    print("no AoA classes referenced by any built class")

    resources = os.path.join(staging, "resources")
    shutil.copytree(os.path.join(ROOT, "src", "main", "resources"), resources)
    props = expand_mods_toml(resources)

    manifest = (
        "Manifest-Version: 1.0\n"
        "Specification-Title: %s\n"
        "Specification-Vendor: %s\n"
        "Specification-Version: 1\n"
        "Implementation-Title: %s\n"
        "Implementation-Version: %s\n"
        "Implementation-Vendor: %s\n"
        "\n" % (MOD_ID, props["mod_authors"], MOD_ID, VERSION, props["mod_authors"])
    ).replace("\n", "\r\n")

    entries = []
    for root, _, files in os.walk(remapped):
        for f in files:
            full = os.path.join(root, f)
            entries.append((os.path.relpath(full, remapped).replace(os.sep, "/"), full))
    for root, _, files in os.walk(resources):
        for f in files:
            full = os.path.join(root, f)
            entries.append((os.path.relpath(full, resources).replace(os.sep, "/"), full))
    entries.sort()

    os.makedirs(os.path.dirname(out_jar), exist_ok=True)
    # Fixed timestamps (the ZIP epoch, 1980-01-01) and a fixed entry order, so repeated builds
    # from unchanged sources produce byte-identical jars.
    stamp = (1980, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as jar:
        for name, path in entries:
            info = zipfile.ZipInfo(name, stamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            with open(path, "rb") as fh:
                jar.writestr(info, fh.read())
        info = zipfile.ZipInfo("META-INF/MANIFEST.MF", stamp)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        jar.writestr(info, manifest)
    print("wrote %s (%d entries, %d bytes)" % (out_jar, len(entries) + 1,
                                               os.path.getsize(out_jar)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--aoa-jar", default=None)
    args = parser.parse_args()

    for path, what in ((MCP_JAR, "MCP-named Forge jar"), (MAPPINGS, "SRG mappings"),
                       (SRG_JAR, "SRG-named Forge jar")):
        if not os.path.isfile(path):
            raise SystemExit("missing %s: %s" % (what, path))

    staging = os.path.join(ROOT, "build", "script")
    if os.path.isdir(staging):
        shutil.rmtree(staging)
    os.makedirs(staging)

    jar_path = os.path.join(ROOT, "dist", "%s-%s.jar" % (MOD_ID, VERSION))
    build(staging, jar_path)

    import hashlib
    digest = hashlib.sha256(open(jar_path, "rb").read()).hexdigest()
    sums = os.path.join(ROOT, "dist", "SHA256SUMS.txt")
    with open(sums, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("%s  %s\n" % (digest, os.path.basename(jar_path)))
    print("sha256 %s" % digest)
