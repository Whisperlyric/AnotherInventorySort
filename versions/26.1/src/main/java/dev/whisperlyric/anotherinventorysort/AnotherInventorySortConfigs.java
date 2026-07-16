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
import java.util.List;

public class AnotherInventorySortConfigs implements IConfigHandler {

    private static final String CONFIG_FILE_NAME = "anotherinventorysort.json";

    // ===== Hotkeys =====
    public static final ConfigHotkey SORT_AT_CURSOR = new ConfigHotkey("sortAtCursor", "BUTTON3", "Sort inventory at cursor position");
    public static final ConfigHotkey LOCK_MODIFIER = new ConfigHotkey("lockModifier", "LMENU", "Modifier key to lock/unlock slots");
    public static final ConfigHotkey TRANSFER_ALL_MODIFIER = new ConfigHotkey("transferAllModifier", "LSHIFT", "Modifier key for full transfer");

    // ===== Toggles =====
    public static final ConfigBoolean SORT_ENABLED = new ConfigBoolean("sortEnabled", true, "Enable inventory sorting");
    public static final ConfigBooleanHotkeyed SLOT_LOCK_ENABLED = new ConfigBooleanHotkeyed("slotLockEnabled", true, "", "Enable slot locking feature");
    public static final ConfigBooleanHotkeyed TRANSFER_BUTTONS_ENABLED = new ConfigBooleanHotkeyed("transferButtonsEnabled", true, "", "Show transfer buttons between container and player inventory");
    public static final ConfigBoolean BLOCK_EXTERNAL_SWAPS = new ConfigBoolean("blockExternalSwaps", true, "Block ItemSwapper SWAP operations on locked slots");

    // ===== Config lists =====
    public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
            SORT_ENABLED,
            SORT_AT_CURSOR,
            LOCK_MODIFIER,
            TRANSFER_ALL_MODIFIER,
            SLOT_LOCK_ENABLED,
            TRANSFER_BUTTONS_ENABLED,
            BLOCK_EXTERNAL_SWAPS
    );

    public static final ImmutableList<@NotNull ConfigHotkey> HOTKEYS = ImmutableList.of(
            SORT_AT_CURSOR,
            LOCK_MODIFIER,
            TRANSFER_ALL_MODIFIER
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
