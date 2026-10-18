"""Generates this add-on's blockstates, models, loot tables, tags, lang files and textures.

Textures: vanilla farmland's light-and-dark pattern, drawn in each AoA soil's own colour.

Only vanilla farmland's *luminance* is taken, and it is applied as a per-pixel brightness
multiplier to the soil's average colour. Vanilla farmland's highlight pixels are slightly cooler
than its furrows; that is invisible on brown, but multiplied by a saturated soil it becomes blue
specks - which neither vanilla farmland nor AoA's fertilised farmland has. Keeping the luminance
alone means a highlight is simply a lighter version of the soil's own colour, so no soil can pick up
a colour that is not its own. The generator asserts exactly that (a neutral soil must come out
neutral).

The moist texture is that result re-shaded at vanilla's own moist brightness. The fertilised tops
are the same thing, darker and slightly richer, at the ratio AoA's own fertilised farmland uses
(0.89x for fertilised, 0.81x for well fertilised); nothing is painted on top of the vanilla pattern.

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
from PIL import Image

MC_JAR = os.path.expanduser(
    r"~\.gradle\caches\forge_gradle\minecraft_repo\versions\1.16.5\client-extra.jar")
AOA_JAR = r"C:\Users\admin\curseforge\minecraft\Instances\Wuhui\mods\AoA3-1.16.5-3.6.11.jar"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
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

TAB_KEY = "itemGroup.aoaagriculture"
TAB_EN = "AoA Agriculture"
TAB_ZH = "虚无世界农业"

# FarmlandBlock#MOISTURE runs 0..7, and vanilla draws 0..6 with one texture and 7 with another.
MOISTURE_LEVELS = 8
WET_LEVEL = MOISTURE_LEVELS - 1

# How much darker the fertilised tops are, and how many pale flecks they carry.
#
# AoA's own fertilised farmland is only 0.89x the brightness of vanilla farmland, which turns out to
# read as "no difference at all" once the soil is dark, so these go a little deeper. The flecks are
# the other half of the same idea: AoA's fertilised top has a handful of pale pixels scattered over
# it, and that is what the eye actually latches onto.
#
# The flecks are deliberately not blue and not green. Each one is its own pixel desaturated towards
# grey and then lightened, so a fleck is a paler, dustier version of that very soil - the neutral grey
# highlight vanilla farmland has, drawn in the soil's own hue.
FERTILISED_BRIGHTNESS = 0.85
WELL_BRIGHTNESS = 0.74

# Fixed positions, so the result is identical on every run and machine.
FLECKS_FERTILISED = ((3, 2), (11, 4), (6, 7), (1, 11), (13, 9), (9, 1))
FLECKS_WELL = ((3, 2), (11, 4), (6, 7), (1, 11), (13, 9), (9, 1), (12, 12), (8, 13), (14, 5))
FLECK_DESATURATE = 0.60          # how far a fleck moves towards its own grey
FLECK_CONTRAST = 1.75            # how many times brighter a fleck is than the pixel under it
FLECK_STRENGTH = 0.85            # how far the pixel moves towards the fleck colour


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


def darken(rgb, factor):
    """Scales brightness only, so the pattern keeps exactly the soil's own hue."""
    return np.clip(np.rint(rgb.astype(np.float64) * factor), 0, 255).astype(np.uint8)


