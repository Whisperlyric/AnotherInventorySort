package dev.whisperlyric.anotherinventorysort;

import dev.whisperlyric.anotherinventorysort.sort.SortHandler;
import dev.whisperlyric.anotherinventorysort.sort.SortMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

import java.nio.file.Path;
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

    // Auto-pickup protection: track previous item state of locked slots
    // Key = inventory slot index, Value = snapshot of the item in that slot
    private static final Map<Integer, ItemStack> previousLockedSlotState = new HashMap<>();
    private static boolean wasScreenOpen = false;

    private static KeyMapping sortAtCursorKey;
    private static KeyMapping lockModifierKey;

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("anotherinventorysort");
        LockSlotManager.init(configDir);

        // Register custom key mapping category (ref: quickshulker)
        KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("anotherinventorysort", "main"));

        sortAtCursorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anotherinventorysort.sort_at_cursor",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                MAIN_CATEGORY
        ));
        System.out.println("[AnotherInventorySort] Registered sortAtCursorKey: " + sortAtCursorKey);

        lockModifierKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anotherinventorysort.lock_modifier",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                MAIN_CATEGORY
        ));
        System.out.println("[AnotherInventorySort] Registered lockModifierKey: " + lockModifierKey);

        // Track server changes for per-server lock persistence
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServer() != null) {
                String addr = client.getCurrentServer().ip;
                LockSlotManager.setServerId(addr != null ? addr : "singleplayer");
            } else {
                LockSlotManager.setServerId("singleplayer");
            }
            // Clear state on join; inventory may not be synced yet.
            // First tick detection will initialize baseline without triggering transfers.
            previousLockedSlotState.clear();
            wasScreenOpen = false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Auto-pickup protection: check both when screen is open and closed.
            // When open: detect items entering locked slots via PICKUP_ALL/ItemScroller.
            // When closed: skip one tick after screen close to avoid false positives from manual moves.
            if (client.screen == null) {
                if (wasScreenOpen) {
                    wasScreenOpen = false;
                    updatePreviousState(client);
                    return;
                }
                checkLockedSlotsForPickups(client);
            } else {
                checkLockedSlotsForPickups(client);
                wasScreenOpen = true;
            }
        });

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
            final int[] recheckCount = {0};

            // After extract: draw buttons + lock indicators
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                if (!initialized[0]) {
                    ContainerCategory category = categorize(menu, containerScreen.getTitle());
                    if (category == ContainerCategory.NON_STORAGE) {
                        initialized[0] = true;
                        return;
                    }

                    populateButtons(buttons, menu, category, guiLeft, guiTop);
                    categoryRef[0] = category;
                    initialized[0] = true;
                }

                if (initialized[0] && recheckCount[0] < 5
                        && categoryRef[0] == ContainerCategory.SORTABLE_STORAGE
                        && menu instanceof ChestMenu) {
                    recheckCount[0]++;
                    ContainerCategory gca = detectGcaContainer(menu, containerScreen.getTitle());
                    if (gca != null) {
                        categoryRef[0] = gca;
                        populateButtons(buttons, menu, gca, guiLeft, guiTop);
                        recheckCount[0] = 5;
                    }
                }

                if (!buttons.isEmpty()) {
                    renderButtons(graphics, mouseX, mouseY, buttons);
                }

                renderLockIndicators(graphics, menu, guiLeft, guiTop);

                if (lockDragActive && isAltHeld() && menu.getCarried().isEmpty()) {
                    Slot hovered = findSlotAt(menu, mouseX, mouseY, guiLeft, guiTop);
                    if (hovered != null && hovered.container instanceof Inventory
                            && LockSlotManager.isLockableSlot(hovered.slot)) {
                        int invSlot = hovered.slot;
                        if (lockDragAction == 0 && !LockSlotManager.isSlotLocked(invSlot)) {
                            lockSlotSynced(invSlot, hovered.getItem());
                        } else if (lockDragAction == 1 && LockSlotManager.isSlotLocked(invSlot)) {
                            unlockSlotSynced(invSlot);
                        }
                    }
                }
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!initialized[0] || categoryRef[0] == null) return true;

                boolean altHeld = isAltHeld();

                if (!altHeld && isSortAtCursorButton(event.button())) {
                    Slot hovered = findSlotAt(menu, event.x(), event.y(), guiLeft, guiTop);
                    if (hovered != null) {
                        String sortCategory;
                        if (hovered.container instanceof Inventory) {
                            sortCategory = "PURE_BACKPACK";
                        } else if (categoryRef[0] != ContainerCategory.PURE_BACKPACK) {
                            sortCategory = categoryRef[0].name();
                        } else {
                            return true;
                        }
                        SortHandler.sortInventory(containerScreen, SortMode.DEFAULT, sortCategory, false);
                        return false;
                    }
                }

                // Left click only from here
                if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

                if (!altHeld) {
                    for (SortButtonInfo btn : buttons) {
                        if (btn.isHovered(event.x(), event.y())) {
                            String categoryToUse = categoryRef[0].name();
                            if (recheckCount[0] < 5
                                    && categoryRef[0] == ContainerCategory.SORTABLE_STORAGE
                                    && menu instanceof ChestMenu) {
                                ContainerCategory gca = detectGcaContainer(menu, containerScreen.getTitle());
                                if (gca != null) {
                                    categoryRef[0] = gca;
                                    categoryToUse = gca.name();
                                    populateButtons(buttons, menu, gca, guiLeft, guiTop);
                                }
                                recheckCount[0] = 5;
                            }
                            SortHandler.sortInventory(containerScreen, btn.sortMode, categoryToUse, false);
                            return false;
                        }
                    }
                }

                // Lock slot: skip when cursor has item to avoid interfering with ItemScroller LAlt batch transfer
                if (altHeld && menu.getCarried().isEmpty()) {
                    Slot clickedSlot = findSlotAt(menu, event.x(), event.y(), guiLeft, guiTop);
                    if (clickedSlot != null && clickedSlot.container instanceof Inventory
                            && LockSlotManager.isLockableSlot(clickedSlot.slot)) {
                        int invSlot = clickedSlot.slot;
                        if (lockDragActive) {
                            if (lockDragAction == 0 && !LockSlotManager.isSlotLocked(invSlot)) {
                                lockSlotSynced(invSlot, clickedSlot.getItem());
                            } else if (lockDragAction == 1 && LockSlotManager.isSlotLocked(invSlot)) {
                                unlockSlotSynced(invSlot);
                            }
                        } else {
                            boolean wasLocked = LockSlotManager.isSlotLocked(invSlot);
                            lockDragAction = wasLocked ? 1 : 0;
                            if (wasLocked) {
                                unlockSlotSynced(invSlot);
                            } else {
                                lockSlotSynced(invSlot, clickedSlot.getItem());
                            }
                            lockDragActive = true;
                        }
                        return false;
                    }
                }

                return true;
            });

            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    lockDragActive = false;
                    lockDragAction = -1;
                }
                return true;
            });
        });
    }

    private static boolean isAltHeld() {
        if (lockModifierKey == null) return false;
        InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(lockModifierKey);
        if (boundKey == null) return false;
        var window = Minecraft.getInstance().getWindow();
        if (boundKey.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, boundKey.getValue());
        } else if (boundKey.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), boundKey.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static boolean isSortAtCursorButton(int button) {
        if (sortAtCursorKey == null) return false;
        InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(sortAtCursorKey);
        return boundKey.getType() == InputConstants.Type.MOUSE && boundKey.getValue() == button;
    }

    private static boolean isShiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);
    }

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

    private static void renderLockIndicators(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
                                              int guiLeft, int guiTop) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory && LockSlotManager.isLockableSlot(slot.slot)
                    && LockSlotManager.isSlotLocked(slot.slot)) {
                int slotX = guiLeft + slot.x;
                int slotY = guiTop + slot.y;

                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FF5555);
                graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, slotX - 1, slotY - 1, 0, 120,
                        6, 8, 128, 128);
            }
        }
    }

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

    // Compute button area

    /** Compute button positions based on category and populate the buttons list (shared by init and re-detect). */
    private static void populateButtons(List<SortButtonInfo> buttons, AbstractContainerMenu menu,
                                         ContainerCategory category, int guiLeft, int guiTop) {
        buttons.clear();
        int[] pos = computeButtonArea(menu, category, guiLeft, guiTop);
        int startX = pos[1] - BUTTON_SPACING * 3;
        int startY = pos[0] - BUTTON_SIZE - BUTTON_GAP;
        buttons.add(new SortButtonInfo(startX, startY, 10, SortMode.DEFAULT));
        buttons.add(new SortButtonInfo(startX + BUTTON_SPACING, startY, 20, SortMode.ROW));
        buttons.add(new SortButtonInfo(startX + BUTTON_SPACING * 2, startY, 30, SortMode.COLUMN));
    }

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

    private static class SortButtonInfo {
        final int x;
        final int y;
        final int textureX;
        final SortMode sortMode;

        SortButtonInfo(int x, int y, int textureX, SortMode sortMode) {
            this.x = x;
            this.y = y;
            this.textureX = textureX;
            this.sortMode = sortMode;
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        }
    }

    private static void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<SortButtonInfo> buttons) {
        for (SortButtonInfo btn : buttons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            float u = btn.textureX;
            float v = hovered ? 10 : 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, btn.x, btn.y, u, v,
                    BUTTON_SIZE, BUTTON_SIZE, 128, 128);
        }
    }

    /** Lock a slot and sync previousLockedSlotState to avoid false "item entered locked slot" detection on next tick. */
    private static void lockSlotSynced(int invSlot, ItemStack current_item) {
        LockSlotManager.lockSlot(invSlot);
        previousLockedSlotState.put(invSlot, current_item.copy());
    }

    /** Unlock a slot and sync previousLockedSlotState. */
    private static void unlockSlotSynced(int invSlot) {
        LockSlotManager.unlockSlot(invSlot);
        previousLockedSlotState.remove(invSlot);
    }

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

                ItemStack current = inv.getItem(invSlot);

                // First detection after join: record baseline only, skip transfer.
                // This avoids false positives when inventory hasn't synced yet.
                if (!previousLockedSlotState.containsKey(invSlot)) {
                    previousLockedSlotState.put(invSlot, current.copy());
                    continue;
                }

                ItemStack previous = previousLockedSlotState.getOrDefault(invSlot, ItemStack.EMPTY);

                boolean changed = false;
                int previousCount = previous.isEmpty() ? 0 : previous.getCount();

                if (previous.isEmpty() && !current.isEmpty()) {
                    // Scenario 1: item placed into an empty locked slot — move the whole stack out
                    changed = true;
                } else if (!previous.isEmpty() && !current.isEmpty()) {
                    if (ItemStack.isSameItemSameComponents(previous, current) && current.getCount() > previous.getCount()) {
                        // Scenario 2: same-type item merged into locked slot — remove only the excess count
                        changed = true;
                    } else if (!ItemStack.isSameItemSameComponents(previous, current)) {
                        // Scenario 3: different-type item placed into locked slot — move the whole stack out
                        changed = true;
                    }
                }

                if (changed) {
                    int menuSlotIndex = findMenuSlotForInvSlot(menu, invSlot);
                    if (menuSlotIndex < 0) {
                        previousLockedSlotState.put(invSlot, current.copy());
                        continue;
                    }

                    boolean sameTypeMerge = !previous.isEmpty() && !current.isEmpty()
                            && ItemStack.isSameItemSameComponents(previous, current)
                            && current.getCount() > previousCount;

                    int containerId = menu.containerId;
                    // Scenario 2 requires an empty cursor; otherwise PICKUP would swap items and break the logic.
                    boolean carriedEmpty = menu.getCarried().isEmpty();

                    if (sameTypeMerge && carriedEmpty) {
                        // Scenario 2: remove only the excess count.
                        // Prerequisite: cursor is empty. PICKUP whole stack to cursor → right-click locked slot
                        // previousCount times (puts back 1 each) → transfer remaining (excess) to a non-locked slot.
                        int targetMenuSlot = findFirstNonLockedEmptySlot(menu, inv);

                        // 1. PICKUP the whole stack to cursor
                        client.gameMode.handleContainerInput(containerId, menuSlotIndex, 0,
                                ContainerInput.PICKUP, client.player);

                        // 2. Right-click the locked slot previousCount times to put back 1 item each click
                        for (int i = 0; i < previousCount; i++) {
                            if (menu.getCarried().isEmpty()) break;
                            client.gameMode.handleContainerInput(containerId, menuSlotIndex, 1,
                                    ContainerInput.PICKUP, client.player);
                        }

                        // 3. Transfer the remaining (excess) items on cursor to a non-locked empty slot.
                        // Note: THROW targets a slot, not the cursor — it cannot be used to discard cursor items.
                        // If no empty slot is available, the cursor items are left as-is (cannot be safely discarded).
                        if (!menu.getCarried().isEmpty() && targetMenuSlot >= 0) {
                            client.gameMode.handleContainerInput(containerId, targetMenuSlot, 0,
                                    ContainerInput.PICKUP, client.player);
                        }
                        // If no empty slot: excess items remain on cursor (locked slot is untouched).
                    } else {
                        // Scenario 1/3: move the whole stack out (also the fallback when cursor is non-empty).
                        int targetMenuSlot = findFirstNonLockedEmptySlot(menu, inv);
                        if (targetMenuSlot >= 0) {
                            client.gameMode.handleContainerInput(containerId, menuSlotIndex, 0,
                                    ContainerInput.PICKUP, client.player);
                            client.gameMode.handleContainerInput(containerId, targetMenuSlot, 0,
                                    ContainerInput.PICKUP, client.player);
                        } else {
                            // Inventory full of no empty slot: Ctrl+Q (button=1) throws the whole stack out.
                            client.gameMode.handleContainerInput(containerId, menuSlotIndex, 1,
                                    ContainerInput.THROW, client.player);
                        }
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
