//#if MC < 1.15
//$$ package net.moonvibe.compat;
//$$
//$$ import com.mojang.blaze3d.platform.GlStateManager;
//$$ import java.util.ArrayDeque;
//$$ import java.util.Deque;
//$$
//$$ /**
//$$  * Ersatz für Minecrafts PoseStack (gibt es erst ab 1.15). In 1.14 zeichnet alles mit der OpenGL-Matrix –
//$$  * Verschieben und Skalieren gehen deshalb direkt dorthin; nebenbei merken wir uns die Werte
//$$  * (für Zeichenbereiche und die Umrechnung in Bildschirm-Pixel).
//$$  */
//$$ public class PoseStack {
//$$ 	private final Deque<float[]> stack = new ArrayDeque<>();
//$$ 	private float x;
//$$ 	private float y;
//$$ 	private float scale = 1;
//$$
//$$ 	public void pushPose() {
//$$ 		stack.push(new float[] {x, y, scale});
//$$ 		GlStateManager.pushMatrix();
//$$ 	}
//$$
//$$ 	public void popPose() {
//$$ 		float[] p = stack.pop();
//$$ 		x = p[0];
//$$ 		y = p[1];
//$$ 		scale = p[2];
//$$ 		GlStateManager.popMatrix();
//$$ 	}
//$$
//$$ 	public void translate(double dx, double dy, double dz) {
//$$ 		x += dx * scale;
//$$ 		y += dy * scale;
//$$ 		GlStateManager.translated(dx, dy, dz);
//$$ 	}
//$$
//$$ 	/** Nur gleichmäßiges Skalieren in x/y (mehr braucht die Oberfläche nicht). */
//$$ 	public void scale(float sx, float sy, float sz) {
//$$ 		scale *= sx;
//$$ 		GlStateManager.scalef(sx, sy, sz);
//$$ 	}
//$$
//$$ 	/** Punkt (px, py) in GUI-Pixeln des Bildschirms */
//$$ 	public float[] transform(float px, float py) {
//$$ 		return new float[] {x + px * scale, y + py * scale};
//$$ 	}
//$$ }
//#endif
