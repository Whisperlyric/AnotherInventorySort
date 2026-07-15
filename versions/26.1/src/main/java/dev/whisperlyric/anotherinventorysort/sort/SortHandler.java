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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sort handler (refactored).
 *
 * Pipeline:
 * 1. Collect non-locked items → merge via HashMap (in-memory, no clicks)
 * 2. Sort the type list (deterministic comparator)
 * 3. Pack into a list of stacks
 * 4. GroupCalculator allocates target slots (obstacle-aware)
 * 5. Build the target state
 * 6. Unified cursor executor (merge + swap in one pass, sandbox-previewed)
 *
 * Locked slots are fully transparent: they do not participate in merge/sort/place
 * and live outside the sortableSlots index space.
 */
public class SortHandler {

    public static boolean canSort(AbstractContainerScreen<?> screen) {
        return !screen.getMenu().slots.isEmpty();
    }

    public static void sortInventory(AbstractContainerScreen<?> screen, SortMode mode,
                                      String category, boolean ignoreLocks) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        AbstractContainerMenu menu = screen.getMenu();
        boolean isPlayerInventory = "PURE_BACKPACK".equals(category);

        // Get sortable slots (menu slot index), including locked slots for position tracking
        List<Integer> sortableSlots = getSortableSlots(menu, isPlayerInventory, category);
        if (sortableSlots.isEmpty()) return;

        // Identify locked slots (within the sortableSlots index space)
        Set<Integer> lockedIndices = new HashSet<>();
        if (isPlayerInventory && !ignoreLocks) {
            for (int i = 0; i < sortableSlots.size(); i++) {
                Slot slot = menu.getSlot(sortableSlots.get(i));
                if (slot.container instanceof Inventory && LockSlotManager.isSlotLocked(slot.slot)) {
                    lockedIndices.add(i);
                }
            }
        }

        // sortableSlots is the active slot space (includes locked; locked are passed as obstacles to GroupCalculator)
        List<Integer> activeSlots = sortableSlots;
        if (activeSlots.size() == lockedIndices.size()) return; // all locked

        // Detect container column/row count
        int columns = detectColumns(sortableSlots, menu);
        int totalSlots = sortableSlots.size();
        int rows = (totalSlots + columns - 1) / columns;

        // Step 1: Collect non-locked items → merge via HashMap + record stack-count distribution per type
        Map<ItemTypeKey, Integer> bucket = new HashMap<>();
        Map<ItemTypeKey, List<Integer>> typeStackCounts = new HashMap<>();
        for (int i = 0; i < activeSlots.size(); i++) {
            if (lockedIndices.contains(i)) continue;
            ItemStack stack = menu.getSlot(activeSlots.get(i)).getItem();
            if (!stack.isEmpty()) {
                ItemTypeKey key = new ItemTypeKey(stack);
                bucket.merge(key, stack.getCount(), Integer::sum);
                typeStackCounts.computeIfAbsent(key, k -> new ArrayList<>()).add(stack.getCount());
            }
        }
        if (bucket.isEmpty()) return;

        // Step 2: Sort the type list
        List<ItemTypeKey> sortedTypes = new ArrayList<>(bucket.keySet());
        sortedTypes.sort((a, b) -> DefaultSortRule.INSTANCE.compare(
                new ItemWrapper(a.sampleStack), new ItemWrapper(b.sampleStack)));

        // Step 3-4: GroupCalculator allocates target slots
        // Build groupSizes (stack count per type, in the same order as sortedTypes)
        List<Integer> groupSizes = new ArrayList<>();
        List<ItemTypeKey> groupTypes = new ArrayList<>();
        for (ItemTypeKey type : sortedTypes) {
            int stackCount = SortUtils.pack(bucket.get(type), type.sampleStack.getMaxStackSize()).size();
            if (stackCount > 0) {
                groupSizes.add(stackCount);
                groupTypes.add(type);
            }
        }

        // GroupCalculator works in the columns×rows space of sortableSlots.
        // lockedIndices are obstacles; no items are allocated to locked positions.
        List<List<Integer>> targetSlots = GroupCalculator.calculate(
                groupSizes, columns, rows, lockedIndices, mode);

        // Step 5: Build the target state.
        // goalStacks[i] = the ItemStack that should be at activeSlots.get(i).
        // Locked slots keep their original items (untouched).
        ItemStack[] goalStacks = new ItemStack[activeSlots.size()];
        for (int i = 0; i < goalStacks.length; i++) {
            if (lockedIndices.contains(i)) {
                goalStacks[i] = menu.getSlot(activeSlots.get(i)).getItem().copy();
            } else {
                goalStacks[i] = ItemStack.EMPTY;
            }
        }

