package com.novaclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The main Nova Client menu. Empty for now; modules get added here later.
 */
public class NovaMenuScreen extends Screen {
	// Colors are ARGB hex: 0xAARRGGBB. 0xB0 alpha is roughly 70% opaque.
	private static final int BACKGROUND_COLOR = 0xB0101014;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int TITLE_Y = 20;

	public NovaMenuScreen() {
		super(Component.literal("Nova Client"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Replace vanilla's blur with a flat dark translucent overlay.
		graphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_Y, TITLE_COLOR);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the game running behind the menu, like a client overlay.
		return false;
	}
}
