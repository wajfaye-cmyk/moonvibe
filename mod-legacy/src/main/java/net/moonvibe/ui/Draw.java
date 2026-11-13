package net.moonvibe.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.Mv;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
//#if MC >= 1.13
//$$ import net.minecraft.client.font.TextRenderer;
//#endif

/**
 * Zeichen-Werkzeuge für die moonvibe-Oberfläche unter Minecraft 1.8.9 – dieselben Funktionen wie in den
 * neuen Versionen. Abgerundete Ecken werden in echten Bildschirm-Pixeln mit Kantenglättung gezeichnet,
 * alle Teile einer Form in einem einzigen Zeichenaufruf.
 */
public final class Draw {
	/** Skalierungen, die über push/scale/pop gesetzt wurden (für die Pixel-Umrechnung) */
	private static final Deque<Float> SCALES = new ArrayDeque<Float>();
	private static float scale = 1f;

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

	// ---------- Verschieben/Skalieren ----------

	public static void push() {
		GlStateManager.pushMatrix();
		SCALES.push(scale);
	}

	public static void pop() {
		GlStateManager.popMatrix();
		scale = SCALES.isEmpty() ? 1f : SCALES.pop();
	}

	public static void translate(float x, float y) {
		GlStateManager.translate(x, y, 0);
	}

	public static void scale(float s) {
		GlStateManager.scale(s, s, 1);
		scale *= s;
	}

	// ---------- Formen ----------

	private static BufferBuilder begin() {
		GlStateManager.enableBlend();
		GlStateManager.disableTexture();
		GlStateManager.disableAlphaTest();
		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		BufferBuilder b = Tessellator.getInstance().getBuffer();
		b.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
		return b;
	}

	private static void end() {
		Tessellator.getInstance().draw();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableAlphaTest();
		GlStateManager.enableTexture();
		GlStateManager.disableBlend();
	}

	private static void quad(BufferBuilder b, float x0, float y0, float x1, float y1, int top, int bottom) {
		if (x1 <= x0 || y1 <= y0) return;
		b.vertex(x0, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).next();
		b.vertex(x1, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).next();
		b.vertex(x1, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).next();
		b.vertex(x0, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).next();
	}

	/** Einfarbiges Rechteck von (x0, y0) bis (x1, y1) – wie fill() in Minecraft. */
	public static void fill(float x0, float y0, float x1, float y1, int color) {
		fillGradient(x0, y0, x1, y1, color, color);
	}

	public static void fillGradient(float x0, float y0, float x1, float y1, int top, int bottom) {
		if (((top | bottom) >>> 24) == 0) return;
		BufferBuilder b = begin();
		quad(b, x0, y0, x1, y1, top, bottom);
		end();
	}

	private static float pixelScale() {
		return Mv.guiScale() * scale;
	}

	/** Abgerundetes Rechteck (x, y, Breite, Höhe, Radius in GUI-Pixeln). */
	public static void rect(float x, float y, float w, float h, float radius, int color) {
		gradient(x, y, w, h, radius, color, color);
	}

	/** Abgerundetes Rechteck mit senkrechtem Farbverlauf. */
	public static void gradient(float x, float y, float w, float h, float radius, int top, int bottom) {
		if (w <= 0 || h <= 0 || ((top | bottom) >>> 24) == 0) return;
		float s = pixelScale();
		GlStateManager.pushMatrix();
		GlStateManager.scale(1 / s, 1 / s, 1);
		int X = Math.round(x * s);
		int Y = Math.round(y * s);
		int W = Math.round((x + w) * s) - X;
		int H = Math.round((y + h) * s) - Y;
		int R = Math.max(0, Math.min(Math.round(radius * s), Math.min(W, H) / 2));

		BufferBuilder b = begin();
		if (H - 2 * R > 0) quad(b, X, Y + R, X + W, Y + H - R, mix(top, bottom, (float) R / H), mix(top, bottom, (float) (H - R) / H));
		for (int j = 0; j < R; j++) {
			float dy = R - j - 0.5f;
			float inset = R - (float) Math.sqrt(R * R - dy * dy);
			row(b, X, Y + j, W, inset, mix(top, bottom, (j + 0.5f) / H));
			row(b, X, Y + H - 1 - j, W, inset, mix(top, bottom, (H - j - 0.5f) / H));
		}
		end();
		GlStateManager.popMatrix();
	}

