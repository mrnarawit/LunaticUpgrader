# Lunatic Upgrader

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1.x-E0662E)
![License MIT](https://img.shields.io/badge/License-MIT-blue)

An MMORPG-style **+1 to +10 upgrade system** for Minecraft. Put a weapon, tool, bow, shield or armor piece in an anvil
with any Smithing Template and try to push it one level higher. Every level adds stats, a new name color and a star
to its seal. Higher levels are riskier, and a failed upgrade costs a level.

## Features

- Upgrade weapons, tools, bows, crossbows, shields and armor from **+1 to +10**, including items from other mods.
- **Any Smithing Template** works as the upgrade material, together with XP.
- Success chance drops at every level. A failed upgrade **drops the item one level**.
- Stat bonuses for every level, merged into the normal tooltip numbers.
- **+10% max durability** per level on every upgradeable item.
- **Colored names** by level, animated names at +9 and +10, and a different +10 color scheme for each item type.
- **Star Seal effects**: a 10-star ring above the anvil and around the anvil's result slot, custom "Lunar Choir"
  sounds, and a big burst of light when the ring is completed at +10.
- Optional support for **Spell Power Attribute**, **Wizards** and **Bard** (RPG Series).
- Works with **Easy Anvils**.

## How to upgrade

1. Put the item in the **left** slot of an anvil and any **Smithing Template** in the **right** slot.
2. The anvil shows the cost (`Upgrade Cost: N XP`) and the action bar shows the success chance.
3. Take the result. The template and the XP are used up whether the upgrade succeeds or fails.

| Upgrade to | Success | XP cost (points) |
|:---:|:---:|:---:|
| +1 | 95% | 25 |
| +2 | 90% | 50 |
| +3 | 85% | 75 |
| +4 | 80% | 100 |
| +5 | 75% | 125 |
| +6 | 70% | 150 |
| +7 | 65% | 175 |
| +8 | 60% | 200 |
| +9 | 55% | 225 |
| +10 | 50% | 250 |

- **On failure** the item drops one level (an item at +0 stays at +0).
- The cost is in **experience points**, not levels. Going from +0 to +10 without a single failure costs 1,375 points.

## Bonuses per level

| Item | Per level | At +10 |
|---|---|---|
| Weapons and tools | +6% Attack Damage | +60% |
| Pickaxes (extra) | +2% mining speed | +20% |
| Bows and crossbows | +6% arrow damage | +60% |
| Shields (while held) | +0.3 Armor, +0.3 Armor Toughness | +3 / +3 |
| Armor | +0.3 Armor, +0.2 Armor Toughness | +3 / +2 |
| All upgradeable items | +10% max durability | +100% |

The attack bonus is based on the weapon's **real** attack damage, including changes made by datapacks or other mods
(for example NeoAttributeModify), and the tooltip shows one combined number (`7 → 11.2 Attack Damage` at +10).
It is applied as a multiply-base modifier, so flat attack bonuses from other equipment are scaled by it too.

### Optional mod support

| Mod | Effect per level |
|---|---|
| Spell Power Attribute | Armor: +1% `spell_power:resistance.generic` |
| Wizards (RPG Series) | Items tagged `rpg_series:archetype/magic_damage_weapon`: +3% `spell_power:generic` instead of Attack Damage |
| Bard (RPG Series Plus) | Lutes: +4% Arcane, +2% Healing, +2% Attack Damage<br>Lyres: +2% Arcane, +4% Healing, +2% Attack Damage |

None of these mods are required. If one is missing, its bonus is simply skipped.

## Name colors

| Level | Style |
|---|---|
| +1 to +6 | Gray, green, mint, light blue, blue, purple |
| +7 | Purple-to-pink gradient |
| +8 | Pink-to-orange gradient, bold |
| +9 | Bold red with a moving shine |
| +10 | Bold animated colors, by item type |

+10 color schemes: **Weapons** Twilight, **Pickaxes** Fire and Ice, **Shields** Nether Forge, **Bows / crossbows**
Toxic Neon, **Staffs / wands** Mint Candy, **Instruments** Ocean Pearl, **Armor** Obsidian Flame.
The animation plays in tooltips; the name above the hotbar uses a still gradient.
Tooltips also show `Upgrade: n/10`, or `Upgrade: 10 [Maxed]` at +10.

## Star Seal effects

| Result | In the world | In the anvil screen |
|---|---|---|
| Success | Sparks gather and a new star lights up | A shooting star flies into the ring, `+N` floats up |
| +10 | Sparks flow into the last star for 1.8 s, then a flash, shockwave and light pillar | Screen shakes, the ring flashes, the frame glows, a big `+10` |
| Fail | Sparks fizzle and the newest star fades | Gray sparks, the newest star fades, `-1` floats up |

Each star uses the color of its level. Nearby players see the world effects; only the player at the anvil sees the
screen flash and camera shake.

## Commands

Operator only (permission level 2). They affect the item in your main hand.

```
/lunaticupgrader set <0-10>   Set the upgrade level (0 removes the upgrade)
/lunaticupgrader get          Show the current upgrade level
```

## Configuration

| File | Side | Contents |
|---|---|---|
| `<world>/serverconfig/lunaticupgrader-server.toml` | Server (synced to clients) | Bonuses per level, optional mod bonuses, success chance and XP cost per level |
| `config/lunaticupgrader-client.toml` | Client | `particles`, `screenEffects` |

Config files keep their old values when the mod is updated. After an update, edit the file or delete it so the new
defaults are written.

## Installation

- Minecraft **1.21.1** with **NeoForge 21.1.x**
- Install on **both the client and the server** (the mod adds sounds, particles and network packets).

## Building from source

Requires JDK 21.

```
Windows:      .\gradlew.bat build
macOS/Linux:  ./gradlew build
```

The jar is written to `build/libs/lunaticupgrader-1.0.0.jar`. Use `gradlew runClient` to test in a dev client.

## Notes

- To give an upgraded item a custom name, rename it **before** its first upgrade. The anvil's name field is ignored
  while upgrading.
- From +7 upward the name above the hotbar is stored as fixed English text (the gradient needs literal text).
  Tooltips still use your game language.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE). Made by naravit.
