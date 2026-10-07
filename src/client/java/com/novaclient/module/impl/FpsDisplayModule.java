package com.novaclient.module.impl;

import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import com.novaclient.module.setting.NumberSetting;

/** Placeholder: settings only, no on-screen display yet. */
public class FpsDisplayModule extends Module {
	private final BooleanSetting showBackground = addSetting(new BooleanSetting("Show Background", true));
	private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.1));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));

	public FpsDisplayModule() {
		super("FPS Display", "Shows your frames per second on screen.", Category.HUD);
	}
}
