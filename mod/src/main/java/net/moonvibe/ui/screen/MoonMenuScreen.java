package net.moonvibe.ui.screen;

import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.module.Modules;
import net.moonvibe.module.hud.HudModule;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Das moonvibe-Menü (Rechte Shift): HUD-Module mit der Maus verschieben (mit Einrasten),
 * Rechtsklick öffnet die Einstellungen, in der Mitte geht es zu allen Mods und Cosmetics.
 */
public class MoonMenuScreen extends MoonScreen {
	private static final Identifier LOGO = Moonvibe.id("textures/gui/title.png");
	private static final int SNAP = 4;
	private static final int MARGIN = 3;

	private @Nullable HudModule dragging;
	private float dragOffX;
	private float dragOffY;
	private final List<float[]> guides = new ArrayList<>(); // {0=senkrecht/1=waagerecht, Position}
	private float modsX, modsY, modsW, modsH, cosX;

	public MoonMenuScreen() {
		super(Component.translatable("moonvibe.menu.title"));
		Anim.set("menu.open", 0);
	}

	private List<HudModule> visibleModules() {
		return Modules.HUD.stream().filter(HudModule::enabled).toList();
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float delta) {
		float open = Anim.easeOut(Anim.approach("menu.open", 1, 10));
		Draw.blur(g);
		Draw.fillGradient(g, 0, 0, width, height, Draw.alpha(0x99060910, open), Draw.alpha(0xCC060910, open));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float open = Anim.easeOut(Anim.approach("menu.open", 1, 10));
		HudModule hovered = dragging != null ? dragging : moduleAt(mouseX, mouseY);

		// Einrast-Linien beim Ziehen
		for (float[] guide : guides) {
			if (guide[0] == 0) Draw.fill(g, (int) guide[1], 0, (int) guide[1] + 1, height, Draw.alpha(Theme.ACCENT, 0.8f));
			else Draw.fill(g, 0, (int) guide[1], width, (int) guide[1] + 1, Draw.alpha(Theme.ACCENT, 0.8f));
		}

		for (HudModule module : visibleModules()) {
			float x = module.x(minecraft, width);
			float y = module.y(minecraft, height);
			float w = module.scaledWidth(minecraft);
			float h = module.scaledHeight(minecraft);
			float hover = Anim.approach("menu.hover." + module.id, module == hovered ? 1 : 0, 16);
			Draw.rect(g, x - 2, y - 2, w + 4, h + 4, 4, Draw.alpha(Theme.ACCENT, 0.18f * hover));
			module.renderAt(g, minecraft, width, height);
			Draw.outline(g, x - 2, y - 2, w + 4, h + 4, 4, 1, Draw.mix(0x55FFFFFF, Theme.ACCENT, hover));
			if (hover > 0.01f) {
				Component name = Draw.ui(module.name());
				float tw = Draw.width(name) + 10;
				float ty = y - 16 < 2 ? y + h + 4 : y - 16;
				Draw.rect(g, x - 2, ty, tw, 12, 3, Draw.alpha(Theme.ACCENT, hover));
				Draw.text(g, name, x + 3, ty + 2, Draw.alpha(0xFFFFFFFF, hover));
			}
		}

		// Mitte: Logo + Knöpfe
		float cx = width / 2f;
		float logoW = Math.min(230, width * 0.38f);
		float logoH = logoW * 341f / 1024f;
		float slide = (1 - open) * 12;
		float logoY = height * 0.5f - logoH - 6 + slide;
		Draw.image(g, LOGO, Math.round(cx - logoW / 2), Math.round(logoY), Math.round(logoW), Math.round(logoH), 1024, 341, open);

		// zwei Knöpfe nebeneinander: Mods (blau) und Cosmetics (gold)
		Component mods = Draw.bold(Component.translatable("moonvibe.menu.mods"));
		Component cosmetics = Draw.bold(Component.translatable("moonvibe.menu.cosmetics"));
		modsW = Math.max(110, Math.max(Draw.width(mods), Draw.width(cosmetics)) + 36);
		modsH = 24;
		modsX = cx - modsW - 4;
		modsY = height * 0.5f + 6 + slide;
		cosX = cx + 4;
		boolean overMods = inside(mouseX, mouseY, modsX, modsY, modsW, modsH) && dragging == null;
		boolean overCos = inside(mouseX, mouseY, cosX, modsY, modsW, modsH) && dragging == null;
		float h = Anim.approach("menu.mods", overMods ? 1 : 0, 16);
		float hc = Anim.approach("menu.cos", overCos ? 1 : 0, 16);
		Draw.gradient(g, modsX, modsY - h, modsW, modsH, 6, Draw.alpha(Draw.mix(Theme.ACCENT, Theme.ACCENT_LIGHT, h), open), Draw.alpha(Draw.mix(0xFF1F7FE0, Theme.ACCENT, h), open));
		Draw.centered(g, mods, modsX + modsW / 2, modsY - h + 8, 1f, Draw.alpha(0xFFFFFFFF, open));
		Draw.gradient(g, cosX, modsY - hc, modsW, modsH, 6, Draw.alpha(Draw.mix(0xFFFFC94A, 0xFFFFDA7A, hc), open), Draw.alpha(Draw.mix(0xFFF08C00, 0xFFFFB627, hc), open));
		Draw.centered(g, cosmetics, cosX + modsW / 2, modsY - hc + 8, 1f, Draw.alpha(0xFF1D1300, open));

		Component hint = Draw.ui(Component.translatable("moonvibe.menu.hint"));
		Draw.centered(g, hint, cx, modsY + modsH + 10, 1f, Draw.alpha(Theme.MUTED, open));
		Draw.text(g, Draw.ui("Moonvibe " + Moonvibe.VERSION), 6, height - 13, Draw.alpha(0x66FFFFFF, open));
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private @Nullable HudModule moduleAt(double mx, double my) {
		List<HudModule> list = visibleModules();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (inside(mx, my, m.x(minecraft, width) - 2, m.y(minecraft, height) - 2, m.scaledWidth(minecraft) + 4, m.scaledHeight(minecraft) + 4)) return m;
		}
		return null;
	}

