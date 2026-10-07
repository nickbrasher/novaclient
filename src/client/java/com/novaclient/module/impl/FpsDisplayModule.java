package com.novaclient.module.impl;

import com.novaclient.gui.RenderUtil;
import com.novaclient.hud.HudElement;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shows the current frame rate. Size and position are handled by HudElement. */
public class FpsDisplayModule extends HudElement {
	private static final int PADDING = 4;

	private final BooleanSetting showBackground = addSetting(new BooleanSetting("Show Background", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));

	public FpsDisplayModule() {
		super("FPS Display", "Shows your frames per second on screen.", 0f, 0f);
	}

	@Override
	public int getWidth() {
		// Sized for a 3-digit number so the box doesn't jitter as the FPS changes.
		return Minecraft.getInstance().font.width("000 FPS") + PADDING * 2;
	}

	@Override
	public int getHeight() {
		return 8 + PADDING * 2;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		Font font = Minecraft.getInstance().font;
		String text = Minecraft.getInstance().getFps() + " FPS";
		if (showBackground.get()) {
			RenderUtil.roundedRect(graphics, 0, 0, getWidth(), getHeight(), 3, 0x66000000);
		}
		RenderUtil.text(graphics, font, text, (getWidth() - font.width(text)) / 2f, PADDING, textColor.get(), !showBackground.get());
	}
}
