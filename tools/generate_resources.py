"""Generates this add-on's blockstates, models, loot tables, tags, lang files and textures.

Textures: vanilla's farmland pattern, recoloured to each AoA soil's palette. Vanilla's farmland
and farmland_moist textures are the same tiled pattern as vanilla dirt, drawn in a different
colour, so dividing farmland by the average colour of vanilla dirt yields a reusable
"tilled" multiplier with a per-channel mean of exactly 1. Multiplying any soil by it gives
that soil in the vanilla tilled pattern, at the soil's own brightness. The moist texture is that
result re-shaded at vanilla's moist brightness. Feeding vanilla dirt back through the dry path
reproduces vanilla farmland.png byte for byte.

The fertilised tops are that same vanilla-derived result, tinted darker and richer, with a few
compost flecks once fully fertilised. Nothing is derived from AoA's own textures.

The block looks the way vanilla farmland looks: one top texture for moisture 0..6 and a darker one
at 7. The fertilised block repeats that split per `well_fertilised` state, like AoA's own block.

Models: like vanilla farmland, only the top face is the tilled texture. The sides and bottom
point at AoA's own soil texture (`aoa3:block/<soil>`), so they stay plain dirt - and since that
is a reference resolved by the game at runtime, no AoA asset is copied into this jar.
"""
import io
import json
import os
import zipfile

import numpy as np
from PIL import Image, ImageEnhance

MC_JAR = os.path.expanduser(
    r"~\.gradle\caches\forge_gradle\minecraft_repo\versions\1.16.5\client-extra.jar")
AOA_JAR = r"C:\Users\admin\curseforge\minecraft\Instances\Wuhui\mods\AoA3-1.16.5-3.6.11.jar"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoa3farmland")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")

# registry name -> (AoA soil texture/block name, AoA soil block id, English, Chinese)
SOILS = [
    ("aromatic_farmland",   "aromatic_dirt",   "aoa3:aromatic_dirt",   "Aromatic Farmland",   "芬芳耕地"),
    ("blackened_farmland",  "blackened_soil",  "aoa3:blackened_soil",  "Blackened Farmland",  "漆黑耕地"),
    ("candied_farmland",    "candied_dirt",    "aoa3:candied_dirt",    "Candied Farmland",    "糖果耕地"),
    ("celevian_farmland",   "celevian_dirt",   "aoa3:celevian_dirt",   "Celevian Farmland",   "玩具耕地"),
    ("coral_farmland",      "coral_soil",      "aoa3:coral_soil",      "Coral Farmland",      "珊瑚耕地"),
    ("creep_farmland",      "creep_dirt",      "aoa3:creep_dirt",      "Creep Farmland",      "蠕变耕地"),
    ("fungal_farmland",     "fungal_dirt",     "aoa3:fungal_dirt",     "Fungal Farmland",     "真菌耕地"),
    ("greckon_farmland",    "greckon_dirt",    "aoa3:greckon_dirt",    "Greckon Farmland",    "格瑞克耕地"),
    ("lunalyte_farmland",   "lunalyte_dirt",   "aoa3:lunalyte_dirt",   "Lunalyte Farmland",   "粉色月球耕地"),
    ("lunasole_farmland",   "lunasole_dirt",   "aoa3:lunasole_dirt",   "Lunasole Farmland",   "蓝色月球耕地"),
    ("weightless_farmland", "weightless_dirt", "aoa3:weightless_dirt", "Weightless Farmland", "轻盈耕地"),
]

TAB_KEY = "itemGroup.aoa3farmland"
TAB_EN = "AoA3 Farmland"
TAB_ZH = "虚无世界耕地"

# FarmlandBlock#MOISTURE runs 0..7, and vanilla draws 0..6 with one texture and 7 with another.
MOISTURE_LEVELS = 8
WET_LEVEL = MOISTURE_LEVELS - 1

# How much richer the fertilised tops look, relative to the plain tilled texture. Picked so the
# difference is obvious in game without turning the soil into a different colour.
FERTILISED_BRIGHTNESS = 0.86
FERTILISED_SATURATION = 1.15
WELL_BRIGHTNESS = 0.74
WELL_SATURATION = 1.25

