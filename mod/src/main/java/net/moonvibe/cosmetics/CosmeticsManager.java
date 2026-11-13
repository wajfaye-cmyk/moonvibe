package net.moonvibe.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.module.Modules;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
//#if MC >= 1.20.2
import net.minecraft.world.entity.player.PlayerSkin;
//#endif
//#if MC >= 1.21.11
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.core.ClientAsset;
import net.minecraft.server.packs.resources.ResourceManager;
//#endif
import org.jspecify.annotations.Nullable;

/**
 * Lädt vom moonvibe-Server, wer welche Cosmetics trägt, dazu Katalog, 3D-Modelle, Texturen und
 * Vorschaubilder. Im Cosmetics-Menü kann man direkt aus dem Spiel heraus etwas anziehen.
 * Die Server-Adresse kommt vom Launcher (-Dmoonvibe.api=…).
 */
public final class CosmeticsManager {
	/** Alle Plätze; "cape" ist eine Textur am Skin, der Rest sind 3D-Modelle. */
	public static final List<String> SLOTS = List.of("cape", "hat", "face", "back", "wings", "shoes", "pet");
	public static final List<String> MODEL_SLOTS = List.of("hat", "face", "back", "wings", "shoes", "pet");
	private static final Map<String, String> FOLDERS = Map.of(
		"cape", "capes", "hat", "hats", "face", "face", "back", "back", "wings", "wings", "shoes", "shoes", "pet", "pets");

