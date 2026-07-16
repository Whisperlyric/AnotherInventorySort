package dev.whisperlyric.anotherinventorysort.transfer;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.Set;

/**
 * Abstract base class for item transfer handlers.
 * Subclasses implement version-specific transfer logic.
 */
public abstract class AbstractTransferHandler {

    /**
     * Execute item transfer based on the specified mode.
     *
     * @param menu The container menu
     * @param mode The transfer mode
     * @param lockedSlots Set of locked slot indices (in player inventory)
     */
    public void executeTransfer(AbstractContainerMenu menu, TransferMode mode, Set<Integer> lockedSlots) {
        switch (mode) {
            case MATCHING_TRANSFER_TO_CONTAINER:
                transferMatching(menu, getPlayerInventorySlots(menu), getContainerSlots(menu), lockedSlots);
                break;
            case ALL_TRANSFER_TO_CONTAINER:
                transferAll(menu, getPlayerInventorySlots(menu), getContainerSlots(menu), lockedSlots);
                break;
            case MATCHING_TRANSFER_TO_PLAYER:
                transferMatching(menu, getContainerSlots(menu), getPlayerInventorySlots(menu), lockedSlots);
                break;
            case ALL_TRANSFER_TO_PLAYER:
                transferAll(menu, getContainerSlots(menu), getPlayerInventorySlots(menu), lockedSlots);
                break;
        }
    }

    /**
     * Get player inventory slot indices.
     *
     * @param menu The container menu
     * @return List of player inventory slot indices
     */
    protected abstract List<Integer> getPlayerInventorySlots(AbstractContainerMenu menu);

    /**
     * Get container slot indices.
     *
     * @param menu The container menu
     * @return List of container slot indices
     */
    protected abstract List<Integer> getContainerSlots(AbstractContainerMenu menu);

    /**
     * Transfer matching items (same type) from source to destination.
     * Uses StackFill logic: fills partial stacks first, then empty slots.
     * Locked slots are excluded from all operations.
     *
     * @param menu The container menu
     * @param srcSlots Source slot indices
     * @param dstSlots Destination slot indices
     * @param lockedSlots Set of locked slot indices (in player inventory)
     */
    protected abstract void transferMatching(
            AbstractContainerMenu menu,
            List<Integer> srcSlots,
            List<Integer> dstSlots,
            Set<Integer> lockedSlots
    );

    /**
     * Transfer all items from source to destination.
     * Fills partial stacks first, then empty slots.
     * Locked slots are excluded from all operations.
     *
     * @param menu The container menu
     * @param srcSlots Source slot indices
     * @param dstSlots Destination slot indices
     * @param lockedSlots Set of locked slot indices (in player inventory)
     */
    protected abstract void transferAll(
            AbstractContainerMenu menu,
            List<Integer> srcSlots,
            List<Integer> dstSlots,
            Set<Integer> lockedSlots
    );
}