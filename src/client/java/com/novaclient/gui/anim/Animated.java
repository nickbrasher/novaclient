package com.novaclient.gui.anim;

/**
 * A value that glides toward a target, covering a fixed fraction of the remaining
 * distance each second. Good for hover and toggle states that can change mid-animation:
 * a new target just bends the motion instead of restarting it.
 */
public final class Animated {
	private final float speed;
	private float value;
	private float target;

	/** Higher speed = snappier. Around 14 settles in roughly a quarter second. */
	public Animated(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public float update(float target, float deltaSeconds) {
		this.target = target;
		value += (target - value) * (1 - (float) Math.exp(-speed * deltaSeconds));
		if (Math.abs(target - value) < 0.001f) {
			value = target;
		}
		return value;
	}

	public void snap(float value) {
		this.value = value;
		this.target = value;
	}

	public float get() {
		return value;
	}
}
