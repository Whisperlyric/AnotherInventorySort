# Another Inventory Sort

A simple Minecraft inventory sorting mod for Fabric.

## Features

- Three sorting modes via GUI buttons:
  - **Default Sort**: Sort items in row-major order, grouping same-type items adjacently
  - **Sort by Row**: Group same-type items in the same row
  - **Sort by Column**: Group same-type items in the same column
- Automatic same-type item merging before sorting
- Compatible with various container types:
  - Storage containers (chests, shulker boxes, dispensers/droppers, horse inventory)
  - Player inventory / crafting interfaces
  - [GCA](https://github.com/Gu-ZT/gugle-carpet-addition) fake player inventory and ender chest

## Supported Versions

| Version | Supported Versions | Status |
|-----------|---------|--------|
| 1.20.1    |1.20-1.20.4 | Supported |
| 1.20.5    |1.20.5-1.20.6 | Supported |
| 1.21.1    |1.21-1.21.1 | Supported |
| 1.21.2    |1.21.2-1.21.5 | Supported |
| 1.21.6    |1.21.6-1.21.8 | Supported |
| 1.21.9    |1.21.9-1.21.10 | Supported |
| 1.21.11   |1.21.11 | Supported |
| 26.1      |26.1-26.2 |  Supported |

## Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Installation

1. Install Fabric Loader for your Minecraft version
2. Download the mod jar from [releases](https://github.com/Whisperlyric/AnotherInventorySort/releases)
3. Place the jar in your `.minecraft/mods/` folder

## Building

```bash
./gradlew buildAll
```

Built jars can be found in `versions/<version>/build/libs/`.

## License

[MIT](LICENSE)
