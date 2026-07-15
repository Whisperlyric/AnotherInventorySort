package dev.whisperlyric.anotherinventorysort.mixin;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts handleContainerInput — the second layer of locked-slot protection.
 *
 * AbstractContainerScreenMixin intercepts operations that go through the screen;
 * this mixin intercepts mods that call handleContainerInput directly
 * (ItemScroller, Mouse Tweaks, etc.).
 *
 * PICKUP_ALL special handling: even when double-clicking a non-locked slot,
 * the server still collects same-type items from locked slots.
 * Therefore, before PICKUP_ALL executes, we check whether any locked slot is affected;
 * if so, we cancel and manually simulate (skipping locked slots).
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void onHandleContainerInput(int containerId, int slotNum, int buttonNum,
                                        ContainerInput containerInput, Player player, CallbackInfo ci) {
        // Allow the mod's own internal operations (e.g., auto-pickup protection)
        if (LockSlotManager.isProcessingLockedPickups()) return;

        if (slotNum < 0) return; // -999 = outside click, -1 = no slot

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;

        // Bounds check
        if (slotNum >= menu.slots.size()) return;

        // PICKUP_ALL special handling: even double-clicking a non-locked slot makes the
        // server collect same-type items from locked slots.
        if (containerInput == ContainerInput.PICKUP_ALL) {
            handlePickupAll(menu, client, ci);
            return;
        }

        Slot slot = menu.getSlot(slotNum);
        if (!(slot.container instanceof Inventory)) return;

        int invSlot = slot.slot;
        if (!LockSlotManager.isLockableSlot(invSlot)) return;

        if (!LockSlotManager.isSlotLocked(invSlot)) return;

        // Block all operations on locked slots
        switch (containerInput) {
            case QUICK_MOVE:
                // Block shift+click transfer
                ci.cancel();
                break;
            case THROW:
                // Block Q drop
                ci.cancel();
                break;
            case SWAP:
                // SWAP: slotId is the container slot, button is the hotbar index (0-8).
                // The check above already intercepts the case where slotId is a locked slot.
                // Also block when the hotbar slot (button) is locked (prevents replacing a locked hotbar slot).
                if (buttonNum >= 0 && buttonNum <= 8 && LockSlotManager.isSlotLocked(buttonNum)) {
                    ci.cancel();
                }
                break;
            case PICKUP:
                // Block mods calling PICKUP directly
                ci.cancel();
                break;
            case QUICK_CRAFT:
                // Block drag operations
                ci.cancel();
                break;
            case CLONE:
                // Block creative-mode clone
                ci.cancel();
                break;
            default:
                ci.cancel();
                break;
        }
    }

    /**
     * PICKUP_ALL (double-click collect) special handling:
     * Checks whether any locked slot contains the same item as the cursor.
     * If so, cancels the original operation and manually simulates PICKUP_ALL
     * (collecting only from non-locked slots).
     */
    @Unique
    private void handlePickupAll(AbstractContainerMenu menu, Minecraft client, CallbackInfo ci) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;

        // Check whether any locked slot contains the same item as the cursor
        boolean hasLockedMatch = false;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.getSlot(i);
            if (s.container instanceof Inventory && LockSlotManager.isSlotLocked(s.slot)) {
                ItemStack item = s.getItem();
                if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, carried)) {
                    hasLockedMatch = true;
                    break;
                }
            }
        }

        if (!hasLockedMatch) return; // No locked slot affected — allow the operation

        // A locked slot is affected: cancel and manually simulate (skipping locked slots)
        ci.cancel();

        int containerId = menu.containerId;
        int maxCount = carried.getMaxStackSize();

        // Iterate all slots, PICKUP same-type items to cursor (skipping locked slots)
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getCarried().getCount() >= maxCount) break; // cursor is full
            Slot s = menu.getSlot(i);
            // Skip locked slots
            if (s.container instanceof Inventory && LockSlotManager.isSlotLocked(s.slot)) continue;
            ItemStack item = s.getItem();
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, carried)) {
                if (client.player != null) {
                    client.gameMode.handleContainerInput(containerId, i, 0,
                            ContainerInput.PICKUP, client.player);
                }
            }
        }
    }
}
