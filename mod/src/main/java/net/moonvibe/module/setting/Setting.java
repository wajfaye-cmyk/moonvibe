package net.moonvibe.module.setting;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

/** Eine Einstellung eines Moduls (Schalter, Schieberegler, Farbe …). */
public abstract class Setting<T> {
	public final String id;
	private final T defaultValue;
	protected T value;

	protected Setting(String id, T defaultValue) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		this.value = defaultValue;
	}

	/** Übersetzter Name: moonvibe.setting.<id> */
	public Component label() {
		return Component.translatable("moonvibe.setting." + id);
	}

	public abstract JsonElement write();

	public abstract void read(JsonElement json);
}
