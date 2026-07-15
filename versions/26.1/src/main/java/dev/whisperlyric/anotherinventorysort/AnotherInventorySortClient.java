package dev.whisperlyric.anotherinventorysort;

import dev.whisperlyric.anotherinventorysort.sort.SortHandler;
import dev.whisperlyric.anotherinventorysort.sort.SortMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class AnotherInventorySortClient implements ClientModInitializer {

    private static final int BUTTON_SIZE = 10;
    private static final int BUTTON_SPACING = 12;
    private static final int BUTTON_GAP = 2;
    private static final Identifier BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(
            "anotherinventorysort", "textures/gui/buttons.png");

    // Lock slot drag state
    private static boolean lockDragActive = false;
    private static int lockDragAction = -1; // 0 = lock, 1 = unlock

    // Tooltip hover tracking
    private static final long TOOLTIP_DELAY_MS = 3000;
    private static int hoveredButtonIndex = -1;
    private static long hoverStartTime = 0;

    // Auto-pickup protection: track previous item state of locked slots
    // Key = inventory slot index, Value = snapshot of the item in that slot
    private static final Map<Integer, ItemStack> previousLockedSlotState = new HashMap<>();
    private static boolean wasScreenOpen = false;

    @Override
    public void onInitializeClient() {
        // Initialize LockSlotManager
        LockSlotManager.init(FabricLoader.getInstance().getConfigDir().resolve("anotherinventorysort"));

        // Track server changes for per-server lock persistence
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServer() != null) {
                String addr = client.getCurrentServer().ip;
                LockSlotManager.setServerId(addr != null ? addr : "singleplayer");
            } else {
                LockSlotManager.setServerId("singleplayer");
            }
            // Reset auto-pickup tracking on join
            previousLockedSlotState.clear();
            wasScreenOpen = false;
        });

        // === Prevent Q drop from locked slots when screen is open + Auto-pickup protection ===
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Auto-pickup protection: only when no screen is open
            if (client.screen == null) {
                // After screen closes, skip one tick to avoid detecting manual moves
                if (wasScreenOpen) {
                    wasScreenOpen = false;
                    updatePreviousState(client);
                    return;
                }
                checkLockedSlotsForPickups(client);
            } else {
                wasScreenOpen = true;
            }
        });

        // Screen event registration
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            if (screen instanceof CreativeModeInventoryScreen) return;

            AbstractContainerMenu menu = containerScreen.getMenu();
            if (isNonStorage(menu)) return;

            int guiLeft = containerScreen.leftPos;
            int guiTop = containerScreen.topPos;

            final ContainerCategory[] categoryRef = {null};
            final List<SortButtonInfo> buttons = new ArrayList<>();
            final boolean[] initialized = {false};

            // After extract: draw buttons + lock indicators
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                if (!initialized[0]) {
                    ContainerCategory category = categorize(menu, containerScreen.getTitle());
                    if (category == ContainerCategory.NON_STORAGE) {
                        initialized[0] = true;
                        return;
                    }

                    int[] pos = computeButtonArea(menu, category, guiLeft, guiTop);
                    int slotTopY = pos[0];
                    int slotRightX = pos[1];

                    int startX = slotRightX - BUTTON_SPACING * 3;
                    int startY = slotTopY - BUTTON_SIZE - BUTTON_GAP;

                    buttons.add(new SortButtonInfo(startX, startY, 10, SortMode.DEFAULT,
                            Component.translatable("anotherinventorysort.button.sort")));
                    buttons.add(new SortButtonInfo(startX + BUTTON_SPACING, startY, 30, SortMode.ROW,
                            Component.translatable("anotherinventorysort.button.sort_row")));
                    buttons.add(new SortButtonInfo(startX + BUTTON_SPACING * 2, startY, 20, SortMode.COLUMN,
                            Component.translatable("anotherinventorysort.button.sort_column")));

                    categoryRef[0] = category;
                    initialized[0] = true;
                }

                if (!buttons.isEmpty()) {
                    renderButtons(graphics, mouseX, mouseY, buttons);
                }

                // Render lock indicators
                renderLockIndicators(graphics, menu, guiLeft, guiTop);

                // Handle lock drag: apply lock/unlock to slots the mouse drags over
                if (lockDragActive && isAltHeld()) {
                    Slot hovered = findSlotAt(menu, mouseX, mouseY, guiLeft, guiTop);
                    if (hovered != null && hovered.container instanceof Inventory
                            && LockSlotManager.isLockableSlot(hovered.slot)) {
                        int invSlot = hovered.slot;
                        if (lockDragAction == 0 && !LockSlotManager.isSlotLocked(invSlot)) {
                            LockSlotManager.lockSlot(invSlot);
                        } else if (lockDragAction == 1 && LockSlotManager.isSlotLocked(invSlot)) {
                            LockSlotManager.unlockSlot(invSlot);
                        }
                    }
                }
            });

            // Mouse click handler
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!initialized[0] || categoryRef[0] == null) return true;

                boolean altHeld = isAltHeld();
                boolean shiftHeld = isShiftHeld();

                // Shift + right-click on sort buttons: sort ignoring locked slots
                if (shiftHeld && event.button() == GLFW.GLFW_MOUSE_BUTTON_2) {
                    for (SortButtonInfo btn : buttons) {
                        if (btn.isHovered(event.x(), event.y())) {
                            LockSlotManager.setProcessingLockedPickups(true);
                            try {
                                SortHandler.sortInventory(containerScreen, btn.sortMode, categoryRef[0].name(), true);
                            } finally {
                                LockSlotManager.setProcessingLockedPickups(false);
                            }
                            return false;
                        }
                    }
                }

                // Left click only from here
                if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

                // Check sort buttons first (only when NOT holding Alt)
                if (!altHeld) {
                    for (SortButtonInfo btn : buttons) {
                        if (btn.isHovered(event.x(), event.y())) {
                            SortHandler.sortInventory(containerScreen, btn.sortMode, categoryRef[0].name(), false);
                            return false;
                        }
                    }
                }

                // Hold Alt + click: lock/unlock slot (IPN behavior)
                // Only allow locking player inventory (0-35), hotbar, and offhand (40)
                if (altHeld) {
                    Slot clickedSlot = findSlotAt(menu, event.x(), event.y(), guiLeft, guiTop);
                    if (clickedSlot != null && clickedSlot.container instanceof Inventory
                            && LockSlotManager.isLockableSlot(clickedSlot.slot)) {
                        int invSlot = clickedSlot.slot;
                        if (lockDragActive) {
                            // Continue drag: apply same action as drag start
                            if (lockDragAction == 0 && !LockSlotManager.isSlotLocked(invSlot)) {
                                LockSlotManager.lockSlot(invSlot);
                            } else if (lockDragAction == 1 && LockSlotManager.isSlotLocked(invSlot)) {
                                LockSlotManager.unlockSlot(invSlot);
                            }
                        } else {
                            // Start of drag
                            boolean wasLocked = LockSlotManager.isSlotLocked(invSlot);
                            lockDragAction = wasLocked ? 1 : 0;
                            LockSlotManager.toggleSlot(invSlot);
                            lockDragActive = true;
                        }
                        return false; // consume click
                    }
                }

                return true;
            });

            // Mouse release: end drag
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    lockDragActive = false;
                    lockDragAction = -1;
                }
                return true;
            });
        });
    }

    // Check if Left Alt is currently held down
    private static boolean isAltHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    // Check if Shift is currently held down
    private static boolean isShiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    // Find which slot is at the given screen coordinates
    private static Slot findSlotAt(AbstractContainerMenu menu, double mouseX, double mouseY,
                                    int guiLeft, int guiTop) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            int slotX = guiLeft + slot.x;
            int slotY = guiTop + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }
        return null;
    }

    // Render lock indicators on locked slots (hotbar 0-8, main inventory 9-35, offhand 40)
    private static void renderLockIndicators(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
                                              int guiLeft, int guiTop) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory && LockSlotManager.isLockableSlot(slot.slot)
                    && LockSlotManager.isSlotLocked(slot.slot)) {
                int slotX = guiLeft + slot.x;
                int slotY = guiTop + slot.y;

                // Background highlight (red tint)
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FF5555);

                // Foreground lock icon
                graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, slotX - 1, slotY - 1, 0, 120,
                        6, 8, 128, 128);
            }
        }
    }

    // ===== Container category =====

    private enum ContainerCategory {
        SORTABLE_STORAGE,
        PURE_BACKPACK,
        NON_STORAGE,
        GCA_FAKE_PLAYER_INVENTORY,
        GCA_FAKE_PLAYER_ENDER_CHEST
    }

    private static boolean isNonStorage(AbstractContainerMenu menu) {
        return menu instanceof BeaconMenu
            || menu instanceof LecternMenu
            || menu instanceof LoomMenu
            || menu instanceof BrewingStandMenu
            || menu instanceof MerchantMenu;
    }

    private static ContainerCategory categorize(AbstractContainerMenu menu, Component screenTitle) {
        if (isNonStorage(menu)) return ContainerCategory.NON_STORAGE;

        if (menu instanceof ChestMenu) {
            ContainerCategory gca = detectGcaContainer(menu, screenTitle);
            if (gca != null) return gca;
        }

        if (menu instanceof ChestMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof ShulkerBoxMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof HorseInventoryMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof DispenserMenu) return ContainerCategory.SORTABLE_STORAGE;

        return ContainerCategory.PURE_BACKPACK;
    }

    private static ContainerCategory detectGcaContainer(AbstractContainerMenu menu, Component screenTitle) {
        if (!(menu instanceof ChestMenu)) return null;
        if (menu.slots.isEmpty()) return null;

        ItemStack stack0 = menu.getSlot(0).getItem();
        if (!stack0.is(Items.STRUCTURE_VOID)) return null;

        var cd = stack0.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;

        CompoundTag tag = cd.copyTag();
        if (tag == null || (!tag.contains("GcaClear") && !tag.contains("gca.clear"))) return null;

        String titleStr = screenTitle.getString().toLowerCase();
        if (titleStr.contains("ender chest") || titleStr.contains("末影箱")) {
            return ContainerCategory.GCA_FAKE_PLAYER_ENDER_CHEST;
        }
        return ContainerCategory.GCA_FAKE_PLAYER_INVENTORY;
    }

    // ===== Compute button area =====

    private static int[] computeButtonArea(AbstractContainerMenu menu, ContainerCategory category,
                                            int guiLeft, int guiTop) {
        switch (category) {
            case SORTABLE_STORAGE:
                return findContainerSlotArea(menu, guiLeft, guiTop);
            case GCA_FAKE_PLAYER_INVENTORY:
            case GCA_FAKE_PLAYER_ENDER_CHEST:
                int firstSlotY = -1;
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot slot = menu.getSlot(i);
                    if (!(slot.container instanceof Inventory)) {
                        firstSlotY = slot.y;
                        break;
                    }
                }
                if (firstSlotY < 0) firstSlotY = 17;
                return new int[]{guiTop + firstSlotY, guiLeft + 176 - 7};
            default:
                return findPlayerStorageArea(menu, guiLeft, guiTop);
        }
    }

    private static int[] findContainerSlotArea(AbstractContainerMenu menu, int guiLeft, int guiTop) {
        int topY = -1;
        int rightX = -1;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (!(slot.container instanceof Inventory)) {
                int slotY = guiTop + slot.y;
                int slotRightX = guiLeft + slot.x + 18;
                if (topY == -1 || slotY < topY) topY = slotY;
                if (slotRightX > rightX) rightX = slotRightX;
            }
        }

        return fallbackIfNeeded(topY, rightX, guiLeft, guiTop);
    }

    private static int[] findPlayerStorageArea(AbstractContainerMenu menu, int guiLeft, int guiTop) {
        Map<Integer, List<Slot>> rowsByY = new LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory) {
                rowsByY.computeIfAbsent(slot.y, k -> new ArrayList<>()).add(slot);
            }
        }

        for (var entry : rowsByY.entrySet()) {
            if (entry.getValue().size() == 9) {
                int topY = guiTop + entry.getKey();
                int rightX = 0;
                for (Slot s : entry.getValue()) {
                    int sx = guiLeft + s.x + 18;
                    if (sx > rightX) rightX = sx;
                }
                return new int[]{topY, rightX};
            }
        }

        return new int[]{guiTop + 5, guiLeft + 176 - 7};
    }

    private static int[] fallbackIfNeeded(int topY, int rightX, int guiLeft, int guiTop) {
        if (topY == -1) {
            return new int[]{guiTop + 5, guiLeft + 176 - 7};
        }
        return new int[]{topY, rightX};
    }

    // ===== Render =====

    private static class SortButtonInfo {
        final int x;
        final int y;
        final int textureX;
        final SortMode sortMode;
        final Component tooltip;

        SortButtonInfo(int x, int y, int textureX, SortMode sortMode, Component tooltip) {
            this.x = x;
            this.y = y;
            this.textureX = textureX;
            this.sortMode = sortMode;
            this.tooltip = tooltip;
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        }
    }

    private static void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<SortButtonInfo> buttons) {
        boolean shiftHeld = isShiftHeld();
        int newHoveredIndex = -1;
        for (int i = 0; i < buttons.size(); i++) {
            SortButtonInfo btn = buttons.get(i);
            boolean hovered = btn.isHovered(mouseX, mouseY);
            float u = btn.textureX;
            float shift = shiftHeld ? 1 : 0;
            float hover = hovered ? 1 : 0;
            float v = shift * 20 + hover * 10 + (shiftHeld && hovered ? 10 : 0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, btn.x, btn.y, u, v,
                    BUTTON_SIZE, BUTTON_SIZE, 128, 128);
            if (hovered) newHoveredIndex = i;
        }

        // Track hover duration for tooltip delay
        if (newHoveredIndex >= 0) {
            if (newHoveredIndex != hoveredButtonIndex) {
                hoveredButtonIndex = newHoveredIndex;
                hoverStartTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - hoverStartTime >= TOOLTIP_DELAY_MS) {
                SortButtonInfo btn = buttons.get(newHoveredIndex);
                Font font = Minecraft.getInstance().font;
                graphics.setTooltipForNextFrame(font, btn.tooltip, (int) mouseX, (int) mouseY);
            }
        } else {
            hoveredButtonIndex = -1;
            hoverStartTime = 0;
        }
    }

    // ===== Auto-pickup protection =====

    private static void updatePreviousState(Minecraft client) {
        previousLockedSlotState.clear();
        if (client.player == null) return;
        Inventory inv = client.player.getInventory();
        for (int invSlot : LockSlotManager.getLockedSlots()) {
            if (LockSlotManager.isLockableSlot(invSlot)) {
                previousLockedSlotState.put(invSlot, inv.getItem(invSlot).copy());
            }
        }
    }

    private static void checkLockedSlotsForPickups(Minecraft client) {
        if (client.player == null) return;
        Inventory inv = client.player.getInventory();
        var menu = client.player.containerMenu;

        LockSlotManager.setProcessingLockedPickups(true);
        try {
            for (int invSlot : LockSlotManager.getLockedSlots()) {
                if (!LockSlotManager.isLockableSlot(invSlot)) continue;

                ItemStack previous = previousLockedSlotState.getOrDefault(invSlot, ItemStack.EMPTY);
                ItemStack current = inv.getItem(invSlot);

                boolean changed = false;

                if (previous.isEmpty() && !current.isEmpty()) {
                    // Item was picked up into an empty locked slot — move it out entirely
                    changed = true;
                } else if (!previous.isEmpty() && !current.isEmpty()) {
                    // Check if items were merged into a non-empty locked slot
                    if (ItemStack.isSameItemSameComponents(previous, current) && current.getCount() > previous.getCount()) {
                        // Same-type items were merged into this locked slot (count increased)
                        changed = true;
                    } else if (!ItemStack.isSameItemSameComponents(previous, current)) {
                        // Different item was placed into the locked slot
                        changed = true;
                    }
                }
                // If previous was non-empty and current is empty, item was removed from locked slot
                // (e.g., by carpet command) — we can't bring it back, just update state

                if (changed) {
                    int menuSlotIndex = findMenuSlotForInvSlot(menu, invSlot);
                    if (menuSlotIndex < 0) {
                        previousLockedSlotState.put(invSlot, current.copy());
                        continue;
                    }

                    // Move the entire item out of the locked slot
                    // For same-type merges, we can't split stacks precisely via container clicks,
                    // so we move the whole stack out to protect the locked slot
                    int targetMenuSlot = findFirstNonLockedEmptySlot(menu, inv);

                    if (targetMenuSlot >= 0) {
                        int containerId = menu.containerId;
                        client.gameMode.handleContainerInput(containerId, menuSlotIndex, 0,
                                ContainerInput.PICKUP, client.player);
                        client.gameMode.handleContainerInput(containerId, targetMenuSlot, 0,
                                ContainerInput.PICKUP, client.player);
                    } else {
                        int containerId = menu.containerId;
                        client.gameMode.handleContainerInput(containerId, menuSlotIndex, 0,
                                ContainerInput.THROW, client.player);
                    }
                }

                previousLockedSlotState.put(invSlot, inv.getItem(invSlot).copy());
            }
        } finally {
            LockSlotManager.setProcessingLockedPickups(false);
        }
    }

    private static int findMenuSlotForInvSlot(AbstractContainerMenu menu, int invSlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory && slot.slot == invSlot) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstNonLockedEmptySlot(AbstractContainerMenu menu, Inventory inv) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory) {
                int invSlot = slot.slot;
                if (!LockSlotManager.isSlotLocked(invSlot) && slot.getItem().isEmpty()) {
                    // Prefer main storage (9-35) over hotbar (0-8) over offhand (40)
                    if (invSlot >= 9 && invSlot <= 35) return i;
                }
            }
        }
        // Fallback: hotbar and offhand
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory) {
                int invSlot = slot.slot;
                if (!LockSlotManager.isSlotLocked(invSlot) && slot.getItem().isEmpty()) {
                    return i;
                }
            }
        }
        return -1;
    }
}
