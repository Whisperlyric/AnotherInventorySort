package dev.whisperlyric.anotherinventorysort.mixin;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CreativeModeInventoryScreen overrides slotClicked, so
 * AbstractContainerScreenMixin doesn't intercept it.
 * This mixin blocks all operations on locked slots in creative mode,
 * including shift+click to the destroy-item slot.
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void onSlotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        if (LockSlotManager.isProcessingLockedPickups()) return;

        if (slot == null) return;
        if (!(slot.container instanceof Inventory)) return;

        int invSlot = slot.slot;
        if (!LockSlotManager.isLockableSlot(invSlot)) return;
        if (!LockSlotManager.isSlotLocked(invSlot)) return;

        // Block ALL actions on locked slots in creative mode
        ci.cancel();
    }
}
