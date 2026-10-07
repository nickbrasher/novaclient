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
import java.util.List;

/**
 * Lists every enabled module, longest name on top, sliding entries in and out as they toggle.
 *
 * Performance: names, text widths, and sort order are cached in `entries` and only
 * rebuilt when a module is switched on or off (tracked by Module.getEnabledStateVersion()).
 * A normal frame just walks the cached list and draws it, creating no new objects.
 */
public class ActiveModsModule extends HudElement {
	private static final int LINE_HEIGHT = 11;
	private static final int PADDING_X = 4;
	private static final float SLIDE_SPEED = 12f;
	private static final Comparator<Entry> WIDEST_FIRST =
			Comparator.comparingInt((Entry entry) -> entry.textWidth).reversed().thenComparing(entry -> entry.name);

	private final BooleanSetting useAccent = addSetting(new BooleanSetting("Use Accent Color", true));
	private final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));
	private final NumberSetting backgroundOpacity = addSetting(new NumberSetting("Background Opacity", 40, 0, 100, 5));
	private final BooleanSetting textShadow = addSetting(new BooleanSetting("Text Shadow", true));
	private final ModeSetting alignment = addSetting(new ModeSetting("Alignment", "Right", "Left", "Right"));

	/** One line of the list. Entries stay while sliding out, then get removed. */
	private static final class Entry {
		final Module module;
		final String name;
		final int textWidth;
		final Animated progress = new Animated(0, SLIDE_SPEED);

		Entry(Module module, Font font) {
			this.module = module;
			this.name = module.getName();
			this.textWidth = font.width(name);
		}
	}

	private final List<Entry> entries = new ArrayList<>(); // always sorted widest first
	private int syncedVersion = -1;
	private int widestText;

	public ActiveModsModule() {
		super("Active Mods", "Lists your enabled modules on screen.", 1f, 0f);
	}

	/** Adds newly enabled modules, but only when something was toggled since last time. */
	private void syncEntries() {
		int version = Module.getEnabledStateVersion();
		if (version == syncedVersion) {
			return;
		}
		syncedVersion = version;
		Font font = Minecraft.getInstance().font;
		boolean added = false;
		List<Module> all = NovaClient.getModules().getAll();
		for (int i = 0; i < all.size(); i++) {
			Module module = all.get(i);
			if (module.isEnabled() && !module.isHidden() && indexOf(module) < 0) {
				entries.add(new Entry(module, font));
				added = true;
			}
		}
		if (added) {
			entries.sort(WIDEST_FIRST);
			recomputeWidest();
		}
	}

	private int indexOf(Module module) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).module == module) {
				return i;
			}
		}
		return -1;
	}

	private void recomputeWidest() {
		widestText = 0;
		for (int i = 0; i < entries.size(); i++) {
			widestText = Math.max(widestText, entries.get(i).textWidth);
		}
	}

	@Override
	public int getWidth() {
		syncEntries();
		return Math.max(widestText, 20) + PADDING_X * 2;
	}

	@Override
	public int getHeight() {
		syncEntries();
		float height = 0;
		for (int i = 0; i < entries.size(); i++) {
			height += LINE_HEIGHT * Easing.easeOutCubic(entries.get(i).progress.get());
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
		boolean shadow = textShadow.get();

		// Advance animations and drop entries that have finished sliding out.
		boolean removed = false;
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			float progress = entry.progress.update(entry.module.isEnabled() ? 1 : 0, deltaSeconds);
			if (progress <= 0 && !entry.module.isEnabled()) {
				entries.remove(i);
				removed = true;
			}
		}
		if (removed) {
			recomputeWidest();
		}

		// Two passes (all backgrounds, then all text) so Minecraft can batch similar draws together.
		if ((background >>> 24) != 0) {
			float y = 0;
			for (int i = 0; i < entries.size(); i++) {
				Entry entry = entries.get(i);
				float shown = Easing.easeOutCubic(entry.progress.get());
				RenderUtil.roundedRect(graphics, lineX(entry, shown, width, alignRight), y,
						entry.textWidth + PADDING_X * 2, LINE_HEIGHT, 0, Theme.withAlpha(background, shown));
				y += LINE_HEIGHT * shown;
			}
		}
		float y = 0;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			float shown = Easing.easeOutCubic(entry.progress.get());
			RenderUtil.text(graphics, font, entry.name, lineX(entry, shown, width, alignRight) + PADDING_X, y + 2,
					Theme.withAlpha(color, shown), shadow);
			y += LINE_HEIGHT * shown;
		}
	}

	/** Lines slide in from (and out toward) the aligned edge. */
	private static float lineX(Entry entry, float shown, int width, boolean alignRight) {
		int lineWidth = entry.textWidth + PADDING_X * 2;
		float slide = (1 - shown) * (lineWidth + 8);
		return alignRight ? width - lineWidth + slide : -slide;
	}
}
