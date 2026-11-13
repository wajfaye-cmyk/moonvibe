package net.moonvibe.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.Mv;
import net.moonvibe.Moonvibe;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * Schrift "Inter" für Minecraft 1.8.9. Die alte Version kann keine TTF-Schriften – daher zeichnet Java (AWT)
 * alle Zeichen einmal in eine Textur, und zwar in der echten Bildschirmauflösung (GUI-Skalierung),
 * damit der Text genauso scharf aussieht wie in den neuen Versionen.
 */
public final class UiFont {
	public static final UiFont REGULAR = new UiFont("inter-medium.ttf", "regular");
	public static final UiFont BOLD = new UiFont("inter-bold.ttf", "bold");

	/** Schriftgröße und Grundlinie in GUI-Pixeln – wie die TTF-Schrift der neuen Versionen (8.5 px, 0.5 px tiefer). */
	private static final float SIZE = 8.5f;
	private static final float BASELINE = 7.5f;
	private static final int PAD = 2;
	private static final String CHARS = charset();

	private static final class Glyph {
		int x;
		int y;
		int w;
		int h;
		float advance;
	}

	private final String file;
	private final String name;
	private Font awt;
	private int builtScale = -1;
	private Identifier texture;
	private int textureId;
	private int texW;
	private int texH;
	private int baselinePx;
	private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();

	private UiFont(String file, String name) {
		this.file = file;
		this.name = name;
	}

	private static String charset() {
		StringBuilder sb = new StringBuilder();
		for (char c = 32; c < 127; c++) sb.append(c);
		for (char c = 160; c < 384; c++) sb.append(c); // Latein inkl. Türkisch
		for (char c = 0x400; c < 0x460; c++) sb.append(c); // Kyrillisch
		sb.append("…‹›«»✕✓★·•—–‘’“”„€°×←→↑↓▶◀●○");
		return sb.toString();
	}

	private void ensure() {
		int scale = Math.max(1, Mv.guiScale());
		if (scale != builtScale) build(scale);
	}

	private static void hints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private void build(int scale) {
		try {
			if (awt == null) {
				try (InputStream in = UiFont.class.getResourceAsStream("/assets/moonvibe/font/" + file)) {
					awt = Font.createFont(Font.TRUETYPE_FONT, in);
				}
			}
		} catch (Exception e) {
			Moonvibe.LOGGER.warn("Schrift {} konnte nicht geladen werden", file, e);
			awt = new Font("SansSerif", Font.PLAIN, 12);
		}
		Font font = awt.deriveFont(SIZE * scale);
		// Zeichen, die Inter nicht hat (z. B. ✕), aus einer Systemschrift
		Font fallback = new Font("Dialog", font.getStyle(), Math.round(SIZE * scale));

		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		hints(pg);
		pg.setFont(font);
		FontMetrics fm = pg.getFontMetrics();
		FontRenderContext frc = pg.getFontRenderContext();
		int ascent = fm.getAscent();
		int cellH = ascent + fm.getDescent() + PAD * 2;
		baselinePx = ascent + PAD;

		// Zeichen nebeneinander in Zeilen anordnen
		int maxW = scale > 3 ? 2048 : 1024;
		int x = 0;
		int y = 0;
		glyphs.clear();
		Map<Character, Font> source = new HashMap<Character, Font>();
		for (int i = 0; i < CHARS.length(); i++) {
			char c = CHARS.charAt(i);
			Font f = font.canDisplay(c) ? font : fallback.canDisplay(c) ? fallback : null;
			if (f == null) continue;
			GlyphVector gv = f.createGlyphVector(frc, String.valueOf(c));
			Glyph g = new Glyph();
			g.advance = (float) gv.getGlyphMetrics(0).getAdvance();
			g.w = (int) Math.ceil(g.advance) + PAD * 2;
			g.h = cellH;
			if (x + g.w > maxW) {
				x = 0;
				y += cellH;
			}
			g.x = x;
			g.y = y;
			x += g.w;
			glyphs.put(c, g);
			source.put(c, f);
		}
		pg.dispose();
		texW = maxW;
		texH = y + cellH;

		BufferedImage atlas = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = atlas.createGraphics();
		hints(g2);
		g2.setColor(java.awt.Color.WHITE);
		for (Map.Entry<Character, Glyph> e : glyphs.entrySet()) {
			Glyph g = e.getValue();
			g2.setFont(source.get(e.getKey()));
			g2.drawString(String.valueOf(e.getKey()), g.x + PAD, g.y + baselinePx);
		}
		g2.dispose();

		if (texture != null) Mv.mc().getTextureManager().close(texture);
		NativeImageBackedTexture tex = Mv.texture(atlas);
		texture = Mv.mc().getTextureManager().registerDynamicTexture("moonvibe_font_" + name, tex);
		textureId = tex.getGlId();
		// weich filtern, damit auch vergrößerter Text (Überschriften) sauber aussieht
		GlStateManager.bindTexture(textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		builtScale = scale;
	}

	private Glyph glyph(char c) {
		Glyph g = glyphs.get(c);
		return g != null ? g : glyphs.get('?');
	}

	/** Breite in GUI-Pixeln */
	public float width(String text) {
		ensure();
		float w = 0;
		for (int i = 0; i < text.length(); i++) {
			Glyph g = glyph(text.charAt(i));
			if (g != null) w += g.advance;
		}
		return w / builtScale;
	}

	/** Text zeichnen; (x, y) = linke obere Ecke wie bei der Minecraft-Schrift. */
	public void draw(String text, float x, float y, int color) {
		if ((color >>> 24) < 4 || text.isEmpty()) return;
		ensure();
		float s = builtScale;
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.enableTexture();
		GlStateManager.bindTexture(textureId);
		GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder b = t.getBuffer();
		b.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
		// auf echte Pixel ausrichten, damit nichts verschwimmt
		float pen = Math.round(x * s) / s;
		float top = Math.round((y + BASELINE) * s - baselinePx) / s;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			Glyph g = glyph(c);
			if (g == null) continue;
			if (c != ' ') {
				float gx = Math.round(pen * s - PAD) / s;
				float gw = g.w / s;
				float gh = g.h / s;
				float u0 = (float) g.x / texW;
				float v0 = (float) g.y / texH;
				float u1 = (float) (g.x + g.w) / texW;
				float v1 = (float) (g.y + g.h) / texH;
				b.vertex(gx, top + gh, 0).texture(u0, v1).next();
				b.vertex(gx + gw, top + gh, 0).texture(u1, v1).next();
				b.vertex(gx + gw, top, 0).texture(u1, v0).next();
				b.vertex(gx, top, 0).texture(u0, v0).next();
			}
			pen += g.advance / s;
		}
		t.draw();
		GlStateManager.color(1, 1, 1, 1);
	}
}
