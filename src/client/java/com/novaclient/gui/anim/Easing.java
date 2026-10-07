package com.novaclient.gui.anim;

/** Easing curves map linear progress (0 to 1) to a curve that feels natural. */
public final class Easing {
	private Easing() {
	}

	/** Starts fast and slows into the end, like something settling into place. */
	public static float easeOutCubic(float t) {
		float inverse = 1 - t;
		return 1 - inverse * inverse * inverse;
	}
}
