package net.moonvibe.ui;

import net.moonvibe.Moonvibe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
//#endif
//#if MC < 1.21.11
//$$ import net.moonvibe.compat.LegacyEntity;
//#endif
//#if MC < 1.20.5
//$$ import net.moonvibe.compat.LegacyBlur;
//#endif
//#if MC >= 1.20 && MC < 1.21.11
//$$ import org.joml.Vector3f;
//#endif
//#if MC >= 26.2
import net.minecraft.client.gui.Hud;
//#elseif MC >= 1.21.11
//$$ import net.minecraft.client.gui.Gui;
//#endif
//#if MC >= 1.21.11
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.FontDescription;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//#endif

/**
 * Zeichen-Werkzeuge für die moonvibe-Oberfläche – und die einzige Stelle, die Minecrafts Zeichenbefehle direkt nutzt.
 * Menüs und HUD-Module zeichnen nur über diese Klasse; was sich zwischen Minecraft-Versionen unterscheidet,
 * steht deshalb nur hier (in //#if-Blöcken).
 *
 * Abgerundete Ecken werden in echten Bildschirm-Pixeln (nicht GUI-Pixeln) und mit Kantenglättung
 * gezeichnet – dadurch sehen sie auch bei großer GUI-Skalierung glatt aus.
 */
public final class Draw {
	//#if MC >= 1.21.11
	public static final FontDescription UI_FONT = new FontDescription.Resource(Moonvibe.id("ui"));
	public static final FontDescription UI_BOLD = new FontDescription.Resource(Moonvibe.id("ui_bold"));
	//#else
	//$$ public static final Identifier UI_FONT = Moonvibe.id("ui");
	//$$ public static final Identifier UI_BOLD = Moonvibe.id("ui_bold");
	//#endif

	private Draw() {}

	// ---------- Farben ----------

	public static int alpha(int color, float factor) {
		int a = Math.round((color >>> 24) * Math.max(0, Math.min(1, factor)));
		return (color & 0xFFFFFF) | (a << 24);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
		int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24
			| Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8
			| Math.round(ab + (bb - ab) * t);
	}

	// ---------- Minecraft-Grundbefehle (je Version anders) ----------

	public static int guiWidth(GuiGraphicsExtractor g) {
		return g.guiWidth();
	}

	public static int guiHeight(GuiGraphicsExtractor g) {
		return g.guiHeight();
	}

	// neuere Versionen haben eine zweidimensionale GUI-Matrix, ältere einen PoseStack
	public static void push(GuiGraphicsExtractor g) {
		//#if MC >= 1.21.11
		g.pose().pushMatrix();
		//#else
		//$$ g.pose().pushPose();
		//#endif
	}

	public static void pop(GuiGraphicsExtractor g) {
		//#if MC >= 1.21.11
		g.pose().popMatrix();
		//#else
		//$$ g.pose().popPose();
		//#endif
	}

	public static void translate(GuiGraphicsExtractor g, float x, float y) {
		//#if MC >= 1.21.11
		g.pose().translate(x, y);
		//#else
		//$$ g.pose().translate(x, y, 0);
		//#endif
	}

	public static void scale(GuiGraphicsExtractor g, float s) {
		//#if MC >= 1.21.11
		g.pose().scale(s, s);
		//#else
		//$$ g.pose().scale(s, s, 1);
		//#endif
	}

	/** Aktuelle Vergrößerung (für die Umrechnung in Bildschirm-Pixel). */
	private static float poseScale(GuiGraphicsExtractor g) {
		//#if MC >= 1.21.11
		return g.pose().m00();
		//#elseif MC >= 1.20
		//$$ return g.pose().last().pose().m00();
		//#else
		//$$ return g.scale();
		//#endif
	}

