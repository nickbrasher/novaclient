package com.novaclient.module.impl;

import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.ColorSetting;

/**
 * Options for the menu itself. It's a Module so it reuses the settings panel and
 * config saving, but it's hidden from the grid and can't be switched off.
 */
public class MenuSettingsModule extends Module {
	private final ColorSetting accentColor = addSetting(new ColorSetting("Accent Color", 0xFF5B8CFF));

	public MenuSettingsModule() {
		super("Menu Settings", "Customize how the Nova Client menu looks.", Category.UTILITY);
	}

	public int getAccentColor() {
		return accentColor.get();
	}

	@Override
	public boolean isHidden() {
		return true;
	}

	@Override
	public boolean isToggleable() {
		return false;
	}
}
