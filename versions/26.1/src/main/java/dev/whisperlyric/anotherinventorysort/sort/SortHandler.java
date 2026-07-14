package dev.whisperlyric.anotherinventorysort.sort;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import dev.whisperlyric.anotherinventorysort.item.ItemWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class SortHandler {

    public static boolean canSort(AbstractContainerScreen<?> screen) {
        return !screen.getMenu().slots.isEmpty();
    }

    public static void sortInventory(AbstractContainerScreen<?> screen, SortMode mode, String category, boolean ignoreLocks) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        AbstractContainerMenu menu = screen.getMenu();
        boolean isPlayerInventory = "PURE_BACKPACK".equals(category);
        List<Integer> sortableSlots = getSortableSlots(menu, isPlayerInventory, category, ignoreLocks);
        if (sortableSlots.isEmpty()) return;

        // Phase 1: Merge same-type items
        mergeSameTypeItems(client, menu, sortableSlots);

        // Phase 2: Re-read items after merge, compute sort target, swap into place
        List<ItemStack> currentItems = new ArrayList<>();
        for (int index : sortableSlots) {
            currentItems.add(menu.getSlot(index).getItem().copy());
        }

        int columns = detectColumns(sortableSlots, menu);
        List<ItemStack> sortedItems = computeSortedItems(currentItems, mode, columns);
        swapIntoPlace(client, menu, sortableSlots, currentItems, sortedItems);
    }

    // Column detection

    private static int detectColumns(List<Integer> sortableSlots, AbstractContainerMenu menu) {
        if (sortableSlots.isEmpty()) return 9;
        // Count slots in the first row by finding slots with the same y coordinate
        int firstY = menu.getSlot(sortableSlots.getFirst()).y;
        int count = 0;
        for (int index : sortableSlots) {
            if (menu.getSlot(index).y == firstY) {
                count++;
            }
        }
        return count > 0 ? count : 9;
    }

    // Merge

    private static void mergeSameTypeItems(Minecraft client, AbstractContainerMenu menu,
                                            List<Integer> slotIndices) {
        int containerId = menu.containerId;

        List<ItemStack> items = new ArrayList<>();
        for (int index : slotIndices) {
            items.add(menu.getSlot(index).getItem().copy());
        }

        // Group by item type
        Map<ItemWrapper, List<Integer>> itemsByType = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                ItemWrapper wrapper = new ItemWrapper(items.get(i));
                itemsByType.computeIfAbsent(wrapper, k -> new ArrayList<>()).add(i);
            }
        }

        for (var entry : itemsByType.entrySet()) {
            List<Integer> positions = entry.getValue();
            if (positions.size() <= 1) continue;

            // Sort by count descending (merge into fullest stacks first)
            positions.sort((a, b) -> items.get(b).getCount() - items.get(a).getCount());

            int targetPos = positions.getFirst();
            for (int i = 1; i < positions.size(); i++) {
                int sourcePos = positions.get(i);
                ItemStack sourceItem = items.get(sourcePos);
                ItemStack targetItem = items.get(targetPos);

                if (sourceItem.isEmpty() || targetItem.getCount() >= targetItem.getMaxStackSize()) {
                    targetPos = sourcePos;
                    continue;
                }

                // Pick up from source, click on target to merge
                client.gameMode.handleContainerInput(containerId,
                        slotIndices.get(sourcePos), 0, ContainerInput.PICKUP, client.player);
                client.gameMode.handleContainerInput(containerId,
                        slotIndices.get(targetPos), 0, ContainerInput.PICKUP, client.player);

                // Update tracking
                int total = sourceItem.getCount() + targetItem.getCount();
                int maxStack = targetItem.getMaxStackSize();
                if (total <= maxStack) {
                    items.set(targetPos, new ItemStack(targetItem.getItem(), total));
                    items.set(sourcePos, ItemStack.EMPTY);
                } else {
                    items.set(targetPos, new ItemStack(targetItem.getItem(), maxStack));
                    items.set(sourcePos, new ItemStack(sourceItem.getItem(), total - maxStack));
                    targetPos = sourcePos;
                }
            }
        }
    }

    // Sort computation

    private static List<ItemStack> computeSortedItems(List<ItemStack> items, SortMode mode, int columns) {
        int totalSlots = items.size();
        int rows = (totalSlots + columns - 1) / columns;

        // Sort items by rule, then collect (merge same-type stacks into full stacks)
        List<ItemStack> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> DefaultSortRule.INSTANCE.compare(new ItemWrapper(a), new ItemWrapper(b)));
        List<ItemStack> combined = combineStacks(sorted);

        // Group same-type items together
        Map<ItemWrapper, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack item : combined) {
            if (item.isEmpty()) continue;
            ItemWrapper wrapper = new ItemWrapper(item);
            groups.computeIfAbsent(wrapper, k -> new ArrayList<>()).add(item);
        }

        // Build group list: each group has an item type and a count of slots it occupies
        List<GroupInfo> groupList = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            groupList.add(new GroupInfo(entry.getKey(), entry.getValue().size()));
        }

        List<ItemStack> result = switch (mode) {
            case COLUMN -> groupInColumns(groups, groupList, columns, rows, totalSlots);
            case ROW -> groupInRows(groups, groupList, columns, rows, totalSlots);
            default -> groupDefault(combined, totalSlots);
        };

        // Fill remaining EMPTY slots with unplaced items (in sorted order)
        if (mode == SortMode.ROW || mode == SortMode.COLUMN) {
            fillRemaining(result, combined);
        }

        return result;
    }

    // Fill remaining EMPTY slots in result with items from combined that weren't placed yet
    private static void fillRemaining(List<ItemStack> result, List<ItemStack> combined) {
        // Collect items already placed in result
        Set<ItemStack> placed = new HashSet<>();
        for (ItemStack item : result) {
            if (!item.isEmpty()) placed.add(item);
        }

        // Collect unplaced items from combined (maintain sorted order)
        List<ItemStack> unplaced = new ArrayList<>();
        boolean[] used = new boolean[combined.size()];
        for (ItemStack r : result) {
            if (r.isEmpty()) continue;
            for (int i = 0; i < combined.size(); i++) {
                if (!used[i] && !combined.get(i).isEmpty() && sameTypeAndCount(r, combined.get(i))) {
                    used[i] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < combined.size(); i++) {
            if (!used[i] && !combined.get(i).isEmpty()) {
                unplaced.add(combined.get(i));
            }
        }

        // Fill EMPTY slots with unplaced items
        int unplacedIdx = 0;
        for (int i = 0; i < result.size() && unplacedIdx < unplaced.size(); i++) {
            if (result.get(i).isEmpty()) {
                result.set(i, unplaced.get(unplacedIdx++));
            }
        }
    }

    // DEFAULT: items placed left-to-right, top-to-bottom (standard row order)
    private static List<ItemStack> groupDefault(List<ItemStack> combined, int totalSlots) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : combined) {
            if (!item.isEmpty()) result.add(item);
        }
        while (result.size() < totalSlots) result.add(ItemStack.EMPTY);
        return result;
    }

    // ROW: same-type items grouped in the same row
    // Each group starts at the beginning of a row. If a group is larger than one row, it spans multiple rows.
    // If remaining space in current row is too small for the group, skip to next row.
    private static List<ItemStack> groupInRows(Map<ItemWrapper, List<ItemStack>> groups,
                                                List<GroupInfo> groupList,
                                                int columns, int rows, int totalSlots) {
        List<ItemStack> result = new ArrayList<>(Collections.nCopies(totalSlots, ItemStack.EMPTY));
        int currentSlot = 0;

        for (GroupInfo group : groupList) {
            List<ItemStack> groupItems = groups.get(group.type);
            int slotsNeeded = groupItems.size();

            // If not at row start and remaining space in current row is less than group size, skip to next row
            int remainingInRow = columns - (currentSlot % columns);
            if (currentSlot % columns != 0 && slotsNeeded > remainingInRow) {
                currentSlot = ((currentSlot + columns - 1) / columns) * columns;
            }

            for (ItemStack item : groupItems) {
                if (currentSlot >= totalSlots) break;
                result.set(currentSlot, item);
                currentSlot++;
            }

            // Small gap: if not at row boundary, skip remaining cells in this row
            // This visually separates groups, but only if there's meaningful remaining space
            if (currentSlot % columns != 0) {
                int nextRowStart = ((currentSlot + columns - 1) / columns) * columns;
                if (nextRowStart <= totalSlots) {
                    currentSlot = nextRowStart;
                }
            }
        }

        return result;
    }

    // COLUMN: same-type items grouped in the same column
    // Each group starts at the top of a column. If a group is larger than one column, it spans multiple columns.
    private static List<ItemStack> groupInColumns(Map<ItemWrapper, List<ItemStack>> groups,
                                                   List<GroupInfo> groupList,
                                                   int columns, int rows, int totalSlots) {
        List<ItemStack> result = new ArrayList<>(Collections.nCopies(totalSlots, ItemStack.EMPTY));
        int currentCol = 0;
        int currentRow = 0;

        for (GroupInfo group : groupList) {
            List<ItemStack> groupItems = groups.get(group.type);
            int slotsNeeded = groupItems.size();

            // If not at column top and remaining space in current column is less than group size, skip to next column
            int remainingInCol = rows - currentRow;
            if (currentRow != 0 && slotsNeeded > remainingInCol) {
                currentCol++;
                currentRow = 0;
            }

            // Place group items going down the column
            for (ItemStack item : groupItems) {
                // Wrap to next column if current is full
                if (currentRow >= rows) {
                    currentCol++;
                    currentRow = 0;
                }
                if (currentCol >= columns) break;

                int slotIndex = currentRow * columns + currentCol;
                if (slotIndex < totalSlots) {
                    result.set(slotIndex, item);
                }
                currentRow++;
            }

            // If not at column top, skip to next column to separate groups visually
            if (currentRow != 0 && currentCol < columns) {
                currentCol++;
                currentRow = 0;
            }
        }

        return result;
    }

    private record GroupInfo(ItemWrapper type, int slotCount) {}

    private static List<ItemStack> combineStacks(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        Map<ItemWrapper, List<ItemStack>> stackMap = new LinkedHashMap<>();

        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            ItemWrapper wrapper = new ItemWrapper(item);
            stackMap.computeIfAbsent(wrapper, k -> new ArrayList<>()).add(item);
        }

        for (List<ItemStack> stacks : stackMap.values()) {
            ItemStack currentStack = null;

            for (ItemStack stack : stacks) {
                if (currentStack == null) {
                    currentStack = stack.copy();
                    continue;
                }

                int maxCount = currentStack.getMaxStackSize();
                int currentCount = currentStack.getCount();
                int canAdd = maxCount - currentCount;

                if (canAdd > 0 && stack.getCount() > 0) {
                    int toAdd = Math.min(stack.getCount(), canAdd);
                    currentStack = new ItemStack(currentStack.getItem(), currentCount + toAdd);
                    int remaining = stack.getCount() - toAdd;
                    if (currentStack.getCount() >= maxCount) {
                        result.add(currentStack);
                        currentStack = remaining > 0 ? new ItemStack(stack.getItem(), remaining) : null;
                    }
                } else {
                    result.add(currentStack);
                    currentStack = stack.copy();
                }
            }

            if (currentStack != null && !currentStack.isEmpty()) {
                result.add(currentStack);
            }
        }

        while (result.size() < items.size()) {
            result.add(ItemStack.EMPTY);
        }

        return result;
    }

    // Swap into place - multi-pass with local state tracking

    private static void swapIntoPlace(Minecraft client, AbstractContainerMenu menu,
                                       List<Integer> slotIndices,
                                       List<ItemStack> currentItems, List<ItemStack> sortedItems) {
        int containerId = menu.containerId;
        int n = currentItems.size();

        for (int pass = 0; pass <= n; pass++) {
            boolean anyChange = false;

            for (int i = 0; i < n; i++) {
                ItemStack cur = currentItems.get(i);
                ItemStack target = sortedItems.get(i);

                if (sameTypeAndCount(cur, target)) continue;

                if (!target.isEmpty()) {
                    int source = findSourceFor(target, currentItems, i);
                    if (source >= 0) {
                        doSwap(client, containerId, slotIndices, source, i);
                        // Track locally: swap source and i
                        ItemStack displaced = currentItems.get(i);
                        currentItems.set(i, currentItems.get(source));
                        currentItems.set(source, displaced);
                        anyChange = true;
                    }
                } else if (!cur.isEmpty()) {
                    int dest = findDestFor(cur, sortedItems, currentItems, i);
                    if (dest >= 0) {
                        doSwap(client, containerId, slotIndices, i, dest);
                        // Track locally: swap i and dest
                        ItemStack displaced = currentItems.get(dest);
                        currentItems.set(dest, currentItems.get(i));
                        currentItems.set(i, displaced);
                        anyChange = true;
                    }
                }
            }

            if (!anyChange) break;
        }
    }

    private static int findSourceFor(ItemStack target, List<ItemStack> currentItems, int excludePos) {
        for (int j = 0; j < currentItems.size(); j++) {
            if (j == excludePos) continue;
            if (sameTypeAndCount(currentItems.get(j), target)) return j;
        }
        for (int j = 0; j < currentItems.size(); j++) {
            if (j == excludePos) continue;
            if (sameType(currentItems.get(j), target)) return j;
        }
        return -1;
    }

    private static int findDestFor(ItemStack item, List<ItemStack> sortedItems,
                                    List<ItemStack> currentItems, int excludePos) {
        for (int j = 0; j < sortedItems.size(); j++) {
            if (j == excludePos) continue;
            if (sameType(sortedItems.get(j), item) && !sameType(currentItems.get(j), sortedItems.get(j))) {
                return j;
            }
        }
        return -1;
    }

    private static void doSwap(Minecraft client, int containerId, List<Integer> slotIndices,
                               int posA, int posB) {
        int srcSlot = slotIndices.get(posA);
        int tgtSlot = slotIndices.get(posB);

        // Full 3-click swap (always safe, 3rd click on empty cursor is no-op):
        // 1. Pick up from A
        // 2. Place at B (picks up B's item)
        // 3. Place B's item at A
        client.gameMode.handleContainerInput(containerId, srcSlot, 0, ContainerInput.PICKUP, client.player);
        client.gameMode.handleContainerInput(containerId, tgtSlot, 0, ContainerInput.PICKUP, client.player);
        client.gameMode.handleContainerInput(containerId, srcSlot, 0, ContainerInput.PICKUP, client.player);
    }

    private static boolean sameTypeAndCount(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    private static boolean sameType(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b);
    }

    // Slot detection

    private static List<Integer> getSortableSlots(AbstractContainerMenu menu, boolean isPlayerInventory, String category, boolean ignoreLocks) {
        List<Integer> slots = new ArrayList<>();

        // GCA fake player inventory: GENERIC_9x6, sortable slots 18-53 (items 0-35, last 4 rows)
        if ("GCA_FAKE_PLAYER_INVENTORY".equals(category)) {
            for (int i = 18; i <= 53 && i < menu.slots.size(); i++) {
                slots.add(i);
            }
            return slots;
        }

        // GCA fake player ender chest: GENERIC_9x6, sortable slots 27-53 (items 0-26, last 3 rows)
        if ("GCA_FAKE_PLAYER_ENDER_CHEST".equals(category)) {
            for (int i = 27; i <= 53 && i < menu.slots.size(); i++) {
                slots.add(i);
            }
            return slots;
        }

        if (isPlayerInventory) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.getSlot(i);
                if (slot.container instanceof Inventory) {
                    int containerSlot = slot.slot;
                    if (containerSlot >= 9 && containerSlot <= 35) {
                        // Skip locked slots unless ignoreLocks is set
                        if (ignoreLocks || !LockSlotManager.isSlotLocked(containerSlot)) {
                            slots.add(i);
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.getSlot(i);
                if (!(slot.container instanceof Inventory)) {
                    slots.add(i);
                }
            }
        }

        return slots;
    }
}
