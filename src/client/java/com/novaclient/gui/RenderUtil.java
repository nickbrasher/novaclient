package com.novaclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers built on top of plain rectangle fills. */
public final class RenderUtil {
	public static final int TOGGLE_WIDTH = 20;
	public static final int TOGGLE_HEIGHT = 10;

	private RenderUtil() {
	}

	/** A filled rectangle with rounded corners, drawn as one row of pixels per corner step. */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
		int r = Math.min(radius, Math.min(width, height) / 2);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}
	}

	/** An iOS-style pill switch: accent with the knob on the right when on. */
	public static void toggleSwitch(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
		roundedRect(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2, on ? Theme.ACCENT : Theme.TOGGLE_OFF);
		int knob = TOGGLE_HEIGHT - 4;
		int knobX = on ? x + TOGGLE_WIDTH - knob - 2 : x + 2;
		roundedRect(graphics, knobX, y + 2, knob, knob, knob / 2, Theme.TEXT);
	}

	/** A thin slider track filled up to progress (0.0 to 1.0), with a small knob. */
	public static void slider(GuiGraphicsExtractor graphics, int x, int y, int width, double progress, int fillColor) {
		roundedRect(graphics, x, y, width, 4, 2, Theme.TOGGLE_OFF);
		int filled = (int) Math.round(width * progress);
		if (filled > 0) {
			roundedRect(graphics, x, y, filled, 4, 2, fillColor);
		}
		roundedRect(graphics, x + filled - 3, y - 2, 6, 8, 3, Theme.TEXT);
	}
}