	@Override
	protected boolean onMouseClicked(double mx, double my, int button) {
		if (button == 0 && inside(mx, my, modsX, modsY, modsW, modsH)) {
			Mc.setScreen(new ModsScreen(this, null));
			return true;
		}
		if (button == 0 && inside(mx, my, cosX, modsY, modsW, modsH)) {
			Mc.setScreen(new CosmeticsScreen(this));
			return true;
		}
		HudModule module = moduleAt(mx, my);
		if (module == null) return false;
		if (button == 1) {
			Mc.setScreen(new ModsScreen(this, module));
			return true;
		}
		if (button == 0) {
			dragging = module;
			dragOffX = (float) mx - module.x(minecraft, width);
			dragOffY = (float) my - module.y(minecraft, height);
			return true;
		}
		return false;
	}

	@Override
	protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging == null) return false;
		float w = dragging.scaledWidth(minecraft);
		float h = dragging.scaledHeight(minecraft);
		float x = (float) mx - dragOffX;
		float y = (float) my - dragOffY;
		guides.clear();

		// Einrasten: Bildschirmränder, Mitte und Kanten anderer Module
		List<Float> xTargets = new ArrayList<>(List.of((float) MARGIN, width / 2f, (float) width - MARGIN));
		List<Float> yTargets = new ArrayList<>(List.of((float) MARGIN, height / 2f, (float) height - MARGIN));
		for (HudModule other : visibleModules()) {
			if (other == dragging) continue;
			float ox = other.x(minecraft, width);
			float oy = other.y(minecraft, height);
			xTargets.add(ox);
			xTargets.add(ox + other.scaledWidth(minecraft));
			yTargets.add(oy);
			yTargets.add(oy + other.scaledHeight(minecraft));
		}
		x = snap(x, w, xTargets, 0);
		y = snap(y, h, yTargets, 1);
		dragging.setPosition(minecraft, x, y, width, height);
		return true;
	}

	/** Rastet linke Kante, Mitte oder rechte Kante an das nächste Ziel ein. */
	private float snap(float pos, float size, List<Float> targets, int axis) {
		float best = SNAP + 1;
		float result = pos;
		float guide = -1;
		float[] edges = {0, size / 2f, size};
		for (float target : targets) {
			for (float edge : edges) {
				float d = Math.abs(pos + edge - target);
				if (d < best) {
					best = d;
					result = target - edge;
					guide = target;
				}
			}
		}
		if (guide >= 0) guides.add(new float[] {axis, guide});
		return result;
	}

	@Override
	protected boolean onMouseReleased(double mx, double my, int button) {
		if (dragging == null) return false;
		dragging = null;
		guides.clear();
		Modules.save();
		return true;
	}

	@Override
	protected boolean onKeyPressed(int key, int scancode) {
		if (!matches(Moonvibe.menuKey, key, scancode)) return false;
		onClose();
		return true;
	}

	@Override
	public void onClose() {
		Modules.save();
		super.onClose();
	}
}