	private static void row(BufferBuilder b, int X, int y, int W, float inset, int color) {
		int ii = (int) inset;
		float coverage = 1f - (inset - ii);
		if (W - 2 * ii - 2 > 0) quad(b, X + ii + 1, y, X + W - ii - 1, y + 1, color, color);
		int edge = alpha(color, coverage);
		quad(b, X + ii, y, X + ii + 1, y + 1, edge, edge);
		if (W - ii - 1 != ii) quad(b, X + W - ii - 1, y, X + W - ii, y + 1, edge, edge);
	}

	/** Rahmen eines abgerundeten Rechtecks (Dicke in GUI-Pixeln). */
	public static void outline(float x, float y, float w, float h, float radius, float thickness, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		float s = pixelScale();
		GlStateManager.pushMatrix();
		GlStateManager.scale(1 / s, 1 / s, 1);
		int X = Math.round(x * s);
		int Y = Math.round(y * s);
		int W = Math.round((x + w) * s) - X;
		int H = Math.round((y + h) * s) - Y;
		int T = Math.max(1, Math.round(thickness * s));
		int R = Math.max(0, Math.min(Math.round(radius * s), Math.min(W, H) / 2));
		int Ri = Math.max(0, R - T);

		BufferBuilder b = begin();
		quad(b, X + R, Y, X + W - R, Y + T, color, color);
		quad(b, X + R, Y + H - T, X + W - R, Y + H, color, color);
		quad(b, X, Y + R, X + T, Y + H - R, color, color);
		quad(b, X + W - T, Y + R, X + W, Y + H - R, color, color);
		for (int j = 0; j < R; j++) {
			float dy = R - j - 0.5f;
			float outer = R - (float) Math.sqrt(R * R - dy * dy);
			float inner;
			if (j < T) {
				inner = R;
			} else {
				float dyi = Ri - (j - T) - 0.5f;
				inner = T + Ri - (float) Math.sqrt(Math.max(0, Ri * Ri - dyi * dyi));
			}
			cornerRow(b, X, Y + j, W, R, outer, inner, color);
			cornerRow(b, X, Y + H - 1 - j, W, R, outer, inner, color);
		}
		end();
		GlStateManager.popMatrix();
	}

	private static void cornerRow(BufferBuilder b, int X, int y, int W, int R, float outer, float inner, int color) {
		int oi = (int) outer;
		int in = Math.min(R, (int) Math.ceil(inner));
		int edge = alpha(color, 1f - (outer - oi));
		quad(b, X + oi, y, X + oi + 1, y + 1, edge, edge);
		quad(b, X + W - oi - 1, y, X + W - oi, y + 1, edge, edge);
		if (in > oi + 1) {
			quad(b, X + oi + 1, y, X + in, y + 1, color, color);
			quad(b, X + W - in, y, X + W - oi - 1, y + 1, color, color);
		}
	}