	private static final String API = System.getProperty("moonvibe.api", "").replaceAll("/+$", "");
	/** Sitzung beim moonvibe-Server, vom Launcher (leer bei Offline-Accounts oder wenn der Server nicht erreichbar war) */
	private static final String SESSION = System.getProperty("moonvibe.session", "");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "moonvibe Cosmetics");
		t.setDaemon(true);
		return t;
	});

	/** Eintrag aus dem Katalog des Servers. */
	public record Cosmetic(String id, String type, String rarity, Map<String, String> names, @Nullable String thumbnail) {
		/** Selbst hochgeladenes Cape des Spielers? */
		public boolean custom() {
			return id.startsWith("custom_");
		}

		/** Name in der Spielsprache (Fallback Englisch). */
		public String name() {
			if (custom()) return I18n.get("moonvibe.cosmetics.custom_cape");
			String lang = Minecraft.getInstance().options.languageCode;
			String code = lang.length() >= 2 ? lang.substring(0, 2) : "en";
			return names.getOrDefault(code, names.getOrDefault("en", id));
		}
	}

	/** Fertig geladenes 3D-Cosmetic. */
	public record Loaded(CosmeticModel model, Identifier texture) {}

	private static final Map<UUID, Map<String, String>> PLAYERS = new ConcurrentHashMap<>();
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
	private static final Map<String, Identifier> CAPES = new ConcurrentHashMap<>();
	private static final Map<String, Loaded> MODELS = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> THUMBNAILS = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
	/** Anprobieren im Cosmetics-Menü: überschreibt nur die eigene Anzeige (Platz → id oder "" für nichts). */
	private static final Map<String, String> PREVIEW = new ConcurrentHashMap<>();

	private static volatile @Nullable List<Cosmetic> catalog;

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
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getUUID().equals(uuid);
	}

	/** Was trägt dieser Spieler in diesem Platz? (null = nichts) */
	public static @Nullable String equipped(UUID uuid, String slot) {
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
	public static @Nullable String ownSaved(String slot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		Map<String, String> e = PLAYERS.get(mc.player.getUUID());
		return e == null ? null : e.get(slot);
	}

	public static void preview(String slot, @Nullable String id) {
		PREVIEW.put(slot, id == null ? "" : id);
	}

	public static void clearPreview() {
		PREVIEW.clear();
	}

	/** Cape-Textur eines Spielers (null = keins oder noch nicht geladen). */
	public static @Nullable Identifier cape(UUID uuid) {
		String cape = equipped(uuid, "cape");
		return cape == null ? null : capeTexture(cape);
	}

	//#if MC >= 1.20.2
	/** Cape am Skin anbringen (aus dem Skin-Mixin, jeden Frame). */
	public static PlayerSkin applyCape(UUID uuid, PlayerSkin skin) {
		Identifier texture = cape(uuid);
		if (texture == null) return skin;
		//#if MC >= 1.21.11
		return new PlayerSkin(skin.body(), new ClientAsset.ResourceTexture(texture, texture), skin.elytra(), skin.model(), skin.secure());
		//#else
		//$$ return new PlayerSkin(skin.texture(), skin.textureUrl(), texture, skin.elytraTexture(), skin.model(), skin.secure());
		//#endif
	}
	//#endif

	/** 3D-Modell holen – wird beim ersten Mal im Hintergrund geladen. */
	public static @Nullable Loaded model(String slot, String id) {
		Loaded loaded = MODELS.get(id);
		if (loaded == null && LOADING.add("model:" + id)) SCHEDULER.execute(() -> loadModel(slot, id));
		return loaded;
	}

	/** Vorschaubild für das Cosmetics-Menü (null, solange es lädt). */
	public static @Nullable Identifier thumbnail(Cosmetic c) {
		if (c.thumbnail() == null) return null;
		Identifier id = THUMBNAILS.get(c.id());
		if (id == null && LOADING.add("thumb:" + c.id())) SCHEDULER.execute(() -> loadThumbnail(c));
		return id;
	}

	/** Eigenes hochgeladenes Cape als Katalog-Eintrag (null = keins). */
	public static @Nullable Cosmetic ownCustomCape() {
		String id = ownSaved("customCape");
		return id == null || !validId(id) ? null : new Cosmetic(id, "cape", "custom", Map.of(), null);
	}

	/** Cape-Textur für die Vorschau im Menü (null, solange sie lädt). */
	public static @Nullable Identifier capeTexture(String id) {
		Identifier texture = CAPES.get(id);
		if (texture == null && LOADING.add("cape:" + id)) SCHEDULER.execute(() -> loadCape(id));
		return texture;
	}

	/**
	 * Bild (PNG/JPG) in ein Cape umwandeln, hochladen und anziehen. Andere moonvibe-Spieler sehen es
	 * danach wie jedes andere Cape.
	 */
	public static CompletableFuture<Void> uploadCape(Path file) {
		Minecraft mc = Minecraft.getInstance();
		String uuid = Mc.userId().toString().replace("-", "");
		return CompletableFuture.runAsync(() -> {
			try {
				byte[] png;
				Path temp = Files.createTempFile("moonvibe-cape", ".png");
				try (NativeImage source = CapeArt.load(file); NativeImage cape = CapeArt.build(source)) {
					cape.writeToFile(temp);
					png = Files.readAllBytes(temp);
				} finally {
					Files.deleteIfExists(temp);
				}
				HttpRequest request = signedIn("/api/me/cape", uuid)
					.timeout(Duration.ofSeconds(20))
					.header("Content-Type", "image/png")
					.PUT(HttpRequest.BodyPublishers.ofByteArray(png))
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 401) throw new IllegalStateException("login");
				if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
				JsonObject saved = JsonParser.parseString(response.body()).getAsJsonObject();
				if (mc.player != null) PLAYERS.put(mc.player.getUUID(), toMap(saved));
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}, SCHEDULER);
	}

	/** Katalog (null, solange er lädt). */
	public static @Nullable List<Cosmetic> catalog() {
		return catalog;
	}

	public static CompletableFuture<List<Cosmetic>> loadCatalog() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				JsonArray list = JsonParser.parseString(new String(get("/api/cosmetics"), StandardCharsets.UTF_8)).getAsJsonArray();
				List<Cosmetic> result = new ArrayList<>();
				for (JsonElement el : list) {
					JsonObject o = el.getAsJsonObject();
					Map<String, String> names = new HashMap<>();
					if (o.has("names")) o.getAsJsonObject("names").entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
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

	/**
	 * Cosmetic anziehen (oder ablegen mit id = null). Angemeldet wird mit der Sitzung vom Launcher;
	 * Offline-Testaccounts funktionieren nur, wenn der Server im Testmodus läuft.
	 */
	public static CompletableFuture<Void> equip(String slot, @Nullable String id) {
		Minecraft mc = Minecraft.getInstance();
		String uuid = Mc.userId().toString().replace("-", "");
		JsonObject body = new JsonObject();
		if (id == null) body.add(slot, com.google.gson.JsonNull.INSTANCE);
		else body.addProperty(slot, id);
		return CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = signedIn("/api/me", uuid)
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 401) throw new IllegalStateException("login");
				if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
				// sofort lokal übernehmen, damit es im Spiel direkt sichtbar ist
				JsonObject saved = JsonParser.parseString(response.body()).getAsJsonObject();
				if (mc.player != null) PLAYERS.put(mc.player.getUUID(), toMap(saved));
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}, SCHEDULER);
	}

	// ---------- Netzwerk ----------

	/**
	 * Anfrage mit Anmeldung: die moonvibe-Sitzung vom Launcher (der Minecraft-Token geht nur an Mojang,
	 * nie an den moonvibe-Server). Im Testmodus des Servers zählt auch die UUID (Offline-Accounts).
	 */
	private static HttpRequest.Builder signedIn(String path, String uuid) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path)).header("X-Dev-Uuid", uuid);
		return SESSION.isEmpty() ? request : request.header("Authorization", "Bearer " + SESSION);
	}

	private static byte[] get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " für " + path);
		return response.body();
	}

	private static @Nullable String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static Map<String, String> toMap(JsonObject o) {
		Map<String, String> map = new HashMap<>();
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
		List<UUID> batch = new ArrayList<>();
		for (UUID uuid : PENDING) {
			batch.add(uuid);
			if (batch.size() >= 100) break;
		}
		batch.forEach(PENDING::remove);
		StringBuilder ids = new StringBuilder();
		for (UUID uuid : batch) {
			if (!ids.isEmpty()) ids.append(',');
			ids.append(uuid.toString().replace("-", ""));
		}
		try {
			JsonObject players = JsonParser.parseString(new String(get("/api/players?uuids=" + ids), StandardCharsets.UTF_8)).getAsJsonObject();
			for (UUID uuid : batch) {
				JsonElement entry = players.get(uuid.toString().replace("-", ""));
				PLAYERS.put(uuid, entry != null && entry.isJsonObject() ? toMap(entry.getAsJsonObject()) : Map.of());
			}
		} catch (Exception e) {
			for (UUID uuid : batch) PLAYERS.putIfAbsent(uuid, Map.of());
			Moonvibe.LOGGER.debug("Cosmetics-Server nicht erreichbar: {}", e.getMessage());
		}
	}

	private static boolean validId(String id) {
		return id.matches("[a-z0-9_]+");
	}

	private static Identifier registerTexture(String kind, String id, byte[] png) throws Exception {
		Identifier location = Moonvibe.id("cosmetics/" + kind + "/" + id);
		NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
		//#if MC >= 1.21.11
		Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> "Moonvibe " + kind + " " + id, image));
		//#else
		//$$ Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
		//#endif
		return location;
	}

	private static void loadCape(String id) {
		if (!validId(id)) return;
		try {
			byte[] png = get("/textures/capes/" + id + ".png");
			Minecraft.getInstance().execute(() -> {
				try {
					Identifier location = registerTexture("cape", id, png);
					CAPES.put(id, location);
				} catch (Exception e) {
					Moonvibe.LOGGER.warn("Cape {} ist kaputt", id, e);
				}
			});
		} catch (Exception e) {
			LOADING.remove("cape:" + id);
			warnOnce("cape:" + id, e);
		}
	}

	private static void loadModel(String slot, String id) {
		if (!validId(id) || !FOLDERS.containsKey(slot)) return;
		try {
			JsonObject json = JsonParser.parseString(new String(get("/models/" + id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			byte[] png = get("/textures/" + FOLDERS.get(slot) + "/" + id + ".png");
			Minecraft.getInstance().execute(() -> {
				try {
					MODELS.put(id, new Loaded(CosmeticModel.fromJson(json), registerTexture(slot, id, png)));
				} catch (Exception e) {
					Moonvibe.LOGGER.warn("Cosmetic {} ist kaputt", id, e);
				}
			});
		} catch (Exception e) {
			LOADING.remove("model:" + id);
			warnOnce("model:" + id, e);
		}
	}

	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** Ladefehler nur einmal melden (es wird jedes Bild neu versucht). */
	private static void warnOnce(String key, Exception e) {
		if (WARNED.add(key)) Moonvibe.LOGGER.warn("Cosmetic {} konnte nicht geladen werden: {}", key, e.toString());
	}

	/** Vorschaubilder werden weich skaliert (nicht pixelig), daher als Textur mit "blur". */
	private static void loadThumbnail(Cosmetic c) {
		try {
			byte[] png = get(c.thumbnail());
			Identifier location = Moonvibe.id("cosmetics/thumb/" + c.id());
			Minecraft.getInstance().execute(() -> {
				//#if MC >= 1.21.11
				Minecraft.getInstance().getTextureManager().registerAndLoad(location, new ReloadableTexture(location) {
					@Override
					public TextureContents loadContents(ResourceManager resourceManager) throws java.io.IOException {
						return new TextureContents(NativeImage.read(new ByteArrayInputStream(png)), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F));
					}
				});
				//#else
				//$$ try {
				//$$ 	DynamicTexture texture = new DynamicTexture(NativeImage.read(new ByteArrayInputStream(png)));
				//$$ 	texture.setFilter(true, false);
				//$$ 	Minecraft.getInstance().getTextureManager().register(location, texture);
				//$$ } catch (java.io.IOException e) {
				//$$ 	return;
				//$$ }
				//#endif
				THUMBNAILS.put(c.id(), location);
			});
		} catch (Exception e) {
			LOADING.remove("thumb:" + c.id());
		}
	}
}
