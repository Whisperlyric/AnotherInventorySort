package dev.whisperlyric.anotherinventorysort;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

public class AnotherInventorySortConfigs implements IConfigHandler {

    private static final String CONFIG_FILE_NAME = "anotherinventorysort.json";
    private static final String HOTKEYS_KEY = "anotherinventorysort.config.hotkeys";
    private static final String GENERIC_KEY = "anotherinventorysort.config.generic";

    // ===== Hotkeys =====
    public static final ConfigHotkey SORT_AT_CURSOR = new ConfigHotkey("sortAtCursor", "BUTTON3").apply(HOTKEYS_KEY);
    public static final ConfigHotkey LOCK_MODIFIER = new ConfigHotkey("lockModifier", "LMENU").apply(HOTKEYS_KEY);
    public static final ConfigHotkey TRANSFER_ALL_MODIFIER = new ConfigHotkey("transferAllModifier", "LSHIFT").apply(HOTKEYS_KEY);
    public static final ConfigHotkey OPEN_CONFIG = new ConfigHotkey("openConfig", "LMENU,C").apply(HOTKEYS_KEY);

    // ===== Toggles =====
    public static final ConfigBoolean SORT_ENABLED = new ConfigBoolean("sortEnabled", true).apply(GENERIC_KEY);
    public static final ConfigBooleanHotkeyed SLOT_LOCK_ENABLED = new ConfigBooleanHotkeyed("slotLockEnabled", true, "").apply(GENERIC_KEY);
    public static final ConfigBooleanHotkeyed TRANSFER_BUTTONS_ENABLED = new ConfigBooleanHotkeyed("transferButtonsEnabled", true, "").apply(GENERIC_KEY);
    public static final ConfigBoolean BLOCK_EXTERNAL_SWAPS = new ConfigBoolean("blockExternalSwaps", true).apply(GENERIC_KEY);

    // ===== Config lists =====
    public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
            SORT_ENABLED,
            SORT_AT_CURSOR,
            LOCK_MODIFIER,
            TRANSFER_ALL_MODIFIER,
            OPEN_CONFIG,
            SLOT_LOCK_ENABLED,
            TRANSFER_BUTTONS_ENABLED,
            BLOCK_EXTERNAL_SWAPS
    );

    public static final ImmutableList<@NotNull ConfigHotkey> HOTKEYS = ImmutableList.of(
            SORT_AT_CURSOR,
            LOCK_MODIFIER,
            TRANSFER_ALL_MODIFIER,
            OPEN_CONFIG
    );

    @Override
    public void load() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile)) {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "anotherinventorysort", OPTIONS);
            }
        }
    }

    @Override
    public void save() {
        Path dir = FileUtils.getConfigDirectory();

        if (!Files.exists(dir)) {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir)) {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "anotherinventorysort", OPTIONS);
            JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
        }
    }
}
