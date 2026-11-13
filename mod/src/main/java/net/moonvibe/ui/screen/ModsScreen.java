package net.moonvibe.ui.screen;

import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.module.Module;
import net.moonvibe.module.Modules;
import net.moonvibe.module.hud.HudModule;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.ChoiceSetting;
import net.moonvibe.module.setting.ColorSetting;
import net.moonvibe.module.setting.Setting;
import net.moonvibe.module.setting.SliderSetting;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Übersicht aller Module als Karten (wie bei Lunar) + Einstellungsseite pro Modul. */
public class ModsScreen extends MoonScreen {
	private static final Identifier MARK = Moonvibe.id("textures/gui/mark.png");
	private static final int HEADER = 38;
	private static final int SIDEBAR = 104;
	private static final int CARD_H = 96;
	private static final int GAP = 8;
	private static final int ROW_H = 28;

	private final Screen parent;
	private Module.@Nullable Category filter;
	private @Nullable Module selected;
	private String search = "";
	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private EditBox searchBox;

	// Klickflächen, die beim Zeichnen gesammelt werden
	private record Hit(float x, float y, float w, float h, Runnable click, @Nullable DoubleConsumer drag) {}

	private final List<Hit> hits = new ArrayList<>();
	private @Nullable Hit activeDrag;

	private float px, py, pw, ph;

