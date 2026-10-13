package com.novaclient.module;

import com.novaclient.module.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature in Nova Client. Each real module "extends" this
 * (inheritance), sets its name/description/category in the constructor, and
 * adds its settings with addSetting().
 */
public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	// Bumped whenever any module is switched on or off, so caches (like Active Mods)
	// can tell when to rebuild without checking every module every frame.
	private static int enabledStateVersion;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	/**
	 * Registers a setting and hands it back, so a subclass can write:
	 * {@code private final BooleanSetting foo = addSetting(new BooleanSetting("Foo", true));}
	 */
	protected <S extends Setting<?>> S addSetting(S setting) {
		settings.add(setting);
		return setting;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		enabledStateVersion++;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/** Called when the module turns on. Subclasses override this when they need it. */
	protected void onEnable() {
	}

	/** Called when the module turns off. */
	protected void onDisable() {
	}

	/**
	 * Called at the start of every client tick (20x per second), before the player moves,
	 * whether or not the module is enabled. Most modules don't need it.
	 */
	public void tick(Minecraft client) {
	}

	/** Hidden modules are left out of the grid and search (e.g. Menu Settings). */
	public boolean isHidden() {
		return false;
	}

	/** Whether the menu shows an on/off switch for this module. */
	public boolean isToggleable() {
		return true;
	}

	public static int getEnabledStateVersion() {
		return enabledStateVersion;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}
}
