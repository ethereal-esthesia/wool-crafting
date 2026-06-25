# WoolCrafting

Paper plugin that registers wool wear crafting recipes for the server.

The plugin adds four wearable wool items:

- `Wool Cap`
- `Wool Jacket`
- `Wool Trousers`
- `Wool Boots`

Each item uses the same recipe geometry as the matching leather armor piece.
Recipes are registered for all 16 wool colors, and every ingredient in a single
craft must use the same wool color. The resulting leather armor item is dyed to
match the wool color, renamed as wool wear, marked unbreakable, and stripped of
visible armor/durability tooltip details.

Wool wear recipes are added to the crafting recipe book under the equipment
category. The plugin unlocks its recipe keys for players when they join, and for
already-online players when the plugin is enabled during a reload.

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
