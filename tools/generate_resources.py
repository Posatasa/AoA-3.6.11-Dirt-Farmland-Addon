"""Generates this add-on's blockstates, models, loot tables, tags, lang files and textures.

Textures: vanilla's farmland pattern, recoloured to each AoA soil's palette. Vanilla's farmland
and farmland_moist textures are the same tiled pattern as vanilla dirt, drawn in a different
colour, so dividing farmland by the average colour of vanilla dirt yields a reusable
"tilled" multiplier with a per-channel mean of exactly 1. Multiplying any soil by it gives
that soil in the vanilla tilled pattern, at the soil's own brightness. Applying vanilla's
moist/dry brightness ratio on top produces the wet state. Feeding vanilla dirt back through
the dry path reproduces vanilla farmland.png byte for byte.

Models: like vanilla farmland, only the top face is the tilled texture. The sides and bottom
point at AoA's own soil texture (`aoa3:block/<soil>`), so they stay plain dirt - and since that
is a reference resolved by the game at runtime, no AoA asset is copied into this jar.
"""
import io
import json
import os
import zipfile

import numpy as np
from PIL import Image

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

# FarmlandBlock#MOISTURE runs 0..7.
MOISTURE_LEVELS = 8


def texture_name(block, level):
    """Texture/model suffix for one moisture level. 0 is the plain name, 7 the vanilla-style
    '_moist' name, so the two extremes keep the conventional spellings."""
    if level == 0:
        return block
    if level == MOISTURE_LEVELS - 1:
        return block + "_moist"
    return "%s_moist_%d" % (block, level)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


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

    # Vanilla has eight moisture levels but only two textures - it looks dry at 0..6 and wet only at
    # 7. These blocks instead get one texture per level, so the soil visibly darkens as it soaks up
    # water and dries out again. Level 0 and level 7 are exactly the vanilla dark/light ends.
    def wetness(level):
        """0.0 for bone dry, 1.0 for fully wet; the ramp is equal steps between them."""
        return max(0.0, min(1.0, level / 7.0))

    def recolor(soil, wet=0.0):
        out = soil.mean(axis=(0, 1), keepdims=True) * tilled
        if wet > 0.0:
            # Shade towards vanilla's moist brightness. Vanilla's wet texture is darker with
            # slightly different channel weights, so the colour is kept and only the brightness
            # moves - which is also what makes a partial wetness meaningful.
            factor = 1.0 + wet * (wet_brightness - 1.0)
            lum = 0.2126 * out[:, :, 0] + 0.7152 * out[:, :, 1] + 0.0722 * out[:, :, 2]
            colour = out / np.maximum(lum[:, :, None], 1e-6)
            out = colour * (lum * factor)[:, :, None]
        return np.clip(np.rint(out), 0, 255).astype(np.uint8)

    # Guard: the dry path must reproduce vanilla farmland exactly on vanilla dirt.
    assert np.array_equal(recolor(dirt, 0.0), farmland.astype(np.uint8)), \
        "texture transfer no longer reproduces vanilla farmland"
    print("texture transfer reproduces vanilla farmland exactly")

    out_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(out_dir, exist_ok=True)
    for name, texture, _, _, _ in SOILS:
        soil = load(za, "assets/aoa3/textures/block/%s.png" % texture)
        for level in range(MOISTURE_LEVELS):
            # RGBA like every vanilla block texture, even though these are fully opaque.
            image = Image.fromarray(recolor(soil, wetness(level))).convert("RGBA")
            image.save(os.path.join(out_dir, texture_name(name, level) + ".png"))
        print("  %s: %d moisture textures" % (name, MOISTURE_LEVELS))


def generate_resources():
    en = {"_comment": "Generated by tools/generate_resources.py"}
    zh = {"_comment": "由 tools/generate_resources.py 生成"}
    en[TAB_KEY] = TAB_EN
    zh[TAB_KEY] = TAB_ZH
    dirt_tag = []

    for name, texture, soil, english, chinese in SOILS:
        bid = "aoa3farmland:" + name

        # blockstate: one model per moisture level, so the soil visibly dries out in steps
        variants = {}
        for level in range(MOISTURE_LEVELS):
            variants["moisture=%d" % level] = {
                "model": "aoa3farmland:block/" + texture_name(name, level)}
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})

        # models: only `top` is ours. `dirt` is AoA's own soil texture, so the sides and bottom
        # stay plain dirt exactly like vanilla farmland - and no AoA asset is copied into the jar.
        for level in range(MOISTURE_LEVELS):
            model = texture_name(name, level)
            write_json(os.path.join(ASSETS, "models", "block", model + ".json"), {
                "parent": "minecraft:block/template_farmland",
                "textures": {
                    "top": "aoa3farmland:block/" + model,
                    "dirt": "aoa3:block/" + texture,
                },
            })
        # The item shows the dry state, like vanilla's farmland item.
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "aoa3farmland:block/" + name})

        # loot: vanilla farmland drops dirt; these drop their own AoA soil instead
        write_json(os.path.join(DATA, "aoa3farmland", "loot_tables", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": soil}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

        en["block.aoa3farmland." + name] = english
        zh["block.aoa3farmland." + name] = chinese
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


if __name__ == "__main__":
    generate_textures()
    generate_resources()
    print("done")
