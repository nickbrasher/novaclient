package com.novaclient.gui;

import com.novaclient.NovaClient;

/** Shared ARGB colors (0xAARRGGBB) and color math so the whole menu stays consistent. */
public final class Theme {
	public static final int TINT = 0x8C000000;
	public static final int PANEL = 0xEE141419;
	public static final int SIDEBAR = 0xEE0E0E12;
	public static final int BORDER = 0x1FFFFFFF;
	public static final int DIVIDER = 0x14FFFFFF;
	public static final int CARD = 0xFF1D1D24;
	public static final int CARD_HOVER = 0xFF26262F;
	public static final int TAB_SELECTED = 0x16FFFFFF;
	public static final int TOGGLE_OFF = 0xFF3A3A46;
	public static final int SHADOW = 0xFF000000;
	public static final int TEXT = 0xFFF2F2F5;
	public static final int TEXT_DIM = 0xFF8C8C9A;

	private Theme() {
	}

	/** The player's chosen accent color from Menu Settings, always fully opaque. */
	public static int accent() {
		return NovaClient.getModules().getMenuSettings().getAccentColor() | 0xFF000000;
	}

	/** Multiplies a color's alpha by mult (0 to 1). */
	public static int withAlpha(int color, float mult) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * Math.clamp(mult, 0f, 1f));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	/** Blends from color a (t = 0) to color b (t = 1), channel by channel. */
	public static int lerp(int a, int b, float t) {
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			result |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return result;
	}
}
