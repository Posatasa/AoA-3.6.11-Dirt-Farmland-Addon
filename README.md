# AoA Agriculture

An add-on for [Advent of Ascension 3](https://github.com/Tslat/Advent-Of-Ascension) **1.16.5-3.6.11**.
Vanilla farming, extended to AoA: every AoA soil gets farmland, AoA crops grow faster on their own
ground, and AoA's metals come with their own buckets.

**Download:** [`dist/aoaagriculture-1.2.20-beta.jar`](dist/aoaagriculture-1.2.20-beta.jar) - drop it in `mods/`.

## What it adds

- **Farmland for all eleven AoA soils** (every dirt and grass block except the polluted ones), in
  each soil's own colour, plus the fertilised form AoA's farming expects - with a well-fertilised
  state, set by AoA's green manure, that yields extra drops. Moisture, trampling and reversion work
  exactly as on vanilla farmland, and breaking one drops its own AoA soil.
- **AoA crops grow three times as fast on their own farmland**, and AoA's Fertilise Farmland ability
  works on it.
- **Buckets for Limonite, Rosite, Shyre, Lunar and Emberstone**, with water, lava and candied water
  variants. Three of them do more than a bucket: Shyre's water bucket is a portable water source,
  Lunar's prevents fall damage while held, and Emberstone's is a lava-only tank that holds four.
- **Where vanilla met AoA:** the petal station turns AoA crops into copper coins and cheap dirt into
  moon soil.
- Optional, and only when [Farmer's Delight](https://github.com/vectorwing/FarmersDelight) is
  installed: tomatoes plant on AoA farmland, and mushroom colonies grow on AoA's fungal dirt and
  blackened soil. Nothing from it is needed to run this.

## Side: BOTH

Ships Java code, a mixin and data, so it must be on the server. It adds no client-only rendering,
and clients that do not have it installed can still join a server that does.

## Changelog

One file per release: [changelogs/](changelogs/).
