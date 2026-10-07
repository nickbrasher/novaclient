package com.novaclient.module.impl;

import com.novaclient.NovaClient;
import com.novaclient.gui.RenderUtil;
import com.novaclient.gui.Theme;
import com.novaclient.gui.anim.Animated;
import com.novaclient.gui.anim.Easing;
import com.novaclient.hud.HudElement;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import com.novaclient.module.setting.ModeSetting;
import com.novaclient.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists every enabled module, longest name on top, sliding entries in and out as they toggle. */
public class ActiveModsModule extends HudElement {
	private static final int LINE_HEIGHT = 11;
	private static final int PADDING_X = 4;
	private static final float SLIDE_SPEED = 12f;

	private final BooleanSetting useAccent = addSetting(new BooleanSetting("Use Accent Color", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));
	private final NumberSetting backgroundOpacity = addSetting(new NumberSetting("Background Opacity", 40, 0, 100, 5));
	private final BooleanSetting textShadow = addSetting(new BooleanSetting("Text Shadow", true));
	private final ModeSetting alignment = addSetting(new ModeSetting("Alignment", "Right", "Left", "Right"));

	// Each listed module and how far it has slid in (0 = gone, 1 = fully shown).
	// Modules stay here while sliding out, then get removed.
	private final Map<Module, Animated> entries = new LinkedHashMap<>();

	public ActiveModsModule() {
		super("Active Mods", "Lists your enabled modules on screen.", 1f, 0f);
	}

	/** Adds newly enabled modules. Removal happens once a disabled module has finished sliding out. */
	private void syncEntries() {
		for (Module module : NovaClient.getModules().getAll()) {
			if (module.isEnabled() && !module.isHidden() && !entries.containsKey(module)) {
				entries.put(module, new Animated(0, SLIDE_SPEED));
			}
		}
	}

	/** Entries sorted widest first, so the list forms a neat staircase. */
	private List<Module> sortedEntries(Font font) {
		List<Module> sorted = new ArrayList<>(entries.keySet());
		sorted.sort(Comparator.comparingInt((Module module) -> font.width(module.getName())).reversed()
				.thenComparing(Module::getName));
		return sorted;
	}

	@Override
	public int getWidth() {
		syncEntries();
		Font font = Minecraft.getInstance().font;
		int widest = 0;
		for (Module module : entries.keySet()) {
			widest = Math.max(widest, font.width(module.getName()));
		}
		return Math.max(widest, 20) + PADDING_X * 2;
	}

	@Override
	public int getHeight() {
		syncEntries();
		float height = 0;
		for (Animated progress : entries.values()) {
			height += LINE_HEIGHT * Easing.easeOutCubic(progress.get());
		}
		return Math.max(Math.round(height), LINE_HEIGHT);
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		syncEntries();
		Font font = Minecraft.getInstance().font;
		int width = getWidth();
		boolean alignRight = alignment.is("Right");
		int color = useAccent.get() ? Theme.accent() : textColor.get();
		int background = Theme.withAlpha(0xFF000000, backgroundOpacity.get().floatValue() / 100f);

		float y = 0;
		for (Module module : sortedEntries(font)) {
			Animated progress = entries.get(module);
			float shown = Easing.easeOutCubic(progress.update(module.isEnabled() ? 1 : 0, deltaSeconds));
			if (shown <= 0 && !module.isEnabled()) {
				entries.remove(module);
				continue;
			}

			String name = module.getName();
			int lineWidth = font.width(name) + PADDING_X * 2;
			// Slide in from (and out toward) the aligned edge.
			float slide = (1 - shown) * (lineWidth + 8);
			float lineX = alignRight ? width - lineWidth + slide : -slide;

			RenderUtil.roundedRect(graphics, lineX, y, lineWidth, LINE_HEIGHT, 0, Theme.withAlpha(background, shown));
			RenderUtil.text(graphics, font, name, lineX + PADDING_X, y + 2, Theme.withAlpha(color, shown), textShadow.get());
			y += LINE_HEIGHT * shown;
		}
	}
}
