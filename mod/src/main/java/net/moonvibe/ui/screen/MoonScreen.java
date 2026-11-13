package net.moonvibe.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 1.21.11
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif
//#if MC < 1.20.5
//$$ import net.minecraft.client.gui.screens.TitleScreen;
//$$ import net.minecraft.client.renderer.PanoramaRenderer;
//#endif
//#if MC < 1.20
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif

/**
 * Grundlage aller moonvibe-Menüs. Minecraft hat Zeichnen und Eingaben über die Versionen mehrfach umgebaut –
 * diese Klasse gleicht das aus, damit die Menüs selbst überall gleich bleiben.
 *
 * Maustasten kommen einheitlich an: 0 = links, 1 = rechts.
 */
public abstract class MoonScreen extends Screen {
	protected MoonScreen(Component title) {
		super(title);
	}

	/** Inhalt zeichnen (Knöpfe/Eingabefelder von Minecraft werden danach darüber gezeichnet). */
	protected abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

	/** Hintergrund: Welt weichgezeichnet und abgedunkelt. */
	protected void drawBackground(GuiGraphicsExtractor g, float delta) {
		Draw.blur(g);
		Draw.fill(g, 0, 0, width, height, Theme.BACKDROP);
	}

	/** Drehendes Minecraft-Panorama (für das Hauptmenü). */
	protected void drawPanorama(GuiGraphicsExtractor g, float delta) {
		//#if MC >= 1.20.5
		extractPanorama(g, delta);
		//#else
		//$$ PANORAMA.render(delta, 1f);
		//#endif
	}

	//#if MC < 1.20.5
	//$$ private static final PanoramaRenderer PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
	//#endif

	protected boolean onMouseClicked(double x, double y, int button) {
		return false;
	}

	protected boolean onMouseDragged(double x, double y, int button, double dx, double dy) {
		return false;
	}

	protected boolean onMouseReleased(double x, double y, int button) {
		return false;
	}

	/** Mausrad: amount > 0 = nach oben */
	protected boolean onScrolled(double amount) {
		return false;
	}

	protected boolean onKeyPressed(int key, int scancode) {
		return false;
	}

	/** Suchfeld ohne Rahmen im moonvibe-Stil (wird direkt als Widget hinzugefügt). */
	protected EditBox searchBox(int x, int y, int w, String hintKey, String value, Consumer<String> responder) {
		//#if MC >= 1.16
		EditBox box = new EditBox(font, x, y, w, 12, Component.translatable(hintKey));
		//#else
		//$$ EditBox box = new EditBox(font, x, y, w, 12, hintKey);
		//#endif
		box.setBordered(false);
		box.setTextColor(Theme.TEXT);
		//#if MC >= 1.19.4
		box.setHint(Draw.ui(Component.translatable(hintKey)).copy().withStyle(s -> s.withColor(Theme.MUTED)));
		//#endif
		box.setValue(value);
		box.setResponder(responder);
		addRenderableWidget(box);
		return box;
	}

	/** Platzhaltertext im leeren Suchfeld (ältere Versionen können das noch nicht selbst). */
	protected void drawSearchHint(GuiGraphicsExtractor g, EditBox box, String hintKey) {
		//#if MC < 1.19.4
		//$$ if (box.getValue().isEmpty() && !box.isFocused()) Draw.text(g, Draw.ui(Component.translatable(hintKey)), box.x, box.y, Theme.MUTED);
		//#endif
	}

	/** Passt die gedrückte Taste zur Tastenbelegung? */
	protected static boolean matches(KeyMapping mapping, int key, int scancode) {
		//#if MC >= 1.21.11
		return mapping.matches(new KeyEvent(key, scancode, 0));
		//#else
		//$$ return mapping.matches(key, scancode);
		//#endif
	}

	private static int button(int raw) {
		return raw == InputConstants.MOUSE_BUTTON_LEFT ? 0 : raw == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 2;
	}

	// ---------- Brücke zu Minecraft ----------

	//#if MC >= 1.21.11
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		drawBackground(g, delta);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		draw(g, mouseX, mouseY, delta);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		return onMouseClicked(event.x(), event.y(), button(event.button()));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (onMouseDragged(event.x(), event.y(), button(event.button()), dx, dy)) return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (onMouseReleased(event.x(), event.y(), button(event.button()))) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		//#if MC >= 26.3
		if (onKeyPressed(event.key(), event.keycode())) return true;
		//#else
		//$$ if (onKeyPressed(event.key(), event.scancode())) return true;
		//#endif
		return super.keyPressed(event);
	}
	//#else
	//#if MC >= 1.20
	//$$ // Hintergrund zeichnet render() selbst zuerst – Minecraft würde ihn sonst über den Inhalt legen
	//$$ @Override
	//$$ public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	//$$ }
	//$$
	//$$ @Override
	//$$ public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	//$$ 	drawBackground(g, delta);
	//$$ 	draw(g, mouseX, mouseY, delta);
	//$$ 	super.render(g, mouseX, mouseY, delta);
	//$$ }
	//#elseif MC >= 1.16
	//$$ @Override
	//$$ public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(pose);
	//$$ 	drawBackground(g, delta);
	//$$ 	draw(g, mouseX, mouseY, delta);
	//$$ 	super.render(pose, mouseX, mouseY, delta);
	//$$ }
	//#else
	//$$ @Override
	//$$ public void render(int mouseX, int mouseY, float delta) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//$$ 	drawBackground(g, delta);
	//$$ 	draw(g, mouseX, mouseY, delta);
	//$$ 	super.render(mouseX, mouseY, delta);
	//$$ }
	//#endif
	//$$
	//$$ @Override
	//$$ public boolean mouseClicked(double x, double y, int button) {
	//$$ 	if (super.mouseClicked(x, y, button)) return true;
	//$$ 	return onMouseClicked(x, y, button(button));
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
	//$$ 	if (onMouseDragged(x, y, button(button), dx, dy)) return true;
	//$$ 	return super.mouseDragged(x, y, button, dx, dy);
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseReleased(double x, double y, int button) {
	//$$ 	if (onMouseReleased(x, y, button(button))) return true;
	//$$ 	return super.mouseReleased(x, y, button);
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean keyPressed(int key, int scancode, int modifiers) {
	//$$ 	if (onKeyPressed(key, scancode)) return true;
	//$$ 	return super.keyPressed(key, scancode, modifiers);
	//$$ }
	//#endif

	//#if MC >= 1.20.2
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (onScrolled(scrollY)) return true;
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
	//#else
	//$$ @Override
	//$$ public boolean mouseScrolled(double x, double y, double amount) {
	//$$ 	if (onScrolled(amount)) return true;
	//$$ 	return super.mouseScrolled(x, y, amount);
	//$$ }
	//#endif

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
