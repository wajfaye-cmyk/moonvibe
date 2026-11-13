package net.moonvibe.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.Mv;
import net.moonvibe.Moonvibe;
import net.moonvibe.module.Modules;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * Lädt vom moonvibe-Server, wer welche Cosmetics trägt, dazu Katalog, 3D-Modelle, Texturen und
 * Vorschaubilder. Gleiches Verhalten wie in den neuen Versionen. Die Server-Adresse kommt vom Launcher (-Dmoonvibe.api=…).
 */
public final class CosmeticsManager {
	/** Alle Plätze; "cape" ist eine Textur, der Rest sind 3D-Modelle. */
	public static final List<String> SLOTS = Collections.unmodifiableList(Arrays.asList("cape", "hat", "face", "back", "wings", "shoes", "pet"));
	public static final List<String> MODEL_SLOTS = Collections.unmodifiableList(Arrays.asList("hat", "face", "back", "wings", "shoes", "pet"));
	private static final Map<String, String> FOLDERS = new HashMap<String, String>();

	static {
		FOLDERS.put("cape", "capes");
		FOLDERS.put("hat", "hats");
		FOLDERS.put("face", "face");
		FOLDERS.put("back", "back");
		FOLDERS.put("wings", "wings");
		FOLDERS.put("shoes", "shoes");
		FOLDERS.put("pet", "pets");
	}

