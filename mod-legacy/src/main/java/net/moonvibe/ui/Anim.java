package net.moonvibe.ui;

import java.util.HashMap;
import java.util.Map;

/** Weiche Übergänge (Hover, Schalter, Tasten) – unabhängig von der Bildrate. */
public final class Anim {
	private static final class State {
		float value;
		long lastNanos;

		State(float value, long now) {
			this.value = value;
			this.lastNanos = now;
		}
	}

	private static final Map<String, State> STATES = new HashMap<>();

	private Anim() {}

	/** Bewegt den Wert "id" weich in Richtung target (speed: höher = schneller) und gibt ihn zurück. */
	public static float approach(String id, float target, float speed) {
		long now = System.nanoTime();
		State s = STATES.get(id);
		if (s == null) {
			STATES.put(id, new State(target, now));
			return target;
		}
		float dt = Math.min((now - s.lastNanos) / 1_000_000_000f, 0.1f);
		s.lastNanos = now;
		s.value += (target - s.value) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - s.value) < 0.001f) s.value = target;
		return s.value;
	}

	/** Setzt einen Wert sofort (z. B. Einblenden beim Öffnen eines Menüs von 0 starten). */
	public static void set(String id, float value) {
		STATES.put(id, new State(value, System.nanoTime()));
	}

	/** Weiche Kurve für 0..1 */
	public static float easeOut(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}
}
