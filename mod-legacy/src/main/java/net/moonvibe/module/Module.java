package net.moonvibe.module;

import com.google.gson.JsonObject;
import net.moonvibe.Mv;
import net.moonvibe.module.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

/** Ein ein-/ausschaltbares Feature von moonvibe mit eigenen Einstellungen. */
public abstract class Module {
	public enum Category {
		HUD,
		MECHANIC,
		CLIENT
	}

	public final String id;
	public final Category category;
	private final ItemStack icon;
	private final boolean enabledByDefault;
	private boolean enabled;
	protected final List<Setting<?>> settings = new ArrayList<Setting<?>>();

	protected Module(String id, Category category, ItemStack icon, boolean enabledByDefault) {
		this.id = id;
		this.category = category;
		this.icon = icon;
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> settings() {
		return settings;
	}

	public String name() {
		return Mv.tr("moonvibe.module." + id);
	}

	public String description() {
		return Mv.tr("moonvibe.module." + id + ".desc");
	}

	public ItemStack icon() {
		return icon;
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {}

	protected void onDisable() {}

	public void resetSettings() {
		for (Setting<?> setting : settings) setting.reset();
	}

	public JsonObject write() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		JsonObject s = new JsonObject();
		for (Setting<?> setting : settings) s.add(setting.id, setting.write());
		json.add("settings", s);
		return json;
	}

	public void read(JsonObject json) {
		enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : enabledByDefault;
		if (json.has("settings") && json.get("settings").isJsonObject()) {
			JsonObject s = json.getAsJsonObject("settings");
			for (Setting<?> setting : settings) {
				if (s.has(setting.id)) setting.read(s.get(setting.id));
			}
		}
	}
}
