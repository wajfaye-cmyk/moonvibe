package net.moonvibe.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * 3D-Modell eines Cosmetics für Minecraft 1.8.9 – dasselbe JSON-Format wie in den neuen Versionen
 * (siehe dort: CosmeticModel). Die Würfel werden selbst gezeichnet, mit derselben Textur-Aufteilung
 * wie Minecraft (auch mit Kommazahlen als Größe, was 1.8.9 selbst nicht kann).
 */
public final class CosmeticModel {
	public enum Attach {
		HEAD,
		BODY,
		/** drawn once on each leg (shoes) */
		LEGS
	}

	/** Ein Pixel im Modell = 1/16 Block */
	private static final float PX = 0.0625f;
	private static final float DEG = (float) (180 / Math.PI);

	private static final class Cube {
		float[] from;
		float[] size;
		int u;
		int v;
		float inflate;
		boolean mirror;
	}

	private static final class Part {
		float[] pivot;
		float[] rotation; // Bogenmaß
		final List<Cube> cubes = new ArrayList<Cube>();
		final List<Part> children = new ArrayList<Part>();
		boolean emissive;
		JsonObject animation;
	}

	public final Attach attach;
	public final boolean hasEmissive;
	private final List<Part> parts = new ArrayList<Part>();
	private final int texW;
	private final int texH;

	private CosmeticModel(Attach attach, int texW, int texH, boolean hasEmissive) {
		this.attach = attach;
		this.texW = texW;
		this.texH = texH;
		this.hasEmissive = hasEmissive;
	}

	public static CosmeticModel fromJson(JsonObject json) {
		int texW = json.has("textureWidth") ? json.get("textureWidth").getAsInt() : 64;
		int texH = json.has("textureHeight") ? json.get("textureHeight").getAsInt() : 64;
		String attachName = json.has("attach") ? json.get("attach").getAsString() : "";
		Attach attach = "head".equals(attachName) ? Attach.HEAD : "legs".equals(attachName) ? Attach.LEGS : Attach.BODY;
		List<Part> parsed = new ArrayList<Part>();
		boolean[] emissive = {false};
		for (JsonElement part : json.getAsJsonArray("parts")) parsed.add(parse(part.getAsJsonObject(), emissive));
		CosmeticModel model = new CosmeticModel(attach, texW, texH, emissive[0]);
		model.parts.addAll(parsed);
		return model;
	}

	private static float[] vec(JsonObject o, String key, float def) {
		if (!o.has(key)) return new float[] {def, def, def};
		JsonArray a = o.getAsJsonArray(key);
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	private static Part parse(JsonObject json, boolean[] anyEmissive) {
		Part p = new Part();
		p.pivot = vec(json, "pivot", 0);
		float[] rot = vec(json, "rotation", 0);
		p.rotation = new float[] {rot[0] / DEG, rot[1] / DEG, rot[2] / DEG};
		p.emissive = json.has("emissive") && json.get("emissive").getAsBoolean();
		anyEmissive[0] |= p.emissive;
		p.animation = json.has("animation") ? json.getAsJsonObject("animation") : null;
		if (json.has("cubes")) {
			for (JsonElement e : json.getAsJsonArray("cubes")) {
				JsonObject c = e.getAsJsonObject();
				Cube cube = new Cube();
				cube.from = vec(c, "from", 0);
				cube.size = vec(c, "size", 1);
				JsonArray uv = c.getAsJsonArray("uv");
				cube.u = uv.get(0).getAsInt();
				cube.v = uv.get(1).getAsInt();
				cube.inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
				cube.mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
				p.cubes.add(cube);
			}
		}
		if (json.has("children")) {
			for (JsonElement child : json.getAsJsonArray("children")) p.children.add(parse(child.getAsJsonObject(), anyEmissive));
		}
		return p;
	}

	/**
	 * Zeichnet das Modell an der aktuellen Position (nach head/body.preRender). Die Textur muss schon gebunden sein.
	 * emissivePass = nur die leuchtenden Teile (mit voller Helligkeit) zeichnen.
	 */
	public void render(float seconds, float movement, boolean animate, boolean emissivePass) {
		if (emissivePass) {
			GlStateManager.disableLighting();
			GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, 240f, 240f);
		}
		for (Part part : parts) renderPart(part, seconds, movement, animate, emissivePass);
		if (emissivePass) GlStateManager.enableLighting();
	}