	public ModsScreen(Screen parent, @Nullable Module selected) {
		super(Component.translatable("moonvibe.menu.mods"));
		this.parent = parent;
		this.selected = selected;
		Anim.set("mods.open", 0);
		Anim.set("mods.scroll", 0);
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 500);
		ph = Math.min(height - 24, 310);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		searchBox = searchBox(Math.round(px + pw - 196), Math.round(py + 13), 150, "moonvibe.mods.search", search, s -> {
			search = s;
			scrollTarget = 0;
			selected = null;
		});
	}

	private boolean over(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Runnable click) {
		hits.add(new Hit(x, y, w, h, click, null));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		hits.clear();
		float open = Anim.easeOut(Anim.approach("mods.open", 1, 12));
		Draw.push(g);
		Draw.translate(g, 0, (1 - open) * 10);

		// Fenster
		Draw.rect(g, px, py, pw, ph, 10, Theme.PANEL);
		Draw.outline(g, px, py, pw, ph, 10, 1, Theme.BORDER);

		// Kopfzeile
		Draw.image(g, MARK, Math.round(px + 12), Math.round(py + 9), 20, 20, 128, 128, 1);
		Draw.text(g, Draw.bold("Moonvibe"), px + 38, py + 11, 1.15f, Theme.TEXT);
		Draw.text(g, Draw.ui(Component.translatable("moonvibe.menu.mods")), px + 40 + Draw.width(Draw.bold("Moonvibe")) * 1.15f + 6, py + 13, Theme.MUTED);
		Draw.rect(g, px + pw - 202, py + 9, 170, 20, 6, Theme.CONTROL);
		drawSearchHint(g, searchBox, "moonvibe.mods.search");
		float close = Anim.approach("mods.close", over(mouseX, mouseY, px + pw - 28, py + 9, 20, 20) ? 1 : 0, 16);
		Draw.rect(g, px + pw - 28, py + 9, 20, 20, 6, Draw.mix(Theme.CONTROL, Theme.DANGER, close));
		Draw.centered(g, Draw.bold("×"), px + pw - 18, py + 13, 1.4f, Theme.TEXT);
		hit(px + pw - 28, py + 9, 20, 20, this::onClose);
		Draw.fill(g, Math.round(px), Math.round(py + HEADER), Math.round(px + pw), Math.round(py + HEADER + 1), Theme.BORDER);

		drawSidebar(g, mouseX, mouseY);

		float cx = px + SIDEBAR + 12;
		float cy = py + HEADER + 10;
		float cw = pw - SIDEBAR - 24;
		float ch = ph - HEADER - 20;
		scroll = Anim.approach("mods.scroll", scrollTarget, 26);
		Draw.scissor(g, Math.round(cx - 2), Math.round(cy - 2), Math.round(cx + cw + 2), Math.round(cy + ch + 2));
		if (selected == null) drawCards(g, mouseX, mouseY, cx, cy - scroll, cw, cy, ch);
		else drawSettings(g, mouseX, mouseY, selected, cx, cy - scroll, cw, cy, ch);
		Draw.endScissor(g);
		drawScrollbar(g, cx + cw + 6, cy, ch);

		Draw.pop(g);
	}

	// ---------- Seitenleiste ----------

	private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float x = px + 8;
		float y = py + HEADER + 10;
		Draw.fill(g, Math.round(px + SIDEBAR), Math.round(py + HEADER + 1), Math.round(px + SIDEBAR + 1), Math.round(py + ph), Theme.BORDER);
		Object[][] entries = {
			{null, "moonvibe.mods.all"},
			{Module.Category.HUD, "moonvibe.mods.hud"},
			{Module.Category.MECHANIC, "moonvibe.mods.mechanic"},
			{Module.Category.CLIENT, "moonvibe.mods.client"},
		};
		for (Object[] entry : entries) {
			Module.Category cat = (Module.Category) entry[0];
			boolean active = filter == cat && selected == null;
			float hover = Anim.approach("mods.cat." + entry[1], active ? 1 : over(mouseX, mouseY, x, y, SIDEBAR - 16, 22) ? 0.5f : 0, 16);
			Draw.rect(g, x, y, SIDEBAR - 16, 22, 6, Draw.alpha(Theme.CONTROL, hover));
			if (active) Draw.rect(g, x, y + 5, 3, 12, 1.5f, Theme.ACCENT);
			Draw.text(g, Draw.ui(Component.translatable((String) entry[1])), x + 10, y + 7, active ? Theme.TEXT : Draw.mix(Theme.MUTED, Theme.TEXT, hover));
			hit(x, y, SIDEBAR - 16, 22, () -> {
				filter = cat;
				selected = null;
				scrollTarget = 0;
			});
			y += 25;
		}

		// unten: zurück zum HUD-Editor
		float by = py + ph - 32;
		boolean overEdit = over(mouseX, mouseY, x, by, SIDEBAR - 16, 22);
		float e = Anim.approach("mods.edit", overEdit ? 1 : 0, 16);
		Draw.rect(g, x, by, SIDEBAR - 16, 22, 6, Draw.mix(Theme.CONTROL, Theme.ACCENT, e));
		Draw.centered(g, Draw.ui(Component.translatable("moonvibe.mods.edit_hud")), x + (SIDEBAR - 16) / 2f, by + 7, 1f, Theme.TEXT);
		hit(x, by, SIDEBAR - 16, 22, () -> Mc.setScreen(new MoonMenuScreen()));
	}

	// ---------- Karten ----------

	private List<Module> visibleModules() {
		String q = search.trim().toLowerCase(Locale.ROOT);
		return Modules.ALL.stream()
			.filter(m -> filter == null || m.category == filter)
			.filter(m -> q.isEmpty() || m.name().getString().toLowerCase(Locale.ROOT).contains(q))
			.toList();
	}

	private void drawCards(GuiGraphicsExtractor g, int mouseX, int mouseY, float x0, float y0, float cw, float clipY, float clipH) {
		List<Module> list = visibleModules();
		int cols = Math.max(2, (int) ((cw + GAP) / (112 + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		boolean mouseInContent = mouseY >= clipY && mouseY < clipY + clipH;

		if (list.isEmpty()) {
			Draw.centered(g, Draw.ui(Component.translatable("moonvibe.mods.empty")), x0 + cw / 2, y0 + 40, 1f, Theme.MUTED);
		}
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			float x = x0 + (i % cols) * (cardW + GAP);
			float y = y0 + (i / cols) * (CARD_H + GAP);
			boolean hovered = mouseInContent && over(mouseX, mouseY, x, y, cardW, CARD_H);
			float hover = Anim.approach("mods.card." + m.id, hovered ? 1 : 0, 16);
			Draw.rect(g, x, y, cardW, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
			Draw.outline(g, x, y, cardW, CARD_H, 8, 1, Draw.mix(Theme.BORDER, Draw.alpha(Theme.ACCENT, 0.6f), hover));

			// Symbol
			Draw.push(g);
			Draw.translate(g, x + cardW / 2 - 12, y + 12 - hover * 1.5f);
			Draw.scale(g, 1.5f);
			Draw.item(g, m.icon(), 0, 0);
			Draw.pop(g);
			Draw.centeredFit(g, Draw.ui(m.name()), x + cardW / 2, y + 44, cardW - 12, 1f, Theme.TEXT);

			// Einstellungen-Knopf (oben rechts)
			if (!m.settings().isEmpty()) {
				float sx = x + cardW - 20;
				float sy = y + 6;
				boolean overSettings = mouseInContent && over(mouseX, mouseY, sx, sy, 14, 14);
				float s = Anim.approach("mods.gear." + m.id, overSettings ? 1 : 0, 16);
				Draw.rect(g, sx, sy, 14, 14, 4, Draw.alpha(Theme.CONTROL, 0.5f + s * 0.5f));
				drawSlidersIcon(g, sx + 3, sy + 3.5f, Draw.mix(Theme.MUTED, Theme.TEXT, s));
				if (mouseInContent) hit(sx, sy, 14, 14, () -> openSettings(m));
			}

			// An/Aus-Leiste unten
			float bx = x + 8;
			float by = y + CARD_H - 26;
			float bw = cardW - 16;
			float on = Anim.approach("mods.on." + m.id, m.enabled() ? 1 : 0, 14);
			Draw.gradient(g, bx, by, bw, 18, 5, Draw.mix(Theme.CONTROL, Theme.ACCENT, on), Draw.mix(Theme.CONTROL, 0xFF1F7FE0, on));
			Component state = Draw.bold(Component.translatable(m.enabled() ? "moonvibe.mods.enabled" : "moonvibe.mods.disabled"));
			Draw.centered(g, state, bx + bw / 2, by + 5, 1f, Draw.mix(Theme.MUTED, 0xFFFFFFFF, on));
			if (mouseInContent) {
				hit(bx, by, bw, 18, () -> {
					m.toggle();
					Modules.save();
				});
				// Klick auf die Karte selbst öffnet die Einstellungen
				if (!m.settings().isEmpty()) hit(x, y, cardW, CARD_H - 28, () -> openSettings(m));
			}
		}
		int rows = (list.size() + cols - 1) / cols;
		contentHeight = rows * (CARD_H + GAP) - GAP;
	}

	private void drawSlidersIcon(GuiGraphicsExtractor g, float x, float y, int color) {
		Draw.rect(g, x, y + 1, 8, 1, 0, color);
		Draw.rect(g, x, y + 5, 8, 1, 0, color);
		Draw.circle(g, x + 5.5f, y + 1.5f, 1.5f, color);
		Draw.circle(g, x + 2.5f, y + 5.5f, 1.5f, color);
	}

	private void openSettings(Module m) {
		selected = m;
		scrollTarget = 0;
		scroll = 0;
		Anim.set("mods.scroll", 0);
	}

	// ---------- Einstellungen eines Moduls ----------

	private void drawSettings(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x0, float y0, float cw, float clipY, float clipH) {
		boolean mouseInContent = mouseY >= clipY && mouseY < clipY + clipH;
		float y = y0;

		// Zurück + Titel + Schalter
		boolean overBack = mouseInContent && over(mouseX, mouseY, x0, y, 22, 22);
		float b = Anim.approach("mods.back", overBack ? 1 : 0, 16);
		Draw.rect(g, x0, y, 22, 22, 6, Draw.mix(Theme.CONTROL, Theme.CARD_HOVER, b));
		Draw.centered(g, Draw.bold("‹"), x0 + 11, y + 6, 1.4f, Theme.TEXT);
		if (mouseInContent) hit(x0, y, 22, 22, () -> {
			selected = null;
			scrollTarget = 0;
		});
		Draw.item(g, m.icon(), Math.round(x0 + 30), Math.round(y + 3));
		Draw.text(g, Draw.bold(m.name()), x0 + 52, y + 1, 1.2f, Theme.TEXT);
		Draw.text(g, Draw.ellipsize(Draw.ui(m.description()), (int) cw - 110), x0 + 52, y + 14, Theme.MUTED);
		toggle(g, mouseInContent, "enabled." + m.id, x0 + cw - 30, y + 4, m.enabled(), () -> {
			m.toggle();
			Modules.save();
		});
		y += 34;

		for (Setting<?> setting : m.settings()) {
			Draw.rect(g, x0, y, cw, ROW_H - 4, 6, Theme.CARD);
			Draw.text(g, Draw.ui(setting.label()), x0 + 10, y + 8, Theme.TEXT);
			float right = x0 + cw - 10;
			if (setting instanceof BoolSetting bool) {
				toggle(g, mouseInContent, m.id + "." + bool.id, right - 26, y + 5.5f, bool.on(), () -> {
					bool.toggle();
					Modules.save();
				});
			} else if (setting instanceof SliderSetting slider) {
				slider(g, mouseInContent, slider, right - 120, y, 120);
			} else if (setting instanceof ColorSetting color) {
				swatches(g, mouseInContent, color, right, y);
			} else if (setting instanceof ChoiceSetting choice) {
				segments(g, mouseX, mouseY, mouseInContent, choice, right, y);
			}
			y += ROW_H;
		}

		if (m instanceof HudModule hud) {
			Component reset = Draw.ui(Component.translatable("moonvibe.mods.reset_position"));
			float w = Draw.width(reset) + 20;
			boolean overReset = mouseInContent && over(mouseX, mouseY, x0, y + 4, w, 20);
			float r = Anim.approach("mods.reset", overReset ? 1 : 0, 16);
			Draw.rect(g, x0, y + 4, w, 20, 6, Draw.mix(Theme.CONTROL, Theme.CARD_HOVER, r));
			Draw.text(g, reset, x0 + 10, y + 10, Theme.TEXT);
			if (mouseInContent) hit(x0, y + 4, w, 20, () -> {
				hud.resetPosition();
				Modules.save();
			});
			y += 28;
		}
		contentHeight = y - y0;
	}

	private void toggle(GuiGraphicsExtractor g, boolean active, String id, float x, float y, boolean on, Runnable click) {
		float t = Anim.approach("mods.toggle." + id, on ? 1 : 0, 18);
		Draw.rect(g, x, y, 26, 14, 7, Draw.mix(Theme.CONTROL, Theme.ACCENT, t));
		Draw.circle(g, x + 7 + t * 12, y + 7, 5, 0xFFFFFFFF);
		if (active) hit(x - 2, y - 2, 30, 18, click);
	}

	private void slider(GuiGraphicsExtractor g, boolean active, SliderSetting s, float x, float rowY, float w) {
		Component value = Draw.ui(s.display());
		Draw.text(g, value, x - Draw.width(value) - 8, rowY + 8, Theme.MUTED);
		float ty = rowY + 11;
		Draw.rect(g, x, ty, w, 3, 1.5f, Theme.CONTROL);
		float f = (float) s.fraction();
		Draw.rect(g, x, ty, w * f, 3, 1.5f, Theme.ACCENT);
		Draw.circle(g, x + w * f, ty + 1.5f, 5, 0xFFFFFFFF);
		if (active) {
			hits.add(new Hit(x - 6, rowY, w + 12, ROW_H - 4, () -> {}, frac -> s.setFraction((frac * (w + 12) - 6) / w)));
		}
	}

	private void swatches(GuiGraphicsExtractor g, boolean active, ColorSetting setting, float right, float rowY) {
		int[] palette = ColorSetting.PALETTE;
		float size = 10;
		float x = right - palette.length * (size + 4) + 4;
		for (int color : palette) {
			float cx = x + size / 2;
			float cy = rowY + 12;
			if (setting.color() == color) Draw.circle(g, cx, cy, size / 2 + 2, Theme.ACCENT_LIGHT);
			Draw.circle(g, cx, cy, size / 2, color);
			if (active) hit(x, rowY + 6, size, size + 2, () -> {
				setting.set(color);
				Modules.save();
			});
			x += size + 4;
		}
	}

	/** Auswahl als nebeneinanderliegende Knöpfe (das gewählte ist blau). */
	private void segments(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean active, ChoiceSetting setting, float right, float rowY) {
		float total = 0;
		float[] widths = new float[setting.choices.size()];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = Draw.width(Draw.ui(ChoiceSetting.choiceLabel(setting.choices.get(i)))) + 14;
			total += widths[i];
		}
		float x = right - total - 4;
		Draw.rect(g, x - 2, rowY + 3, total + 4, 18, 6, Theme.CONTROL);
		for (int i = 0; i < widths.length; i++) {
			String choice = setting.choices.get(i);
			boolean selected = setting.is(choice);
			boolean over = active && over(mouseX, mouseY, x, rowY + 4, widths[i], 16);
			float t = Anim.approach("mods.choice." + setting.id + "." + choice, selected ? 1 : over ? 0.4f : 0, 16);
			Draw.rect(g, x, rowY + 4, widths[i], 16, 5, Draw.alpha(Theme.ACCENT, t));
			Draw.centered(g, Draw.ui(ChoiceSetting.choiceLabel(choice)), x + widths[i] / 2, rowY + 8, 1f, selected ? 0xFFFFFFFF : Theme.MUTED);
			if (active) hit(x, rowY + 4, widths[i], 16, () -> {
				setting.set(choice);
				Modules.save();
			});
			x += widths[i];
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor g, float x, float y, float h) {
		if (contentHeight <= h) return;
		float barH = Math.max(20, h * h / contentHeight);
		float barY = y + (h - barH) * (scroll / (contentHeight - h));
		Draw.rect(g, x, barY, 3, barH, 1.5f, 0x40FFFFFF);
	}

	// ---------- Eingaben ----------

	@Override
	protected boolean onMouseClicked(double mx, double my, int button) {
		if (button != 0) return false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (over(mx, my, hit.x, hit.y, hit.w, hit.h)) {
				if (hit.drag != null) {
					activeDrag = hit;
					hit.drag.accept((mx - hit.x) / hit.w);
				} else {
					hit.click.run();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
		if (activeDrag == null || activeDrag.drag == null) return false;
		activeDrag.drag.accept((mx - activeDrag.x) / activeDrag.w);
		return true;
	}

	@Override
	protected boolean onMouseReleased(double mx, double my, int button) {
		if (activeDrag == null) return false;
		activeDrag = null;
		Modules.save();
		return true;
	}

	@Override
	protected boolean onScrolled(double amount) {
		float visible = ph - HEADER - 20;
		scrollTarget = (float) Math.max(0, Math.min(Math.max(0, contentHeight - visible), scrollTarget - amount * 24));
		return true;
	}

	@Override
	public void onClose() {
		Modules.save();
		Mc.setScreen(parent);
	}
}
