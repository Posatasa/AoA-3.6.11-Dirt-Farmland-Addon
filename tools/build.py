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
               # The mixin annotation processor would try to generate a refmap of its own and
               # needs a mapping service this build does not provide; the refmap is committed and
               # verified by verify_refmap() instead.
               "-proc:none",
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


def _declaring_ancestor(mappings, supertypes, name, params):
    """The ancestor that declares this member, decided from the class hierarchy alone.

    Deliberately not using Mappings.map_declared_method: a check that asks the rename code what it
    thinks it renamed cannot detect the rename code being wrong.
    """
    hierarchy = mappings.hierarchy
    if hierarchy is None:
        return None
    seen = set()
    stack = [s for s in supertypes if s]
    while stack:
        current = stack.pop(0)
        if current in seen:
            continue
        seen.add(current)
        if (name, params) in hierarchy.members.get(current, ()):
            return current
        if current in hierarchy.supers:
            stack.append(hierarchy.supers[current])
        stack.extend(hierarchy.interfaces.get(current, ()))
    return None


def verify_overrides(staging, mappings):
    """Checks that every overridden method carries its SRG name after remapping.

    This is the failure mode that shipped twice: a method that overrides a vanilla method is
    declared in our class and referenced nowhere, so nothing in the constant pool forces the
    remapper to rename it. Left alone, it silently stops overriding - and the JVM only complains
    later, as `AbstractMethodError`, when something calls the abstract method (opening the creative
    inventory, in the case that motivated this check).

    "Is this an override?" is answered from the class hierarchy, and the expected name comes
    straight from the mapping tables, so disabling the rename makes this check fail.
    """
    mcp_dir = os.path.join(staging, "classes-mcp")
    srg_dir = os.path.join(staging, "classes")
    problems = []
    checked = 0
    for root, _, files in os.walk(mcp_dir):
        for filename in files:
            if not filename.endswith(".class"):
                continue
            path = os.path.join(root, filename)
            rel = os.path.relpath(path, mcp_dir)
            with open(path, "rb") as fh:
                before = remap.ClassFile(fh.read())
            with open(os.path.join(srg_dir, rel), "rb") as fh:
                after = remap.ClassFile(fh.read())
            declared_after = after.declared_members()
            supertypes = [before.super_name] + before.interface_names()
            for kind, _, name_index, descriptor_index in before.declared_slots():
                name = before.utf8(name_index)
                descriptor = before.utf8(descriptor_index)
                if kind == "method":
                    params = remap.Mappings.params(descriptor)
                    ancestor = _declaring_ancestor(mappings, supertypes, name, params)
                    table = mappings.methods
                else:
                    params = None
                    ancestor = _declaring_ancestor(mappings, supertypes, name, None)
                    table = mappings.fields
                if ancestor is None:
                    continue                       # not an override: keeps its own name
                entry = table.get((ancestor, name, params))
                if entry is None:
                    continue                       # a Forge-added member; never remapped
                srg = entry[0] if kind == "method" else entry
                if srg == name:
                    continue
                checked += 1
                if (srg, params) not in declared_after:
                    problems.append("%s declares %s, which overrides %s.%s and must be %s"
                                    % (before.this_name(), name, ancestor, name, srg))
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("%d override(s) would not override anything in the built jar; refusing "
                         "to package" % len(problems))
    print("verified %d overridden member(s) carry their SRG names" % checked)


def verify_block_properties(staging):
    """Checks that the farmland blocks are built with vanilla farmland's properties.

    This exists because of a silent failure that shipped: the blocks were created without
    `randomTicks()`, so they never received random ticks, `FarmlandBlock#randomTick` never ran,
    moisture could never change, and every block stayed bone dry forever - no water nearby made any
    difference, and nothing logged an error. A class-file check cannot see that, so the required
    property chain is asserted here instead.

    The method references are read from the *pre-remap* class, where the builders still carry their
    MCP names.
    """
    REQUIRED = ("randomTicks", "strength", "sound", "isSuffocating", "isViewBlocking")
    path = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoa3farmland",
                        "FarmlandBlocks.class")
    with open(path, "rb") as fh:
        cls = remap.ClassFile(fh.read())

    owner_wanted = "net/minecraft/block/AbstractBlock$Properties"
    found = set()
    for i in range(1, cls.count):
        entry = cls.entries[i]
        if entry is None or entry[0] != remap.CONSTANT_METHODREF:
            continue
        payload = entry[1]
        owner = cls.utf8(cls.u2(cls.entries[cls.u2(payload)][1]))
        if owner != owner_wanted:
            continue
        nat_payload = cls.entries[cls.u2(payload, 2)][1]
        found.add(cls.utf8(cls.u2(nat_payload)))

    missing = [name for name in REQUIRED if name not in found]
    if missing:
        raise SystemExit("farmland is missing vanilla farmland's properties: %s. Without "
                         "randomTicks() the block never ticks, so its moisture can never change "
                         "and it always looks bone dry." % ", ".join(missing))
    print("farmland properties include vanilla's own chain (%s)" % ", ".join(REQUIRED))


