package com.novaclient.module.impl;

import com.novaclient.gui.RenderUtil;
import com.novaclient.hud.HudElement;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import com.novaclient.util.UpdateTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows your block position, and optionally which way you're facing and the biome you're in. */
public class CoordinatesModule extends HudElement {
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;

	private final BooleanSetting showDirection = addSetting(new BooleanSetting("Show Direction", true));
	private final BooleanSetting showBiome = addSetting(new BooleanSetting("Show Biome", false));
	private final BooleanSetting showBackground = addSetting(new BooleanSetting("Show Background", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));

	// Lines are rebuilt 10x per second rather than every frame.
	private final UpdateTimer refresh = new UpdateTimer(100);
	private final List<String> lines = new ArrayList<>();
	private int widestLine;

	public CoordinatesModule() {
		super("Coordinates", "Shows your XYZ position and facing.", 0f, 0.2f);
	}

	private void refreshLines(boolean editing) {
		lines.clear();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			if (editing) {
				// Sample text so there's something to position in the HUD editor's title-screen preview.
				lines.add("XYZ: 128, 64, -256");
				if (showDirection.get()) {
					lines.add("Facing: North (-Z)");
				}
				if (showBiome.get()) {
					lines.add("Biome: Plains");
				}
			}
		} else {
			BlockPos pos = player.blockPosition();
			lines.add("XYZ: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
			if (showDirection.get()) {
				lines.add("Facing: " + describe(player.getDirection()));
			}
			if (showBiome.get()) {
				String biome = player.level().getBiome(pos).unwrapKey()
						.map(key -> titleCase(key.identifier().getPath()))
						.orElse("Unknown");
				lines.add("Biome: " + biome);
			}
		}

		Font font = Minecraft.getInstance().font;
		widestLine = 0;
		for (String line : lines) {
			widestLine = Math.max(widestLine, font.width(line));
		}
	}

	/** e.g. NORTH -> "North (-Z)", so you know which coordinate goes up as you walk. */
	private static String describe(Direction direction) {
		String axis = switch (direction) {
			case NORTH -> "-Z";
			case SOUTH -> "+Z";
			case EAST -> "+X";
			case WEST -> "-X";
			case UP -> "+Y";
			case DOWN -> "-Y";
		};
		return titleCase(direction.getName()) + " (" + axis + ")";
	}

	/** "dark_forest" -> "Dark Forest" */
	private static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			if (!word.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return out.toString();
	}

	@Override
	public int getWidth() {
		return Math.max(widestLine, 60) + PADDING * 2;
	}

	@Override
	public int getHeight() {
		return Math.max(lines.size(), 1) * LINE_HEIGHT + PADDING * 2 - 2;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		if (refresh.ready() || lines.isEmpty()) {
			refreshLines(editing);
		}
		if (lines.isEmpty()) {
			return;
		}
		if (showBackground.get()) {
			RenderUtil.roundedRect(graphics, 0, 0, getWidth(), getHeight(), 3, 0x66000000);
		}
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < lines.size(); i++) {
			RenderUtil.text(graphics, font, lines.get(i), PADDING, PADDING + i * LINE_HEIGHT, textColor.get(),
					!showBackground.get());
		}
	}
}
