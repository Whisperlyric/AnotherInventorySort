package dev.whisperlyric.anotherinventorysort.transfer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles item transfer operations between player inventory and container.
 * Optimized for performance with minimal clicks.
 */
public class TransferHandler extends AbstractTransferHandler {

    @Override
    protected List<Integer> getPlayerInventorySlots(AbstractContainerMenu menu) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory) {
                // Player inventory slot (0-35 + 40 for offhand)
                if (slot.slot >= 0 && slot.slot <= 35 || slot.slot == 40) {
                    slots.add(i);
                }
            }
        }
        return slots;
    }

    @Override
    protected List<Integer> getContainerSlots(AbstractContainerMenu menu) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (!(slot.container instanceof Inventory)) {
                slots.add(i);
            }
        }
        return slots;
    }

    /**
     * Transfers matching items (same type) from source to destination.
     * Uses StackFill logic: fills partial stacks first, then empty slots.
     * Locked slots are excluded from all operations.
     *
     * @param menu The container menu
     * @param srcSlots Source slot indices
     * @param dstSlots Destination slot indices
     * @param lockedSlots Set of locked slot indices (in player inventory)
     */
    @Override
    public void transferMatching(
            AbstractContainerMenu menu,
            List<Integer> srcSlots,
            List<Integer> dstSlots,
            Set<Integer> lockedSlots
    ) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        int containerId = menu.containerId;

        // Get source and destination slots
        List<Slot> srcSlotList = new ArrayList<>();
        List<ItemStack> srcStacks = new ArrayList<>();
        for (int slotIndex : srcSlots) {
            Slot slot = menu.getSlot(slotIndex);
            // Skip locked slots (only for player inventory)
            if (slot.container instanceof Inventory) {
                if (lockedSlots.contains(slot.slot)) continue;
            }
            srcSlotList.add(slot);
            srcStacks.add(slot.getItem().copy());
        }

        List<Slot> dstSlotList = new ArrayList<>();
        List<ItemStack> dstStacks = new ArrayList<>();
        for (int slotIndex : dstSlots) {
            Slot slot = menu.getSlot(slotIndex);
            // Skip locked slots (only for player inventory)
            if (slot.container instanceof Inventory) {
                if (lockedSlots.contains(slot.slot)) continue;
            }
            dstSlotList.add(slot);
            dstStacks.add(slot.getItem().copy());
        }

        // Work backwards from source, looking for non-empty stacks
        for (int i = srcSlotList.size() - 1; i >= 0; i--) {
            Slot srcSlot = srcSlotList.get(i);
            ItemStack srcStack = srcStacks.get(i);

            if (srcStack.isEmpty()) continue;

            // Work forwards from destination, looking for matching partial stacks
            for (int j = 0; j < dstSlotList.size(); j++) {
                Slot dstSlot = dstSlotList.get(j);
                ItemStack dstStack = dstStacks.get(j);

                if (dstStack.isEmpty()) continue;
                if (dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack)) continue;
                if (!ItemStack.isSameItemSameComponents(srcStack, dstStack)) continue;

                // Matching partial stack found - pick up source stack
                client.gameMode.handleContainerInput(
                        containerId,
                        srcSlot.index,
                        0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP,
                        player
                );

                // Place into destination slot
                client.gameMode.handleContainerInput(
                        containerId,
                        dstSlot.index,
                        0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP,
                        player
                );

                // Update logical records
                int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                delta = Math.min(delta, srcStack.getCount());
                srcStack.setCount(srcStack.getCount() - delta);
                dstStack.setCount(dstStack.getCount() + delta);

                // Place remaining items back if any
                if (srcStack.getCount() > 0) {
                    client.gameMode.handleContainerInput(
                            containerId,
                            srcSlot.index,
                            0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP,
                            player
                    );
                } else {
                    // Mark source slot as empty
                    srcStacks.set(i, ItemStack.EMPTY);
                }

                // If no items remain, stop looking for this source stack
                if (srcStack.getCount() <= 0) break;
            }
        }
    }

    /**
     * Transfers all items from source to destination.
     * Fills partial stacks first, then empty slots.
     * Locked slots are excluded from all operations.
     *
     * @param menu The container menu
     * @param srcSlots Source slot indices
     * @param dstSlots Destination slot indices
     * @param lockedSlots Set of locked slot indices (in player inventory)
     */
    @Override
    public void transferAll(
            AbstractContainerMenu menu,
            List<Integer> srcSlots,
            List<Integer> dstSlots,
            Set<Integer> lockedSlots
    ) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        int containerId = menu.containerId;

        // Get source and destination slots
        List<Slot> srcSlotList = new ArrayList<>();
        List<ItemStack> srcStacks = new ArrayList<>();
        for (int slotIndex : srcSlots) {
            Slot slot = menu.getSlot(slotIndex);
            // Skip locked slots (only for player inventory)
            if (slot.container instanceof Inventory) {
                if (lockedSlots.contains(slot.slot)) continue;
            }
            srcSlotList.add(slot);
            srcStacks.add(slot.getItem().copy());
        }

        List<Slot> dstSlotList = new ArrayList<>();
        List<ItemStack> dstStacks = new ArrayList<>();
        for (int slotIndex : dstSlots) {
            Slot slot = menu.getSlot(slotIndex);
            // Skip locked slots (only for player inventory)
            if (slot.container instanceof Inventory) {
                if (lockedSlots.contains(slot.slot)) continue;
            }
            dstSlotList.add(slot);
            dstStacks.add(slot.getItem().copy());
        }

        // Work backwards from source, looking for non-empty stacks
        for (int i = srcSlotList.size() - 1; i >= 0; i--) {
            Slot srcSlot = srcSlotList.get(i);
            ItemStack srcStack = srcStacks.get(i);

            if (srcStack.isEmpty()) continue;

            // Phase 1: Fill matching partial stacks
            for (int j = 0; j < dstSlotList.size(); j++) {
                Slot dstSlot = dstSlotList.get(j);
                ItemStack dstStack = dstStacks.get(j);

                if (dstStack.isEmpty()) continue;
                if (dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack)) continue;
                if (!ItemStack.isSameItemSameComponents(srcStack, dstStack)) continue;

                // Matching partial stack found - pick up source stack
                client.gameMode.handleContainerInput(
                        containerId,
                        srcSlot.index,
                        0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP,
                        player
                );

                // Place into destination slot
                client.gameMode.handleContainerInput(
                        containerId,
                        dstSlot.index,
                        0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP,
                        player
                );

                // Update logical records
                int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                delta = Math.min(delta, srcStack.getCount());
                srcStack.setCount(srcStack.getCount() - delta);
                dstStack.setCount(dstStack.getCount() + delta);

                // Place remaining items back if any
                if (srcStack.getCount() > 0) {
                    client.gameMode.handleContainerInput(
                            containerId,
                            srcSlot.index,
                            0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP,
                            player
                    );
                } else {
                    // Mark source slot as empty
                    srcStacks.set(i, ItemStack.EMPTY);
                }

                // If no items remain, stop looking for this source stack
                if (srcStack.getCount() <= 0) break;
            }

            // If source stack still has items, find empty slot
            if (srcStack.getCount() > 0) {
                for (int j = 0; j < dstSlotList.size(); j++) {
                    Slot dstSlot = dstSlotList.get(j);
                    ItemStack dstStack = dstStacks.get(j);

                    if (!dstStack.isEmpty()) continue;

                    // Empty slot found - pick up source stack
                    client.gameMode.handleContainerInput(
                            containerId,
                            srcSlot.index,
                            0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP,
                            player
                    );

                    // Place into empty slot
                    client.gameMode.handleContainerInput(
                            containerId,
                            dstSlot.index,
                            0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP,
                            player
                    );

                    // Update logical records
                    dstStacks.set(j, srcStack.copy());
                    srcStacks.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }
}