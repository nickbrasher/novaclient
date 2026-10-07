package com.novaclient.gui.anim;

/**
 * A fixed-length animation between 0 and 1 that can play forwards or backwards.
 * Playing backwards retraces the same curve, so a close looks like an open in reverse.
 */
public final class Tween {
	private final float durationSeconds;
	private float progress;
	private boolean forward;

	public Tween(float durationSeconds, float initialProgress, boolean forward) {
		this.durationSeconds = durationSeconds;
		this.progress = initialProgress;
		this.forward = forward;
	}

	public void play(boolean forward) {
		this.forward = forward;
	}

	/** Jumps back to the start and plays forwards. */
	public void restart() {
		progress = 0;
		forward = true;
	}

	public void update(float deltaSeconds) {
		float step = deltaSeconds / durationSeconds;
		progress = Math.clamp(progress + (forward ? step : -step), 0f, 1f);
	}

	public boolean isForward() {
		return forward;
	}

	public boolean isFinished() {
		return forward ? progress >= 1 : progress <= 0;
	}

	public float eased() {
		return Easing.easeOutCubic(progress);
	}
}
