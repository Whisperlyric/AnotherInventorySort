package dev.whisperlyric.anotherinventorysort;

import dev.whisperlyric.anotherinventorysort.sort.SortHandler;
import dev.whisperlyric.anotherinventorysort.sort.SortMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
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
    private static final int TEXTURE_SIZE = 256;

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
            ContainerCategory category = categorize(menu);
            if (category == ContainerCategory.NON_STORAGE) return;

            int guiLeft = containerScreen.leftPos;
            int guiTop = containerScreen.topPos;

            int slotTopY;
            int slotRightX;

            int[] pos;
            if (category == ContainerCategory.SORTABLE_STORAGE) {
                pos = findContainerSlotArea(menu, guiLeft, guiTop);
            } else {
                pos = findPlayerStorageArea(menu, guiLeft, guiTop);
            }
            slotTopY = pos[0];
            slotRightX = pos[1];

            int startX = slotRightX - BUTTON_SPACING * 3;
            int startY = slotTopY - BUTTON_SIZE - BUTTON_GAP;

            List<SortButtonInfo> buttons = new ArrayList<>();
            buttons.add(new SortButtonInfo(startX, startY, 10, SortMode.DEFAULT,
                    Component.translatable("anotherinventorysort.button.sort")));
            buttons.add(new SortButtonInfo(startX + BUTTON_SPACING, startY, 30, SortMode.ROW,
                    Component.translatable("anotherinventorysort.button.sort_row")));
            buttons.add(new SortButtonInfo(startX + BUTTON_SPACING * 2, startY, 20, SortMode.COLUMN,
                    Component.translatable("anotherinventorysort.button.sort_column")));

            ScreenEvents.afterRender(screen).register((screen1, graphics, mouseX, mouseY, tickDelta) -> renderButtons(graphics, mouseX, mouseY, buttons));

            ScreenMouseEvents.allowMouseClick(screen).register((screen1, mouseX1, mouseY1, button) -> {
                if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
                for (SortButtonInfo btn : buttons) {
                    if (btn.isHovered(mouseX1, mouseY1)) {
                        SortHandler.sortInventory(containerScreen, btn.sortMode);
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
        NON_STORAGE
    }

    private static ContainerCategory categorize(AbstractContainerMenu menu) {
        if (menu instanceof BeaconMenu) return ContainerCategory.NON_STORAGE;
        if (menu instanceof LecternMenu) return ContainerCategory.NON_STORAGE;
        if (menu instanceof LoomMenu) return ContainerCategory.NON_STORAGE;
        if (menu instanceof BrewingStandMenu) return ContainerCategory.NON_STORAGE;
        if (menu instanceof MerchantMenu) return ContainerCategory.NON_STORAGE;

        if (menu instanceof ChestMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof ShulkerBoxMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof HorseInventoryMenu) return ContainerCategory.SORTABLE_STORAGE;
        if (menu instanceof DispenserMenu) return ContainerCategory.SORTABLE_STORAGE;

        return ContainerCategory.PURE_BACKPACK;
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

        if (topY == -1) {
            topY = guiTop + 5;
            rightX = guiLeft + 176 - 7;
        }

        return new int[]{topY, rightX};
    }

    private static int[] findPlayerStorageArea(AbstractContainerMenu menu, int guiLeft, int guiTop) {
        java.util.Map<Integer, List<Slot>> rowsByY = new java.util.LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container instanceof Inventory) {
                rowsByY.computeIfAbsent(slot.y, k -> new java.util.ArrayList<>()).add(slot);
            }
        }

        int topY = -1;
        int rightX = -1;

        for (var entry : rowsByY.entrySet()) {
            if (entry.getValue().size() == 9) {
                topY = guiTop + entry.getKey();
                int maxSlotX = 0;
                for (Slot s : entry.getValue()) {
                    int sx = guiLeft + s.x + 18;
                    if (sx > maxSlotX) maxSlotX = sx;
                }
                rightX = maxSlotX;
                break;
            }
        }

        if (topY == -1) {
            topY = guiTop + 5;
            rightX = guiLeft + 176 - 7;
        }

        return new int[]{topY, rightX};
    }

    // Render

    private static void renderButtons(GuiGraphics graphics, int mouseX, int mouseY, List<SortButtonInfo> buttons) {
        for (SortButtonInfo btn : buttons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            int u = btn.textureX;
            int v = hovered ? BUTTON_SIZE : 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, btn.x, btn.y, u, v, BUTTON_SIZE, BUTTON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
