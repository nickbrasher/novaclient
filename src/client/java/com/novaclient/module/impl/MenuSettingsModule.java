package com.novaclient.module.impl;

import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;

/**
 * Options for the menu itself. It's a Module so it reuses the settings panel and
 * config saving, but it's hidden from the grid and can't be switched off.
 */
public class MenuSettingsModule extends Module {
	private final ColorSetting accentColor = addSetting(new ColorSetting("Accent Color", 0xFF5B8CFF));
	private final BooleanSetting backgroundBlur = addSetting(new BooleanSetting("Background Blur", true));
	private final BooleanSetting showRenderTime = addSetting(new BooleanSetting("Show Render Time", false));

	public MenuSettingsModule() {
		super("Menu Settings", "Customize how the Nova Client menu looks.", Category.UTILITY);
	}

	public int getAccentColor() {
		return accentColor.get();
	}

	/** Blur is the most expensive effect in the menu, so it can be turned off. */
	public boolean isBackgroundBlurEnabled() {
		return backgroundBlur.get();
	}

	public boolean isShowRenderTimeEnabled() {
		return showRenderTime.get();
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
