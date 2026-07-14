package dev.whisperlyric.anotherinventorysort.mixin;

import dev.whisperlyric.anotherinventorysort.LockSlotManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts handleContainerInput at the game mode level.
 * This is the second layer of protection for locked slots.
 *
 * While AbstractContainerScreenMixin catches operations that go through the screen's
 * slotClicked method, some mods (ItemScroller, Mouse Tweaks, TweakMore, HotBaaaar, etc.)
 * may call handleContainerInput directly, bypassing the screen entirely.
 *
 * This mixin blocks QUICK_MOVE and THROW operations on locked slots at the network level,
 * which are the most common operations performed by these mods:
 * - ItemScroller: shift+click throw outside, shift+click container transfer, continuous crafting
 * - TweakMore: auto-fill container
 * - HotBaaaar: throw/drop
 * - Mouse Tweaks: scroll wheel transfer, drag transfer
 * - Carpet commands: server-side drops (cannot be fully prevented client-side)
 *
 * PICKUP operations are handled by the screen-level mixin since mods that use PICKUP
 * typically go through the screen. The auto-pickup protection (tick-based monitor) handles
 * any items that appear in locked slots through server-side operations.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void onHandleContainerInput(int containerId, int slotId, int button,
                                         ContainerInput action, Player player, CallbackInfo ci) {
        // Allow the mod's own internal operations (e.g., auto-pickup protection)
        if (LockSlotManager.isProcessingLockedPickups()) return;

        if (slotId < 0) return; // -999 = outside click, -1 = no slot

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;

        // Bounds check
        if (slotId >= menu.slots.size()) return;

        Slot slot = menu.getSlot(slotId);
        if (!(slot.container instanceof Inventory)) return;

        int invSlot = slot.slot;
        if (!LockSlotManager.isLockableSlot(invSlot)) return;

        if (!LockSlotManager.isSlotLocked(invSlot)) return;

        // Block all operations on locked slots at the network level
        switch (action) {
            case QUICK_MOVE:
                // Block shift+click transfer from locked slots
                // (ItemScroller shift+click, Mouse Tweaks shift+drag, TweakMore auto-fill)
                ci.cancel();
                break;
            case THROW:
                // Block Q drop from locked slots
                // (ItemScroller throw outside, HotBaaaar drop)
                ci.cancel();
                break;
            case SWAP:
                // Block number key swap with locked slots
                ci.cancel();
                break;
            case PICKUP:
                // Block direct PICKUP calls from mods that bypass the screen
                // (e.g., ItemScroller ctrl+c continuous crafting)
                ci.cancel();
                break;
            case QUICK_CRAFT:
                // Block drag operations from mods like Mouse Tweaks
                ci.cancel();
                break;
            case PICKUP_ALL:
                // Block double-click collect
                ci.cancel();
                break;
            case CLONE:
                // Block creative clone
                ci.cancel();
                break;
            default:
                ci.cancel();
                break;
        }
    }
}
