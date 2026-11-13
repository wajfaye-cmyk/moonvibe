package net.moonvibe.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
//#if MC >= 1.15
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#endif
//#if MC >= 1.17
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//#endif
//#if MC < 1.21.11 && MC >= 1.15
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.blaze3d.vertex.VertexConsumer;
//#endif

/**
 * 3D-Modell eines Cosmetics (Hut, Flügel …), gebaut aus einer JSON-Beschreibung vom moonvibe-Server.
 *
 * Format (Minecraft-Modellraum, Pixel, y nach unten):
 * { "attach": "head"|"body"|"legs", "textureWidth": 64, "textureHeight": 64,
 *   "parts": [ { "name", "pivot": [x,y,z], "rotation": [x,y,z] (Grad),
 *                "cubes": [ { "from": [x,y,z], "size": [w,h,d], "uv": [u,v], "inflate": 0, "mirror": false } ],
 *                "animation": { "type": "flap"|"spin"|"bob", "axis": "x"|"y"|"z", "amplitude", "speed", "phase" },
 *                "emissive": false, "children": [ … ] } ] }
 */
//#if MC >= 1.21.11
public final class CosmeticModel extends Model<CosmeticModel.State> {
//#else
//$$ public final class CosmeticModel extends Model {
//#endif
	private static final float DEG = (float) (Math.PI / 180);
	private static final float TWO_PI = (float) (Math.PI * 2);

	public enum Attach {
		HEAD,
		BODY,
		/** drawn once on each leg (shoes) */
		LEGS
	}

	/** Pro Spieler und Bild erzeugter Zustand – wird erst beim Zeichnen ausgewertet. */
	public record State(float seconds, float movement, boolean emissivePass, boolean animate) {}

	/** Ein Modellteil mit Ausgangslage {x, y, z, xRot, yRot, zRot}. */
	private record PartInfo(ModelPart part, float[] initial, boolean emissive, JsonObject animation) {
		PartInfo(ModelPart part, boolean emissive, JsonObject animation) {
			this(part, new float[] {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot}, emissive, animation);
		}

		void reset() {
			part.x = initial[0];
			part.y = initial[1];
			part.z = initial[2];
			part.xRot = initial[3];
			part.yRot = initial[4];
			part.zRot = initial[5];
		}
	}

	private record PendingPart(String path, boolean emissive, JsonObject animation) {}

	public final Attach attach;
	public final boolean hasEmissive;
	private final List<PartInfo> parts;
	//#if MC < 1.21.11
	//$$ private final ModelPart root;
	//#endif
	//#if MC < 1.19
	//$$ // Einzelne Teile ausblenden geht erst ab 1.19 – davor stecken die leuchtenden Teile in einem zweiten Modell
	//$$ private ModelPart glowRoot;
	//$$ private boolean glowPass;
	//#endif

	private CosmeticModel(ModelPart root, Attach attach, List<PartInfo> parts) {
		//#if MC >= 1.21.11
		super(root, RenderTypes::entityCutout);
		//#elseif MC >= 1.15
		//$$ super(RenderTypes::entityCutout);
		//$$ this.root = root;
		//#else
		//$$ this.root = root;
		//#endif
		this.attach = attach;
		this.parts = parts;
		this.hasEmissive = parts.stream().anyMatch(PartInfo::emissive);
	}

	//#if MC < 1.21.11 && MC >= 1.15
	//$$ @Override
	//$$ public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
	//#if MC >= 1.19
	//$$ 	root.render(pose, consumer, light, overlay, r, g, b, a);
	//#else
	//$$ 	(glowPass ? glowRoot : root).render(pose, consumer, light, overlay, r, g, b, a);
	//#endif
	//$$ }
	//#elseif MC < 1.15
	//$$ /** 1.14 zeichnet direkt mit OpenGL (scale = Größe eines Modell-Pixels) */
	//$$ public void render(float scale) {
	//$$ 	(glowPass ? glowRoot : root).render(scale);
	//$$ }
	//#endif

