package dev.whisperlyric.anotherinventorysort.mixin;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import net.minecraft.client.Minecraft;
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
 * Blocks ALL interactions with locked inventory slots in survival/adventure mode.
 * In creative mode, lock protection is disabled (players have infinite items).
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void onSlotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        // Allow the mod's own internal operations (e.g., auto-pickup protection)
        if (LockSlotManager.isProcessingLockedPickups()) return;

        // Skip lock protection in creative mode
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.isCreative()) return;

        if (slot == null) return;
        if (!(slot.container instanceof Inventory)) return;

        int invSlot = slot.slot;
        if (!LockSlotManager.isLockableSlot(invSlot)) return;
        if (!LockSlotManager.isSlotLocked(invSlot)) return;

        // Block ALL actions on locked slots in survival/adventure mode
        ci.cancel();
    }
}
