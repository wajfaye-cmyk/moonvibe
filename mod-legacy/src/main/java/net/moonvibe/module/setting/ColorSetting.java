package net.moonvibe.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Farbe aus einer festen Palette (ARGB). */
public class ColorSetting extends Setting<Integer> {
	public static final int[] PALETTE = {
		0xFFFFFFFF, 0xFFB8C2D6, 0xFF2E9BFF, 0xFF4FD1FF, 0xFFFFB82E, 0xFFFF8A2E, 0xFFFF5470, 0xFF4ADE80, 0xFFB57CFF, 0xFF111111
	};

	public ColorSetting(String id, int defaultValue) {
		super(id, defaultValue);
	}

	public int color() {
		return value;
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(String.format("#%08X", value));
	}

	@Override
	public void read(JsonElement json) {
		try {
			value = (int) Long.parseLong(json.getAsString().replace("#", ""), 16);
		} catch (Exception ignored) {
			// ungültig → Standard behalten
		}
	}
}