def fleck(rgb, positions):
    """Adds pale, near-neutral dust flecks, each in its own pixel's hue.

    The fleck colour is the pixel desaturated towards its own grey, then scaled to a fixed multiple
    of that pixel's brightness. Defining it by contrast rather than by a fixed colour is what makes
    the flecks readable on both a pale soil and a near-black one - and keeping the hue is what stops
    them turning into the blue and green dots this started with.
    """
    out = rgb.astype(np.float64)
    for x, y in positions:
        base = out[y, x]
        grey = base.mean()
        colour = base * (1.0 - FLECK_DESATURATE) + grey * FLECK_DESATURATE
        target = base.mean() * FLECK_CONTRAST + 10.0
        colour = colour * (target / max(colour.mean(), 1e-6))
        out[y, x] = base * (1.0 - FLECK_STRENGTH) + np.clip(colour, 0, 255) * FLECK_STRENGTH
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

    def luminance(rgb):
        return 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]

    # The tilled pattern, as a per-pixel brightness multiplier with a mean of 1.
    #
    # Only vanilla farmland's *light and dark* is taken, not its colour. Vanilla's highlight pixels
    # are slightly cooler than its furrows - unnoticeable on brown, but multiplied by a saturated
    # soil it turns into blue specks, which vanilla farmland has nowhere and AoA's fertilised
    # farmland does not have either. Keeping the luminance alone means every highlight comes out as
    # a lighter version of the soil's own colour, which is what "the vanilla pattern in that soil's
    # palette" actually means.
    pattern = luminance(farmland) / luminance(farmland).mean()
    wet_brightness = luminance(farmland_moist).mean() / luminance(farmland).mean()

    def recolor(soil, wet):
        out = soil.mean(axis=(0, 1), keepdims=True) * pattern[:, :, None]
        if wet:
            # Re-shade at vanilla's moist brightness: keep the colour, move only the brightness.
            lum = luminance(out)
            colour = out / np.maximum(lum[:, :, None], 1e-6)
            out = colour * (lum * wet_brightness)[:, :, None]
        return np.clip(np.rint(out), 0, 255).astype(np.uint8)

    # The pattern must not carry a colour of its own: a neutral soil has to stay neutral, or that
    # colour shows up as specks on soils of a different hue. This is what caught the blue dots.
    neutral = np.full((16, 16, 3), 128.0)
    spread = np.abs(recolor(neutral, False).astype(np.float64)
                    - recolor(neutral, False).mean(axis=2, keepdims=True)).max()
    assert spread <= 1.0, "the tilled pattern carries a colour cast (%.1f)" % spread
    # And its brightness structure must be vanilla's own, at the same overall level.
    assert abs(float(pattern.mean()) - 1.0) < 1e-9
    print("tilled pattern is vanilla farmland's luminance, with no colour of its own")

    out_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(out_dir, exist_ok=True)
    clean(out_dir)
    for name, texture, _, _, _ in SOILS:
        soil = load(za, "assets/aoa3/textures/block/%s.png" % texture)
        dry, moist = recolor(soil, False), recolor(soil, True)
        tops = [
            (model_name(name, "", False), dry),
            (model_name(name, "_moist", False), moist),
            (model_name(name, "", True),
             fleck(darken(dry, FERTILISED_BRIGHTNESS), FLECKS_FERTILISED)),
            (model_name(name, "_moist", True),
             fleck(darken(moist, FERTILISED_BRIGHTNESS), FLECKS_FERTILISED)),
            (model_name(name, "_well", True),
             fleck(darken(dry, WELL_BRIGHTNESS), FLECKS_WELL)),
            (model_name(name, "_well_moist", True),
             fleck(darken(moist, WELL_BRIGHTNESS), FLECKS_WELL)),
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
                "top": "aoaagriculture:block/" + model,
                "dirt": "aoa3:block/" + soil_texture,
            },
        })
        return model

    for name, texture, soil, english, chinese in SOILS:
        bid = "aoaagriculture:" + name
        fertilised = fertilised_name(name)

        # ---- plain farmland: exactly vanilla's split, dry for 0..6 and moist at 7
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, suffix, False)}
        write_json(os.path.join(blockstates_dir, name + ".json"), {"variants": variants})
        write_model(name, "", False, texture)
        write_model(name, "_moist", False, texture)
        # The item shows the dry state, like vanilla's farmland item.
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "aoaagriculture:block/" + name})

        # ---- fertilised farmland: AoA's extra `well_fertilised` state, same moisture split
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d,well_fertilised=false" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, suffix, True)}
            variants["moisture=%d,well_fertilised=true" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, "_well" + suffix, True)}
        write_json(os.path.join(blockstates_dir, fertilised + ".json"), {"variants": variants})
        write_model(name, "", True, texture)
        write_model(name, "_moist", True, texture)
        write_model(name, "_well", True, texture)
        write_model(name, "_well_moist", True, texture)
        write_json(os.path.join(ASSETS, "models", "item", fertilised + ".json"),
                   {"parent": "aoaagriculture:block/" + fertilised})

        # loot: both drop the AoA soil they were tilled from, not vanilla dirt
        for lootName in (name, fertilised):
            write_json(os.path.join(DATA, "aoaagriculture", "loot_tables", "blocks",
                                    lootName + ".json"), {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": soil}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

        en["block.aoaagriculture." + name] = english
        zh["block.aoaagriculture." + name] = chinese
        en["block.aoaagriculture." + fertilised] = english.replace("Farmland",
                                                                 "Fertilised Farmland")
        zh["block.aoaagriculture." + fertilised] = chinese.replace("耕地", "肥沃耕地")
        dirt_tag.append(bid)
        dirt_tag.append("aoaagriculture:" + fertilised)

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang", "zh_cn.json"), zh)

    # These blocks are dirt that happens to be tilled, so they join AoA's own dirt tag; the entry
    # list is deliberately explicit rather than "the forge:dirt tag" so it cannot go circular.
    #
    # Being in this tag does not make them hoe-tillable: Forge's tilling rule is a hardcoded
    # dirt -> farmland map, not a tag test. What it does affect is other mods' "is this soil?"
    # checks, which is the point.
    write_json(os.path.join(DATA, "forge", "tags", "blocks", "dirt.json"), {
        "_comment": "The AoA farmland this add-on adds still counts as dirt.",
        "replace": False,
        "values": dirt_tag,
    })

    # The harvest modifier: AoA does this for its own fertilised farmland with a global loot
    # modifier; ours is the per-soil equivalent, registered the same way.
    write_json(os.path.join(DATA, "aoaagriculture", "loot_modifiers",
                            "fertilised_farmland.json"), {
        "conditions": [],
        "type": "aoaagriculture:fertilised_farmland",
    })
    write_json(os.path.join(DATA, "forge", "loot_modifiers",
                            "global_loot_modifiers.json"), {
        "_comment": "Adds this add-on's harvest modifier; AoA's own entries are merged in.",
        "replace": False,
        "entries": ["aoaagriculture:fertilised_farmland"],
    })


if __name__ == "__main__":
    generate_textures()
    generate_resources()
    print("done")