# Fixed positions where the well fertilised texture gets its compost flecks, so the result is
# deterministic across runs and machines.
COMPOST_FLECKS = ((2, 3), (7, 1), (11, 6), (4, 12), (13, 10), (9, 14))


def fertilised_name(block):
    return block.replace("_farmland", "_fertilised_farmland")


def model_name(block, suffix, fertilised):
    """Texture/model name: the block's own name plus a state suffix ("" / "_moist" / "_well" ...)."""
    return (fertilised_name(block) if fertilised else block) + suffix


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def clean(directory):
    """Removes generated files so a changed naming scheme cannot leave stale ones behind."""
    if not os.path.isdir(directory):
        return
    for name in os.listdir(directory):
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            os.remove(path)


def enrich(rgb, brightness, saturation):
    """Vanilla-derived tinting: no new imagery, just a darker, richer version of the same pixels."""
    image = ImageEnhance.Color(Image.fromarray(rgb)).enhance(saturation)
    image = ImageEnhance.Brightness(image).enhance(brightness)
    return np.asarray(image).astype(np.uint8)


def speckle(rgb):
    """Adds a few compost flecks, keeping the soil's own colour underneath."""
    out = rgb.astype(np.float64)
    for x, y in COMPOST_FLECKS:
        out[y, x] = out[y, x] * 0.55 + np.array([44.0, 72.0, 28.0]) * 0.45
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def generate_textures():
    zm = zipfile.ZipFile(MC_JAR)
    za = zipfile.ZipFile(AOA_JAR)

    def load(archive, path):
        with archive.open(path) as fh:
            return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGB")).astype(np.float64)

    dirt = load(zm, "assets/minecraft/textures/block/dirt.png")
    farmland = load(zm, "assets/minecraft/textures/block/farmland.png")
    farmland_moist = load(zm, "assets/minecraft/textures/block/farmland_moist.png")

    tilled = farmland / dirt.mean(axis=(0, 1), keepdims=True)
    wet_brightness = farmland_moist.mean() / farmland.mean()

    def recolor(soil, wet):
        out = soil.mean(axis=(0, 1), keepdims=True) * tilled
        if wet:
            # Re-shade at vanilla's moist brightness. Vanilla's wet texture is darker with slightly
            # different channel weights, so the colour is kept and only the brightness moves.
            lum = 0.2126 * out[:, :, 0] + 0.7152 * out[:, :, 1] + 0.0722 * out[:, :, 2]
            colour = out / np.maximum(lum[:, :, None], 1e-6)
            out = colour * (lum * wet_brightness)[:, :, None]
        return np.clip(np.rint(out), 0, 255).astype(np.uint8)

    # Guard: the dry path must reproduce vanilla farmland exactly on vanilla dirt.
    assert np.array_equal(recolor(dirt, False), farmland.astype(np.uint8)), \
        "texture transfer no longer reproduces vanilla farmland"
    print("texture transfer reproduces vanilla farmland exactly")

    out_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(out_dir, exist_ok=True)
    clean(out_dir)
    for name, texture, _, _, _ in SOILS:
        soil = load(za, "assets/aoa3/textures/block/%s.png" % texture)
        dry, moist = recolor(soil, False), recolor(soil, True)
        tops = [
            (model_name(name, "", False), dry),
            (model_name(name, "_moist", False), moist),
            (model_name(name, "", True), enrich(dry, FERTILISED_BRIGHTNESS, FERTILISED_SATURATION)),
            (model_name(name, "_moist", True),
             enrich(moist, FERTILISED_BRIGHTNESS, FERTILISED_SATURATION)),
            (model_name(name, "_well", True), speckle(enrich(dry, WELL_BRIGHTNESS, WELL_SATURATION))),
            (model_name(name, "_well_moist", True),
             speckle(enrich(moist, WELL_BRIGHTNESS, WELL_SATURATION))),
        ]
        for model, array in tops:
            # RGBA like every vanilla block texture, even though these are fully opaque.
            Image.fromarray(array).convert("RGBA").save(os.path.join(out_dir, model + ".png"))
        print("  %s: %d textures" % (name, len(tops)))


