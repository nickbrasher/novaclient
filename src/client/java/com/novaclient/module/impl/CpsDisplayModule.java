package com.novaclient.module.impl;

import com.novaclient.gui.RenderUtil;
import com.novaclient.hud.HudElement;
import com.novaclient.module.Category;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/**
 * Shows clicks per second. Clicks are recorded by MouseHandlerMixin (in-game only);
 * CPS is simply how many clicks happened in the last second.
 */
public class CpsDisplayModule extends HudElement {
	private static final int PADDING = 4;
	private static final long WINDOW_MILLIS = 1000;

	private final BooleanSetting showRightClicks = addSetting(new BooleanSetting("Show Right Clicks", true));
	private final BooleanSetting showBackground = addSetting(new BooleanSetting("Show Background", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));

	// Timestamps (ms) of recent clicks, oldest first.
	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

	public CpsDisplayModule() {
		super("CPS Display", "Shows your clicks per second.", Category.PVP, 0f, 0.05f);
	}

	/** Called by MouseHandlerMixin on every in-game mouse press. */
	public void recordClick(int button) {
		long now = Util.getMillis();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			leftClicks.addLast(now);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			rightClicks.addLast(now);
		}
	}

	/** Drops clicks older than one second and returns how many are left. */
	private static int count(ArrayDeque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_MILLIS) {
			clicks.removeFirst();
		}
		return clicks.size();
	}

	private String text() {
		long now = Util.getMillis();
		int left = count(leftClicks, now);
		if (!showRightClicks.get()) {
			return left + " CPS";
		}
		return left + " | " + count(rightClicks, now) + " CPS";
	}

	@Override
	public int getWidth() {
		// Sized for two-digit counts so the box doesn't jump around while clicking.
		String sample = showRightClicks.get() ? "00 | 00 CPS" : "00 CPS";
		return Minecraft.getInstance().font.width(sample) + PADDING * 2;
	}

	@Override
	public int getHeight() {
		return 8 + PADDING * 2;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		Font font = Minecraft.getInstance().font;
		String text = text();
		if (showBackground.get()) {
			RenderUtil.roundedRect(graphics, 0, 0, getWidth(), getHeight(), 3, 0x66000000);
		}
		RenderUtil.text(graphics, font, text, (getWidth() - font.width(text)) / 2f, PADDING, textColor.get(),
				!showBackground.get());
	}
}
