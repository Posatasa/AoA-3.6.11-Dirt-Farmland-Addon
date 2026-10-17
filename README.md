# AoA3 Farmland Addon

Adds a farmland block for every dirt block in [Advent of Ascension 3](https://github.com/Tslat/Advent-Of-Ascension)
**1.16.5-3.6.11**, so a hoe works on AoA soil the same way it works on vanilla dirt.

## 2. Side: BOTH

Ships Java code, a mixin and data, so it must be on the server. It adds no client-only rendering,
and clients that do not have it installed can still join a server that does.

## Changelog

- **1.2.4** — fertilised farmland is visible again: darker than plain farmland by more than AoA's
  own margin (0.85x / 0.74x instead of 0.89x / 0.81x, which reads as nothing on a dark soil) and
  carrying a few pale flecks, like AoA's own texture does. The flecks are the pixel's own colour
  desaturated and lifted to a fixed brightness, so they stay in the soil's hue - no blue, no green.
- **1.2.3** — texture pass: the tilled top no longer picks up stray blue specks, and the fertilised
  tops lost their green flecks. Vanilla farmland's pattern is now transferred by luminance only, so
  a highlight is a lighter patch of the soil's own colour; the earlier per-channel ratio carried
  vanilla's cool highlight tint, which turned into blue dots once multiplied by a saturated soil.
  Fertilised and well fertilised are now distinguished the way AoA distinguishes its own, by
  brightness alone (0.89x and 0.81x).
- **1.2.2** — fertilising now awards the same Farming experience the ability awards for vanilla
  farmland (reached through AoA's own API by name, and checked against the AoA jar at build time),
  and the fertilised textures got the pale and green flecks AoA's own fertilised farmland has,
  instead of only being a shade darker.
- **1.2.1** — fixed a crash on the first harvest (`NoSuchMethodError: List.func_190926_b`).
  `ItemStack.isEmpty` and `java.util.List.isEmpty` shared one constant-pool NameAndType entry in the
  loot modifier's class file, so renaming the Minecraft one also renamed the JDK one. References
  that need renaming now get their own entry, and the build rejects any library member left with an
  SRG name - the check was verified by reproducing the crash's bytecode.
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
