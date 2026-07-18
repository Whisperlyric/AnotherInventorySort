package dev.whisperlyric.anotherinventorysort;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {

    private static final InputHandler INSTANCE = new InputHandler();

    public static InputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : AnotherInventorySortConfigs.HOTKEYS) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("Another Inventory Sort", "anotherinventorysort.hotkeys.category", AnotherInventorySortConfigs.HOTKEYS);
    }

    @Override
    public boolean onKeyInput(KeyEvent input, boolean eventKeyState) {
        return handleInput(eventKeyState);
    }

    @Override
    public boolean onMouseClick(MouseButtonEvent input, boolean eventKeyState) {
        return handleInput(eventKeyState);
    }

    private boolean handleInput(boolean eventKeyState) {
        if (!eventKeyState) return false;

        IKeybind openConfigKey = AnotherInventorySortConfigs.OPEN_CONFIG.getKeybind();
        if (openConfigKey.isKeybindHeld()) {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                GuiBase.openGui(new GuiConfig());
            });
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return false;
    }
}
