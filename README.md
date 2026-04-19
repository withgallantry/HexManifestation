# Manifestation

A companion mod for [Hex Casting](https://github.com/FallingColors/HexMod) on Fabric. Add two new Hex patterns that let you build interactive menus out of stacked iotas.

## What it does

Manifestation adds two Hex Casting operators:

- **`CREATE_LIST_MENU`** — signature `aqwqa` starting east. Pops a title and a list of buttons off the stack, opens a vertical-list menu on the caster's screen.
- **`CREATE_GRID_MENU`** — signature `aqwqaqwqa` starting east. Pops a column count, a title, and a list of buttons. Opens a grid-layout menu with that many columns.

Clicking a button casts the patterns that button was built with, as ordinary Hex casts.

## Installation

Install on both the client and the server. Manifestation declares both entrypoints and requires Hex Casting and Fabric Language Kotlin as dependencies.

## Stack shape

For `CREATE_LIST_MENU`:

```
top → title (any iota; its display() is the title text)
      [ button, button, ... ]
```

For `CREATE_GRID_MENU`:

```
top → columns (number iota, clamped 1..10)
      title
      [ button, button, ... ]
```

Each button is itself a 2-element list:

```
[ [pattern, pattern, ...], label ]
  ^--- list of patterns      ^--- any iota; its display() is the button label
```

## Error handling

- Stack too shallow → mishap (standard Hex `NotEnoughArgs`).
- Title slot wrong shape → no mishap (title accepts any iota and uses `display()`).
- Outer button-list isn't a list → mishap (`InvalidIota`).
- Column count isn't a number (grid only) → mishap.
- No player caster is available (for example an unbound circle/impetus trigger) → mishap with message "Manifestation requires a caster's will."
- Individual button malformed (wrong shape, bad pattern, empty action list) → silently skipped. The menu opens with the valid buttons only.

## Design notes

**Pattern signature audit.** Both triggers (`aqwqa` and `aqwqaqwqa`) were audited against the full pattern lists of vanilla Hex Casting plus Hexal, Hexical, Hexpose, Slateworks, Complex Hex, Ioticblocks, and Hexic Planes. Both are unused and geometrically valid in every legal start direction.

**String iotas.** Vanilla Hex Casting doesn't ship a dedicated string iota — `StringIota` lives in the MoreIotas addon. To keep Manifestation dependency-free beyond base Hex, the title and button labels accept any iota and use its `display()` component. In practice:
- With MoreIotas installed, you push actual strings for labels.
- Without it, you can push anything — a number, a pattern, an entity ref — and its `display()` value will appear on the button.

**Server-side state.** The menu definition lives on the Hex stack, which lives on the server. The operators read it there, then send a packaged `MenuPayload` to the caster's client via a custom S2C packet. The client renders the menu. If no player caster exists for the current cast context, menu creation mishaps. Button clicks re-enter Hex's pipeline as standard `MsgNewSpellPatternC2S` casts — indistinguishable from a player drawing those patterns in the staff GUI.
This was done because without using patterns for intents, it got quickly complicated to manage. E.g. lists of lists that contain different iotas like strings, numbers etc in specific orders to denote interactive UI elements. It also made it difficult to detect when ill-formed ui elements were being created.

## Architecture (auto-generated)

```
src/main/java/com/bluup/manifestation/
├── Manifestation.java                  — mod-id + logger, both-sides
├── common/                              — shared data + packet ID
│   ├── ManifestationNetworking.java
│   └── menu/
│       ├── MenuPayload.java             — serializable: title + entries + layout
│       ├── MenuEntry.java               — serializable: label + pattern list
│       └── StoredPattern.java           — serializable: signature + start dir
├── client/
│   ├── ManifestationClient.java         — client entrypoint, S2C receiver
│   ├── ActiveMenuState.java             — "one menu at a time"
│   └── menu/
│       ├── ui/MenuScreen.java           — list/grid modal
│       └── execution/PatternExecutor.java — button-click dispatch

src/main/kotlin/com/bluup/manifestation/
└── server/
    ├── ManifestationServer.kt           — mod init, registers the two actions
    └── action/
        ├── MenuReader.kt                — stack-reading + validation
        ├── OpCreateListMenu.kt          — LIST operator
        └── OpCreateGridMenu.kt          — GRID operator
```

The mixin machinery from v1 is gone. The mod is now a "normal" Hex Casting addon — its patterns go through Hex's registry like everyone else's.

## Building

1. `hexcasting_version` in `gradle.properties` should match the Hex build you're targeting. Default: `0.11.3`.
2. `./gradlew build`
3. Jar lands in `build/libs/`.

## Documentation site (GitHub Pages)

This repo now includes a GitHub Pages docs site sourced from `docs/`.

- Homepage source: `docs/index.md`
- Deploy workflow: `.github/workflows/docs-pages.yml`

Deployment runs on pushes to `main` and can also be run manually from the Actions tab.

## Future-proofing

The payload/packet layer and the menu model are stable. Things you could add without touching them:
- **More layouts** — add a new value to `MenuPayload.Layout` and a new branch in `MenuScreen.init()`. Everything else stays the same.
- **Richer entries** — `MenuEntry` currently has label + patterns. Adding icon, tooltip, color would mean extending that struct and the `write/read` methods on both sides.
- **Pre-registered menus** — if you want code-defined menus alongside the Hex-defined ones, add a registry on the client and have your own trigger route to it. Doesn't interact with the Hex operators at all.