	public static CosmeticModel fromJson(JsonObject json) {
		String attachName = json.has("attach") ? json.get("attach").getAsString() : "";
		Attach attach = "head".equals(attachName) ? Attach.HEAD : "legs".equals(attachName) ? Attach.LEGS : Attach.BODY;
		//#if MC >= 1.19
		List<PendingPart> pending = new ArrayList<>();
		ModelPart root = bake(json, null, pending);
		return new CosmeticModel(root, attach, infos(root, pending));
		//#elseif MC >= 1.17
		//$$ List<PendingPart> pending = new ArrayList<>();
		//$$ ModelPart root = bake(json, false, pending);
		//$$ ModelPart glow = bake(json, true, new ArrayList<>());
		//$$ List<PartInfo> infos = infos(root, pending);
		//$$ infos.addAll(infos(glow, pending));
		//$$ CosmeticModel model = new CosmeticModel(root, attach, infos);
		//$$ model.glowRoot = glow;
		//$$ return model;
		//#else
		//$$ List<PartInfo> infos = new ArrayList<>();
		//$$ ModelPart root = legacyTree(json, false, infos);
		//$$ ModelPart glow = legacyTree(json, true, infos);
		//$$ CosmeticModel model = new CosmeticModel(root, attach, infos);
		//$$ model.glowRoot = glow;
		//$$ return model;
		//#endif
	}

	private static int texWidth(JsonObject json) {
		return json.has("textureWidth") ? json.get("textureWidth").getAsInt() : 64;
	}

	private static int texHeight(JsonObject json) {
		return json.has("textureHeight") ? json.get("textureHeight").getAsInt() : 64;
	}

	private static boolean emissive(JsonObject part) {
		return part.has("emissive") && part.get("emissive").getAsBoolean();
	}

	private static JsonObject animation(JsonObject part) {
		return part.has("animation") ? part.getAsJsonObject("animation") : null;
	}

	/** Würfel dieses Teils aufnehmen? Nur leuchtende (true), nur normale (false) oder alle (null). */
	private static boolean takeCubes(JsonObject part, Boolean emissiveCubes) {
		return part.has("cubes") && (emissiveCubes == null || emissiveCubes == emissive(part));
	}