	private void renderPart(Part p, float seconds, float movement, boolean animate, boolean emissivePass) {
		float rx = p.rotation[0];
		float ry = p.rotation[1];
		float rz = p.rotation[2];
		float y = p.pivot[1];
		if (p.animation != null && animate) {
			JsonObject a = p.animation;
			String type = a.has("type") ? a.get("type").getAsString() : "";
			String axis = a.has("axis") ? a.get("axis").getAsString() : "y";
			float amplitude = a.has("amplitude") ? a.get("amplitude").getAsFloat() : 10;
			float speed = a.has("speed") ? a.get("speed").getAsFloat() : 1;
			float phase = a.has("phase") ? a.get("phase").getAsFloat() : 0;
			float t = seconds * speed * (float) (Math.PI * 2) + phase / DEG;
			float angle = 0;
			if (type.equals("flap")) {
				// beim Laufen schlagen Flügel stärker
				angle = (float) Math.sin(t * (1 + movement)) * amplitude * (1 + movement * 0.6f) / DEG;
			} else if (type.equals("spin")) {
				angle = seconds * speed * (float) (Math.PI * 2);
			} else if (type.equals("sway")) {
				angle = (float) Math.sin(t) * amplitude / DEG;
			} else if (type.equals("bob")) {
				y += (float) Math.sin(t) * amplitude;
			}
			if (axis.equals("x")) rx += angle;
			else if (axis.equals("z")) rz += angle;
			else ry += angle;
		}

		GlStateManager.pushMatrix();
		GlStateManager.translate(p.pivot[0] * PX, y * PX, p.pivot[2] * PX);
		if (rz != 0) GlStateManager.rotate(rz * DEG, 0, 0, 1);
		if (ry != 0) GlStateManager.rotate(ry * DEG, 0, 1, 0);
		if (rx != 0) GlStateManager.rotate(rx * DEG, 1, 0, 0);
		if (p.emissive == emissivePass && !p.cubes.isEmpty()) {
			BufferBuilder b = Tessellator.getInstance().getBuffer();
			b.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_NORMAL);
			for (Cube cube : p.cubes) cube(b, cube);
			Tessellator.getInstance().draw();
		}
		for (Part child : p.children) renderPart(child, seconds, movement, animate, emissivePass);
		GlStateManager.popMatrix();
	}

	/** Würfel mit Minecraft-Textur-Aufteilung ("Box-UV"), wie ModelPart der neuen Versionen. */
	private void cube(BufferBuilder b, Cube c) {
		float w = c.size[0];
		float h = c.size[1];
		float d = c.size[2];
		float g = c.inflate;
		float minX = c.from[0] - g;
		float maxX = c.from[0] + w + g;
		float minY = c.from[1] - g;
		float maxY = c.from[1] + h + g;
		float minZ = c.from[2] - g;
		float maxZ = c.from[2] + d + g;
		if (c.mirror) {
			float t = minX;
			minX = maxX;
			maxX = t;
		}
		float[] t0 = {minX, minY, minZ};
		float[] t1 = {maxX, minY, minZ};
		float[] t2 = {maxX, maxY, minZ};
		float[] t3 = {minX, maxY, minZ};
		float[] l0 = {minX, minY, maxZ};
		float[] l1 = {maxX, minY, maxZ};
		float[] l2 = {maxX, maxY, maxZ};
		float[] l3 = {minX, maxY, maxZ};

		float u = c.u;
		float v = c.v;
		float u0 = u;
		float u1 = u + d;
		float u2 = u + d + w;
		float u22 = u + d + w + w;
		float u3 = u + d + w + d;
		float u4 = u + d + w + d + w;
		float v0 = v;
		float v1 = v + d;
		float v2 = v + d + h;

		face(b, c.mirror, new float[][] {l1, l0, t0, t1}, u1, v0, u2, v1, 0, -1, 0);
		face(b, c.mirror, new float[][] {t2, t3, l3, l2}, u2, v1, u22, v0, 0, 1, 0);
		face(b, c.mirror, new float[][] {t0, l0, l3, t3}, u0, v1, u1, v2, -1, 0, 0);
		face(b, c.mirror, new float[][] {t1, t0, t3, t2}, u1, v1, u2, v2, 0, 0, -1);
		face(b, c.mirror, new float[][] {l1, t1, t2, l2}, u2, v1, u3, v2, 1, 0, 0);
		face(b, c.mirror, new float[][] {l0, l1, l2, l3}, u3, v1, u4, v2, 0, 0, 1);
	}

	/** Ecke0→(uB,vA) Ecke1→(uA,vA) Ecke2→(uA,vB) Ecke3→(uB,vB) – gespiegelt in umgekehrter Reihenfolge. */
	private void face(BufferBuilder b, boolean mirror, float[][] verts, float ua, float va, float ub, float vb, float nx, float ny, float nz) {
		float[][] uv = {{ub, va}, {ua, va}, {ua, vb}, {ub, vb}};
		for (int k = 0; k < 4; k++) {
			int i = mirror ? 3 - k : k;
			float[] p = verts[i];
			b.vertex(p[0] * PX, p[1] * PX, p[2] * PX).texture(uv[i][0] / texW, uv[i][1] / texH).normal(mirror ? -nx : nx, ny, nz).next();
		}
	}
}
