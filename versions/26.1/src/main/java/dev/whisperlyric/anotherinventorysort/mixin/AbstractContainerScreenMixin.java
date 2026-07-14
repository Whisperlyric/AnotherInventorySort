package dev.whisperlyric.anotherinventorysort.mixin;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts all slot click operations at the screen level.
 * Blocks ALL interactions with locked inventory slots, including:
 * - PICKUP (normal click to pick up or place items)
 * - QUICK_MOVE (shift+click transfer)
 * - THROW (Q drop)
 * - SWAP (number key swap)
 * - CLONE (middle-click in creative)
 * - QUICK_CRAFT (drag operations)
 * - PICKUP_ALL (double-click collect)
 *
 * This catches operations from vanilla and most mods that go through the screen.
 * For mods that bypass the screen and call handleContainerInput directly,
 * see MultiPlayerGameModeMixin.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void onSlotClicked(Slot slot, int slotIndex, int button, ContainerInput action, CallbackInfo ci) {
        // Allow the mod's own internal operations (e.g., auto-pickup protection)
        if (LockSlotManager.isProcessingLockedPickups()) return;

        if (slot == null) return;
        if (!(slot.container instanceof Inventory)) return;

        int invSlot = slot.slot;
        if (!LockSlotManager.isLockableSlot(invSlot)) return;

        boolean isLocked = LockSlotManager.isSlotLocked(invSlot);

        if (!isLocked) return;

        // Block ALL actions on locked slots
        switch (action) {
            case PICKUP:
                // Block picking up items from locked slots
                // Also block placing cursor items into locked slots
                ci.cancel();
                break;
            case QUICK_MOVE:
                // Block shift+click transfer from locked slots
                ci.cancel();
                break;
            case SWAP:
                // Block number key / offhand swap with locked slots
                ci.cancel();
                break;
            case CLONE:
                // Block middle-click clone in creative
                ci.cancel();
                break;
            case THROW:
                // Block Q drop from locked slots
                ci.cancel();
                break;
            case QUICK_CRAFT:
                // Block drag operations involving locked slots
                ci.cancel();
                break;
            case PICKUP_ALL:
                // Block double-click collect from locked slots
                ci.cancel();
                break;
            default:
                // Block any unknown action types on locked slots
                ci.cancel();
                break;
        }
    }
}