	/** Rechteck von (x0, y0) bis (x1, y1) in GUI-Pixeln */
	public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y1, color);
	}

	public static void fillGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom) {
		g.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	/** Welt hinter einem Menü weichzeichnen */
	public static void blur(GuiGraphicsExtractor g) {
		//#if MC >= 1.21.11
		g.blurBeforeThisStratum();
		//#elseif MC >= 1.20.5
		//$$ Minecraft mc = Minecraft.getInstance();
		//$$ g.flush();
		//$$ mc.gameRenderer.processBlurEffect(mc.getFrameTime());
		//$$ mc.getMainRenderTarget().bindWrite(false);
		//#else
		//$$ g.flush();
		//$$ LegacyBlur.apply();
		//#endif
	}

	/** Zeichenbereich begrenzen (in GUI-Pixeln, Verschiebung/Skalierung zählen mit). */
	public static void scissor(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		//#if MC >= 1.20 && MC < 1.21.11
		//$$ // 1.20.x rechnet die Verschiebung nicht selbst um
		//$$ Vector3f a = g.pose().last().pose().transformPosition(x0, y0, 0, new Vector3f());
		//$$ Vector3f b = g.pose().last().pose().transformPosition(x1, y1, 0, new Vector3f());
		//$$ g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
		//#else
		g.enableScissor(x0, y0, x1, y1);
		//#endif
	}

	public static void endScissor(GuiGraphicsExtractor g) {
		g.disableScissor();
	}

	/** Gegenstand wie in der Hotbar (16×16) */
	public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		g.item(stack, x, y);
	}

	/** Text in der Minecraft-Schrift (HUD-Anzeigen) */
	public static void mcText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean shadow) {
		g.text(Minecraft.getInstance().font, text, x, y, color, shadow);
	}

	public static void mcText(GuiGraphicsExtractor g, Component text, int x, int y, int color, boolean shadow) {
		g.text(Minecraft.getInstance().font, text, x, y, color, shadow);
	}

	public static int mcWidth(String text) {
		return Minecraft.getInstance().font.width(text);
	}

	/** Symbol eines Trank-Effekts */
	//#if MC >= 1.20.5
	public static void effectIcon(GuiGraphicsExtractor g, Holder<MobEffect> effect, int x, int y, int size) {
	//#else
	//$$ public static void effectIcon(GuiGraphicsExtractor g, MobEffect effect, int x, int y, int size) {
	//#endif
		//#if MC >= 26.2
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect), x, y, size, size);
		//#elseif MC >= 1.21.11
		//$$ g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect), x, y, size, size);
		//#else
		//$$ g.blit(x, y, 0, size, size, Minecraft.getInstance().getMobEffectTextures().get(effect));
		//#endif
	}

	/**
	 * Ausschnitt einer Textur: (u, v, uw, vh) in Pixeln einer texW×texH großen Textur,
	 * gezeichnet bei (x, y) mit Größe w×h.
	 */
	public static void region(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
		//#if MC >= 1.21.11
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uw, vh, texW, texH, 0xFFFFFFFF);
		//#else
		//$$ RenderSystem.enableBlend();
		//$$ g.blit(texture, x, y, w, h, u, v, uw, vh, texW, texH);
		//$$ RenderSystem.disableBlend();
		//#endif
	}

	/** Ganze Textur skaliert zeichnen. */
	public static void image(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
		//#if MC >= 1.21.11
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, texW, texH, texW, texH, alpha(0xFFFFFFFF, alpha));
		//#else
		//$$ RenderSystem.enableBlend();
		//$$ g.setColor(1, 1, 1, alpha);
		//$$ g.blit(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
		//$$ g.setColor(1, 1, 1, 1);
		//$$ RenderSystem.disableBlend();
		//#endif
	}

	/** Spielerfigur wie im Inventar, aber mit frei drehbarem Blickwinkel (yaw in Grad). */
	public static void entity(GuiGraphicsExtractor g, LivingEntity entity, float yaw, int x0, int y0, int x1, int y1, int size) {
		//#if MC >= 1.21.11
		EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = yaw;
			living.yRot = 0;
			living.xRot = 0;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf tilt = new Quaternionf().rotateX(-0.12f);
		rotation.mul(tilt);
		Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		g.entity(state, size, translation, rotation, tilt, x0, y0, x1, y1);
		//#else
		//$$ LegacyEntity.render(g, entity, yaw, x0, y0, x1, y1, size);
		//#endif
	}

	// ---------- Formen ----------

	private static float pixelScale(GuiGraphicsExtractor g) {
		return (float) Minecraft.getInstance().getWindow().getGuiScale() * poseScale(g);
	}

	/** Abgerundetes Rechteck (x, y, Breite, Höhe, Radius in GUI-Pixeln). */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		gradient(g, x, y, w, h, radius, color, color);
	}

	/** Abgerundetes Rechteck mit senkrechtem Farbverlauf. */
	public static void gradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		if (w <= 0 || h <= 0 || ((top | bottom) >>> 24) == 0) return;
		float s = pixelScale(g);
		push(g);
		scale(g, 1 / s);
		int X = Math.round(x * s);
		int Y = Math.round(y * s);
		int W = Math.round((x + w) * s) - X;
		int H = Math.round((y + h) * s) - Y;
		int R = Math.max(0, Math.min(Math.round(radius * s), Math.min(W, H) / 2));

		if (H - 2 * R > 0) {
			if (top == bottom) fill(g, X, Y + R, X + W, Y + H - R, top);
			else fillGradient(g, X, Y + R, X + W, Y + H - R, mix(top, bottom, (float) R / H), mix(top, bottom, (float) (H - R) / H));
		}
		for (int j = 0; j < R; j++) {
			float dy = R - j - 0.5f;
			float inset = R - (float) Math.sqrt(R * R - dy * dy);
			row(g, X, Y + j, W, inset, mix(top, bottom, (j + 0.5f) / H));
			row(g, X, Y + H - 1 - j, W, inset, mix(top, bottom, (H - j - 0.5f) / H));
		}
		pop(g);
	}

	private static void row(GuiGraphicsExtractor g, int X, int y, int W, float inset, int color) {
		int ii = (int) inset;
		float coverage = 1f - (inset - ii);
		if (W - 2 * ii - 2 > 0) fill(g, X + ii + 1, y, X + W - ii - 1, y + 1, color);
		int edge = alpha(color, coverage);
		fill(g, X + ii, y, X + ii + 1, y + 1, edge);
		if (W - ii - 1 != ii) fill(g, X + W - ii - 1, y, X + W - ii, y + 1, edge);
	}

	/** Rahmen eines abgerundeten Rechtecks (Dicke in GUI-Pixeln). */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float thickness, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		float s = pixelScale(g);
		push(g);
		scale(g, 1 / s);
		int X = Math.round(x * s);
		int Y = Math.round(y * s);
		int W = Math.round((x + w) * s) - X;
		int H = Math.round((y + h) * s) - Y;
		int T = Math.max(1, Math.round(thickness * s));
		int R = Math.max(0, Math.min(Math.round(radius * s), Math.min(W, H) / 2));
		int Ri = Math.max(0, R - T);

		// gerade Seiten
		fill(g, X + R, Y, X + W - R, Y + T, color);
		fill(g, X + R, Y + H - T, X + W - R, Y + H, color);
		fill(g, X, Y + R, X + T, Y + H - R, color);
		fill(g, X + W - T, Y + R, X + W, Y + H - R, color);
		// Ecken zeilenweise
		for (int j = 0; j < R; j++) {
			float dy = R - j - 0.5f;
			float outer = R - (float) Math.sqrt(R * R - dy * dy);
			float inner;
			if (j < T) {
				inner = R; // in der oberen Randzone: bis zum geraden Stück füllen
			} else {
				float dyi = Ri - (j - T) - 0.5f;
				inner = T + Ri - (float) Math.sqrt(Math.max(0, Ri * Ri - dyi * dyi));
			}
			cornerRow(g, X, Y + j, W, R, outer, inner, color);
			cornerRow(g, X, Y + H - 1 - j, W, R, outer, inner, color);
		}
		pop(g);
	}

	private static void cornerRow(GuiGraphicsExtractor g, int X, int y, int W, int R, float outer, float inner, int color) {
		int oi = (int) outer;
		int in = Math.min(R, (int) Math.ceil(inner));
		int edge = alpha(color, 1f - (outer - oi));
		fill(g, X + oi, y, X + oi + 1, y + 1, edge);
		fill(g, X + W - oi - 1, y, X + W - oi, y + 1, edge);
		if (in > oi + 1) {
			fill(g, X + oi + 1, y, X + in, y + 1, color);
			fill(g, X + W - in, y, X + W - oi - 1, y + 1, color);
		}
	}

	/** Kreis (z. B. Schalter-Knopf). */
	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		rect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	// ---------- Text (Schrift "Inter") ----------

	public static Component ui(String text) {
		return ui(Component.literal(text));
	}

	public static Component ui(Component text) {
		//#if MC >= 1.16
		return text.copy().withStyle(st -> st.withFont(UI_FONT));
		//#else
		//$$ return text.copy().withStyle(st -> st.setInsertion(UI_FONT.toString())); // siehe GuiGraphics.font
		//#endif
	}

	public static Component bold(String text) {
		return bold(Component.literal(text));
	}

	public static Component bold(Component text) {
		//#if MC >= 1.16
		return text.copy().withStyle(st -> st.withFont(UI_BOLD));
		//#else
		//$$ return text.copy().withStyle(st -> st.setInsertion(UI_BOLD.toString()));
		//#endif
	}

	public static int width(Component text) {
		//#if MC >= 1.16
		return Minecraft.getInstance().font.width(text);
		//#else
		//$$ return GuiGraphicsExtractor.width(text);
		//#endif
	}

	public static void text(GuiGraphicsExtractor g, Component text, float x, float y, int color) {
		text(g, text, x, y, 1f, color);
	}

	/** Text mit Skalierung (z. B. 1.5 für Überschriften). */
	public static void text(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		push(g);
		translate(g, x, y);
		if (scale != 1f) scale(g, scale);
		mcText(g, text, 0, 0, color, false);
		pop(g);
	}

	public static void centered(GuiGraphicsExtractor g, Component text, float cx, float y, float scale, int color) {
		text(g, text, cx - width(text) * scale / 2f, y, scale, color);
	}

	/**
	 * Zentriert in maxWidth: zu langer Text (z. B. "Dirección del servidor") wird erst etwas kleiner geschrieben
	 * und nur, wenn das nicht reicht, mit "…" gekürzt.
	 */
	public static void centeredFit(GuiGraphicsExtractor g, Component text, float cx, float y, float maxWidth, float scale, int color) {
		float fit = Math.max(scale * 0.8f, Math.min(scale, maxWidth / width(text)));
		centered(g, ellipsize(text, (int) (maxWidth / fit)), cx, y + (scale - fit) * 4, fit, color);
	}

	/** Kürzt Text mit "…", damit er in maxWidth passt. */
	public static Component ellipsize(Component text, int maxWidth) {
		if (width(text) <= maxWidth) return text;
		String s = text.getString();
		while (s.length() > 1 && width(ui(s + "…")) > maxWidth) s = s.substring(0, s.length() - 1);
		return ui(s + "…");
	}
}