	public static void circle(float cx, float cy, float radius, int color) {
		rect(cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	// ---------- Bilder ----------

	/** Ganze Textur skaliert zeichnen. */
	public static void image(Identifier texture, float x, float y, float w, float h, float alpha) {
		region(texture, x, y, w, h, 0, 0, 1, 1, alpha(0xFFFFFFFF, alpha));
	}

	/** Ausschnitt einer Textur (u/v von 0 bis 1). */
	public static void region(Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
		if ((color >>> 24) == 0) return;
		MinecraftClient.getInstance().getTextureManager().bindTexture(texture);
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
		BufferBuilder b = Tessellator.getInstance().getBuffer();
		b.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
		b.vertex(x, y + h, 0).texture(u0, v1).next();
		b.vertex(x + w, y + h, 0).texture(u1, v1).next();
		b.vertex(x + w, y, 0).texture(u1, v0).next();
		b.vertex(x, y, 0).texture(u0, v0).next();
		Tessellator.getInstance().draw();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.disableBlend();
	}

	/** Gegenstand wie in der Hotbar zeichnen (16×16). */
	public static void item(ItemStack stack, float x, float y) {
		if (Mv.isEmpty(stack)) return;
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0);
		GlStateManager.enableRescaleNormal();
		DiffuseLighting.enable();
		//#if MC >= 1.13
		//$$ MinecraftClient.getInstance().getHeldItemRenderer().method_19397(stack, 0, 0); // ItemRenderer.renderInGuiWithOverrides
		//#elseif MC >= 1.9
		//$$ MinecraftClient.getInstance().getItemRenderer().method_12461(stack, 0, 0); // renderInGuiWithOverrides
		//#else
		MinecraftClient.getInstance().getItemRenderer().renderInGuiWithOverrides(stack, 0, 0);
		//#endif
		DiffuseLighting.disable();
		GlStateManager.disableRescaleNormal();
		GlStateManager.popMatrix();
	}

	// ---------- Ausschnitt (alles außerhalb wird abgeschnitten) ----------

	public static void scissor(float x0, float y0, float x1, float y1) {
		int f = Mv.guiScale();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(Math.round(x0 * f), Mv.displayHeight() - Math.round(y1 * f), Math.max(0, Math.round((x1 - x0) * f)), Math.max(0, Math.round((y1 - y0) * f)));
	}

	public static void endScissor() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	// ---------- Text (Schrift "Inter") ----------

	/** Text in der moonvibe-Schrift (normal oder fett). */
	public static final class Txt {
		public final String text;
		public final boolean bold;

		private Txt(String text, boolean bold) {
			this.text = text;
			this.bold = bold;
		}

		private UiFont font() {
			return bold ? UiFont.BOLD : UiFont.REGULAR;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	public static Txt ui(String text) {
		return new Txt(text, false);
	}

	public static Txt bold(String text) {
		return new Txt(text, true);
	}

	/** Übersetzter Text in der moonvibe-Schrift */
	public static Txt tr(String key, Object... args) {
		return ui(Mv.tr(key, args));
	}

	public static Txt trBold(String key, Object... args) {
		return bold(Mv.tr(key, args));
	}

	public static float width(Txt text) {
		return text.font().width(text.text);
	}

	public static void text(Txt text, float x, float y, int color) {
		text.font().draw(text.text, x, y, color);
	}

	/** Text mit Skalierung (z. B. 1.5 für Überschriften). */
	public static void text(Txt text, float x, float y, float scale, int color) {
		if (scale == 1f) {
			text(text, x, y, color);
			return;
		}
		push();
		translate(x, y);
		scale(scale);
		text.font().draw(text.text, 0, 0, color);
		pop();
	}

	public static void centered(Txt text, float cx, float y, float scale, int color) {
		text(text, cx - width(text) * scale / 2f, y, scale, color);
	}

	/**
	 * Zentriert in maxWidth: zu langer Text (z. B. "Dirección del servidor") wird erst etwas kleiner geschrieben
	 * und nur, wenn das nicht reicht, mit "…" gekürzt.
	 */
	public static void centeredFit(Txt text, float cx, float y, float maxWidth, float scale, int color) {
		float fit = Math.max(scale * 0.8f, Math.min(scale, maxWidth / width(text)));
		centered(ellipsize(text, (int) (maxWidth / fit)), cx, y + (scale - fit) * 4, fit, color);
	}

	/** Kürzt Text mit "…", damit er in maxWidth passt. */
	public static Txt ellipsize(Txt text, int maxWidth) {
		if (width(text) <= maxWidth) return text;
		String s = text.text;
		while (s.length() > 1 && text.font().width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
		return new Txt(s + "…", text.bold);
	}

	/** Kürzt vorne ("…Ende"), z. B. für lange Eingaben im Suchfeld. */
	public static Txt ellipsizeStart(Txt text, int maxWidth) {
		if (width(text) <= maxWidth) return text;
		String s = text.text;
		while (s.length() > 1 && text.font().width("…" + s) > maxWidth) s = s.substring(1);
		return new Txt("…" + s, text.bold);
	}

	/** Minecraft-Schrift (für HUD-Anzeigen wie in den neuen Versionen). */
	public static void mcText(String text, float x, float y, int color, boolean shadow) {
		//#if MC >= 1.13
		//$$ TextRenderer font = MinecraftClient.getInstance().textRenderer;
		//$$ if (shadow) font.drawWithShadow(text, x, y, color);
		//$$ else font.method_18355(text, x, y, color); // draw
		//#else
		MinecraftClient.getInstance().textRenderer.draw(text, x, y, color, shadow);
		//#endif
		GlStateManager.color(1, 1, 1, 1);
	}

	public static int mcWidth(String text) {
		return MinecraftClient.getInstance().textRenderer.getStringWidth(text);
	}
}
