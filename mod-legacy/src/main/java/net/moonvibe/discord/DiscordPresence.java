package net.moonvibe.discord;

import com.google.gson.JsonObject;
import net.moonvibe.Mv;
import net.moonvibe.Moonvibe;
import net.moonvibe.module.Modules;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

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
		MinecraftClient mc = Mv.mc();
		if (mc.world == null) return Mv.tr("moonvibe.discord.menu");
		if (mc.isIntegratedServerRunning()) return Mv.tr("moonvibe.discord.singleplayer");
		ServerInfo server = mc.getCurrentServerEntry();
		if (server != null && Modules.DISCORD.showServer.on()) return Mv.tr("moonvibe.discord.server", server.address);
		return Mv.tr("moonvibe.discord.multiplayer");
	}

	private static JsonObject activity(String state) {
		JsonObject activity = new JsonObject();
		activity.addProperty("details", "Minecraft " + Mv.minecraftVersion());
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
