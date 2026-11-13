package net.moonvibe.cosmetics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Macht aus einem beliebigen Bild eine Cape-Textur (512×256, Minecraft-Aufteilung) – wie der
 * Cape-Editor im Launcher: Fotos füllen das Cape, Logos mit durchsichtigem Hintergrund werden eingepasst.
 * Fertige Cape-Dateien (2:1, Ecke oben links leer) bleiben, wie sie sind.
 */
public final class CapeArt {
	/** Pixel je Cape-Pixel (64×32 → 512×256) */
	private static final int SCALE = 8;

	private CapeArt() {}

	public static BufferedImage build(BufferedImage source) {
		int w = source.getWidth();
		int h = source.getHeight();
		BufferedImage out = new BufferedImage(64 * SCALE, 32 * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		boolean smooth = w > 80 || h > 128;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		if (w == h * 2 && (source.getRGB(0, 0) >>> 24) == 0) {
			g.drawImage(source, 0, 0, out.getWidth(), out.getHeight(), null);
			g.dispose();
			return out;
		}
		boolean logo = transparency(source) > 0.08f;
		Color background = average(source, logo ? 0.35f : 0.8f);
		Color edge = scale(background, 0.75f);

		BufferedImage art = new BufferedImage(10 * SCALE, 16 * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D a = art.createGraphics();
		a.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		a.setColor(background);
		a.fillRect(0, 0, art.getWidth(), art.getHeight());
		if (logo) {
			// ganz einpassen, mittig auf dunklem Hintergrund in der Farbe des Logos
			float s = Math.min((float) art.getWidth() / w, (float) art.getHeight() / h);
			int tw = Math.max(1, Math.round(w * s));
			int th = Math.max(1, Math.round(h * s));
			a.drawImage(source, (art.getWidth() - tw) / 2, (art.getHeight() - th) / 2, tw, th, null);
		} else {
			// Ausschnitt im Seitenverhältnis 10:16 aus der Bildmitte
			int cw = w;
			int ch = h;
			if (w * 16 > h * 10) cw = Math.max(1, h * 10 / 16);
			else ch = Math.max(1, w * 16 / 10);
			int sx = (w - cw) / 2;
			int sy = (h - ch) / 2;
			a.drawImage(source, 0, 0, art.getWidth(), art.getHeight(), sx, sy, sx + cw, sy + ch, null);
		}
		a.dispose();

		g.setColor(edge);
		fill(g, 1, 0, 10, 1);
		fill(g, 11, 0, 10, 1);
		fill(g, 0, 1, 1, 16);
		fill(g, 11, 1, 1, 16);
		fill(g, 22, 0, 24, 22); // Elytra-Bereich (gibt es in 1.8.9 nicht, aber das Cape bleibt so überall gleich)
		g.drawImage(art, SCALE, SCALE, null);
		// Innenseite: gespiegelt und etwas dunkler, wie Stoff von innen
		g.drawImage(art, 12 * SCALE + art.getWidth(), SCALE, -art.getWidth(), art.getHeight(), null);
		g.setColor(new Color(0, 0, 0, 64));
		fill(g, 12, 1, 10, 16);
		g.drawImage(art, 24 * SCALE, 2 * SCALE, 10 * SCALE, 20 * SCALE, null);
		g.drawImage(art, 36 * SCALE + 10 * SCALE, 2 * SCALE, -10 * SCALE, 20 * SCALE, null);
		g.dispose();
		return out;
	}

	private static void fill(Graphics2D g, int x, int y, int w, int h) {
		g.fillRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE);
	}

	/** Anteil (fast) durchsichtiger Pixel. */
	private static float transparency(BufferedImage image) {
		int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 96);
		int clear = 0;
		int total = 0;
		for (int y = 0; y < image.getHeight(); y += step) {
			for (int x = 0; x < image.getWidth(); x += step) {
				if ((image.getRGB(x, y) >>> 24) < 32) clear++;
				total++;
			}
		}
		return total == 0 ? 0 : (float) clear / total;
	}

	/** Durchschnittsfarbe der sichtbaren Pixel, abgedunkelt. */
	private static Color average(BufferedImage image, float brightness) {
		int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 96);
		long r = 0;
		long g = 0;
		long b = 0;
		long n = 0;
		for (int y = 0; y < image.getHeight(); y += step) {
			for (int x = 0; x < image.getWidth(); x += step) {
				int c = image.getRGB(x, y);
				if ((c >>> 24) < 128) continue;
				r += c >> 16 & 255;
				g += c >> 8 & 255;
				b += c & 255;
				n++;
			}
		}
		if (n == 0) return new Color(0x1B1D24);
		return scale(new Color((int) (r / n), (int) (g / n), (int) (b / n)), brightness);
	}

	private static Color scale(Color c, float f) {
		return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
	}
}
