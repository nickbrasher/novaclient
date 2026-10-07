package com.novaclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** Picks one option from a fixed list, shown as a segmented control (e.g. Left | Right). */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String defaultMode, String... modes) {
		super(name, defaultMode);
		this.modes = List.of(modes);
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	/** Ignores values that aren't one of the options, so a typo in the config can't break anything. */
	@Override
	public void set(String mode) {
		if (modes.contains(mode)) {
			super.set(mode);
		}
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
