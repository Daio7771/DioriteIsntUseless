# DIU - Diorite Isn't Useless

![Logo](https://github.com/Daio7771/DioriteIsntUseless/blob/main/banner.png)


This mod takes the diorite and turns it into something useful. No more hate for diorite... not yet. You can build a house instantly using the dioritine axe or go deeper... much deeper using the dioritine pickaxe, but be careful, don't overuse d...

This mod is open-source, you can contribute.

> [!WARNING]
>
> This mod contains psychological horror elements and unsettling sounds.
>
> Nothing in this mod will harm your computer, your files, or your worlds.
> There are no jump scares or flashing lights.
>
> But some things might not be what they seem.
>
> You can disable these elements in the mod config, called "Abuse mode".

## Features

- **Stronger diorite:** 2× blast resistance (6 → 12), slightly harder to mine.
- **Diorite Crystal:** smelt 1 diorite → 6 crystals.
- **Dioritine Ingot:** 6 crystals → 1 ingot.
- **Dioritine Axe:** fells whole trees. Sneak for one log. Useless for anything else.
- **Dioritine Pickaxe:** mines 3×3 in stone and ores. Sneak for one block. Useless for anything else.
- **Two advancements.** Find out how.


## Requirements

- Minecraft 1.20.1
- Fabric API
- Fabric Language Kotlin
- Cloth Config (optional)
- Mod Menu (optional)

## Configuration

Edit `config/dioriteisntuseless.json`, or use the in-game screen with **Cloth Config** + **Mod Menu** installed.

- Diorite hardness and blast resistance
- Tree felling and 3×3 mining (on/off, sneak behaviour, drops)
- Tool durability and enchantment limit
- Abuse mode

Server operators can apply changes without restarting: `/diu reload`

## Building from source

Requires **JDK 17** (Gradle downloads it automatically if it's missing).

```bash
git clone https://github.com/Daio7771/DioriteIsntUseless.git
cd DioriteIsntUseless
./gradlew build
```

The mod jar is in `build/libs/` (use the one **without** `-sources`).

To test it in a dev client: `./gradlew runClient`

On Windows, use `gradlew.bat` instead of `./gradlew`.

## AI disclosure

The idea, the design and the logo are my own. The code and the in-game assets were generated with AI (Claude) following my direction, and I tested and refined everything in game.

## License

This mod is licensed under the [MIT License](LICENSE.txt).