def verify_turn_to_soil_paths(staging):
    """Checks that all three ways farmland can be destroyed are overridden.

    Vanilla turns farmland into dirt from three places - trampling (`fallOn`), a block being placed
    on top (`tick`), and drying out (`randomTick`) - and it does so through a *static* helper that
    names `Blocks.DIRT` directly. Static calls have no virtual dispatch, so each of those three
    methods has to be overridden for the block to become its own soil. Missing one is silent: the
    block simply reverts to vanilla dirt on that particular path, which is exactly the bug this
    check exists for.
    """
    REQUIRED = ("fallOn", "tick", "randomTick")
    path = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoa3farmland", "block",
                        "AoAFarmlandBlock.class")
    with open(path, "rb") as fh:
        cls = remap.ClassFile(fh.read())
    declared = {name for name, _ in cls.declared_members()}

    missing = [name for name in REQUIRED if name not in declared]
    if missing:
        raise SystemExit("AoAFarmlandBlock does not override %s; vanilla would turn these blocks "
                         "into minecraft:dirt on that path instead of their AoA soil."
                         % ", ".join(missing))
    print("block reverts to its soil on all three vanilla paths (%s)" % ", ".join(REQUIRED))


def verify_refmap(mappings, tsrg_path, srg_jar):
    """Checks that every name in the committed mixin refmap is the right SRG name.

    A mixin names its target method with the *source* name and relies on the refmap to find it in
    production. If an entry is wrong the failure happens while the class is being transformed, which
    means the game dies at startup - the least forgiving place to be wrong. So each entry is checked
    twice: against the TSRG tables, and against the method that actually exists in the SRG jar.
    """
    import verify_refs
    config_path = os.path.join(ROOT, "src", "main", "resources", "aoa3farmland.mixins.json")
    refmap_path = os.path.join(ROOT, "src", "main", "resources", "aoa3farmland.refmap.json")
    with open(config_path, encoding="utf-8") as fh:
        config = json.load(fh)
    with open(refmap_path, encoding="utf-8") as fh:
        refmap = json.load(fh)

    package = config["package"].replace(".", "/")
    refmap_name = config["refmap"]
    if os.path.basename(refmap_path) != refmap_name:
        raise SystemExit("mixin config references %s but the refmap is %s"
                         % (refmap_name, os.path.basename(refmap_path)))
    for mixin in config.get("mixins", []) + config.get("client", []):
        expected = os.path.join(ROOT, "build", "script", "classes", package, mixin + ".class")
        if not os.path.isfile(expected):
            raise SystemExit("mixin %s is listed in the config but was not compiled" % mixin)

    # The methods these mixins inject into, per target class.
    TARGETS = {
        "mayPlaceOn": (
            ["net/minecraft/block/CropsBlock", "net/minecraft/block/StemBlock",
             "net/minecraft/block/AttachedStemBlock"],
            "(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IBlockReader;"
            "Lnet/minecraft/util/math/BlockPos;)Z"),
        "destroy": (
            ["net/minecraft/block/Block"],
            "(Lnet/minecraft/world/IWorld;Lnet/minecraft/util/math/BlockPos;"
            "Lnet/minecraft/block/BlockState;)V"),
    }

    srg = verify_refs.load_srg(srg_jar)
    problems = []
    for mixin_class, members in refmap.get("mappings", {}).items():
        for source_name, srg_name in members.items():
            if source_name not in TARGETS:
                problems.append("refmap names %s, which this build does not know how to check"
                                % source_name)
                continue
            owners, descriptor = TARGETS[source_name]
            params = remap.Mappings.params(descriptor)
            for owner in owners:
                entry = mappings.methods.get((owner, source_name, params))
                if entry is None:
                    problems.append("TSRG has no %s.%s%s" % (owner, source_name, descriptor))
                elif entry[0] != srg_name:
                    problems.append("refmap says %s for %s.%s but TSRG says %s"
                                    % (srg_name, owner, source_name, entry[0]))
                info = srg.get(owner)
                if info is None:
                    problems.append("SRG jar has no class %s" % owner)
                elif descriptor not in info.methods.get(srg_name, ()):
                    problems.append("SRG jar has no %s.%s%s" % (owner, srg_name, descriptor))
    for env, table in refmap.get("data", {}).items():
        for mixin_class, members in table.items():
            for source_name, mapped in members.items():
                if env == "searge" and mapped != refmap["mappings"].get(mixin_class, {}).get(source_name):
                    problems.append("refmap data.searge disagrees with mappings for %s" % source_name)
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("mixin refmap does not match the game's actual names; refusing to package")
    print("mixin refmap verified against the TSRG tables and the SRG jar")


