package net.moonvibe.discord;

import com.google.gson.JsonObject;
import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.module.Modules;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;

/** Zeigt in Discord an, dass man moonvibe spielt (und wo). */
public final class DiscordPresence {
	/** Discord-App von moonvibe (aus dem alten Projekt übernommen). */
	private static final String CLIENT_ID = "1335654397614624888";
	private static final long STARTED = System.currentTimeMillis() / 1000;

	private static volatile boolean running;
	private static Thread thread;

	private DiscordPresence() {}

	public static synchronized void start() {
		if (running) return;
		running = true;
		thread = new Thread(DiscordPresence::loop, "moonvibe Discord");
		thread.setDaemon(true);
		thread.start();
	}

	public static synchronized void stop() {
		running = false;
		if (thread != null) thread.interrupt();
	}

	private static void loop() {
		DiscordIpc ipc = null;
		String lastState = null;
		while (running) {
			try {
				if (ipc == null) {
					ipc = DiscordIpc.connect(CLIENT_ID);
					lastState = null;
					Moonvibe.LOGGER.info("Mit Discord verbunden");
				}
				String state = currentState();
				if (!state.equals(lastState)) {
					ipc.setActivity(activity(state));
					lastState = state;
				}
			} catch (IOException e) {
				// Discord nicht offen oder Verbindung verloren – später nochmal probieren
				if (ipc != null) ipc.close();
				ipc = null;
			}
			try {
				Thread.sleep(ipc == null ? 30_000 : 5_000);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (ipc != null) ipc.close();
	}

	private static String currentState() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return I18n.get("moonvibe.discord.menu");
		if (mc.hasSingleplayerServer()) return I18n.get("moonvibe.discord.singleplayer");
		ServerData server = mc.getCurrentServer();
		if (server != null && Modules.DISCORD.showServer.on()) return I18n.get("moonvibe.discord.server", server.ip);
		return I18n.get("moonvibe.discord.multiplayer");
	}

	private static JsonObject activity(String state) {
		JsonObject activity = new JsonObject();
		activity.addProperty("details", "Minecraft " + Mc.versionName());
		activity.addProperty("state", state);

		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", STARTED);
		activity.add("timestamps", timestamps);

		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", "large");
		assets.addProperty("large_text", "Moonvibe " + Moonvibe.VERSION);
		activity.add("assets", assets);
		return activity;
	}
}
