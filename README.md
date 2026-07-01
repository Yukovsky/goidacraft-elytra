<div align="center">

# 🪂 GoidaCraft Elytra Control

**A server-side NeoForge mod that decides where and how players are allowed to glide on elytras.**

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.228%2B-orange)](https://neoforged.net/)
[![Build](https://github.com/Yukovsky/goidacraft-elytra/actions/workflows/build.yml/badge.svg)](https://github.com/Yukovsky/goidacraft-elytra/actions/workflows/build.yml)

</div>

The mod is entirely server-side: clients don't need to install anything, a plain vanilla launcher
connects just fine. All checks and speed corrections run on the server tick, so behaviour stays
correct even when TPS drops.

## Contents

- [Why this exists](#why-this-exists)
- [Behaviour per dimension](#behaviour-per-dimension)
- [Player-facing messages](#player-facing-messages)
- [Configuration](#configuration)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [How it works internally](#how-it-works-internally)
- [License](#license)

## Why this exists

On survival servers, elytras in the End trivialize long-range flight and turn zones that are
supposed to be risky into safe ones, while in the Overworld they turn travel into unrestrained
firework-boosted speedrunning. This mod surgically corrects that behaviour per dimension without
touching the item itself — elytras can still be crafted, repaired, and worn as armor.

The original implementation was a pair of KubeJS scripts (preserved in the repository's history),
but it had a fundamental flaw: the client could get ahead of the server in movement prediction and
briefly end up with a "phantom" flight state. The Java mod fixes this by forcing a server-side
entity state resync.

## Behaviour per dimension

| Dimension | What happens |
|---|---|
| 🌌 **The End** (`minecraft:the_end`) | Elytras are forbidden entirely, with no exceptions: right-click, shift-click, drag into slot, dispenser, `/item`, other mods — the server forcibly unequips the elytra and instantly stops flight. |
| 🌍 **Overworld** (`minecraft:overworld`) | Flying is allowed, but gliding-down only: vertical speed is always slightly negative, horizontal speed is capped. Fireworks in flight are forbidden, so there's no way to accelerate. |
| 🔥 **The Nether** (`minecraft:the_nether`) | No restrictions — elytras behave normally. |

## Player-facing messages

All notifications go through the action bar, with no chat spam. The mod ships with Russian
messages by default (the source server is Russian-speaking) — override them in your own resource
pack / translation layer if you need another language:

| Situation | Message |
|---|---|
| End, tries to equip/fly | `[Элитры] В Крае надевать элитры запрещено!` |
| End, right-clicks elytra from hotbar | `[Элитры] В Крае элитры не работают!` |
| Overworld, tries to accelerate | `[Элитры] Ускорение заблокировано!` |
| Overworld, starts gliding (once) | `[Элитры] Только планирование вниз` |
| Overworld, firework while flying | `[Элитры] Использование фейерверков на элитрах запрещено!` |

Repeated notifications are throttled by a cooldown (~2 seconds); a direct player action
(right-clicking the elytra) is never throttled, so the response stays instant.

## Configuration

File `config/goidacraft_elytra-server.toml`, auto-synced from the server to clients.

| Key | Default | Description |
|---|---|---|
| `elytraIds` | `minecraft:elytra`, `betterend:elytra_armored`, `betterend:elytra_crystalite` | Registry ids treated as restricted elytras (exact match). |
| `autoDetectByName` | `false` | If `true`, any item with `elytra` in its id is treated as an elytra — but only if it actually grants flight (`canElytraFly`); unrelated items are left alone. |
| `maxHorizontal` | `1.0` | Maximum horizontal speed magnitude in the Overworld, blocks/tick. |
| `maxVerticalUp` | `-0.05` | Vertical speed ceiling (`vy = min(vy, value)`); a value `≤ 0` guarantees descent. |
| `notifyCooldownTicks` | `40` | Cooldown for repeated notifications, in ticks (20 ticks = 1 second). |

## Installation

1. Download the jar from [Releases](https://github.com/Yukovsky/goidacraft-elytra/releases), or build it yourself (see below).
2. Drop the file into your NeoForge 1.21.1 server's `mods/` folder.
3. Start the server once to generate the config, then edit it if needed.

To support **Better End** elytras, also install Better End itself — it's an optional dependency,
everything else works fine without it.

## Building from source

Requires JDK 21.

```bash
./gradlew build        # Linux/macOS
.\gradlew.bat build    # Windows
```

The finished jar lands in `build/libs/goidacraft_elytra-<version>.jar`.

## How it works internally

The checks are split across three independent layers in `ElytraEventHandler`, to cover every path
an elytra could take into the chestplate slot or start accelerating:

1. **Preventive right-click interception** (`PlayerInteractEvent.RightClickItem`) — cancels
   equipping the elytra in the End and launching a firework in flight before the action even applies.
2. **Equipment-change tracking** (`LivingEquipmentChangeEvent`) — catches any other way to equip
   the elytra in the End: shift-click, dragging, dispensers, third-party mods.
3. **Per-tick safety net** (`PlayerTickEvent.Post`) — clamps speed in the Overworld every tick,
   and cleans up anything that slipped past the first two layers in the End.

The key reliability detail: after adjusting speed, the server sets `player.hurtMarked = true`,
which forces a `ClientboundSetEntityMotionPacket` to be sent (the same mechanism vanilla knockback
uses) — the client is required to accept the server's velocity instead of its own prediction. This
is exactly what the original KubeJS version was missing, which is why the client could occasionally
get "stuck" with a phantom flight state for a second or two after the server cancelled an action.

## License

Distributed under the [Apache License 2.0](LICENSE).
