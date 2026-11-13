package net.moonvibe.ui.screen;

import net.moonvibe.Mv;
import net.moonvibe.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
//#if MC < 1.13
import org.lwjgl.input.Mouse;
//#endif

/**
 * Grundlage der moonvibe-Menüs für die alten Versionen: weichgezeichneter Hintergrund (wie in den neuen Versionen),
 * Mausrad, ESC zum Schließen und Maus-Ziehen mit Abstand seit dem letzten Bild.
 * Bis 1.12 fragt Minecraft Maus und Tastatur selbst ab, ab 1.13 kommen Ereignisse – beides landet in den on…-Methoden.
 */
public abstract class MoonScreen extends Screen {
	private static final Identifier BLUR = new Identifier("shaders/post/blur.json");
	private static boolean blurOn;

	private int lastDragX;
	private int lastDragY;

	@Override
	public void init() {
		blur(true);
	}

	@Override
	public void removed() {
		blur(false);
		Mv.repeatKeys(false);
	}

	/** Welt hinter dem Menü weichzeichnen (Minecrafts eigener Blur-Shader). */
	private static void blur(boolean on) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || on == blurOn) return;
		try {
			GameRendererAccessor renderer = (GameRendererAccessor) Mv.gameRenderer();
			if (on) renderer.moonvibe$loadShader(BLUR);
			else renderer.moonvibe$disableShader();
			blurOn = on;
		} catch (Throwable ignored) {
			// Grafikkarte ohne Shader-Unterstützung – dann eben ohne Weichzeichnen
		}
	}

	/** Mausrad: amount > 0 = nach oben */
	protected void onScrolled(double amount) {}

	/** button: 0 = links, 1 = rechts */
	protected void onMouseClicked(int mouseX, int mouseY, int button) {}

	protected void onMouseDragged(int mouseX, int mouseY, int button, double dx, double dy) {}

	protected void onMouseReleased(int mouseX, int mouseY, int button) {}

	/** Tastatur: character = getipptes Zeichen (oder 0), code = Taste (Codes siehe Mv.KEY_…) */
	protected void onKey(char character, int code) {}

	public void onClose() {
		client.setScreen(null);
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}

	// ---------- Brücke zu Minecraft ----------

	//#if MC >= 1.13
	//$$ @Override
	//$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
	//$$ 	onMouseClicked((int) mouseX, (int) mouseY, button);
	//$$ 	return true;
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
	//$$ 	onMouseDragged((int) mouseX, (int) mouseY, button, dx, dy);
	//$$ 	return true;
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
	//$$ 	onMouseReleased((int) mouseX, (int) mouseY, button);
	//$$ 	return true;
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseScrolled(double amount) {
	//$$ 	onScrolled(amount);
	//$$ 	return true;
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean keyPressed(int key, int scanCode, int modifiers) {
	//$$ 	if (key == Mv.KEY_ESCAPE) onClose();
	//$$ 	else onKey((char) 0, key);
	//$$ 	return true;
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean charTyped(char character, int modifiers) {
	//$$ 	onKey(character, 0);
	//$$ 	return true;
	//$$ }
	//#else
	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) onScrolled(wheel > 0 ? 1 : -1);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		lastDragX = mouseX;
		lastDragY = mouseY;
		onMouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseDragged(int mouseX, int mouseY, int button, long time) {
		onMouseDragged(mouseX, mouseY, button, mouseX - lastDragX, mouseY - lastDragY);
		lastDragX = mouseX;
		lastDragY = mouseY;
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		onMouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected void keyPressed(char character, int code) {
		if (code == Mv.KEY_ESCAPE) {
			onClose();
			return;
		}
		onKey(character, code);
	}
	//#endif
}
