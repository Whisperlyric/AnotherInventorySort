package dev.whisperlyric.anotherinventorysort;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiConfig extends GuiConfigsBase implements IConfigGuiAllTab {

    public GuiConfig() {
        super(10, 50, "anotherinventorysort", null,
                "anotherinventorysort.gui.title.configs", "1.1.1");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(AnotherInventorySortConfigs.OPTIONS);
    }

    @Override
    public boolean useAllTab() {
        return false;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs() {
        return ConfigOptionWrapper.createFor(AnotherInventorySortConfigs.OPTIONS);
    }
}
