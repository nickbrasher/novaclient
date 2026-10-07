package com.novaclient.module.setting;

import com.google.gson.JsonElement;

/**
 * Base class for a single module option. {@code <T>} is a generic type parameter,
 * like {@code Setting<T>} in TypeScript: BooleanSetting is a Setting<Boolean>, etc.
 */
public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	protected T value;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		set(defaultValue);
	}

	/** Converts the current value to JSON for the config file. */
	public abstract JsonElement toJson();

	/** Loads a value from the config file. Bad or missing data keeps the current value. */
	public abstract void fromJson(JsonElement json);
}