	private static float[] vec(JsonObject o, String key, float def) {
		if (!o.has(key)) return new float[] {def, def, def};
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	//#if MC >= 1.17
	/** Modell mit Minecrafts Modell-Baukasten bauen (ab 1.17). */
	private static ModelPart bake(JsonObject json, Boolean emissiveCubes, List<PendingPart> pending) {
		MeshDefinition mesh = new MeshDefinition();
		int counter = 0;
		for (JsonElement part : json.getAsJsonArray("parts")) {
			counter = addPart(mesh.getRoot(), part.getAsJsonObject(), "", emissiveCubes, pending, counter);
		}
		return LayerDefinition.create(mesh, texWidth(json), texHeight(json)).bakeRoot();
	}

	private static List<PartInfo> infos(ModelPart root, List<PendingPart> pending) {
		List<PartInfo> infos = new ArrayList<>();
		for (PendingPart p : pending) {
			ModelPart part = root;
			for (String name : p.path().split("/")) part = part.getChild(name);
			infos.add(new PartInfo(part, p.emissive(), p.animation()));
		}
		return infos;
	}

	private static int addPart(PartDefinition parent, JsonObject json, String path, Boolean emissiveCubes, List<PendingPart> pending, int counter) {
		String name = "p" + counter++;
		CubeListBuilder cubes = CubeListBuilder.create();
		if (takeCubes(json, emissiveCubes)) {
			for (JsonElement c : json.getAsJsonArray("cubes")) {
				JsonObject cube = c.getAsJsonObject();
				float[] from = vec(cube, "from", 0);
				float[] size = vec(cube, "size", 1);
				JsonArray uv = cube.getAsJsonArray("uv");
				float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
				boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
				cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
					.mirror(mirror)
					.addBox(from[0], from[1], from[2], size[0], size[1], size[2], new CubeDeformation(inflate));
			}
		}
		float[] pivot = vec(json, "pivot", 0);
		float[] rot = vec(json, "rotation", 0);
		PartDefinition def = parent.addOrReplaceChild(
			name,
			cubes,
			PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0] * DEG, rot[1] * DEG, rot[2] * DEG)
		);
		String fullPath = path.isEmpty() ? name : path + "/" + name;
		pending.add(new PendingPart(fullPath, emissive(json), animation(json)));
		if (json.has("children")) {
			for (JsonElement child : json.getAsJsonArray("children")) {
				counter = addPart(def, child.getAsJsonObject(), fullPath, emissiveCubes, pending, counter);
			}
		}
		return counter;
	}
	//#else
	//$$ /** Vor 1.17 gibt es den Modell-Baukasten noch nicht – Teile direkt zusammensetzen. */
	//$$ private static ModelPart legacyTree(JsonObject json, boolean emissiveCubes, List<PartInfo> infos) {
	//$$ 	int texW = texWidth(json);
	//$$ 	int texH = texHeight(json);
	//$$ 	ModelPart root = newPart(texW, texH);
	//$$ 	for (JsonElement part : json.getAsJsonArray("parts")) {
	//$$ 		root.addChild(legacyPart(part.getAsJsonObject(), texW, texH, emissiveCubes, infos));
	//$$ 	}
	//$$ 	return root;
	//$$ }
	//$$
	//$$ private static ModelPart legacyPart(JsonObject json, int texW, int texH, boolean emissiveCubes, List<PartInfo> infos) {
	//$$ 	ModelPart part = newPart(texW, texH);
	//$$ 	if (takeCubes(json, emissiveCubes)) {
	//$$ 		for (JsonElement c : json.getAsJsonArray("cubes")) {
	//$$ 			JsonObject cube = c.getAsJsonObject();
	//$$ 			float[] from = vec(cube, "from", 0);
	//$$ 			float[] size = vec(cube, "size", 1);
	//$$ 			JsonArray uv = cube.getAsJsonArray("uv");
	//$$ 			float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
	//$$ 			part.mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
	//$$ 			part.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt());
	//#if MC >= 1.15
	//$$ 			part.addBox(from[0], from[1], from[2], size[0], size[1], size[2], inflate);
	//#else
	//$$ 			part.addBox(from[0], from[1], from[2], Math.round(size[0]), Math.round(size[1]), Math.round(size[2]), inflate);
	//#endif
	//$$ 		}
	//$$ 	}
	//$$ 	float[] pivot = vec(json, "pivot", 0);
	//$$ 	float[] rot = vec(json, "rotation", 0);
	//$$ 	part.setPos(pivot[0], pivot[1], pivot[2]);
	//$$ 	part.xRot = rot[0] * DEG;
	//$$ 	part.yRot = rot[1] * DEG;
	//$$ 	part.zRot = rot[2] * DEG;
	//$$ 	infos.add(new PartInfo(part, emissive(json), animation(json)));
	//$$ 	if (json.has("children")) {
	//$$ 		for (JsonElement child : json.getAsJsonArray("children")) {
	//$$ 			part.addChild(legacyPart(child.getAsJsonObject(), texW, texH, emissiveCubes, infos));
	//$$ 		}
	//$$ 	}
	//$$ 	return part;
	//$$ }
	//$$
	//$$ private static ModelPart newPart(int texW, int texH) {
	//#if MC >= 1.15
	//$$ 	return new ModelPart(texW, texH, 0, 0);
	//#else
	//$$ 	// 1.14: Modellteile brauchen ein Modell, das die Texturgröße vorgibt
	//$$ 	Model owner = new Model();
	//$$ 	owner.texWidth = texW;
	//$$ 	owner.texHeight = texH;
	//$$ 	return new ModelPart(owner);
	//#endif
	//$$ }
	//#endif

	//#if MC >= 1.21.11
	@Override
	//#endif
	public void setupAnim(State state) {
		//#if MC < 1.19
		//$$ glowPass = state.emissivePass();
		//#endif
		for (PartInfo info : parts) info.reset();
		for (PartInfo info : parts) {
			// Leuchtende Teile werden in einem eigenen Durchgang mit voller Helligkeit gezeichnet
			//#if MC >= 1.19
			info.part.skipDraw = info.emissive != state.emissivePass();
			//#endif
			if (info.animation == null || !state.animate()) continue;
			JsonObject a = info.animation;
			String type = a.has("type") ? a.get("type").getAsString() : "";
			String axis = a.has("axis") ? a.get("axis").getAsString() : "y";
			float amplitude = a.has("amplitude") ? a.get("amplitude").getAsFloat() : 10;
			float speed = a.has("speed") ? a.get("speed").getAsFloat() : 1;
			float phase = a.has("phase") ? a.get("phase").getAsFloat() : 0;
			float t = state.seconds() * speed * TWO_PI + phase * DEG;
			switch (type) {
				case "flap" -> {
					// beim Laufen schlagen Flügel stärker
					float angle = Mth.sin(t * (1 + state.movement())) * amplitude * (1 + state.movement() * 0.6f) * DEG;
					rotate(info.part, axis, angle);
				}
				case "spin" -> rotate(info.part, axis, state.seconds() * speed * TWO_PI);
				case "bob" -> info.part.y += Mth.sin(t) * amplitude;
				case "sway" -> rotate(info.part, axis, Mth.sin(t) * amplitude * DEG);
				default -> {}
			}
		}
	}

	private static void rotate(ModelPart part, String axis, float angle) {
		switch (axis) {
			case "x" -> part.xRot += angle;
			case "z" -> part.zRot += angle;
			default -> part.yRot += angle;
		}
	}
}
