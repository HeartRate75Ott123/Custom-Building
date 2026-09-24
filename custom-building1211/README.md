# Custom Building / 自定义建筑

A NeoForge 1.21.1 mod that lets you create building blueprint items entirely from a datapack:
structure, preview image, item texture, display name and tooltip are all described by one JSON file.
After `/reload` the blueprint shows up in the **Custom Building** / **自定义建筑** creative tab.

## Datapack layout

```
data/<namespace>/custom_building/blueprint/<name>.json     the blueprint definition
data/<namespace>/structure/<name>.nbt                      the structure to build
assets/<namespace>/textures/...                            the preview image and the item texture
```

The blueprint id is `<namespace>:<name>`.  A non-JSON template file
(`blueprint_template.txt`) ships next to the built-in example; it is never parsed.

## Blueprint definition

```json
{
    "structure": "custom_building:sakura_cottage",
    "preview":   "assets/custom_building/textures/blueprint/sakura_cottage.png",
    "texture":   "assets/custom_building/textures/item/sakura_blueprint.png",
    "name":      "§d🌸樱花小屋蓝图",
    "tooltip":   ["§a右键地面来使用"]
}
```

| field | required | meaning |
|---|---|---|
| `structure` | yes | structure template id; a bare name is resolved against the namespace of the file |
| `preview` | no | image shown while hovering the item; always stretched into a 4:3 box |
| `texture` | no | inventory texture; empty or `default` falls back to `custom_building:textures/item/prefab.png` |
| `name` | no | item name - a translation key, or plain text with legacy `§` colour codes; defaults to the blueprint id |
| `tooltip` | no | one string or an array of strings, same rules as `name` |

Texture paths are forgiving:

```
custom_building:textures/item/sakura_blueprint.png           full resource location
assets/custom_building/textures/item/sakura_blueprint.png    path as it appears in the jar
textures/item/sakura_blueprint.png                           relative to the file's namespace
item/sakura_blueprint.png                                    "textures/" is added for you
item/sakura_blueprint                                        ".png" is added for you
```

## Behaviour

* Right-click the ground with a blueprint to open the building panel (copied from MC-Prefab 1.21.1:
  the same panel textures, the same layout, the same Preview / Cancel / Build buttons).
  * The left panel turns the building around: **朝向 / Facing** cycles 北 → 东 → 南 → 西.  The panel
    opens on the orientation used for the last preview / build, stored in
    `config/custom_building-client.toml`; on first use it faces the player, just like Prefab's houses do.
  * The right panel shows the preview image, stretched into a 4:3 box.
  * **预览！ / Preview!** closes the panel and shows the structure as a translucent **ghost** in the
    world, at the exact position and rotation it will be built.  The rendering follows Litematica's
    ghost overlay: block models are baked once per 16³ section into cached vertex buffers, every quad
    is emitted unlit at full brightness and the whole thing is drawn through the translucent block
    layer with the shader colour alpha at 0.5.
    * **Left click cancels the preview.**
    * **Right click builds it right away**, no panel needed.
    * **Sneak + mouse wheel turns it** in place; the new orientation is remembered for the next panel too.
  * **建造！ / Build!** builds directly from the panel.
* On success the chat reports `建造完成！` / `Structure Complete!` in green and a chime plays.
  On failure a yellow message explains why (protected/unbreakable blocks, spawn protection,
  missing structure file), mirroring MC-Prefab 1.21.1's wording.
* Survival consumes one blueprint per build, creative does not.
* The creative tab always contains one default example item - an unbound blueprint with no behaviour.

The structure blocks travel to the client on demand (`SchematicRequestPayload` /
`SchematicResponsePayload`, palette + var-int relative positions), so the preview also works on a
dedicated server where the client never sees the datapack.

## How it works

Items cannot be added to a registry after the game has started, so every blueprint shares the single
`custom_building:blueprint` item and carries its identity in the `custom_building:blueprint` data
component.  The server owns the authoritative blueprint list (datapack reload listener) and pushes it
to clients on join and on every `/reload`; the client then invalidates
`CreativeModeTabs.CACHED_PARAMETERS` so the creative tab contents are rebuilt - the same technique
KubeJS uses.  Because the item texture is resolved at render time by a
`BlockEntityWithoutLevelRenderer`, no resource pack or generated model is needed for a new blueprint.

## Building

```
gradlew build        # jar in build/libs
gradlew runClient    # dev client
gradlew runServer    # dev server (accept run/eula.txt first)
```

## Credits

Author: Plume Jade.  Blueprint/preview/placement behaviour modelled on
[MC-Prefab](https://github.com/WuestMan/MC-Prefab) 1.21.1; creative tab refresh technique modelled on
[KubeJS](https://github.com/KubeJS-Mods/KubeJS).
