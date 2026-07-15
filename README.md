# Another Inventory Sort

A simple Minecraft inventory sorting mod for Fabric.

## Features

### Sorting

- Three sorting modes via GUI buttons:
  - **Default Sort**: Sort items in row-major order, grouping same-type items adjacently
  - **Sort by Row**: Group same-type items in the same row
  - **Sort by Column**: Group same-type items in the same column
- Automatic same-type item merging before sorting
- Position-based sorting via middle mouse button (configurable): sort the area under the cursor
- Compatible with various container types:
  - Storage containers (chests, shulker boxes, dispensers/droppers, horse inventory)
  - Player inventory / crafting interfaces
  - [GCA](https://github.com/Gu-ZT/gugle-carpet-addition) fake player inventory and ender chest

### Slot Locking (1.1.0)

- Lock individual player inventory slots (hotbar 0-8, main inventory 9-35, offhand 40)
- Default modifier key: Left Alt (configurable in Controls → Another Inventory Sort)
  - Left-click a slot while holding the modifier to lock/unlock
  - Drag across slots while holding the modifier to lock/unlock multiple at once
  - Locking is only allowed when the cursor is empty
- Locked slots block all item interactions:
  - PICKUP, QUICK_MOVE (shift+click), THROW (Q drop), SWAP (number keys)
  - CLONE (creative middle-click), QUICK_CRAFT (drag), PICKUP_ALL (double-click collect)
  - Blocks ItemScroller / Mouse Tweaks batch operations via `handleContainerInput` interception
- Auto-pickup protection: items that enter locked slots (via PICKUP_ALL or mod batch operations) are automatically moved out
  - Same-type merges remove only the excess count; full-stack insertions move the whole stack out
- Per-server persistence: lock state is saved separately for each server
- Lock indicator: red tint + lock icon on locked slots

### Sort Refactor (1.1.0)

- Refactored sort pipeline with a sandbox-preview cursor executor (merge + swap in one pass)
- Obstacle-aware group calculator (ported from IPN's `GroupInColumnsCalculator`)
- Locked slots are treated as obstacles during sorting and do not participate in allocation
- Deterministic comparator for stable sorting of NBT items (e.g., shulker boxes)

## Supported Versions

| Version | Supported Versions | Status    |
|---------|--------------------|-----------|
| 1.20.1  | 1.20-1.20.4        | Supported |
| 1.20.5  | 1.20.5-1.20.6      | Supported |
| 1.21.1  | 1.21-1.21.1        | Supported |
| 1.21.2  | 1.21.2-1.21.5      | Supported |
| 1.21.6  | 1.21.6-1.21.8      | Supported |
| 1.21.9  | 1.21.9-1.21.10     | Supported |
| 1.21.11 | 1.21.11            | Supported |
| 26.1    | 26.1-26.2          | Supported |

## Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Installation

1. Install Fabric Loader for your Minecraft version
2. Download the mod jar from [releases](https://github.com/Whisperlyric/AnotherInventorySort/releases)
3. Place the jar in your `.minecraft/mods/` folder

Built jars can be found in `versions/<version>/build/libs/`.

## License

[MIT](LICENSE)
