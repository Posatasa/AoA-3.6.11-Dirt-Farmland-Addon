# AoA3 Farmland Addon

Adds a farmland block for every dirt block in [Advent of Ascension 3](https://github.com/Tslat/Advent-Of-Ascension)
**1.16.5-3.6.11**, so a hoe works on AoA soil the same way it works on vanilla dirt.

**Download:** [`dist/aoa3farmland-1.2.0.jar`](dist/aoa3farmland-1.2.0.jar) — drop it into `mods/`
(checksums in [`dist/SHA256SUMS.txt`](dist/SHA256SUMS.txt)).

## 1. What it adds

Eleven farmland blocks, one for each AoA block that AoA itself puts in the `forge:dirt` tag,
in their own creative tab ("AoA3 Farmland" / 虚无世界耕地) and in English and Chinese:

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
rules, same trampling and crop support — with three differences:

- Breaking it gives back the AoA soil it came from, not `minecraft:dirt` - and so does every way
  the block can be destroyed on its own: trampling, drying out when there is no water, and having a
  block placed on top of it.
- Only the top face is a tilled texture, exactly like vanilla farmland; the sides and bottom use
  AoA's own soil texture. That texture is *referenced* by resource location, not copied, so it
  always matches the AoA version installed.
- The top face changes appearance with moisture exactly the way vanilla's does: one texture while
  the block is dry (moisture 0-6) and a darker one once it is fully wet (moisture 7), so farmland
  next to water looks different from farmland that has dried out.
- Vanilla and AoA crops can be planted on it. Both hard-code "the block below must be
  `minecraft:farmland`", a check no modded farmland can satisfy, so this add-on widens that single
  check for its own blocks (see `src/.../mixin`). It only ever turns a `false` into a `true`, so
  every other planting rule stays vanilla's. Forge-aware crops are supported through the normal
  `canSustainPlant` contract instead.

Each of those also has a **fertilised** counterpart (`aromatic_fertilised_farmland`, ...), the
per-soil version of AoA's `aoa3:fertilised_farmland`. They carry AoA's `well_fertilised` state and
behave the way AoA's does, including its two-way conversion:

- **Bone meal** on one of these farmlands, with AoA's *Fertilise Farmland* ability unlocked, turns it
  into its fertilised variant - moisture kept, one bone meal spent. AoA's ability only recognised
  vanilla farmland (`state.getBlock() == Blocks.FARMLAND`), so this add-on hooks the ability itself
  rather than reimplementing it: the conversion only happens for players who actually have it.
- Breaking a mature **green manure** crop well-fertilises the soil under it, as with AoA's crop.
- Harvesting a mature crop over a fertilised block adds **2** extra of every non-seed drop and
  leaves it plain fertilised; over a plain fertilised block it adds **1** and leaves the plain
  farmland behind. Moisture is preserved, and green manure is exempt so it keeps the block well
  fertilised. This is AoA's own rule set, applied to these blocks by an equivalent loot modifier.

One deviation worth knowing: the *Fertilise Farmland* ability also grants Farming experience, and
that award does not happen for these blocks, because the ability is cancelled before it gets that
far. The conversion and the bone meal cost do match.

The tilled top is vanilla's own farmland pattern recoloured to that soil's palette. A fertilised
top is that same pattern tinted darker and richer, with a few compost flecks once well fertilised.

Deliberately **not** included: `aoa3:polluted_soil` and `aoa3:polluted_grass`, `aoa3:archaic_dirt`
(a pickaxe-mined stone block that merely happens to be named dirt), and the AoA grass variants
that sit on stone or rock rather than on soil (`abyssal_grass`, `bright_grass`, `irograss`,
`lelyetian_grass`, `inverted_lelyetian_grass`, `precasian_grass`, `runic_grass`) — a farmland
tilled out of stone would not read as farmland.

## 2. Side: BOTH

Ships Java code, a mixin and data, so it must be on the server. It adds no client-only rendering,
and clients that do not have it installed can still join a server that does.

## 3. AoA licensing

Advent of Ascension is source-visible but **not** open source, and its license forbids
redistributing its code or assets. This add-on contains none of either:

- No AoA class is named anywhere in the source or in the compiled classes; the association to
  AoA's blocks is made purely by registry name.
- No AoA texture or model is copied. Every texture shipped here is derived from vanilla
  Minecraft's own `dirt`, `farmland` and `farmland_moist` textures; the block models merely point
  at AoA's soil texture by resource location, which the game reads from AoA's own jar at runtime.

AoA's license permits creating and distributing add-ons. AoA is the property of its author; this
project is not affiliated with or endorsed by Tslat. This add-on itself is MIT — see
[LICENSE](LICENSE).

---

## 中文

为 [虚无世界 3](https://github.com/Tslat/Advent-Of-Ascension) **1.16.5-3.6.11** 的每一种泥土
方块添加了对应的耕地变种，让锄头在虚无世界的泥土上也能像在原版泥土上一样使用。

**下载：** [`dist/aoa3farmland-1.2.0.jar`](dist/aoa3farmland-1.2.0.jar)，放进 `mods/` 即可。

1. **加了什么**：为 AoA 自己放进 `forge:dirt` 标签的全部泥土各加一块耕地，共 11 块，放在独立的
   创造模式物品栏「虚无世界耕地」中，带中英文名称。性质完全继承原版耕地（同一套湿度、干燥、踩踏
   与作物规则），区别只有三点：破坏或被踩踏时掉落的是对应的虚无世界泥土而非原版泥土；只有**上表面**
   是耕地纹理，侧面和底面用 AoA 自己的泥土纹理（按资源路径引用，不复制，所以永远和所装的 AoA 版本
   一致）；**原版和 AoA 的作物都能种在上面**——它们把「下方必须是 `minecraft:farmland`」写死了，
   任何附属耕地都无法满足，所以这里只针对本附属的方块放宽了那一处判断（见 `src/.../mixin`），
   它只会把 `false` 改成 `true`，其余种植规则仍是原版的；Forge 系作物则通过标准的
   `canSustainPlant` 支持。耕地上表面用原版耕地的图案，配色改成对应泥土的色调。
   上表面的外观变化与原版一致：湿度 0–6 用「干」的纹理，到 7 换成更深的「湿」纹理，所以
   旁边有水时和干透时的观感不同。
   刻意不含 `polluted_soil` / `polluted_grass`（按需求排除）、`archaic_dirt`（是镐挖的石头方块，
   只是名字叫泥土），以及长在石头上的那些草方块。
   每种耕地还各有一块**肥沃耕地**（`aromatic_fertilised_farmland` 等），即 AoA 那块
   `aoa3:fertilised_farmland` 的分土版本。它们带 AoA 的 `well_fertilised` 状态，并按 AoA 的规则
   双向转化：解锁 AoA 的「施肥」能力后手持**骨粉**右击本附属的耕地 → 变成对应的肥沃耕地
   （保持湿度、消耗 1 个骨粉）；破坏成熟的**绿肥**作物 → 下方变成「良好施肥」的肥沃耕地；
   在肥沃耕地上收割成熟作物 → 每种非种子掉落额外 +2（良好施肥时）或 +1，并依次降级为
   普通肥沃耕地 / 普通耕地（保持湿度，绿肥作物不触发降级）。AoA 的能力原本只认原版耕地
   （`== Blocks.FARMLAND`），所以这里挂的是 AoA 自己的能力、而不是另写一套：只有真正拥有该能力
   的玩家才会触发。
   ⚠️ 已知差异：AoA 的「施肥」能力会给农业经验，本附属的方块不发放经验（能力在被调用前就交给我们
   处理了），转化本身与骨粉消耗与原版一致。
2. **安装侧**：`BOTH`（两端）。包含 Java 代码、mixin 与数据，服务端必须安装；没装的客户端也能进服。
3. **许可证**：虚无世界的许可证禁止再分发其代码与素材，本附属两者都不含——源码与编译后的类里没有
   出现任何 AoA 类名，关联只靠注册名；随包发布的纹理全部由原版 `dirt` / `farmland` /
   `farmland_moist` 推导而来，模型里只是按路径引用 AoA 自己的泥土纹理。AoA 许可证允许制作与分发
   附属。本附属自身为 MIT。

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
every reference against the SRG jar, checks the mixin refmap against the game's real names, and
packages the jar. It needs Python 3 and Pillow only if you also want to regenerate the textures
and models:

```
python tools/generate_resources.py
```

The remaining helpers are `tools/remap.py` (MCP → SRG), `tools/verify_refs.py` (reference check)
and `tools/verify_link.py` (loads the built classes against the SRG classpath, so the JVM
verifier has the final say; it also rejects listener methods that Forge's `addListener` would
throw on).

## Changelog

- **1.2.0** — fertilised farmland for every soil, wired into AoA's farming system: bone meal
  through AoA's *Fertilise Farmland* ability, the green manure crop that sets `well_fertilised`, and
  an equivalent harvest modifier so a mature crop gives its +1/+2 bonus and steps the block back
  down toward plain farmland. AoA's ability and crop are hooked by mixin *name* (string targets,
  `require = 0`, `required: false`), so the build still links against no AoA type at all.
- **1.1.3** — fixed farmland turning into vanilla dirt when it dried out or when a block was placed
  on top. Vanilla destroys farmland from three places and does it through a *static* helper that
  names `minecraft:dirt` directly, so no virtual dispatch reaches it; only the trampling path had
  been overridden. All three are now overridden, and the build asserts that they are.
- **1.1.2** — back to vanilla's two appearances: one top texture for moisture 0-6 and a darker
  one at 7, instead of the eight-step ramp from 1.1.0. The `randomTicks()` fix from 1.1.1 stays,
  so the block still actually dries out and wets up.
- **1.1.1** — fixed the moisture never changing. The blocks were created without vanilla
  farmland's `randomTicks()`, so they never received random ticks: `FarmlandBlock#randomTick` never
  ran, moisture stayed at 0, and every block looked bone dry no matter how much water was next to
  it (and never dried back into dirt either). Nothing logged an error, which is why the build now
  asserts the property chain against vanilla's own. The 8-level appearance from 1.1.0 is unchanged.
- **1.1.0** — each farmland now has eight top textures, one per moisture level, so it visibly
  dries out and wets up step by step instead of flipping between two looks at level 7. Purely
  asset-side change. The build also gained a resource check: every blockstate, model parent,
  texture and translation key must resolve, for both its own namespace and (when an AoA jar is
  present) AoA's.
- **1.0.4** — fixed a server crash when bone-mealing a melon/pumpkin stem on this farmland
  (NoSuchFieldError: MOISTURE). isFertile reads the inherited FarmlandBlock.MOISTURE
  constant, which compiles to a reference whose owner is *our* class; the remapper only walked the
  hierarchy of Minecraft classes, so it never resolved it. Inherited members now resolve through
  our classes' own supertypes too, and the reference check no longer skips references that point at
  our own classes - which is exactly how this one escaped.
- **1.0.3** — fixed a crash when opening the creative inventory. The creative tab's makeIcon
  override had been left with its MCP name by the build's remapper, so the tab class still had an
  unimplemented abstract method and the game died with AbstractMethodError the moment the tab was
  drawn. The remapper now renames overridden members too, and two checks make that failure mode
  unshippable: every override must carry its SRG name, and no concrete class may still expose an
  abstract method.
- **1.0.2** — own creative tab; Chinese translation; only the top face is tilled and the sides
  and bottom use AoA's soil texture, as in vanilla farmland; crops can be planted (vanilla and AoA
  crops through a one-line mixin that widens their hard-coded farmland check, Forge crops through
  `canSustainPlant`), and moist farmland counts as fertile again.
- **1.0.1** — fixed two bugs: registering a *generic* event listener (`RegistryEvent.Register`)
  through `addListener`, which Forge rejects while the mod is being constructed; and tilling,
  which was hooked on the farmland block instead of on the AoA block being tilled and so never
  ran. Tilling now goes through `UseHoeEvent`.
- **1.0.0** — first release.
