package com.novaclient.module.impl;

import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;

/** Placeholder: settings only, sprinting isn't changed yet. */
public class ToggleSprintModule extends Module {
	private final BooleanSetting showIndicator = addSetting(new BooleanSetting("Show HUD Indicator", true));
	private final ColorSetting indicatorColor = addSetting(new ColorSetting("Indicator Color", 0xFF5B8CFF));

	public ToggleSprintModule() {
		super("Toggle Sprint", "Keeps you sprinting without holding the key.", Category.UTILITY);
	}
}
