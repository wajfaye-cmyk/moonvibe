package net.moonvibe.feature;

import java.util.ArrayDeque;
import java.util.Deque;

/** Zählt Klicks pro Sekunde für linke und rechte Maustaste. */
public final class CpsTracker {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;

	private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
	private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();

	private CpsTracker() {}

	public static synchronized void click(int side) {
		(side == LEFT ? LEFT_CLICKS : RIGHT_CLICKS).addLast(System.currentTimeMillis());
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
		return clicks.size();
	}

	public static synchronized int left() {
		return count(LEFT_CLICKS);
	}

	public static synchronized int right() {
		return count(RIGHT_CLICKS);
	}
}
