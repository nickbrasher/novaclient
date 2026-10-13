package com.novaclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * A keyboard key, stored as a GLFW key code (e.g. GLFW.GLFW_KEY_C).
 * GLFW_KEY_UNKNOWN (-1) means "not bound".
 */
public class KeybindSetting extends Setting<Integer> {
	public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

	public KeybindSetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public boolean isBound() {
		return value != NONE;
	}

	public boolean matches(int key) {
		return isBound() && value == key;
	}

	/** The key's name as shown in vanilla's Controls screen (e.g. "C", "Left Shift"), or "None". */
	public String getKeyName() {
		if (!isBound()) {
			return "None";
		}
		return InputConstants.Type.KEYSYM.getOrCreate(value).getDisplayName().getString();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
