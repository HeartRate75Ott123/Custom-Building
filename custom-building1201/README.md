# Custom Building — Minecraft 1.20.1 (Forge)

This is the 1.20.1 / Forge 47.x build of Custom Building.  It behaves exactly like the 1.21.1 build in
`../custom-building1211`: the datapack format, the creative tab, the build panel and the ghost preview are
all the same, so a datapack written for one version works in the other.

## Building

```bash
./gradlew build        # jar in build/libs, reobfuscated for production
./gradlew runClient    # dev client
./gradlew runServer    # dev server
```

Requires JDK 17 (the toolchain is declared in `build.gradle`, Gradle will fetch it if needed).

## Datapack format

```
data/<namespace>/custom_building/blueprint/<name>.json     the blueprint definition
data/<namespace>/structure/<name>.nbt                      the structure to build
assets/<namespace>/textures/...                            the preview image and the item texture
```

```json
{
    "structure": "custom_building:initial_house",
    "preview":   "assets/custom_building/textures/blueprint/initial_house.png",
    "texture":   "assets/custom_building/textures/item/initial_house.png",
    "name":      "§6初始小屋蓝图",
    "tooltip":   ["§a右键地面来使用"]
}
```

`structure` is required; the other fields are optional.  `name` and `tooltip` accept either a translation
key or plain text with legacy `§` colour codes.  See `custom-building1211/README.md` for the full field and
path-resolution reference - it is identical.

## Behaviour

Right-click the ground with a blueprint to open the building panel (same layout and textures as the
1.21.1 build, copied from MC-Prefab).  Choose the orientation on the left, **预览！** to show the
translucent ghost of the building in the world, then:

* **left click** cancels the preview,
* **right click** builds it right away,
* **sneak + mouse wheel** turns it in place.

The chosen orientation is remembered in `config/custom_building-client.toml`.

### Blocks this version does not know

The structure file is read by the mod itself instead of by vanilla's `StructureTemplate`, because vanilla
parses the palette with `getOrThrow`: one block id that does not exist here - a block from an optional mod,
or a block that was renamed after this version - makes the whole template fail to load and the blueprint
becomes unusable.

Instead, unknown blocks are treated as **air**: the spot they occupy is cleared when the blueprint is built,
and the missing ids are listed once in the log.  This also means the built-in cottage still builds without
`kaleidoscope_cookery`, `sophisticatedstorage`, `enchantinginfuser`, `exposure` and `easyanvils` (its kitchen
furniture is simply absent), and that `minecraft:short_grass` from a newer datapack maps back to
`minecraft:grass`.

Vanilla's behaviour of clearing the space the structure occupies is kept: the structure nbt stores air for
every empty position in its bounding box, and those positions are cleared as well.

## Differences from the 1.21.1 build

The gameplay is the same; only the plumbing differs, because 1.20.1 predates several of the APIs the
1.21.1 build uses:

| | 1.21.1 (`custom-building1211`) | 1.20.1 (this project) |
|---|---|---|
| blueprint identity | `custom_building:blueprint` data component | NBT tag `Blueprint` on the stack |
| item name | `DataComponents.ITEM_NAME` | `display.Name` with an explicit non italic style |
| tooltip lines | `DataComponents.LORE` | added in `appendHoverText`, so vanilla does not wrap them in brackets |
| networking | `CustomPacketPayload` + `StreamCodec` | Forge `SimpleChannel` |
| creative tab refresh | invalidate the vanilla tab cache (access transformer) | rebuild our own tab with the public `CreativeModeTab#buildContents` |
| item renderer | `RegisterClientExtensionsEvent` | `Item#initializeClient` |
| written book | `WrittenBookContent` component | `title` / `author` / `pages` NBT |
| mod loader | NeoForge 21.1.238 | Forge 47.4.10 |
