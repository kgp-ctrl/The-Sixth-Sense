# The Sixth Sense

**A little warning. A little more time to react.**

The Sixth Sense gives nearby danger a visual cue: a drawing bow, an approaching explosion, a circling Shulker bullet, or a Warden's beating heart. Small, expressive pixel animations help you read the moment without filling your screen.

This source tree targets **Minecraft Java 1.21.1 · Fabric · mod version 1.1.1**. Features in other published Minecraft or loader versions may differ.

[Download on Modrinth](https://modrinth.com/mod/the-sixth-sense) · [Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/sixth-sense)

## What it shows

| Warning | What to look for |
| --- | --- |
| Bow & crossbow | Nearby armed mobs and players, with weapon animations reflecting observed drawing and charging states. |
| Explosion | A fuse-driven warning for Creepers, primed TNT, and ignited TNT minecarts. |
| Ghast | A floating head whose mouth follows charging and firing, with an optional nearby-threat distance readout. |
| Shulker | A shell-less head and orbiting projectile, with a count of nearby Shulker bullets. |
| Warden | A centered heartbeat, a smoothly turning compass, and dark thorns that react to targeting and Warden hits. Distance text is optional. |
| Raid | A locator showing a nearby raider's name, coordinates, direction, and distance during a raid, with configurable entity highlighting. |

Warnings use entities and states available to your client. A nearby weapon or projectile alert does not by itself mean that its owner has selected you as its target.

The Warden heart holds the center of the automatic warning row while other alerts arrange themselves beside it. Custom positions remain yours to choose. Thorns appear smoothly over one second when targeted; a Warden hit draws them inward and shrinks them slightly before they release. Their tips turn blood red as health falls. Exact targeting is available in singleplayer; on vanilla multiplayer servers, the reticle gives confirmed Warden-hit feedback because the server does not send target identity to clients.

## Make the HUD yours

Press **H** in a world to open the HUD editor. Drag individual elements, scroll to resize them, and use the optional grid to line them up. Saved positions anchor to a screen edge or the center, so changing GUI scale preserves their reference point. A size adjustment at 3× GUI scale keeps the small animations readable.

| Control | Action |
| --- | --- |
| H | Open the HUD editor |
| Drag / mouse wheel | Move / resize an element |
| Grid | Toggle alignment guides and snapping |
| Debug | Preview animations by hovering over an element |
| Reload images (top-right in Debug) | Reread the mod's HUD PNGs without leaving the world or reloading code |
| i (under Reload images) | Open a brief, scrollable checklist of every feature; Esc returns |
| R | Reset the selected element |
| Shift + R | Open the reset-all confirmation |
| Esc | Save and close the editor |
| Numpad 0 | Toggle The Sixth Sense HUD |

Both keybindings can be reassigned in Minecraft's Controls menu. Individual warning toggles, distance options, and the raid highlight setting are available through **Mod Menu with Cloth Config** or `config/sixthsense.json`.

The interface follows Minecraft's selected language. Translations are included for **English, Simplified Chinese, Hindi, Spanish, Arabic, Turkish, and Azerbaijani**, with English as the fallback.

## Install

1. Install Fabric Loader for **Minecraft 1.21.1** and Fabric API.
2. Put the matching The Sixth Sense JAR in your instance's `mods` folder.
3. Optionally install **Mod Menu and Cloth Config** for the settings screen. The H-key layout editor works independently.

This is a client-side mod; install it on the client. Minecraft 1.21.1 uses Java 21 or newer. The exact loader requirement is recorded in [`fabric.mod.json`](src/main/resources/fabric.mod.json).

## Build from source

Use JDK 25 (the CI version) or another compatible JDK with Java 21 compilation support. The output targets Java 21. On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
./gradlew build
```

The build runs the HUD regression checks and writes the mod to `build/libs/`. Install the normal JAR, rather than the `-sources` JAR. These checks cover animation and layout logic; they do not replace visual testing in Minecraft.

`src/main` contains the mod's code and assets. `src/test` contains automated regression checks run by the build and CI; these are not included in the playable JAR. The explosion and Warden-heart sprites retain the author's original artwork.

The Debug image-reload button reads edited PNGs directly from `src/main/resources` in development. Installed builds can use PNG overrides under `config/sixthsense/visuals/textures/gui/`; missing overrides use the active resource pack/mod. Only the eight HUD images are refreshed. Invalid files leave the current images in place. The thorn image is `src/main/resources/assets/sixthsense/textures/gui/warning_warden_thorns.png` (64×128): the upper 64×64 frame is the base artwork, and the lower frame is a transparent overlay for the blood-red tips. Keep the frame dimensions when editing and save before pressing Reload images. This button does not reload the mod logo, code, language, settings, sounds or worlds.

Local caches, development worlds, IDE files, and build outputs are excluded from Git. Authoring tools, previews, store-page drafts and historical backups are kept outside the repository and are not required to build or run the mod.

## License

**All Rights Reserved — Copyright (c) 2026 Gareyn_.** See [`LICENSE`](LICENSE) for the full terms, including permission for personal gameplay and private modifications. Redistribution, re-uploads and reuse in distributed projects require separate permission except where prior licenses, platform terms or law already grant those rights. This notice does not revoke licenses validly granted for earlier copies. The license is also included in the built JAR.
