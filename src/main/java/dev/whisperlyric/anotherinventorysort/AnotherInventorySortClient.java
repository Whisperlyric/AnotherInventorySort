package dev.whisperlyric.anotherinventorysort;

import dev.whisperlyric.anotherinventorysort.sort.SortHandler;
import dev.whisperlyric.anotherinventorysort.sort.SortMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AnotherInventorySortClient implements ClientModInitializer {

    private static final int BUTTON_SIZE = 10;
    private static final int BUTTON_SPACING = 12;
    private static final int BUTTON_GAP = 2;
    private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "anotherinventorysort", "textures/gui/gui_buttons.png");

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

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            if (screen instanceof CreativeModeInventoryScreen) return;

            AbstractContainerMenu menu = containerScreen.getMenu();

            // Quick pre-filter: NON_STORAGE types (no timing issues here)
            if (isNonStorage(menu)) return;

            int guiLeft = containerScreen.leftPos;
            int guiTop = containerScreen.topPos;

            // Lazily initialized on first render frame (allows STRUCTURE_VOID NBT to sync from server)
            final ContainerCategory[] categoryRef = {null};
            final List<SortButtonInfo> buttons = new ArrayList<>();
            final boolean[] initialized = {false};

            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                if (!initialized[0]) {
                    ContainerCategory category = categorize(menu, containerScreen.getTitle());
                    if (category == ContainerCategory.NON_STORAGE) {
                        initialized[0] = true; // prevent re-detection, just render nothing
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
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
                if (!initialized[0] || categoryRef[0] == null) return true;
                for (SortButtonInfo btn : buttons) {
                    if (btn.isHovered(mouseX, mouseY)) {
                        SortHandler.sortInventory(containerScreen, btn.sortMode, categoryRef[0].name());
                        return false;
                    }
                }
                return true;
            });
        });
    }

    // Container category

    private enum ContainerCategory {
        SORTABLE_STORAGE,
        PURE_BACKPACK,
        NON_STORAGE,
        GCA_FAKE_PLAYER_INVENTORY,
        GCA_FAKE_PLAYER_ENDER_CHEST
    }

    /** Quick pre-filter for definitely non-sortable menu types (no timing-dependent state). */
    private static boolean isNonStorage(AbstractContainerMenu menu) {
        return menu instanceof BeaconMenu
            || menu instanceof LecternMenu
            || menu instanceof LoomMenu
            || menu instanceof BrewingStandMenu
            || menu instanceof MerchantMenu;
    }

    /** Full categorization, including GCA detection. */
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

    /** Detect GCA (gugle-carpet-addition) fake player containers via slot 0 STRUCTURE_VOID marker
     *  + menu title to distinguish inventory vs ender chest. */
    private static ContainerCategory detectGcaContainer(AbstractContainerMenu menu, Component screenTitle) {
        if (!(menu instanceof ChestMenu)) return null;
        if (menu.slots.isEmpty()) return null;

        ItemStack stack0 = menu.getSlot(0).getItem();
        if (!stack0.is(Items.STRUCTURE_VOID)) return null;

        var cd = stack0.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;

        CompoundTag tag = cd.copyTag();
        if (tag == null || (!tag.contains("GcaClear") && !tag.contains("gca.clear"))) return null;

        // GCA confirmed. Determine type via screen title.
        // Inventory: title = fake player's name ("Steve")
        // Ender chest: title = "Ender Chest" (translation of container.enderchest)
        String titleStr = screenTitle.getString().toLowerCase();
        if (titleStr.contains("ender chest") || titleStr.contains("末影箱")) {
            return ContainerCategory.GCA_FAKE_PLAYER_ENDER_CHEST;
        }
        return ContainerCategory.GCA_FAKE_PLAYER_INVENTORY;
    }

    // Compute button area based on category

    private static int[] computeButtonArea(AbstractContainerMenu menu, ContainerCategory category,
                                            int guiLeft, int guiTop) {
        switch (category) {
            case SORTABLE_STORAGE:
                return findContainerSlotArea(menu, guiLeft, guiTop);
            case GCA_FAKE_PLAYER_INVENTORY:
            case GCA_FAKE_PLAYER_ENDER_CHEST:
                // Place buttons at top-right of the GUI, aligned with first container row
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
            default: // PURE_BACKPACK
                return findPlayerStorageArea(menu, guiLeft, guiTop);
        }
    }

    // Slot area location

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
        java.util.Map<Integer, List<Slot>> rowsByY = new java.util.LinkedHashMap<>();
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

    // Render

    private static void renderButtons(GuiGraphics graphics, int mouseX, int mouseY, List<SortButtonInfo> buttons) {
        for (SortButtonInfo btn : buttons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            int u = btn.textureX;
            int v = hovered ? BUTTON_SIZE : 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, btn.x, btn.y, u, v,
                    BUTTON_SIZE, BUTTON_SIZE, 128, 128);
        }
    }
}
