package net.moonvibe.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.moonvibe.Moonvibe;
import net.moonvibe.module.hud.ArmorModule;
import net.moonvibe.module.hud.CompassModule;
import net.moonvibe.module.hud.ItemCounterModule;
import net.moonvibe.module.hud.PvpModules;
import net.moonvibe.module.hud.HudModule;
import net.moonvibe.module.hud.KeystrokesModule;
import net.moonvibe.module.hud.PotionsModule;
import net.moonvibe.module.hud.TextModules;
import net.moonvibe.module.mechanic.CosmeticsModule;
import net.moonvibe.module.mechanic.CrosshairModule;
import net.moonvibe.module.mechanic.DiscordModule;
import net.moonvibe.module.mechanic.ZoomModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Alle Module von moonvibe + Speichern/Laden in config/moonvibe/modules.json */
public final class Modules {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("moonvibe").resolve("modules.json");

	public static final ZoomModule ZOOM = new ZoomModule();
	public static final DiscordModule DISCORD = new DiscordModule();
	public static final CosmeticsModule COSMETICS = new CosmeticsModule();
	public static final CrosshairModule CROSSHAIR = new CrosshairModule();
	public static final PvpModules.ToggleSprint TOGGLE_SPRINT = new PvpModules.ToggleSprint();

	public static final List<HudModule> HUD = List.of(
		new TextModules.Fps(),
		new TextModules.Cps(),
		new TextModules.Ping(),
		new TextModules.Coords(),
		new TextModules.Clock(),
		new TextModules.Server(),
		new TextModules.Memory(),
		new KeystrokesModule(),
		new ArmorModule(),
		new PotionsModule(),
		TOGGLE_SPRINT,
		new PvpModules.Combo(),
		new PvpModules.Reach(),
		new PvpModules.Speed(),
		new CompassModule(),
		new ItemCounterModule()
	);

	public static final List<Module> ALL;

	static {
		var all = new java.util.ArrayList<Module>(HUD);
		all.add(ZOOM);
		all.add(CROSSHAIR);
		all.add(DISCORD);
		all.add(COSMETICS);
		ALL = List.copyOf(all);
	}

	private Modules() {}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Module module : ALL) {
				if (root.has(module.id) && root.get(module.id).isJsonObject()) module.read(root.getAsJsonObject(module.id));
			}
		} catch (Exception e) {
			Moonvibe.LOGGER.warn("Konnte modules.json nicht lesen – nutze Standardwerte", e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Module module : ALL) root.add(module.id, module.write());
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Moonvibe.LOGGER.warn("Konnte modules.json nicht speichern", e);
		}
	}
}
