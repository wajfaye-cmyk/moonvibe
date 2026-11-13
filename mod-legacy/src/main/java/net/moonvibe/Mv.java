package net.moonvibe;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
//#if MC >= 1.13
//$$ import net.minecraft.class_4107;
//$$ import net.minecraft.class_4277;
//$$ import org.lwjgl.glfw.GLFW;
//#else
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
//#endif

/**
 * Kurze Helfer für die alten Minecraft-Versionen (Fenster, Maus, Tasten, Übersetzungen, Ressourcen, Gegenstände).
 * Was sich zwischen 1.8.9 und 1.13.2 unterscheidet, steht möglichst hier.
 *
 * 1.13 hat neue Eingabe (GLFW statt LWJGL 2) und in den Legacy-Yarn-Namen viele noch unbenannte Teile
 * (method_…/field_…) – deren Bedeutung steht jeweils daneben.
 */
public final class Mv {
	//#if MC >= 1.13
	//$$ public static final int KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
	//$$ public static final int KEY_BACKSPACE = GLFW.GLFW_KEY_BACKSPACE;
	//$$ public static final int KEY_C = GLFW.GLFW_KEY_C;
	//$$ public static final int KEY_RSHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT;
	//#else
	public static final int KEY_ESCAPE = Keyboard.KEY_ESCAPE;
	public static final int KEY_BACKSPACE = Keyboard.KEY_BACK;
	public static final int KEY_C = Keyboard.KEY_C;
	public static final int KEY_RSHIFT = Keyboard.KEY_RSHIFT;
	//#endif

	private Mv() {}

