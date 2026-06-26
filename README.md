# WoolCrafting

Paper plugin that registers wool wear crafting recipes for the server.

The plugin adds four wearable wool items:

- `Wool Cap`
- `Wool Jacket`
- `Wool Trousers`
- `Wool Boots`

It also adds:

- `Woven Saddle`, a normal saddle with a custom name
- `Woven Sac`, a normal bundle with a custom name

Each item uses the same recipe geometry as the matching leather armor piece.
Recipes are registered for all 16 wool colors, and every ingredient in a single
craft must use the same wool color. The resulting leather armor item is dyed to
match the wool color, renamed as wool wear, marked unbreakable, and stripped of
visible armor/durability tooltip details.

Wool wear recipes are added to the crafting recipe book under the equipment
category. The plugin unlocks its recipe keys for players when they join, and for
already-online players when the plugin is enabled during a reload.

The woven saddle recipe unlocks when a player receives honeycomb or receives a
`Woven Saddle`. Players who already have either item in their inventory also get
the recipe when they join or when the plugin is enabled.

The woven sac recipe unlocks when a player receives wool or receives a
`Woven Sac`. Players who already have either item in their inventory also get the
recipe when they join or when the plugin is enabled.

The woven saddle recipe is:

```text
Honeycomb  Honeycomb  Honeycomb
Wool       Any Slab   Wool
```

The woven sac recipe is:

```text
String  Wool  String
Wool          Wool
Wool    Wool  Wool
```

## Tailors

Shepherd and leatherworker villagers are treated as tailors by this plugin.
Existing loaded villagers, newly spawned villagers, and villagers loaded with
chunks are given the visible custom name `Tailor`, and their trades are rewritten
when possible.

Shepherd trade replacements:

- colored wool sales become `Cloth Boots` sales for 12-24 emeralds
- colored carpet sales become `Cloth Cap` sales for 12-24 emeralds
- colored bed sales become `Cloth Vest` sales for 12-24 emeralds
- banner/map-marker sales become `Cloth Leggings` sales for 12-24 emeralds

These shepherd cloth trades always sell white cloth gear.

Leatherworker trade replacements:

- leather purchases become string purchases
- rabbit hide purchases become `Woven Sac` sales for 16-24 emeralds
- saddle sales become `Woven Saddle` sales
- leather armor sales become the matching white wool wear item

The vanilla profession label in the client trade UI still comes from Minecraft's
language files; changing that label globally would require a resource pack.

## Build

```bash
./scripts/build-release.sh
```

The release jar is named:

```text
build/libs/WoolCrafting-<pluginVersion>-paper-<paperApiVersion>.jar
```

## Release Pinning

Release metadata is pinned in `gradle.properties`.

The `Release for Paper Version` GitHub workflow accepts a Paper version and full
Paper API dependency version, updates the pin, bumps the plugin patch version
when the Paper pin changes, commits that change to `main`, and creates a GitHub
release with the built jar.

If `pluginVersion` is unchanged but `main` has new code, the workflow creates a
commit-suffixed release tag such as `v0.2.1-gabc1234def56`. The server deployer
downloads the release that targets the current `main` commit, so pushed plugin
changes are not skipped just because the version was not bumped.
