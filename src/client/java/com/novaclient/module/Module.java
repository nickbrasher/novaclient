package com.novaclient.module;

import com.novaclient.module.setting.Setting;

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
