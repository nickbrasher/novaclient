package com.novaclient.gui.anim;

/**
 * Momentum scrolling: each wheel notch adds velocity, and friction slows it down,
 * so the list glides to a stop instead of jumping a fixed amount per notch.
 */
public final class SmoothScroller {
	private static final float IMPULSE = 280f; // GUI pixels per second added per wheel notch
	private static final float FRICTION = 9f;  // how quickly the glide dies out
	private static final float STOP_SPEED = 2f;

	private float offset;
	private float velocity;
	private float maxOffset;

	/** amount is in wheel notches; positive scrolls down. */
	public void impulse(double amount) {
		velocity += (float) amount * IMPULSE;
	}

	public void update(float deltaSeconds, float maxOffset) {
		this.maxOffset = Math.max(0, maxOffset);
		offset += velocity * deltaSeconds;
		velocity *= (float) Math.exp(-FRICTION * deltaSeconds);
		if (Math.abs(velocity) < STOP_SPEED) {
			velocity = 0;
		}
		if (offset < 0 || offset > this.maxOffset) {
			offset = Math.clamp(offset, 0, this.maxOffset);
			velocity = 0;
		}
	}

	public void reset() {
		offset = 0;
		velocity = 0;
	}

	public float get() {
		return offset;
	}

	public float getMax() {
		return maxOffset;
	}

	public boolean isMoving() {
		return velocity != 0;
	}
}
