package dev.whisperlyric.anotherinventorysort.transfer;

/**
 * Transfer modes.
 */
public enum TransferMode {
    /**
     * Transfer matching items (same type) from player inventory to container.
     * Uses StackFill logic: fills partial stacks first.
     */
    MATCHING_TRANSFER_TO_CONTAINER,

    /**
     * Transfer all items from player inventory to container.
     * Fills partial stacks first, then empty slots.
     */
    ALL_TRANSFER_TO_CONTAINER,

    /**
     * Transfer matching items (same type) from container to player inventory.
     * Uses StackFill logic: fills partial stacks first.
     */
    MATCHING_TRANSFER_TO_PLAYER,

    /**
     * Transfer all items from container to player inventory.
     * Fills partial stacks first, then empty slots.
     */
    ALL_TRANSFER_TO_PLAYER;

    /**
     * Check if this transfer mode transfers to container.
     * @return true if transferring to container, false if transferring to player
     */
    public boolean isTransferToContainer() {
        return this == MATCHING_TRANSFER_TO_CONTAINER || this == ALL_TRANSFER_TO_CONTAINER;
    }

    /**
     * Check if this transfer mode is a full transfer (all items).
     *
     * @return true if transferring all items, false if transferring matching items only
     */
    public boolean isTransferAll() {
        return this == ALL_TRANSFER_TO_CONTAINER || this == ALL_TRANSFER_TO_PLAYER;
    }
}