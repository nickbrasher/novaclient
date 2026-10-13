# Nova Client

A clean, Lunar-inspired mod menu for Minecraft, built as a client-side Fabric mod.
Press **Right Shift** to open a single animated menu where you can toggle modules,
tweak their settings, and bind them to keys. Every setting is saved automatically.

## Screenshots

> _Screenshots coming soon._
>
> <!-- Add images to docs/ and reference them here, e.g. ![Menu](docs/menu.png) -->

## Features

**Menu**
- Category sidebar (All, HUD, PvP, Visual, Utility), search, and a module card grid
- Per-module settings page with switches, sliders, option pickers, RGBA color pickers, and keybind buttons
- Smooth open/close, page, and hover animations; optional background blur
- Custom accent color (Settings tab)

**HUD editor** (Right Alt)
- Drag HUD elements anywhere, resize from a corner, snap to edges, centers, and other elements
- Right-click an element to jump to its settings; positions are saved as screen fractions, so they survive window resizes

**Modules**

| Module | Category | What it does |
|---|---|---|
| FPS Display | HUD | Shows your frame rate |
| Coordinates | HUD | Shows XYZ, plus optional facing direction and biome |
| Armor HUD | HUD | Shows your armor and held item with remaining durability (vertical or horizontal) |
| Active Mods | HUD | Lists enabled modules, sliding in and out as they toggle |
| CPS Display | PvP | Shows left (and optionally right) clicks per second |
| Zoom | Visual | Hold **C** to zoom, with smooth zoom and reduced mouse sensitivity |
| Toggle Sprint | Utility | Press sprint once to keep sprinting |

Every toggleable module also has a **Keybind** setting: click it, press a key, and that key toggles the
module in-game. Press Esc or Backspace while binding to clear it.

**Config**

Everything is saved to `.minecraft/config/novaclient.json`, including module on/off state, settings,
keybinds, and HUD positions. Saves happen in the background and are written atomically, so a crash
can't corrupt the file.

## Supported version

| | |
|---|---|
| Minecraft | 26.2 |
| Loader | Fabric Loader 0.19.5 or newer |
| Requires | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Java | 25 or newer |

Nova Client is client-side only; it doesn't need to be installed on servers.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Put the Fabric API jar and the Nova Client jar (`novaclient-<version>.jar`) in your `.minecraft/mods` folder.
3. Launch the game with the Fabric profile and press **Right Shift**.

Both menu keys can be changed in **Options → Controls → Key Binds → Nova Client**.

## Build from source

Requires a JDK 25 or newer.

```bash
git clone https://github.com/nickbrasher/novaclient.git
cd novaclient
./gradlew build        # the mod jar ends up in build/libs/
./gradlew runClient    # launches a development client with the mod loaded
```

## Project layout

```
src/client/java/com/novaclient/
├── NovaClient.java      entrypoint: keybinds, tick and HUD hooks
├── config/              JSON config load/save
├── gui/                 the menu screen, drawing helpers, animations
├── hud/                 HUD element base class, HUD renderer, HUD editor
├── mixin/               small hooks into Minecraft (keyboard, mouse, camera FOV)
└── module/              module base class, settings, and the modules themselves (impl/)
```

To add a module, extend `Module` (or `HudElement` for something drawn on screen), add settings with
`addSetting(...)`, and register it in `ModuleManager`'s constructor.

## License

[MIT](LICENSE)
