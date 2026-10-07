package com.novaclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A number slider. Values are clamped to [min, max] and snapped to the nearest step. */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	@Override
	public void set(Double newValue) {
		double snapped = min + Math.round((newValue - min) / step) * step;
		// Trim floating-point noise like 1.2000000000000002 so the config file stays readable.
		double rounded = Math.round(snapped * 1_000_000) / 1_000_000.0;
		super.set(Math.clamp(rounded, min, max));
	}

	/** Where the value sits between min and max, from 0.0 to 1.0. Used to draw the slider. */
	public double getProgress() {
		return (value - min) / (max - min);
	}

	public void setFromProgress(double progress) {
		set(min + Math.clamp(progress, 0.0, 1.0) * (max - min));
	}

	/** Shows whole numbers without a decimal point (e.g. "5" instead of "5.0"). */
	public String getDisplayValue() {
		if (step >= 1 && value == Math.floor(value)) {
			return String.valueOf(value.longValue());
		}
		int decimals = Math.max(1, (int) Math.ceil(-Math.log10(step)));
		return String.format("%." + decimals + "f", value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
