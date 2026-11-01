# The Sixth Sense

Compact, animated warnings for nearby danger, with a customizable HUD and an active Sixth Sense ability.

**Minecraft 1.21.1 · Fabric · 1.2.0**

[Modrinth](https://modrinth.com/mod/the-sixth-sense) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sixth-sense) · [Report an issue](https://github.com/kgp-ctrl/The-Sixth-Sense/issues)

## Features

- Animated warnings for bows, crossbows, explosions, Ghasts, Shulkers and Phantoms.
- A Warden heartbeat compass and a raid locator.
- Distant bow-hit predictions for creatures, wooden buttons and End crystals. For curved shots, creatures below the crosshair can glow to indicate estimated damage.
- A movable, resizable HUD with animation previews and reduced motion.
- Optional inner voice, off by default under **Settings → Special**.

## Sixth Sense

Press **Shift + H** while no creature is targeting you. Requires **level 20** and costs **109 XP**, equivalent to going from level 20 to 18. The cost stays fixed at higher levels.

For **60 seconds**, reveal threats within **52 blocks**, move 10% faster and become harder to spot. Threats hidden more than 24 blocks below are excluded; open drops keep the full range. A slim hotbar indicator shows the remaining time.

Sneak behind an unaware creature, jump and land a fully charged sword hit while falling to deal double damage. After using the ability, **sleep through one night** to recover. Until then, direct sunlight strains your eyes; shade or a carved pumpkin protects them.

## Install

Requires **Java 21+**, **Fabric Loader 0.19.2+** and **Fabric API for 1.21.1**. Put the mod in `mods`. Mod Menu is optional; Cloth Config is not required.

- **Singleplayer:** all features are available.
- **Multiplayer:** install the matching mod and Fabric API on both sides for Sixth Sense. Warnings still work on servers without the mod, though some rely on estimates.

## Controls

| Key | Action |
| --- | --- |
| H | HUD editor and settings |
| Shift + H | Activate Sixth Sense |
| Drag / scroll | Move / resize an icon |
| G / D | Grid / animation previews |
| R / Shift + R | Reset one / all positions |
| Esc | Save and close |

The in-game guide covers individual warnings and options. Languages: English, Turkish, German, French, Spanish, Japanese and Simplified Chinese.

## Development

Run `gradlew check` with a compatible JDK for automated checks, or `gradlew build` to create the mod in `build/libs/`. Use the normal JAR, not the sources JAR. Code changes require a restart; **Reload images** refreshes supported HUD PNGs in-game.

**Gareyn_ · All Rights Reserved.** See [LICENSE](LICENSE).
