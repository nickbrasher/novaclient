package com.novaclient.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import com.novaclient.gui.RenderUtil;
import com.novaclient.hud.HudElement;
import com.novaclient.module.Category;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Press the sprint key once to start sprinting, press again to stop.
 *
 * How it works: while toggled, we keep Minecraft's sprint key marked as "held".
 * Vanilla's own sprint rules then do the rest, so sprinting stops when you stand
 * still and resumes when you walk forward again. On top of that we explicitly
 * stop sprinting while sneaking, using an item, or too hungry.
 */
public class ToggleSprintModule extends HudElement {
	private static final String SAMPLE_TEXT = "[Sprinting (Toggled)]";
	private static final int MIN_FOOD_TO_SPRINT = 7; // vanilla needs more than 6 hunger points (3 drumsticks)

	private final BooleanSetting showStatus = addSetting(new BooleanSetting("Show Status Text", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));
	private final BooleanSetting textShadow = addSetting(new BooleanSetting("Text Shadow", true));

	private boolean toggled;

	public ToggleSprintModule() {
		super("Toggle Sprint", "Press sprint once to keep sprinting.", Category.UTILITY, 0f, 0.1f);
	}

	/** Called every client tick from NovaClient, before the player moves. */
	public void tick(Minecraft client) {
		KeyMapping sprintKey = client.options.keySprint;
		LocalPlayer player = client.player;
		if (!isEnabled() || player == null) {
			toggled = false;
			return;
		}

		// consumeClick() returns true once per key press, so each press flips the toggle.
		// Vanilla never consumes sprint presses, so we're not stealing them from anything.
		while (sprintKey.consumeClick()) {
			toggled = !toggled;
			if (!toggled) {
				// Let go of the fake hold, unless the key really is being held right now.
				sprintKey.setDown(isPhysicallyHeld(client, sprintKey));
			}
		}

		if (toggled && client.gui.screen() == null) {
			if (shouldBlockSprint(player)) {
				sprintKey.setDown(false);
				if (player.isSprinting()) {
					player.setSprinting(false);
				}
			} else {
				sprintKey.setDown(true);
			}
		}
	}

	private static boolean shouldBlockSprint(LocalPlayer player) {
		boolean tooHungry = player.getFoodData().getFoodLevel() < MIN_FOOD_TO_SPRINT && !player.getAbilities().mayfly;
		return tooHungry || player.isShiftKeyDown() || player.isUsingItem();
	}

	private static boolean isPhysicallyHeld(Minecraft client, KeyMapping key) {
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		return bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(client.getWindow(), bound.getValue());
	}

	@Override
	protected void onDisable() {
		if (toggled) {
			toggled = false;
			Minecraft client = Minecraft.getInstance();
			client.options.keySprint.setDown(isPhysicallyHeld(client, client.options.keySprint));
		}
	}

	/** The status line, or null when not sprinting. */
	private String statusText() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isSprinting()) {
			return null;
		}
		return toggled ? "[Sprinting (Toggled)]" : "[Sprinting (Vanilla)]";
	}

	@Override
	public int getWidth() {
		// Sized for the longest message so the box doesn't change size as the status changes.
		return Minecraft.getInstance().font.width(SAMPLE_TEXT);
	}

	@Override
	public int getHeight() {
		return 9;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		if (!showStatus.get()) {
			return;
		}
		String text = statusText();
		if (text == null && editing) {
			text = SAMPLE_TEXT; // show something to grab in the HUD editor
		}
		if (text != null) {
			Font font = Minecraft.getInstance().font;
			RenderUtil.text(graphics, font, text, 0, 0, textColor.get(), textShadow.get());
		}
	}
}