	private static final String API = System.getProperty("moonvibe.api", "").replaceAll("/+$", "");
	/** Sitzung beim moonvibe-Server, vom Launcher (leer bei Offline-Accounts oder wenn der Server nicht erreichbar war) */
	private static final String SESSION = System.getProperty("moonvibe.session", "");
	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "moonvibe Cosmetics");
		t.setDaemon(true);
		return t;
	});

	/** Eintrag aus dem Katalog des Servers. */
	public static final class Cosmetic {
		public final String id;
		public final String type;
		public final String rarity;
		private final Map<String, String> names;
		public final String thumbnail;

		Cosmetic(String id, String type, String rarity, Map<String, String> names, String thumbnail) {
			this.id = id;
			this.type = type;
			this.rarity = rarity;
			this.names = names;
			this.thumbnail = thumbnail;
		}

		/** Selbst hochgeladenes Cape des Spielers? */
		public boolean custom() {
			return id.startsWith("custom_");
		}

		/** Name in der Spielsprache (Fallback Englisch). */
		public String name() {
			if (custom()) return Mv.tr("moonvibe.cosmetics.custom_cape");
			String lang = Mv.mc().options.language;
			String code = lang.length() >= 2 ? lang.substring(0, 2) : "en";
			String name = names.get(code);
			if (name == null) name = names.get("en");
			return name == null ? id : name;
		}
	}

	/** Fertig geladenes 3D-Cosmetic. */
	public static final class Loaded {
		public final CosmeticModel model;
		public final Identifier texture;

		Loaded(CosmeticModel model, Identifier texture) {
			this.model = model;
			this.texture = texture;
		}
	}

	private static final Map<UUID, Map<String, String>> PLAYERS = new ConcurrentHashMap<UUID, Map<String, String>>();
	private static final Set<UUID> PENDING = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
	private static final Map<String, Identifier> CAPES = new ConcurrentHashMap<String, Identifier>();
	private static final Map<String, Loaded> MODELS = new ConcurrentHashMap<String, Loaded>();
	private static final Map<String, Identifier> THUMBNAILS = new ConcurrentHashMap<String, Identifier>();
	private static final Set<String> LOADING = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private static final Set<String> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	/** Anprobieren im Cosmetics-Menü: überschreibt nur die eigene Anzeige (Platz → id oder "" für nichts). */
	private static final Map<String, String> PREVIEW = new ConcurrentHashMap<String, String>();

	private static volatile List<Cosmetic> catalog;

	private CosmeticsManager() {}

	public static boolean available() {
		return !API.isEmpty() && Modules.COSMETICS.enabled();
	}

	public static boolean serverConfigured() {
		return !API.isEmpty();
	}

	public static void start() {
		if (API.isEmpty()) return;
		SCHEDULER.scheduleWithFixedDelay(CosmeticsManager::fetchPending, 1, 1, TimeUnit.SECONDS);
		// regelmäßig neu laden, damit Änderungen aus dem Launcher ankommen
		SCHEDULER.scheduleWithFixedDelay(() -> PENDING.addAll(PLAYERS.keySet()), 60, 60, TimeUnit.SECONDS);
	}

	private static boolean isOwn(UUID uuid) {
		MinecraftClient mc = Mv.mc();
		return mc.player != null && mc.player.getUuid().equals(uuid);
	}

	/** Was trägt dieser Spieler in diesem Platz? (null = nichts) */
	public static String equipped(UUID uuid, String slot) {
		if (!available()) return null;
		boolean own = isOwn(uuid);
		if (!(own ? Modules.COSMETICS.showOwn.on() : Modules.COSMETICS.showOthers.on())) return null;
		if (own && PREVIEW.containsKey(slot)) {
			String preview = PREVIEW.get(slot);
			return preview.isEmpty() ? null : preview;
		}
		Map<String, String> e = PLAYERS.get(uuid);
		if (e == null) {
			PENDING.add(uuid);
			return null;
		}
		return e.get(slot);
	}

	/** Eigene, gespeicherte Auswahl (ohne Anprobieren). */
	public static String ownSaved(String slot) {
		MinecraftClient mc = Mv.mc();
		if (mc.player == null) return null;
		Map<String, String> e = PLAYERS.get(mc.player.getUuid());
		return e == null ? null : e.get(slot);
	}

	public static void preview(String slot, String id) {
		PREVIEW.put(slot, id == null ? "" : id);
	}

	public static void clearPreview() {
		PREVIEW.clear();
	}

	/** Cape-Textur eines Spielers (für den Cape-Mixin) – null = keins / lädt noch. */
	public static Identifier cape(UUID uuid) {
		String cape = equipped(uuid, "cape");
		return cape == null ? null : capeTexture(cape);
	}

	/** Cape-Textur nach ID (null, solange sie lädt). */
	public static Identifier capeTexture(String id) {
		Identifier texture = CAPES.get(id);
		if (texture == null && LOADING.add("cape:" + id)) SCHEDULER.execute(() -> loadCape(id));
		return texture;
	}

	/** 3D-Modell holen – wird beim ersten Mal im Hintergrund geladen. */
	public static Loaded model(String slot, String id) {
		Loaded loaded = MODELS.get(id);
		if (loaded == null && LOADING.add("model:" + id)) SCHEDULER.execute(() -> loadModel(slot, id));
		return loaded;
	}

	/** Vorschaubild für das Cosmetics-Menü (null, solange es lädt). */
	public static Identifier thumbnail(Cosmetic c) {
		if (c.thumbnail == null) return null;
		Identifier id = THUMBNAILS.get(c.id);
		if (id == null && LOADING.add("thumb:" + c.id)) SCHEDULER.execute(() -> loadThumbnail(c));
		return id;
	}

	/** Katalog (null, solange er lädt). */
	public static List<Cosmetic> catalog() {
		return catalog;
	}

	/** Eigenes hochgeladenes Cape als Katalog-Eintrag (null = keins). */
	public static Cosmetic ownCustomCape() {
		String id = ownSaved("customCape");
		return id == null || !validId(id) ? null : new Cosmetic(id, "cape", "custom", Collections.<String, String>emptyMap(), null);
	}

	public static CompletableFuture<List<Cosmetic>> loadCatalog() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				JsonArray list = new JsonParser().parse(new String(get("/api/cosmetics"), StandardCharsets.UTF_8)).getAsJsonArray();
				List<Cosmetic> result = new ArrayList<Cosmetic>();
				for (JsonElement el : list) {
					JsonObject o = el.getAsJsonObject();
					Map<String, String> names = new HashMap<String, String>();
					if (o.has("names")) {
						for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(e.getKey(), e.getValue().getAsString());
					}
					result.add(new Cosmetic(o.get("id").getAsString(), o.get("type").getAsString(),
						o.has("rarity") ? o.get("rarity").getAsString() : "common", names, str(o, "thumbnail")));
				}
				catalog = result;
				return result;
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}, SCHEDULER);
	}

	/** Cosmetic anziehen (oder ablegen mit id = null). */
	public static CompletableFuture<Void> equip(String slot, String id) {
		JsonObject body = new JsonObject();
		if (id == null) body.add(slot, JsonNull.INSTANCE);
		else body.addProperty(slot, id);
		byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
		return CompletableFuture.runAsync(() -> savedFrom(send("/api/me", "application/json", data)), SCHEDULER);
	}

	/** Bild (PNG/JPG …) in ein Cape umwandeln, hochladen und anziehen. */
	public static CompletableFuture<Void> uploadCape(File file) {
		return CompletableFuture.runAsync(() -> {
			try {
				BufferedImage source = ImageIO.read(file);
				if (source == null) throw new IOException("Kein Bild");
				ByteArrayOutputStream png = new ByteArrayOutputStream();
				ImageIO.write(CapeArt.build(source), "png", png);
				savedFrom(send("/api/me/cape", "image/png", png.toByteArray()));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}, SCHEDULER);
	}

	private static void savedFrom(String response) {
		JsonObject saved = new JsonParser().parse(response).getAsJsonObject();
		MinecraftClient mc = Mv.mc();
		if (mc.player != null) PLAYERS.put(mc.player.getUuid(), toMap(saved));
	}

	// ---------- Netzwerk ----------

	private static byte[] get(String path) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
		c.setConnectTimeout(5000);
		c.setReadTimeout(10000);
		if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " für " + path);
		try (InputStream in = c.getInputStream()) {
			return readAll(in);
		}
	}

	/**
	 * PUT mit Anmeldung: die moonvibe-Sitzung vom Launcher (der Minecraft-Token geht nur an Mojang,
	 * nie an den moonvibe-Server). Im Testmodus des Servers zählt auch die UUID (Offline-Accounts).
	 */
	private static String send(String path, String type, byte[] body) {
		MinecraftClient mc = Mv.mc();
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
			c.setRequestMethod("PUT");
			c.setDoOutput(true);
			c.setConnectTimeout(5000);
			c.setReadTimeout(20000);
			c.setRequestProperty("Content-Type", type);
			if (!SESSION.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + SESSION);
			c.setRequestProperty("X-Dev-Uuid", mc.getSession().getUuid().replace("-", ""));
			try (OutputStream out = c.getOutputStream()) {
				out.write(body);
			}
			int status = c.getResponseCode();
			if (status == 401) throw new IllegalStateException("login");
			if (status != 200) throw new IllegalStateException("HTTP " + status);
			try (InputStream in = c.getInputStream()) {
				return new String(readAll(in), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		return out.toByteArray();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static Map<String, String> toMap(JsonObject o) {
		Map<String, String> map = new HashMap<String, String>();
		for (String slot : SLOTS) {
			String id = str(o, slot);
			if (id != null) map.put(slot, id);
		}
		String custom = str(o, "customCape");
		if (custom != null) map.put("customCape", custom);
		return map;
	}

	private static void fetchPending() {
		if (PENDING.isEmpty()) return;
		List<UUID> batch = new ArrayList<UUID>();
		for (UUID uuid : PENDING) {
			batch.add(uuid);
			if (batch.size() >= 100) break;
		}
		PENDING.removeAll(batch);
		StringBuilder ids = new StringBuilder();
		for (UUID uuid : batch) {
			if (ids.length() > 0) ids.append(',');
			ids.append(uuid.toString().replace("-", ""));
		}
		try {
			JsonObject players = new JsonParser().parse(new String(get("/api/players?uuids=" + ids), StandardCharsets.UTF_8)).getAsJsonObject();
			for (UUID uuid : batch) {
				JsonElement entry = players.get(uuid.toString().replace("-", ""));
				PLAYERS.put(uuid, entry != null && entry.isJsonObject() ? toMap(entry.getAsJsonObject()) : Collections.<String, String>emptyMap());
			}
		} catch (Exception e) {
			for (UUID uuid : batch) PLAYERS.putIfAbsent(uuid, Collections.<String, String>emptyMap());
		}
	}

	private static boolean validId(String id) {
		return id.matches("[a-z0-9_]+");
	}

	private static void warnOnce(String key, Exception e) {
		if (WARNED.add(key)) Moonvibe.LOGGER.warn("Cosmetic {} konnte nicht geladen werden: {}", key, e.toString());
	}

	/** Bild als Textur anmelden (auf dem Spiel-Thread); smooth = weich skalieren (für Vorschaubilder). */
	private static Identifier register(String kind, String id, BufferedImage image, boolean smooth) {
		Identifier location = Mv.id("cosmetics/" + kind + "/" + id);
		NativeImageBackedTexture texture = Mv.texture(image);
		Mv.mc().getTextureManager().loadTexture(location, texture);
		if (smooth) {
			GlStateManager.bindTexture(texture.getGlId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		}
		return location;
	}

	private static BufferedImage image(byte[] data) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
		if (image == null) throw new IOException("kein Bild");
		return image;
	}

	private static void loadCape(String id) {
		if (!validId(id)) return;
		try {
			BufferedImage image = image(get("/textures/capes/" + id + ".png"));
			Mv.mc().submit(() -> CAPES.put(id, register("cape", id, image, false)));
		} catch (Exception e) {
			LOADING.remove("cape:" + id);
			warnOnce("cape:" + id, e);
		}
	}

	private static void loadModel(String slot, String id) {
		if (!validId(id) || !FOLDERS.containsKey(slot)) return;
		try {
			JsonObject json = new JsonParser().parse(new String(get("/models/" + id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			BufferedImage image = image(get("/textures/" + FOLDERS.get(slot) + "/" + id + ".png"));
			CosmeticModel model = CosmeticModel.fromJson(json);
			Mv.mc().submit(() -> MODELS.put(id, new Loaded(model, register(slot, id, image, false))));
		} catch (Exception e) {
			LOADING.remove("model:" + id);
			warnOnce("model:" + id, e);
		}
	}

	private static void loadThumbnail(Cosmetic c) {
		try {
			BufferedImage image = image(get(c.thumbnail));
			Mv.mc().submit(() -> THUMBNAILS.put(c.id, register("thumb", c.id, image, true)));
		} catch (Exception e) {
			LOADING.remove("thumb:" + c.id);
		}
	}
}
