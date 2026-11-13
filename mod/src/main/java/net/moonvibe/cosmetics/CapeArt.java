package net.moonvibe.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Macht aus einem beliebigen Bild eine Cape-Textur (512×256, Minecraft-Aufteilung) – wie der
 * Cape-Editor im Launcher: Fotos füllen das Cape, Logos mit durchsichtigem Hintergrund werden eingepasst.
 * Fertige Cape-Dateien (2:1, Ecke oben links leer) bleiben, wie sie sind.
 */
public final class CapeArt {
	/** Pixel je Cape-Pixel (64×32 → 512×256) */
	private static final int SCALE = 8;
	private static final int MAX_SIZE = 4096;

	private CapeArt() {}

	/** PNG oder JPG laden (NativeImage.read kann nur PNG). */
	public static NativeImage load(Path file) throws IOException {
		byte[] bytes = Files.readAllBytes(file);
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			buffer.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer comp = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(buffer, w, h, comp, 4);
			if (pixels == null) throw new IOException(STBImage.stbi_failure_reason());
			try {
				int width = w.get(0);
				int height = h.get(0);
				if (width > MAX_SIZE || height > MAX_SIZE) throw new IOException("too large");
				NativeImage image = new NativeImage(width, height, false);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int i = (x + y * width) * 4;
						set(image, x, y, color(pixels.get(i + 3) & 255, pixels.get(i) & 255, pixels.get(i + 1) & 255, pixels.get(i + 2) & 255));
					}
				}
				return image;
			} finally {
				STBImage.stbi_image_free(pixels);
			}
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	public static NativeImage build(NativeImage source) {
		int w = source.getWidth();
		int h = source.getHeight();
		NativeImage out = new NativeImage(64 * SCALE, 32 * SCALE, true);
		if (w == h * 2 && alpha(get(source, 0, 0)) == 0) {
			scale(source, 0, 0, w, h, out);
			return out;
		}
		boolean logo = transparency(source) > 0.08f;
		int background = average(source, logo ? 0.35f : 0.8f);
		try (NativeImage art = new NativeImage(10 * SCALE, 16 * SCALE, false);
			NativeImage wing = new NativeImage(10 * SCALE, 20 * SCALE, false)) {
			fillRect(art, 0, 0, art.getWidth(), art.getHeight(), background);
			if (logo) {
				// ganz einpassen, mittig auf dunklem Hintergrund in der Farbe des Logos
				float s = Math.min((float) art.getWidth() / w, (float) art.getHeight() / h);
				int tw = Math.max(1, Math.round(w * s));
				int th = Math.max(1, Math.round(h * s));
				try (NativeImage fitted = new NativeImage(tw, th, false)) {
					scale(source, 0, 0, w, h, fitted);
					blend(fitted, art, (art.getWidth() - tw) / 2, (art.getHeight() - th) / 2, background);
				}
			} else {
				// Ausschnitt im Seitenverhältnis 10:16 aus der Bildmitte
				int cw = w;
				int ch = h;
				if (w * 16 > h * 10) cw = Math.max(1, h * 10 / 16);
				else ch = Math.max(1, w * 16 / 10);
				try (NativeImage cropped = new NativeImage(art.getWidth(), art.getHeight(), false)) {
					scale(source, (w - cw) / 2, (h - ch) / 2, cw, ch, cropped);
					blend(cropped, art, 0, 0, background);
				}
			}
			int edge = scaleRGB(background, 0.75f);
			fill(out, 1, 0, 10, 1, edge);
			fill(out, 11, 0, 10, 1, edge);
			fill(out, 0, 1, 1, 16, edge);
			fill(out, 11, 1, 1, 16, edge);
			copy(art, out, 1, 1, false, 1f);
			copy(art, out, 12, 1, true, 0.75f);
			// Elytra bekommen dasselbe Motiv
			fill(out, 22, 0, 24, 22, edge);
			art.resizeSubRectTo(0, 0, art.getWidth(), art.getHeight(), wing);
			copy(wing, out, 24, 2, false, 1f);
			copy(wing, out, 36, 2, true, 1f);
		}
		return out;
	}

	/** Kleine Bilder (Pixel-Art) scharf vergrößern, große weich verkleinern. */
	private static void scale(NativeImage from, int sx, int sy, int sw, int sh, NativeImage to) {
		if (sw >= to.getWidth() && sh >= to.getHeight()) {
			from.resizeSubRectTo(sx, sy, sw, sh, to);
			return;
		}
		for (int y = 0; y < to.getHeight(); y++) {
			for (int x = 0; x < to.getWidth(); x++) {
				set(to, x, y, get(from, sx + x * sw / to.getWidth(), sy + y * sh / to.getHeight()));
			}
		}
	}

	/** Anteil (fast) durchsichtiger Pixel. */
	private static float transparency(NativeImage image) {
		int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 96);
		int clear = 0;
		int total = 0;
		for (int y = 0; y < image.getHeight(); y += step) {
			for (int x = 0; x < image.getWidth(); x += step) {
				if (alpha(get(image, x, y)) < 32) clear++;
				total++;
			}
		}
		return total == 0 ? 0 : (float) clear / total;
	}

	/** Durchschnittsfarbe der sichtbaren Pixel, abgedunkelt. */
	private static int average(NativeImage image, float brightness) {
		int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 96);
		long r = 0;
		long g = 0;
		long b = 0;
		long n = 0;
		for (int y = 0; y < image.getHeight(); y += step) {
			for (int x = 0; x < image.getWidth(); x += step) {
				int c = get(image, x, y);
				if (alpha(c) < 128) continue;
				r += red(c);
				g += green(c);
				b += blue(c);
				n++;
			}
		}
		if (n == 0) return 0xFF1B1D24;
		return scaleRGB(color(255, (int) (r / n), (int) (g / n), (int) (b / n)), brightness);
	}

	/** Bild auf den Hintergrund legen (Capes sind nicht durchsichtig). */
	private static void blend(NativeImage from, NativeImage to, int ox, int oy, int background) {
		for (int y = 0; y < from.getHeight(); y++) {
			for (int x = 0; x < from.getWidth(); x++) {
				int c = get(from, x, y);
				set(to, ox + x, oy + y, opaque(lerp(alpha(c) / 255f, background, c)));
			}
		}
	}

	private static void fill(NativeImage out, int x, int y, int w, int h, int color) {
		fillRect(out, x * SCALE, y * SCALE, w * SCALE, h * SCALE, color);
	}

	private static void copy(NativeImage from, NativeImage out, int x, int y, boolean mirror, float brightness) {
		for (int py = 0; py < from.getHeight(); py++) {
			for (int px = 0; px < from.getWidth(); px++) {
				int c = get(from, mirror ? from.getWidth() - 1 - px : px, py);
				set(out, x * SCALE + px, y * SCALE + py, brightness == 1f ? c : scaleRGB(c, brightness));
			}
		}
	}

	// ---------- Pixel als ARGB (ältere Versionen speichern sie als ABGR) ----------

	private static int get(NativeImage image, int x, int y) {
		//#if MC >= 1.21.11
		return image.getPixel(x, y);
		//#else
		//$$ return swapRedBlue(image.getPixelRGBA(x, y));
		//#endif
	}

	private static void set(NativeImage image, int x, int y, int argb) {
		//#if MC >= 1.21.11
		image.setPixel(x, y, argb);
		//#else
		//$$ image.setPixelRGBA(x, y, swapRedBlue(argb));
		//#endif
	}

	private static void fillRect(NativeImage image, int x, int y, int w, int h, int argb) {
		for (int py = y; py < y + h; py++) {
			for (int px = x; px < x + w; px++) set(image, px, py, argb);
		}
	}

	private static int swapRedBlue(int c) {
		return c & 0xFF00FF00 | (c & 0xFF) << 16 | (c >> 16 & 0xFF);
	}

	private static int alpha(int c) {
		return c >>> 24;
	}

	private static int red(int c) {
		return c >> 16 & 0xFF;
	}

	private static int green(int c) {
		return c >> 8 & 0xFF;
	}

	private static int blue(int c) {
		return c & 0xFF;
	}

	private static int color(int a, int r, int g, int b) {
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int opaque(int c) {
		return c | 0xFF000000;
	}

	private static int scaleRGB(int c, float f) {
		return color(alpha(c), Math.min(255, (int) (red(c) * f)), Math.min(255, (int) (green(c) * f)), Math.min(255, (int) (blue(c) * f)));
	}

	/** Zwischen zwei Farben überblenden (t = 0 → a, t = 1 → b). */
	private static int lerp(float t, int a, int b) {
		return color(
			Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
			Math.round(red(a) + (red(b) - red(a)) * t),
			Math.round(green(a) + (green(b) - green(a)) * t),
			Math.round(blue(a) + (blue(b) - blue(a)) * t)
		);
	}
}
