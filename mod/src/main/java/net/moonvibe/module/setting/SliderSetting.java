package net.moonvibe.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Function;

public class SliderSetting extends Setting<Float> {
	public final float min;
	public final float max;
	public final float step;
	private final Function<Float, String> format;

	public SliderSetting(String id, float defaultValue, float min, float max, float step, Function<Float, String> format) {
		super(id, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
	}

	public float value() {
		return value;
	}

	/** Setzt den Wert aus einer Position 0..1 auf dem Regler (gerundet auf "step"). */
	public void setFraction(double fraction) {
		float raw = (float) (min + Math.max(0, Math.min(1, fraction)) * (max - min));
		value = Math.max(min, Math.min(max, Math.round(raw / step) * step));
	}

	public double fraction() {
		return (value - min) / (max - min);
	}

	public String display() {
		return format.apply(value);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(value);
	}

	@Override
	public void read(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = Math.max(min, Math.min(max, json.getAsFloat()));
		}
	}
}
