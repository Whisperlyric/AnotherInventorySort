package dev.whisperlyric.anotherinventorysort.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.whisperlyric.anotherinventorysort.GuiConfig;

public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (parent) -> new GuiConfig();
    }
}