def verify_resources(resources_dir, aoa_jar):
    """Checks that every blockstate, model, texture and translation key actually resolves.

    The resources are generated, and there are a lot of them (one model and one texture per soil
    per moisture level), so a typo in the generator would otherwise only show up as a missing model
    in game. References into AoA's namespace are checked against the AoA jar when one was found.
    """
    assets_root = os.path.join(resources_dir, "assets", "aoa3farmland")
    ours = set()
    for root, _, files in os.walk(assets_root):
        for filename in files:
            ours.add(os.path.relpath(os.path.join(root, filename), assets_root).replace(os.sep, "/"))

    aoa_assets = set()
    if aoa_jar and os.path.isfile(aoa_jar):
        with zipfile.ZipFile(aoa_jar) as jar:
            for entry in jar.namelist():
                if entry.startswith("assets/aoa3/"):
                    aoa_assets.add(entry[len("assets/aoa3/"):])

    problems = []

    def resolves(reference, kind):
        """Resolves "namespace:path" to a file under assets/<ns>/<kind>/ — `kind` is the asset
        root ("models" or "textures"), and the reference path already carries its subfolder."""
        ns, _, path = reference.partition(":")
        if ns == "aoa3farmland":
            wanted = "%s/%s%s" % (kind, path, _extension(kind))
            return wanted in ours, wanted
        if ns == "aoa3":
            wanted = "%s/%s%s" % (kind, path, _extension(kind))
            if not aoa_assets:
                return True, wanted          # AoA jar unavailable: cannot check
            return wanted in aoa_assets, wanted
        return True, path                    # vanilla or another mod: out of scope

    def _extension(kind):
        return ".png" if kind == "textures" else ".json"

    # blockstates -> models
    for entry in sorted(ours):
        if not entry.startswith("blockstates/"):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            data = json.load(fh)
        for key, variant in data.get("variants", {}).items():
            model = variant["model"] if isinstance(variant, dict) else variant[0]["model"]
            ok, wanted = resolves(model, "models")
            if not ok:
                problems.append("%s: %s -> missing %s" % (entry, key, wanted))

    # block and item models -> parents and textures
    for entry in sorted(ours):
        if not entry.startswith("models/"):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            data = json.load(fh)
        parent = data.get("parent")
        if parent and parent.partition(":")[0] == "aoa3farmland":
            ok, wanted = resolves(parent, "models")
            if not ok:
                problems.append("%s: parent -> missing %s" % (entry, wanted))
        for slot, texture in data.get("textures", {}).items():
            ok, wanted = resolves(texture, "textures")
            if not ok:
                problems.append("%s: texture '%s' -> missing %s" % (entry, slot, wanted))

    # every block needs a name in every language file
    lang_dir = os.path.join(assets_root, "lang")
    languages = {}
    for entry in sorted(ours):
        if entry.startswith("lang/") and entry.endswith(".json"):
            with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
                languages[entry] = json.load(fh)
    if not languages:
        problems.append("no language files")
    blocks = [e[len("blockstates/"):-len(".json")] for e in sorted(ours)
              if e.startswith("blockstates/")]
    for language, table in languages.items():
        if "itemGroup.aoa3farmland" not in table:
            problems.append("%s: missing itemGroup.aoa3farmland" % language)
        for block in blocks:
            if "block.aoa3farmland." + block not in table:
                problems.append("%s: missing name for %s" % (language, block))

    # The fertilised blocks carry AoA's extra `well_fertilised` state, so their blockstates must
    # cover both values at every moisture level. A missing variant shows up in game as an
    # invisible block, with no error anywhere.
    for entry in sorted(ours):
        if not (entry.startswith("blockstates/") and entry.endswith("_fertilised_farmland.json")):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            variants = json.load(fh).get("variants", {})
        for level in range(8):
            for well in ("false", "true"):
                key = "moisture=%d,well_fertilised=%s" % (level, well)
                if key not in variants:
                    problems.append("%s: missing variant %s" % (entry, key))

    # The harvest modifier only runs if its data files line up with the registered serializer.
    data_root = os.path.join(resources_dir, "data")
    modifier = os.path.join(data_root, "aoa3farmland", "loot_modifiers",
                            "fertilised_farmland.json")
    if not os.path.isfile(modifier):
        problems.append("missing data/aoa3farmland/loot_modifiers/fertilised_farmland.json")
    else:
        with open(modifier, encoding="utf-8") as fh:
            if json.load(fh).get("type") != "aoa3farmland:fertilised_farmland":
                problems.append("the harvest modifier's type must be its serializer id")
    global_modifiers = os.path.join(data_root, "forge", "loot_modifiers",
                                    "global_loot_modifiers.json")
    if not os.path.isfile(global_modifiers):
        problems.append("missing data/forge/loot_modifiers/global_loot_modifiers.json")
    else:
        with open(global_modifiers, encoding="utf-8") as fh:
            entries = json.load(fh).get("entries", [])
        if "aoa3farmland:fertilised_farmland" not in entries:
            problems.append("the harvest modifier is not registered in the global list")
        if json.load(open(global_modifiers, encoding="utf-8")).get("replace"):
            problems.append("the global loot modifier list must merge, not replace")

    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("%d broken resource reference(s); refusing to package" % len(problems))
    print("verified %d assets (%d blocks, %d languages)"
          % (len(ours), len(blocks), len(languages)))


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
    if mappings.classes:
        # The remapper rewrites the constant pool only. Annotation element values and generic
        # Signature attributes also carry class names, as UTF8 descriptors rather than pool
        # entries, so they are not covered - which is harmless exactly as long as classes are
        # spelled the same in both naming schemes, as they are for 1.16.5.
        raise SystemExit("MCP and SRG disagree on %d class name(s); the remapper would leave "
                         "annotation and signature descriptors stale" % len(mappings.classes))
    print("remapping MCP -> SRG (%d methods, %d class hierarchies)"
          % (len(mappings.methods),
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

    # The mod must not link against any AoA type - that is the whole licensing story. This looks
    # at CONSTANT_Class entries, i.e. at types the JVM would actually resolve and load, rather
    # than at raw bytes: the mixins name AoA's classes in @Mixin(targets = "...") *strings* on
    # purpose (that is how they cooperate with AoA without depending on it at compile time), and
    # those strings are not references.
    for root, _, files in os.walk(remapped):
        for f in files:
            if not f.endswith(".class"):
                continue
            path = os.path.join(root, f)
            with open(path, "rb") as fh:
                cls = remap.ClassFile(fh.read())
            for i in range(1, cls.count):
                entry = cls.entries[i]
                if entry is None or entry[0] != remap.CONSTANT_CLASS:
                    continue
                name = cls.utf8(cls.u2(entry[1]))
                if name.startswith(("net/tslat/", "net/minecraft/aoa")):
                    raise SystemExit("%s links against the AoA type %s" % (path, name))
    print("no AoA type is linked by any built class")

    verify_overrides(staging, mappings)
    verify_block_properties(staging)
    verify_turn_to_soil_paths(staging)

    # A mixin that names the wrong SRG method fails at class-transform time, i.e. it takes the
    # game down at startup. The refmap is the only place those names live, so check every entry
    # against the TSRG tables *and* against the SRG jar before packaging.
    verify_refmap(mappings, MAPPINGS, SRG_JAR)

    resources = os.path.join(staging, "resources")
    shutil.copytree(os.path.join(ROOT, "src", "main", "resources"), resources)
    props = expand_mods_toml(resources)
    verify_resources(resources, aoa)

    manifest = (
        "Manifest-Version: 1.0\n"
        "Specification-Title: %s\n"
        "Specification-Vendor: %s\n"
        "Specification-Version: 1\n"
        "Implementation-Title: %s\n"
        "Implementation-Version: %s\n"
        "Implementation-Vendor: %s\n"
        "MixinConfigs: aoa3farmland.mixins.json\n"
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