	public static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	/** Laufende Minecraft-Version, z. B. "1.12.2" */
	public static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}

	public static Identifier id(String path) {
		return new Identifier("moonvibe", path);
	}

	/** Übersetzter Text (Sprachdateien in assets/moonvibe/lang). */
	public static String tr(String key, Object... args) {
		return I18n.translate(key, args);
	}

	// ---------- Fenster und Maus ----------

	/** Breite des Bildschirms in GUI-Pixeln */
	public static int width() {
		//#if MC >= 1.13
		//$$ return mc().field_19944.method_18321(); // window.getScaledWidth()
		//#else
		return (int) Math.ceil(new Window(mc()).getScaledWidth());
		//#endif
	}

	public static int height() {
		//#if MC >= 1.13
		//$$ return mc().field_19944.method_18322(); // window.getScaledHeight()
		//#else
		return (int) Math.ceil(new Window(mc()).getScaledHeight());
		//#endif
	}

	/** Echte Bildschirmpixel pro GUI-Pixel */
	public static int guiScale() {
		//#if MC >= 1.13
		//$$ return (int) mc().field_19944.method_18325(); // window.getScaleFactor()
		//#else
		return new Window(mc()).getScaleFactor();
		//#endif
	}

	/** Fenstergröße in echten Pixeln */
	public static int displayWidth() {
		//#if MC >= 1.13
		//$$ return mc().field_19944.method_18317(); // window.getFramebufferWidth()
		//#else
		return mc().width;
		//#endif
	}

	public static int displayHeight() {
		//#if MC >= 1.13
		//$$ return mc().field_19944.method_18318(); // window.getFramebufferHeight()
		//#else
		return mc().height;
		//#endif
	}

	/** Maus in GUI-Pixeln */
	public static int mouseX() {
		//#if MC >= 1.13
		//$$ return (int) (mc().field_19945.method_18249() * width() / displayWidth()); // mouse.getX()
		//#else
		return Mouse.getX() * width() / mc().width;
		//#endif
	}

	public static int mouseY() {
		//#if MC >= 1.13
		//$$ return (int) (mc().field_19945.method_18250() * height() / displayHeight()); // mouse.getY() (von oben)
		//#else
		return height() - Mouse.getY() * height() / mc().height - 1;
		//#endif
	}

	/** Minecrafts GameRenderer (für die Shader, siehe GameRendererAccessor) */
	public static Object gameRenderer() {
		//#if MC >= 1.13
		//$$ return mc().field_3818; // gameRenderer
		//#else
		return mc().gameRenderer;
		//#endif
	}

	/** HUD mit F1 ausgeblendet? */
	public static boolean hudHidden() {
		//#if MC >= 1.13
		//$$ return mc().options.field_19987; // hudHidden
		//#else
		return mc().options.hudHidden;
		//#endif
	}

	// ---------- Tastatur ----------

	/** Text aus der Zwischenablage */
	public static String clipboard() {
		//#if MC >= 1.13
		//$$ return mc().field_19946.method_18177(); // keyboard.getClipboard()
		//#else
		return Screen.getClipboard();
		//#endif
	}

	/** Gedrückt gehaltene Tasten wiederholt melden (z. B. Löschen im Suchfeld) */
	public static void repeatKeys(boolean on) {
		//#if MC >= 1.13
		//$$ mc().field_19946.method_18191(on); // keyboard.enableRepeatEvents
		//#else
		Keyboard.enableRepeatEvents(on);
		//#endif
	}

	/** Gehört die gedrückte Taste (Code aus dem Menü) zu dieser Tastenbelegung? */
	public static boolean matches(KeyBinding binding, int code) {
		//#if MC >= 1.13
		//$$ return binding.method_18166(code, 0); // matchesKey(keyCode, scanCode)
		//#else
		return code == binding.getCode();
		//#endif
	}

	/** Ist die Taste dieser Belegung gerade wirklich gedrückt (egal, was Minecraft gerade annimmt)? */
	public static boolean physicallyDown(KeyBinding binding) {
		//#if MC >= 1.13
		//$$ class_4107.class_4108 key = binding.method_18172(); // getBoundKey()
		//$$ long window = mc().field_19944.method_18315(); // window.getHandle()
		//$$ int code = key.method_18159(); // getKeyCode()
		//$$ if (code < 0) return false;
		//$$ if (key.method_18158() == class_4107.class_4109.MOUSE) return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
		//$$ return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
		//#else
		int code = binding.getCode();
		if (code < 0) return Mouse.isButtonDown(code + 100);
		return code > 0 && Keyboard.isKeyDown(code);
		//#endif
	}

	/** Minecraft eine gedrückte bzw. losgelassene Taste melden */
	public static void setPressed(KeyBinding binding, boolean down) {
		//#if MC >= 1.13
		//$$ KeyBinding.method_18168(binding.method_18172(), down); // setKeyPressed(getBoundKey(), down)
		//#else
		KeyBinding.setKeyPressed(binding.getCode(), down);
		//#endif
	}

	// ---------- Texturen ----------

	/** Bild als Textur (ab 1.13 braucht Minecraft dafür ein eigenes Bildformat) */
	public static NativeImageBackedTexture texture(BufferedImage image) {
		//#if MC >= 1.13
		//$$ class_4277 pixels = new class_4277(image.getWidth(), image.getHeight(), true); // NativeImage
		//$$ for (int y = 0; y < image.getHeight(); y++) {
		//$$ 	for (int x = 0; x < image.getWidth(); x++) {
		//$$ 		int argb = image.getRGB(x, y);
		//$$ 		// NativeImage speichert ABGR
		//$$ 		pixels.method_19460(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF)); // setPixelRgba
		//$$ 	}
		//$$ }
		//$$ return new NativeImageBackedTexture(pixels);
		//#else
		return new NativeImageBackedTexture(image);
		//#endif
	}

	// ---------- Gegenstände (vor 1.11 sind leere Plätze null, danach ItemStack.EMPTY) ----------

	public static boolean isEmpty(ItemStack stack) {
		//#if MC >= 1.11
		//$$ return stack == null || stack.isEmpty();
		//#else
		return stack == null;
		//#endif
	}

	public static int count(ItemStack stack) {
		//#if MC >= 1.11
		//$$ return stack.getCount();
		//#else
		return stack.count;
		//#endif
	}

	/** Getragene Rüstung: 0 = Schuhe … 3 = Helm */
	public static ItemStack armor(int slot) {
		//#if MC >= 1.11
		//$$ return mc().player.inventory.field_15083.get(slot); // "armor" – in den Legacy-Yarn-Namen noch unbenannt
		//#else
		return mc().player.inventory.armor[slot];
		//#endif
	}

	/** Die 36 Plätze des Inventars (Hotbar + Rucksack) */
	public static List<ItemStack> mainInventory() {
		//#if MC >= 1.11
		//$$ return mc().player.inventory.field_15082; // "main"
		//#else
		return Arrays.asList(mc().player.inventory.main);
		//#endif
	}
}