def generate_resources():
    en = {"_comment": "Generated by tools/generate_resources.py"}
    zh = {"_comment": "由 tools/generate_resources.py 生成"}
    en[TAB_KEY] = TAB_EN
    zh[TAB_KEY] = TAB_ZH
    dirt_tag = []

    models_dir = os.path.join(ASSETS, "models", "block")
    blockstates_dir = os.path.join(ASSETS, "blockstates")
    clean(models_dir)
    clean(blockstates_dir)

    def write_model(block, suffix, fertilised, soil_texture):
        model = model_name(block, suffix, fertilised)
        write_json(os.path.join(models_dir, model + ".json"), {
            "parent": "minecraft:block/template_farmland",
            "textures": {
                "top": "aoa3farmland:block/" + model,
                "dirt": "aoa3:block/" + soil_texture,
            },
        })
        return model

    for name, texture, soil, english, chinese in SOILS:
        bid = "aoa3farmland:" + name
        fertilised = fertilised_name(name)

        # ---- plain farmland: exactly vanilla's split, dry for 0..6 and moist at 7
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d" % level] = {
                "model": "aoa3farmland:block/" + model_name(name, suffix, False)}
        write_json(os.path.join(blockstates_dir, name + ".json"), {"variants": variants})
        write_model(name, "", False, texture)
        write_model(name, "_moist", False, texture)
        # The item shows the dry state, like vanilla's farmland item.
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "aoa3farmland:block/" + name})

        # ---- fertilised farmland: AoA's extra `well_fertilised` state, same moisture split
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d,well_fertilised=false" % level] = {
                "model": "aoa3farmland:block/" + model_name(name, suffix, True)}
            variants["moisture=%d,well_fertilised=true" % level] = {
                "model": "aoa3farmland:block/" + model_name(name, "_well" + suffix, True)}
        write_json(os.path.join(blockstates_dir, fertilised + ".json"), {"variants": variants})
        write_model(name, "", True, texture)
        write_model(name, "_moist", True, texture)
        write_model(name, "_well", True, texture)
        write_model(name, "_well_moist", True, texture)
        write_json(os.path.join(ASSETS, "models", "item", fertilised + ".json"),
                   {"parent": "aoa3farmland:block/" + fertilised})

        # loot: both drop the AoA soil they were tilled from, not vanilla dirt
        for lootName in (name, fertilised):
            write_json(os.path.join(DATA, "aoa3farmland", "loot_tables", "blocks",
                                    lootName + ".json"), {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": soil}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

        en["block.aoa3farmland." + name] = english
        zh["block.aoa3farmland." + name] = chinese
        en["block.aoa3farmland." + fertilised] = english.replace("Farmland",
                                                                 "Fertilised Farmland")
        zh["block.aoa3farmland." + fertilised] = chinese.replace("耕地", "肥沃耕地")
        dirt_tag.append(bid)

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang", "zh_cn.json"), zh)

    # These blocks are dirt that happens to be tilled, so they join AoA's own dirt tag; the entry
    # list is deliberately explicit rather than "the forge:dirt tag" so it cannot go circular.
    write_json(os.path.join(DATA, "forge", "tags", "blocks", "dirt.json"), {
        "_comment": "The AoA farmland this add-on adds still counts as dirt.",
        "replace": False,
        "values": dirt_tag,
    })

    # The harvest modifier: AoA does this for its own fertilised farmland with a global loot
    # modifier; ours is the per-soil equivalent, registered the same way.
    write_json(os.path.join(DATA, "aoa3farmland", "loot_modifiers",
                            "fertilised_farmland.json"), {
        "conditions": [],
        "type": "aoa3farmland:fertilised_farmland",
    })
    write_json(os.path.join(DATA, "forge", "loot_modifiers",
                            "global_loot_modifiers.json"), {
        "_comment": "Adds this add-on's harvest modifier; AoA's own entries are merged in.",
        "replace": False,
        "entries": ["aoa3farmland:fertilised_farmland"],
    })


if __name__ == "__main__":
    generate_textures()
    generate_resources()
    print("done")