        // Build goal per group independently: when current stack count == pack stack count,
        // keep the current count distribution (avoids infinite loops).
        for (int g = 0; g < groupTypes.size(); g++) {
            List<Integer> slots = targetSlots.get(g);
            ItemTypeKey type = groupTypes.get(g);
            List<Integer> packCounts = SortUtils.pack(bucket.get(type),
                    type.sampleStack.getMaxStackSize());
            List<Integer> currentCounts = typeStackCounts.get(type);

            // Decide the goal stack-count distribution
            List<Integer> goalCounts;
            if (currentCounts != null && currentCounts.size() == packCounts.size()) {
                // current is already at the minimum stack count: keep original distribution (pure swap, avoids loops)
                goalCounts = currentCounts;
            } else {
                // current stack count > pack stack count: merge needed, use pack result
                goalCounts = packCounts;
            }

            for (int s = 0; s < slots.size() && s < goalCounts.size(); s++) {
                int slotIdx = slots.get(s);
                if (slotIdx < goalStacks.length) {
                    goalStacks[slotIdx] = type.sampleStack.copy();
                    goalStacks[slotIdx].setCount(goalCounts.get(s));
                }
            }
        }

        // Step 6: Unified cursor executor (skips locked slots)
        executeWithCursor(client, menu, activeSlots, goalStacks, lockedIndices);
    }

    /**
     * Lock-aware unified cursor executor.
     *
     * Uses only left-click PICKUP; merges and swaps in one pass:
     * - cursor empty: pick up an "excess" item (skip locked slots)
     * - cursor non-empty: find a target to place/merge/swap (skip locked slots)
     *
     * Sandbox preview: currentStacks[] tracks state in memory.
     */
    private static void executeWithCursor(Minecraft client, AbstractContainerMenu menu,
                                           List<Integer> activeSlots, ItemStack[] goalStacks,
                                           Set<Integer> lockedIndices) {
        int containerId = menu.containerId;
        int n = activeSlots.size();

        ItemStack[] current = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            current[i] = menu.getSlot(activeSlots.get(i)).getItem().copy();
        }

        ItemStack cursor = ItemStack.EMPTY;
        int loopGuard = 0;
        int maxLoops = n * 4 + 100;

        while (hasMismatch(current, goalStacks, lockedIndices) || !cursor.isEmpty()) {
            if (++loopGuard > maxLoops) break; // safety guard

            if (cursor.isEmpty()) {
                int pickIdx = findExcessSlot(current, goalStacks, lockedIndices);
                if (pickIdx < 0) break;

                if (client.player != null) {
                    client.gameMode.handleContainerInput(containerId,
                            activeSlots.get(pickIdx), 0, ContainerInput.PICKUP, client.player);
                }
                cursor = current[pickIdx];
                current[pickIdx] = ItemStack.EMPTY;
            } else {
                int targetIdx = findTargetSlot(cursor, current, goalStacks, lockedIndices);

                if (targetIdx < 0) {
                    // No same-type target: place in an empty slot
                    targetIdx = findEmptySlot(current, lockedIndices);
                }
                if (targetIdx < 0) {
                    // Full container, no empty slot: swap with a misplaced different-type slot (transit strategy)
                    targetIdx = findSwapSlot(cursor, current, goalStacks, lockedIndices);
                }
                if (targetIdx < 0) break; // Truly unresolvable — exit to avoid infinite loop

                if (client.player != null) {
                    client.gameMode.handleContainerInput(containerId,
                            activeSlots.get(targetIdx), 0, ContainerInput.PICKUP, client.player);
                }

                // Simulate MC left-click behavior
                ItemStack dst = current[targetIdx];
                if (dst.isEmpty()) {
                    current[targetIdx] = cursor;
                    cursor = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(cursor, dst)
                        && dst.getCount() < dst.getMaxStackSize()) {
                    int total = cursor.getCount() + dst.getCount();
                    int max = dst.getMaxStackSize();
                    if (total <= max) {
                        current[targetIdx] = dst.copy();
                        current[targetIdx].setCount(total);
                        cursor = ItemStack.EMPTY;
                    } else {
                        current[targetIdx] = dst.copy();
                        current[targetIdx].setCount(max);
                        cursor = cursor.copy();
                        cursor.setCount(total - max);
                    }
                } else {
                    current[targetIdx] = cursor;
                    cursor = dst;
                }
            }
        }
    }

    /**
     * Check whether any position is still mismatched (skip locked).
     */
    private static boolean hasMismatch(ItemStack[] current, ItemStack[] goal, Set<Integer> locked) {
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (itemStackEquals(current[i], goal[i])) return true;
        }
        return false;
    }

    /**
     * Find an "excess" item position (pick up when cursor is empty, skip locked).
     * Priority:
     * 1. Current has item but goal is empty (fully excess)
     * 2. Current type != goal type (wrong position)
     * 3. Current count > goal count (count excess)
     */
    private static int findExcessSlot(ItemStack[] current, ItemStack[] goal, Set<Integer> locked) {
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (!current[i].isEmpty() && goal[i].isEmpty()) return i;
        }
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (!current[i].isEmpty() && !goal[i].isEmpty()
                    && !ItemStack.isSameItemSameComponents(current[i], goal[i])) return i;
        }
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (!current[i].isEmpty() && !goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(current[i], goal[i])
                    && current[i].getCount() > goal[i].getCount()) return i;
        }
        // Fallback: any mismatched non-empty position
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (!current[i].isEmpty() && itemStackEquals(current[i], goal[i])) return i;
        }
        return -1;
    }

    /**
     * Find a target position for the cursor item (skip locked).
     * Priority (avoids infinite loops: prefer exact count match first):
     * 1. Goal same type + current empty + goal.count == cursor.count → perfect place
     * 2. Goal same type + current same type not full + merged == goal.count → merge to correct count
     * 3. Goal same type + current empty + goal.count != cursor.count → place (cursor has remainder or deficit)
     * 4. Goal same type + current same type not full + merged <= goal.count → merge (deficit)
     * 5. Goal same type + current different type → swap
     */
    private static int findTargetSlot(ItemStack cursor, ItemStack[] current, ItemStack[] goal,
                                       Set<Integer> locked) {
        int cursorCount = cursor.getCount();
        for (int i = 0; i < goal.length; i++) {
            if (locked.contains(i)) continue;
            if (!goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, goal[i])
                    && current[i].isEmpty()
                    && goal[i].getCount() == cursorCount) return i;
        }
        for (int i = 0; i < goal.length; i++) {
            if (locked.contains(i)) continue;
            if (!goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, goal[i])
                    && !current[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, current[i])
                    && current[i].getCount() < current[i].getMaxStackSize()
                    && current[i].getCount() + cursorCount == goal[i].getCount()) return i;
        }
        for (int i = 0; i < goal.length; i++) {
            if (locked.contains(i)) continue;
            if (!goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, goal[i])
                    && current[i].isEmpty()) return i;
        }
        for (int i = 0; i < goal.length; i++) {
            if (locked.contains(i)) continue;
            if (!goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, goal[i])
                    && !current[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, current[i])
                    && current[i].getCount() < current[i].getMaxStackSize()
                    && current[i].getCount() + cursorCount <= goal[i].getCount()) return i;
        }
        for (int i = 0; i < goal.length; i++) {
            if (locked.contains(i)) continue;
            if (!goal[i].isEmpty()
                    && ItemStack.isSameItemSameComponents(cursor, goal[i])
                    && !current[i].isEmpty()
                    && !ItemStack.isSameItemSameComponents(cursor, current[i])) return i;
        }
        return -1;
    }

    /**
     * Find an empty slot (skip locked).
     */
    private static int findEmptySlot(ItemStack[] current, Set<Integer> locked) {
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (current[i].isEmpty()) return i;
        }
        return -1;
    }

    /**
     * Full-container fallback: swap with a misplaced different-type slot (transit strategy).
     * Condition: current[i] != goal[i] (misplaced) and current[i] is a different type from cursor.
     * After swap, cursor becomes current[i]; the old cursor item is temporarily placed at slot i.
     */
    private static int findSwapSlot(ItemStack cursor, ItemStack[] current, ItemStack[] goal,
                                     Set<Integer> locked) {
        for (int i = 0; i < current.length; i++) {
            if (locked.contains(i)) continue;
            if (current[i].isEmpty()) continue;
            // Misplaced and different type
            if (itemStackEquals(current[i], goal[i])
                    && !ItemStack.isSameItemSameComponents(cursor, current[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * ItemStack equality check (type + components + count).
     */
    private static boolean itemStackEquals(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return false;
        if (a.isEmpty() || b.isEmpty()) return true;
        return !ItemStack.isSameItemSameComponents(a, b) || a.getCount() != b.getCount();
    }

    /**
     * Item type key (used for HashMap merging).
     * Equality is based on item + components, excluding count.
     */
    private static class ItemTypeKey {
        final ItemStack sampleStack;

        ItemTypeKey(ItemStack stack) {
            this.sampleStack = stack.copy();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ItemTypeKey)) return false;
            return ItemStack.isSameItemSameComponents(sampleStack, ((ItemTypeKey) obj).sampleStack);
        }

        @Override
        public int hashCode() {
            return sampleStack.getItem().hashCode() * 31
                    + sampleStack.getComponents().hashCode();
        }
    }

    private static int detectColumns(List<Integer> sortableSlots, AbstractContainerMenu menu) {
        if (sortableSlots.isEmpty()) return 9;
        int firstY = menu.getSlot(sortableSlots.getFirst()).y;
        int count = 0;
        for (int index : sortableSlots) {
            if (menu.getSlot(index).y == firstY) {
                count++;
            }
        }
        return count > 0 ? count : 9;
    }

    private static List<Integer> getSortableSlots(AbstractContainerMenu menu, boolean isPlayerInventory,
                                                   String category) {
        List<Integer> slots = new ArrayList<>();

        if ("GCA_FAKE_PLAYER_INVENTORY".equals(category)) {
            for (int i = 18; i <= 53 && i < menu.slots.size(); i++) slots.add(i);
            return slots;
        }
        if ("GCA_FAKE_PLAYER_ENDER_CHEST".equals(category)) {
            for (int i = 27; i <= 53 && i < menu.slots.size(); i++) slots.add(i);
            return slots;
        }

        if (isPlayerInventory) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.getSlot(i);
                if (slot.container instanceof Inventory) {
                    int containerSlot = slot.slot;
                    if (containerSlot >= 9 && containerSlot <= 35) {
                        slots.add(i);
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
