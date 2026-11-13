package net.moonvibe.ui.screen;

import net.moonvibe.Mv;
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
import net.moonvibe.ui.SearchField;
import net.moonvibe.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

/** Übersicht aller Module als Karten (wie bei Lunar) + Einstellungsseite pro Modul. */
public class ModsScreen extends MoonScreen {
	private static final Identifier MARK = Mv.id("textures/gui/mark.png");
	private static final int HEADER = 38;
	private static final int SIDEBAR = 104;
	private static final int CARD_H = 96;
	private static final int GAP = 8;
	private static final int ROW_H = 28;

	private final Screen parent;
	private Module.Category filter;
	private Module selected;
	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private final SearchField searchBox;

	/** Klickfläche, die beim Zeichnen gesammelt wird (drag != null = Schieberegler) */
	private static final class Hit {
		final float x, y, w, h;
		final Runnable click;
		final DoubleConsumer drag;

		Hit(float x, float y, float w, float h, Runnable click, DoubleConsumer drag) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
			this.drag = drag;
		}
	}

	private final List<Hit> hits = new ArrayList<Hit>();
	private Hit activeDrag;

	private float px, py, pw, ph;

	public ModsScreen(Screen parent, Module selected) {
		this.parent = parent;
		this.selected = selected;
		this.searchBox = new SearchField(Mv.tr("moonvibe.mods.search"), s -> {
			scrollTarget = 0;
			this.selected = null;
		});
		Anim.set("mods.open", 0);
	}

	@Override
	public void init() {
		super.init();
		Mv.repeatKeys(true);
		pw = Math.min(width - 24, 500);
		ph = Math.min(height - 24, 310);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		searchBox.place(px + pw - 194, py + 15, 158);
	}

	private boolean over(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void hit(float x, float y, float w, float h, Runnable click) {
		hits.add(new Hit(x, y, w, h, click, null));
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		hits.clear();
		Draw.fill(0, 0, width, height, Theme.BACKDROP);
		float open = Anim.easeOut(Anim.approach("mods.open", 1, 12));
		Draw.push();
		Draw.translate(0, (1 - open) * 10);

		// Fenster
		Draw.rect(px, py, pw, ph, 10, Theme.PANEL);
		Draw.outline(px, py, pw, ph, 10, 1, Theme.BORDER);

		// Kopfzeile
		Draw.image(MARK, Math.round(px + 12), Math.round(py + 9), 20, 20, 1);
		Draw.text(Draw.bold("Moonvibe"), px + 38, py + 11, 1.15f, Theme.TEXT);
		Draw.text(Draw.tr("moonvibe.menu.mods"), px + 40 + Draw.width(Draw.bold("Moonvibe")) * 1.15f + 6, py + 13, Theme.MUTED);
		Draw.rect(px + pw - 202, py + 9, 170, 20, 6, Theme.CONTROL);
		searchBox.render();
		float close = Anim.approach("mods.close", over(mouseX, mouseY, px + pw - 28, py + 9, 20, 20) ? 1 : 0, 16);
		Draw.rect(px + pw - 28, py + 9, 20, 20, 6, Draw.mix(Theme.CONTROL, Theme.DANGER, close));
		Draw.centered(Draw.bold("✕"), px + pw - 18, py + 15, 1f, Theme.TEXT);
		hit(px + pw - 28, py + 9, 20, 20, this::onClose);
		Draw.fill(Math.round(px), Math.round(py + HEADER), Math.round(px + pw), Math.round(py + HEADER + 1), Theme.BORDER);

		drawSidebar(mouseX, mouseY);

		float cx = px + SIDEBAR + 12;
		float cy = py + HEADER + 10;
		float cw = pw - SIDEBAR - 24;
		float ch = ph - HEADER - 20;
		scroll += (scrollTarget - scroll) * 0.35f;
		float slide = (1 - open) * 10;
		Draw.scissor(cx - 2, cy - 2 + slide, cx + cw + 2, cy + ch + 2 + slide);
		if (selected == null) drawCards(mouseX, mouseY, cx, cy - scroll, cw, cy, ch);
		else drawSettings(mouseX, mouseY, selected, cx, cy - scroll, cw, cy, ch);
		Draw.endScissor();
		drawScrollbar(cx + cw + 6, cy, ch);

		Draw.pop();
	}

	// ---------- Seitenleiste ----------

	private void drawSidebar(int mouseX, int mouseY) {
		float x = px + 8;
		float y = py + HEADER + 10;
		Draw.fill(Math.round(px + SIDEBAR), Math.round(py + HEADER + 1), Math.round(px + SIDEBAR + 1), Math.round(py + ph), Theme.BORDER);
		Object[][] entries = {
			{null, "moonvibe.mods.all"},
			{Module.Category.HUD, "moonvibe.mods.hud"},
			{Module.Category.MECHANIC, "moonvibe.mods.mechanic"},
			{Module.Category.CLIENT, "moonvibe.mods.client"},
		};
		for (Object[] entry : entries) {
			final Module.Category cat = (Module.Category) entry[0];
			boolean active = filter == cat && selected == null;
			float hover = Anim.approach("mods.cat." + entry[1], active ? 1 : over(mouseX, mouseY, x, y, SIDEBAR - 16, 22) ? 0.5f : 0, 16);
			Draw.rect(x, y, SIDEBAR - 16, 22, 6, Draw.alpha(Theme.CONTROL, hover));
			if (active) Draw.rect(x, y + 5, 3, 12, 1.5f, Theme.ACCENT);
			Draw.text(Draw.tr((String) entry[1]), x + 10, y + 7, active ? Theme.TEXT : Draw.mix(Theme.MUTED, Theme.TEXT, hover));
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
		Draw.rect(x, by, SIDEBAR - 16, 22, 6, Draw.mix(Theme.CONTROL, Theme.ACCENT, e));
		Draw.centered(Draw.tr("moonvibe.mods.edit_hud"), x + (SIDEBAR - 16) / 2f, by + 7, 1f, Theme.TEXT);
		hit(x, by, SIDEBAR - 16, 22, () -> client.setScreen(new MoonMenuScreen()));
	}

	// ---------- Karten ----------

	private List<Module> visibleModules() {
		String q = searchBox.value().trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<Module>();
		for (Module m : Modules.ALL) {
			if (filter != null && m.category != filter) continue;
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(m);
		}
		return list;
	}

	private void drawCards(int mouseX, int mouseY, float x0, float y0, float cw, float clipY, float clipH) {
		List<Module> list = visibleModules();
		int cols = Math.max(2, (int) ((cw + GAP) / (112 + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		boolean mouseInContent = mouseY >= clipY && mouseY < clipY + clipH;

		if (list.isEmpty()) {
			Draw.centered(Draw.tr("moonvibe.mods.empty"), x0 + cw / 2, y0 + 40, 1f, Theme.MUTED);
		}
		for (int i = 0; i < list.size(); i++) {
			final Module m = list.get(i);
			float x = x0 + (i % cols) * (cardW + GAP);
			float y = y0 + (i / cols) * (CARD_H + GAP);
			boolean hovered = mouseInContent && over(mouseX, mouseY, x, y, cardW, CARD_H);
			float hover = Anim.approach("mods.card." + m.id, hovered ? 1 : 0, 16);
			Draw.rect(x, y, cardW, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
			Draw.outline(x, y, cardW, CARD_H, 8, 1, Draw.mix(Theme.BORDER, Draw.alpha(Theme.ACCENT, 0.6f), hover));

			// Symbol
			Draw.push();
			Draw.translate(x + cardW / 2 - 12, y + 12 - hover * 1.5f);
			Draw.scale(1.5f);
			Draw.item(m.icon(), 0, 0);
			Draw.pop();
			Draw.centeredFit(Draw.ui(m.name()), x + cardW / 2, y + 44, cardW - 12, 1f, Theme.TEXT);

			// Einstellungen-Knopf (oben rechts)
			if (!m.settings().isEmpty()) {
				float sx = x + cardW - 20;
				float sy = y + 6;
				boolean overSettings = mouseInContent && over(mouseX, mouseY, sx, sy, 14, 14);
				float s = Anim.approach("mods.gear." + m.id, overSettings ? 1 : 0, 16);
				Draw.rect(sx, sy, 14, 14, 4, Draw.alpha(Theme.CONTROL, 0.5f + s * 0.5f));
				drawSlidersIcon(sx + 3, sy + 3.5f, Draw.mix(Theme.MUTED, Theme.TEXT, s));
				if (mouseInContent) hit(sx, sy, 14, 14, () -> openSettings(m));
			}

			// An/Aus-Leiste unten
			float bx = x + 8;
			float by = y + CARD_H - 26;
			float bw = cardW - 16;
			float on = Anim.approach("mods.on." + m.id, m.enabled() ? 1 : 0, 14);
			Draw.gradient(bx, by, bw, 18, 5, Draw.mix(Theme.CONTROL, Theme.ACCENT, on), Draw.mix(Theme.CONTROL, 0xFF1F7FE0, on));
			Draw.Txt state = Draw.trBold(m.enabled() ? "moonvibe.mods.enabled" : "moonvibe.mods.disabled");
			Draw.centered(state, bx + bw / 2, by + 5, 1f, Draw.mix(Theme.MUTED, 0xFFFFFFFF, on));
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

	private void drawSlidersIcon(float x, float y, int color) {
		Draw.rect(x, y + 1, 8, 1, 0, color);
		Draw.rect(x, y + 5, 8, 1, 0, color);
		Draw.circle(x + 5.5f, y + 1.5f, 1.5f, color);
		Draw.circle(x + 2.5f, y + 5.5f, 1.5f, color);
	}

	private void openSettings(Module m) {
		selected = m;
		scrollTarget = 0;
		scroll = 0;
	}

	// ---------- Einstellungen eines Moduls ----------

	private void drawSettings(int mouseX, int mouseY, final Module m, float x0, float y0, float cw, float clipY, float clipH) {
		boolean mouseInContent = mouseY >= clipY && mouseY < clipY + clipH;
		float y = y0;

		// Zurück + Titel + Schalter
		boolean overBack = mouseInContent && over(mouseX, mouseY, x0, y, 22, 22);
		float b = Anim.approach("mods.back", overBack ? 1 : 0, 16);
		Draw.rect(x0, y, 22, 22, 6, Draw.mix(Theme.CONTROL, Theme.CARD_HOVER, b));
		Draw.centered(Draw.bold("‹"), x0 + 11, y + 6, 1.4f, Theme.TEXT);
		if (mouseInContent) hit(x0, y, 22, 22, () -> {
			selected = null;
			scrollTarget = 0;
		});
		Draw.item(m.icon(), x0 + 30, y + 3);
		Draw.text(Draw.bold(m.name()), x0 + 52, y + 1, 1.2f, Theme.TEXT);
		Draw.text(Draw.ellipsize(Draw.ui(m.description()), (int) cw - 110), x0 + 52, y + 14, Theme.MUTED);
		toggle(mouseInContent, "enabled." + m.id, x0 + cw - 30, y + 4, m.enabled(), () -> {
			m.toggle();
			Modules.save();
		});
		y += 34;

		for (Setting<?> setting : m.settings()) {
			Draw.rect(x0, y, cw, ROW_H - 4, 6, Theme.CARD);
			Draw.text(Draw.ui(setting.label()), x0 + 10, y + 8, Theme.TEXT);
			float right = x0 + cw - 10;
			if (setting instanceof BoolSetting) {
				final BoolSetting bool = (BoolSetting) setting;
				toggle(mouseInContent, m.id + "." + bool.id, right - 26, y + 5.5f, bool.on(), () -> {
					bool.toggle();
					Modules.save();
				});
			} else if (setting instanceof SliderSetting) {
				slider(mouseInContent, (SliderSetting) setting, right - 120, y, 120);
			} else if (setting instanceof ColorSetting) {
				swatches(mouseInContent, (ColorSetting) setting, right, y);
			} else if (setting instanceof ChoiceSetting) {
				segments(mouseX, mouseY, mouseInContent, (ChoiceSetting) setting, right, y);
			}
			y += ROW_H;
		}

		if (m instanceof HudModule) {
			final HudModule hud = (HudModule) m;
			Draw.Txt reset = Draw.tr("moonvibe.mods.reset_position");
			float w = Draw.width(reset) + 20;
			boolean overReset = mouseInContent && over(mouseX, mouseY, x0, y + 4, w, 20);
			float r = Anim.approach("mods.reset", overReset ? 1 : 0, 16);
			Draw.rect(x0, y + 4, w, 20, 6, Draw.mix(Theme.CONTROL, Theme.CARD_HOVER, r));
			Draw.text(reset, x0 + 10, y + 10, Theme.TEXT);
			if (mouseInContent) hit(x0, y + 4, w, 20, () -> {
				hud.resetPosition();
				Modules.save();
			});
			y += 28;
		}
		contentHeight = y - y0;
	}

	private void toggle(boolean active, String id, float x, float y, boolean on, Runnable click) {
		float t = Anim.approach("mods.toggle." + id, on ? 1 : 0, 18);
		Draw.rect(x, y, 26, 14, 7, Draw.mix(Theme.CONTROL, Theme.ACCENT, t));
		Draw.circle(x + 7 + t * 12, y + 7, 5, 0xFFFFFFFF);
		if (active) hit(x - 2, y - 2, 30, 18, click);
	}

	private void slider(boolean active, final SliderSetting s, final float x, float rowY, final float w) {
		Draw.Txt value = Draw.ui(s.display());
		Draw.text(value, x - Draw.width(value) - 8, rowY + 8, Theme.MUTED);
		float ty = rowY + 11;
		Draw.rect(x, ty, w, 3, 1.5f, Theme.CONTROL);
		float f = (float) s.fraction();
		Draw.rect(x, ty, w * f, 3, 1.5f, Theme.ACCENT);
		Draw.circle(x + w * f, ty + 1.5f, 5, 0xFFFFFFFF);
		if (active) {
			hits.add(new Hit(x - 6, rowY, w + 12, ROW_H - 4, () -> {}, frac -> s.setFraction((frac * (w + 12) - 6) / w)));
		}
	}

	private void swatches(boolean active, final ColorSetting setting, float right, float rowY) {
		int[] palette = ColorSetting.PALETTE;
		float size = 10;
		float x = right - palette.length * (size + 4) + 4;
		for (final int color : palette) {
			float cx = x + size / 2;
			float cy = rowY + 12;
			if (setting.color() == color) Draw.circle(cx, cy, size / 2 + 2, Theme.ACCENT_LIGHT);
			Draw.circle(cx, cy, size / 2, color);
			if (active) hit(x, rowY + 6, size, size + 2, () -> {
				setting.set(color);
				Modules.save();
			});
			x += size + 4;
		}
	}

	/** Auswahl als nebeneinanderliegende Knöpfe (das gewählte ist blau). */
	private void segments(int mouseX, int mouseY, boolean active, final ChoiceSetting setting, float right, float rowY) {
		float total = 0;
		float[] widths = new float[setting.choices.size()];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = Draw.width(Draw.ui(ChoiceSetting.choiceLabel(setting.choices.get(i)))) + 14;
			total += widths[i];
		}
		float x = right - total - 4;
		Draw.rect(x - 2, rowY + 3, total + 4, 18, 6, Theme.CONTROL);
		for (int i = 0; i < widths.length; i++) {
			final String choice = setting.choices.get(i);
			boolean selected = setting.is(choice);
			boolean over = active && over(mouseX, mouseY, x, rowY + 4, widths[i], 16);
			float t = Anim.approach("mods.choice." + setting.id + "." + choice, selected ? 1 : over ? 0.4f : 0, 16);
			Draw.rect(x, rowY + 4, widths[i], 16, 5, Draw.alpha(Theme.ACCENT, t));
			Draw.centered(Draw.ui(ChoiceSetting.choiceLabel(choice)), x + widths[i] / 2, rowY + 8, 1f, selected ? 0xFFFFFFFF : Theme.MUTED);
			if (active) hit(x, rowY + 4, widths[i], 16, () -> {
				setting.set(choice);
				Modules.save();
			});
			x += widths[i];
		}
	}

	private void drawScrollbar(float x, float y, float h) {
		if (contentHeight <= h) return;
		float barH = Math.max(20, h * h / contentHeight);
		float barY = y + (h - barH) * (scroll / (contentHeight - h));
		Draw.rect(x, barY, 3, barH, 1.5f, 0x40FFFFFF);
	}

	// ---------- Eingaben ----------

	@Override
	protected void onMouseClicked(int mouseX, int mouseY, int button) {
		if (searchBox.clicked(mouseX, mouseY) || button != 0) return;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (over(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
				if (hit.drag != null) {
					activeDrag = hit;
					hit.drag.accept((mouseX - hit.x) / hit.w);
				} else {
					hit.click.run();
				}
				return;
			}
		}
	}

	@Override
	protected void onMouseDragged(int mouseX, int mouseY, int button, double dx, double dy) {
		if (activeDrag != null) activeDrag.drag.accept((mouseX - activeDrag.x) / activeDrag.w);
	}

	@Override
	protected void onMouseReleased(int mouseX, int mouseY, int button) {
		if (activeDrag != null) {
			activeDrag = null;
			Modules.save();
		}
	}

	@Override
	protected void onScrolled(double amount) {
		float visible = ph - HEADER - 20;
		scrollTarget = (float) Math.max(0, Math.min(Math.max(0, contentHeight - visible), scrollTarget - amount * 24));
	}

	@Override
	protected void onKey(char character, int code) {
		searchBox.key(character, code);
	}

	@Override
	public void onClose() {
		Modules.save();
		client.setScreen(parent);
	}
}
