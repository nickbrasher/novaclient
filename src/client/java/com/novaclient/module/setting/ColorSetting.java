package com.novaclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A color stored as a single ARGB int (0xAARRGGBB), the format Minecraft draws with. */
public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, int defaultArgb) {
		super(name, defaultArgb);
	}

	/** Reads one channel. shift is 24 for alpha, 16 red, 8 green, 0 blue. */
	public int getChannel(int shift) {
		return (value >>> shift) & 0xFF;
	}

	public void setChannel(int shift, int channelValue) {
		int clamped = Math.clamp(channelValue, 0, 255);
		set((value & ~(0xFF << shift)) | (clamped << shift));
	}

	public String toHex() {
		return String.format("#%08X", value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(toHex());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonPrimitive()) {
			return;
		}
		String hex = json.getAsString().replace("#", "");
		try {
			set((int) Long.parseLong(hex, 16));
		} catch (NumberFormatException ignored) {
			// Keep the current color if the file has garbage in it.
		}
	}
}
