# AoA3 Farmland Addon

Adds a farmland block for every dirt block in [Advent of Ascension 3](https://github.com/Tslat/Advent-Of-Ascension)
**1.16.5-3.6.11**, so a hoe works on AoA soil the same way it works on vanilla dirt.

**Download:** [`dist/aoa3farmland-1.0.0.jar`](dist/aoa3farmland-1.0.0.jar) — drop it into `mods/`
(checksums in [`dist/SHA256SUMS.txt`](dist/SHA256SUMS.txt)).

## 1. What it adds

Eleven farmland blocks, one for each AoA block that AoA itself puts in the `forge:dirt` tag:

| Farmland | Tilled from | Drops |
| --- | --- | --- |
| Aromatic Farmland | `aoa3:aromatic_dirt`, `aoa3:aromatic_grass` | `aoa3:aromatic_dirt` |
| Blackened Farmland | `aoa3:blackened_soil`, `aoa3:faded_grass` | `aoa3:blackened_soil` |
| Candied Farmland | `aoa3:candied_dirt`, `aoa3:candied_grass` | `aoa3:candied_dirt` |
| Celevian Farmland | `aoa3:celevian_dirt`, `aoa3:celevian_grass` | `aoa3:celevian_dirt` |
| Coral Farmland | `aoa3:coral_soil`, `aoa3:coral_grass` | `aoa3:coral_soil` |
| Creep Farmland | `aoa3:creep_dirt`, `aoa3:creep_grass` | `aoa3:creep_dirt` |
| Fungal Farmland | `aoa3:fungal_dirt`, `aoa3:fungal_grass` | `aoa3:fungal_dirt` |
| Greckon Farmland | `aoa3:greckon_dirt`, `aoa3:greckon_grass` | `aoa3:greckon_dirt` |
| Lunalyte Farmland | `aoa3:lunalyte_dirt`, `aoa3:lunalyte_grass` | `aoa3:lunalyte_dirt` |
| Lunasole Farmland | `aoa3:lunasole_dirt`, `aoa3:lunasole_grass` | `aoa3:lunasole_dirt` |
| Weightless Farmland | `aoa3:weightless_dirt`, `aoa3:weightless_grass` | `aoa3:weightless_dirt` |

Each one behaves exactly like vanilla farmland — same block class, same moisture and drying
rules, same trampling and crop support — with two differences:

- Breaking it (or trampling it) gives back the AoA soil it came from, not `minecraft:dirt`.
- Its textures are vanilla's own farmland pattern recoloured to that soil's palette, so the
  tilled ridges look vanilla while the colour matches the AoA block.

Deliberately **not** included: `aoa3:polluted_soil` and `aoa3:polluted_grass`, `aoa3:archaic_dirt`
(a pickaxe-mined stone block that merely happens to be named dirt), and the AoA grass variants
that sit on stone or rock rather than on soil (`abyssal_grass`, `bright_grass`, `irograss`,
`lelyetian_grass`, `inverted_lelyetian_grass`, `precasian_grass`, `runic_grass`) — a farmland
tilled out of stone would not read as farmland.

## 2. Side: BOTH

Ships Java code and data, so it must be on the server. It adds no client-only rendering, and
clients that do not have it installed can still join a server that does.

## 3. AoA licensing

Advent of Ascension is source-visible but **not** open source, and its license forbids
redistributing its code or assets. This add-on contains none of either:

- No AoA class is named anywhere in the source or in the compiled classes; the association to
  AoA's blocks is made purely by registry name.
- No AoA texture or model is copied. Every texture here is derived from vanilla Minecraft's own
  `dirt`, `farmland` and `farmland_moist` textures.

AoA's license permits creating and distributing add-ons. AoA is the property of its author; this
project is not affiliated with or endorsed by Tslat. This add-on itself is MIT — see
[LICENSE](LICENSE).

---

## 中文

为 [虚无世界 3](https://github.com/Tslat/Advent-Of-Ascension) **1.16.5-3.6.11** 的每一种泥土
方块添加了对应的耕地变种，让锄头在虚无世界的泥土上也能像在原版泥土上一样使用。

**下载：** [`dist/aoa3farmland-1.0.0.jar`](dist/aoa3farmland-1.0.0.jar)，放进 `mods/` 即可。

1. **加了什么**：为 AoA 自己放进 `forge:dirt` 标签的全部泥土各加一块耕地，共 11 块；性质完全
   继承原版耕地（同一套湿度、干燥、踩踏与作物规则），区别只有两点：破坏或被踩踏时掉落的是
   对应的虚无世界泥土而非原版泥土；纹理用原版耕地的图案，配色改成对应泥土的色调。
   刻意不含 `polluted_soil` / `polluted_grass`（按需求排除）、`archaic_dirt`（是镐挖的石头方块，
   只是名字叫泥土），以及长在石头上的那些草方块。
2. **安装侧**：`BOTH`（两端）。包含 Java 代码与数据，服务端必须安装；没装的客户端也能进服。
3. **许可证**：虚无世界的许可证禁止再分发其代码与素材，本附属两者都不含——源码与编译后的类
   里没有出现任何 AoA 类名，关联只靠注册名；纹理全部由原版 `dirt` / `farmland` /
   `farmland_moist` 推导而来。AoA 许可证允许制作与分发附属。本附属自身为 MIT。

## Building

Normal build (JDK 8 + internet, exactly as for any ForgeGradle mod):

```
gradlew build
```

The finished jar lands in `dist/`.

Offline build, for a machine with no internet but with a populated ForgeGradle cache:

```
python tools/build.py
```

This compiles against the cached MCP-named Forge jar, remaps the result to SRG names, checks
every reference against the SRG jar, and packages the jar. It needs Python 3 and Pillow only if
you also want to regenerate the textures:

```
python tools/generate_resources.py
```

The remaining helpers are `tools/remap.py` (MCP → SRG), `tools/verify_refs.py` (reference check)
and `tools/verify_link.py` (loads the built classes against the SRG classpath, so the JVM
verifier has the final say).
