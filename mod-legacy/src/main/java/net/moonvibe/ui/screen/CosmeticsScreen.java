package net.moonvibe.ui.screen;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.Mv;
import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.cosmetics.CosmeticsManager.Cosmetic;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.FilePicker;
import net.moonvibe.ui.SearchField;
import net.moonvibe.ui.Theme;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/** Cosmetics direkt im Spiel anprobieren und anziehen (Rechte Shift → Cosmetics). */
public class CosmeticsScreen extends MoonScreen {
	private static final Identifier MARK = Mv.id("textures/gui/mark.png");
	private static final Map<String, Integer> RARITY = new HashMap<String, Integer>();
	/** Platzhalter-Eintrag für die Karte "Eigenes Cape hochladen" */
	private static final String UPLOAD_ID = "__upload";
	private static final int HEADER = 38;
	private static final int PREVIEW_W = 136;
	private static final int CARD_W = 78;
	private static final int CARD_H = 92;
	private static final int GAP = 6;

	static {
		RARITY.put("common", 0xFFA3AEC2);
		RARITY.put("rare", 0xFF3FA9FF);
		RARITY.put("epic", 0xFFB77CFF);
		RARITY.put("legendary", 0xFFFFB627);
		RARITY.put("custom", 0xFFFF6BD5);
	}

	private final Screen parent;
	private String slot = "cape";
	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private float yaw = 200;
	private boolean draggingPreview;
	private String status;
	private int statusColor = Theme.MUTED;
	private boolean loadFailed;
	private final SearchField searchBox;

	private static final class Hit {
		final float x, y, w, h;
		final Runnable click;

		Hit(float x, float y, float w, float h, Runnable click) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
		}
	}

	private final List<Hit> hits = new ArrayList<Hit>();
	private float px, py, pw, ph;

	public CosmeticsScreen(Screen parent) {
		this.parent = parent;
		this.searchBox = new SearchField(Mv.tr("moonvibe.cosmetics.search"), s -> scrollTarget = 0);
		Anim.set("cos.open", 0);
		if (CosmeticsManager.catalog() == null && CosmeticsManager.serverConfigured()) {
			CosmeticsManager.loadCatalog().exceptionally(e -> {
				loadFailed = true;
				return null;
			});
		}
	}

	@Override
	public void init() {
		super.init();
		Mv.repeatKeys(true);
		pw = Math.min(width - 24, 560);
		ph = Math.min(height - 24, 330);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		searchBox.place(px + pw - 194, py + 15, 158);
	}

	private boolean over(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		hits.clear();
		Draw.fill(0, 0, width, height, Theme.BACKDROP);
		float open = Anim.easeOut(Anim.approach("cos.open", 1, 12));
		Draw.push();
		Draw.translate(0, (1 - open) * 10);

		Draw.rect(px, py, pw, ph, 10, Theme.PANEL);
		Draw.outline(px, py, pw, ph, 10, 1, Theme.BORDER);

		// Kopfzeile
		Draw.image(MARK, Math.round(px + 12), Math.round(py + 9), 20, 20, 1);
		Draw.text(Draw.bold("Moonvibe"), px + 38, py + 11, 1.15f, Theme.TEXT);
		Draw.text(Draw.tr("moonvibe.cosmetics.title"), px + 40 + Draw.width(Draw.bold("Moonvibe")) * 1.15f + 6, py + 13, Theme.MUTED);
		Draw.rect(px + pw - 202, py + 9, 170, 20, 6, Theme.CONTROL);
		searchBox.render();
		float close = Anim.approach("cos.close", over(mouseX, mouseY, px + pw - 28, py + 9, 20, 20) ? 1 : 0, 16);
		Draw.rect(px + pw - 28, py + 9, 20, 20, 6, Draw.mix(Theme.CONTROL, Theme.DANGER, close));
		Draw.centered(Draw.bold("✕"), px + pw - 18, py + 15, 1f, Theme.TEXT);
		hits.add(new Hit(px + pw - 28, py + 9, 20, 20, this::onClose));
		Draw.fill(Math.round(px), Math.round(py + HEADER), Math.round(px + pw), Math.round(py + HEADER + 1), Theme.BORDER);

		drawPreview(mouseX, mouseY, open);

		float cx = px + PREVIEW_W + 22;
		float cy = py + HEADER + 10;
		float cw = pw - PREVIEW_W - 34;
		drawTabs(mouseX, mouseY, cx, cy, cw);
		float gridY = cy + 26;
		float gridH = py + ph - 12 - gridY;
		scroll += (scrollTarget - scroll) * 0.35f;
		float slide = (1 - open) * 10;
		Draw.scissor(cx - 2, gridY - 2 + slide, cx + cw + 2, gridY + gridH + 2 + slide);
		drawGrid(mouseX, mouseY, cx, gridY - scroll, cw, gridY, gridH);
		Draw.endScissor();

		Draw.pop();
	}

	// ---------- Vorschau (eigener Spieler, mit der Maus drehbar) ----------

	private void drawPreview(int mouseX, int mouseY, float open) {
		float x = px + 12;
		float y = py + HEADER + 10;
		float w = PREVIEW_W;
		float h = ph - HEADER - 22;
		Draw.gradient(x, y, w, h, 8, 0xFF16243A, 0xFF0B111B);
		Draw.outline(x, y, w, h, 8, 1, Theme.BORDER);
		Draw.rect(x + w / 2 - 40, y + h - 34, 80, 10, 5, 0x5532A0FF);

		if (client.player != null) {
			if (!draggingPreview) yaw += 0.15f;
			drawEntity(client.player, x + w / 2, y + h - 30 + (1 - open) * 10, (int) (h * 0.33f), yaw);
		}
		Draw.Txt text = status != null ? Draw.ui(status) : Draw.tr("moonvibe.cosmetics.drag");
		Draw.centered(text, x + w / 2, y + h - 16, 1f, status != null ? statusColor : Theme.MUTED);
	}

	/** Wie die Spielerfigur im Inventar, aber mit frei drehbarem Blickwinkel. */
	private static void drawEntity(LivingEntity entity, float x, float y, int size, float yaw) {
		GlStateManager.enableDepthTest();
		GlStateManager.enableColorMaterial();
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 50.0F);
		GlStateManager.scale((float) -size, (float) size, (float) size);
		GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
		float bodyYaw = entity.bodyYaw;
		float entityYaw = entity.yaw;
		float pitch = entity.pitch;
		float prevHeadYaw = entity.prevHeadYaw;
		float headYaw = entity.headYaw;
		GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
		DiffuseLighting.enableNormally();
		GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(-7.0F, 1.0F, 0.0F, 0.0F);
		entity.bodyYaw = yaw;
		entity.yaw = yaw;
		entity.pitch = 0;
		entity.headYaw = yaw;
		entity.prevHeadYaw = yaw;
		EntityRenderDispatcher dispatcher = Mv.mc().getEntityRenderManager();
		dispatcher.setYaw(180.0F);
		dispatcher.setRenderShadows(false);
		//#if MC >= 1.9
		//$$ dispatcher.method_12446(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, false); // render
		//#else
		dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F);
		//#endif
		dispatcher.setRenderShadows(true);
		entity.bodyYaw = bodyYaw;
		entity.yaw = entityYaw;
		entity.pitch = pitch;
		entity.prevHeadYaw = prevHeadYaw;
		entity.headYaw = headYaw;
		GlStateManager.popMatrix();
		DiffuseLighting.disable();
		GlStateManager.disableRescaleNormal();
		GlStateManager.activeTexture(GLX.lightmapTextureUnit);
		GlStateManager.disableTexture();
		GlStateManager.activeTexture(GLX.textureUnit);
		GlStateManager.disableDepthTest();
	}

	// ---------- Kategorien ----------

	private void drawTabs(int mouseX, int mouseY, float x, float y, float w) {
		List<String> slots = CosmeticsManager.SLOTS;
		// Breite je nach Text ("Begleiter" braucht mehr Platz als "Hüte"), der Rest gleichmäßig verteilt;
		// erst wenn das nicht reicht, wird die Schrift kleiner – abgeschnitten wird nichts
		Draw.Txt[] labels = new Draw.Txt[slots.size()];
		float textW = 0;
		for (int i = 0; i < slots.size(); i++) {
			labels[i] = Draw.tr("moonvibe.cosmetics.category." + slots.get(i));
			textW += Draw.width(labels[i]);
		}
		float free = w - (slots.size() - 1) * 4;
		float scale = Math.max(0.5f, Math.min(1f, (free - slots.size() * 8) / textW));
		float pad = (free - textW * scale) / slots.size();
		float tx = x;
		for (int i = 0; i < slots.size(); i++) {
			final String s = slots.get(i);
			float tabW = Draw.width(labels[i]) * scale + pad;
			boolean active = s.equals(slot);
			float hover = Anim.approach("cos.tab." + s, active ? 1 : over(mouseX, mouseY, tx, y, tabW, 20) ? 0.4f : 0, 16);
			Draw.rect(tx, y, tabW, 20, 6, Draw.mix(Theme.CONTROL, Theme.ACCENT, hover * 0.85f));
			Draw.centered(labels[i], tx + tabW / 2, y + 6 + (1 - scale) * 4, scale, active ? 0xFFFFFFFF : Draw.mix(Theme.MUTED, Theme.TEXT, hover));
			hits.add(new Hit(tx, y, tabW, 20, () -> showSlot(s)));
			tx += tabW + 4;
		}
	}

	/** Kategorie wechseln (auch für automatische Tests). */
	public void showSlot(String s) {
		CosmeticsManager.preview(slot, CosmeticsManager.ownSaved(slot));
		slot = s;
		scrollTarget = 0;
		// Rücken-Sachen sieht man besser von hinten
		yaw = s.equals("cape") || s.equals("back") || s.equals("wings") ? 20 : 200;
	}

	// ---------- Karten ----------

	/** Einträge der Kategorie; null = "Keins", UPLOAD_ID = Hochladen-Karte */
	private List<Object> items() {
		List<Cosmetic> catalog = CosmeticsManager.catalog();
		if (catalog == null) return Collections.emptyList();
		String q = searchBox.value().trim().toLowerCase(Locale.ROOT);
		List<Object> result = new ArrayList<Object>();
		result.add(null);
		Cosmetic custom = CosmeticsManager.ownCustomCape();
		if (slot.equals("cape") && q.isEmpty()) {
			result.add(UPLOAD_ID);
			if (custom != null) result.add(custom);
		}
		List<Cosmetic> list = new ArrayList<Cosmetic>();
		for (Cosmetic c : catalog) {
			if (!c.type.equals(slot)) continue;
			if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(c);
		}
		Collections.sort(list, (a, b) -> rank(a.rarity) - rank(b.rarity));
		result.addAll(list);
		return result;
	}

	private static int rank(String rarity) {
		if (rarity.equals("legendary")) return 0;
		if (rarity.equals("epic")) return 1;
		if (rarity.equals("rare")) return 2;
		return 3;
	}

	private void drawGrid(int mouseX, int mouseY, float x0, float y0, float cw, float clipY, float clipH) {
		if (!CosmeticsManager.serverConfigured() || loadFailed) {
			Draw.centered(Draw.tr("moonvibe.cosmetics.offline"), x0 + cw / 2, y0 + 50, 1f, Theme.MUTED);
			return;
		}
		if (CosmeticsManager.catalog() == null) {
			Draw.centered(Draw.tr("moonvibe.cosmetics.loading"), x0 + cw / 2, y0 + 50, 1f, Theme.MUTED);
			return;
		}
		List<Object> list = items();
		int cols = Math.max(2, (int) ((cw + GAP) / (CARD_W + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		boolean inside = mouseY >= clipY && mouseY < clipY + clipH;
		String saved = CosmeticsManager.ownSaved(slot);
		boolean anyHover = false;

		for (int i = 0; i < list.size(); i++) {
			Object entry = list.get(i);
			float x = x0 + (i % cols) * (cardW + GAP);
			float y = y0 + (i / cols) * (CARD_H + GAP);
			boolean hovered = inside && over(mouseX, mouseY, x, y, cardW, CARD_H);
			if (UPLOAD_ID.equals(entry)) {
				drawUploadCard(x, y, cardW, hovered);
				if (inside) hits.add(new Hit(x, y, cardW, CARD_H, this::pickCape));
				continue;
			}
			final Cosmetic c = (Cosmetic) entry;
			final String id = c == null ? null : c.id;
			Integer rc = c == null ? null : RARITY.get(c.rarity);
			int rarity = c == null ? Theme.MUTED : rc == null ? 0xFFA3AEC2 : rc;
			float hover = Anim.approach("cos.card." + slot + "." + id, hovered ? 1 : 0, 16);
			boolean equipped = Objects.equals(saved, id);

			Draw.rect(x, y, cardW, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
			Draw.rect(x + 1, y + 1, cardW - 2, 46, 7, Draw.alpha(rarity, 0.08f + hover * 0.08f));
			Draw.outline(x, y, cardW, CARD_H, 8, equipped ? 1.5f : 1, equipped ? Theme.GOLD : Draw.mix(Theme.BORDER, Draw.alpha(rarity, 0.7f), hover));
			if (c != null) {
				Identifier thumb = CosmeticsManager.thumbnail(c);
				float size = 54 + hover * 4;
				if (thumb != null) Draw.image(thumb, Math.round(x + cardW / 2 - size / 2), Math.round(y + 26 - size / 2), Math.round(size), Math.round(size), 1);
				else if (c.type.equals("cape")) drawCapeFront(c.id, x + cardW / 2, y + 26, hover);
				Draw.centeredFit(Draw.ui(c.name()), x + cardW / 2, y + CARD_H - 30, cardW - 8, 1f, Theme.TEXT);
				Draw.centered(Draw.trBold(c.custom() ? "moonvibe.rarity.custom" : "moonvibe.rarity." + c.rarity), x + cardW / 2, y + CARD_H - 18, 0.8f, rarity);
				Draw.rect(x + 8, y + CARD_H - 4, cardW - 16, 2, 1, rarity);
			} else {
				Draw.outline(x + cardW / 2 - 16, y + 10, 32, 32, 16, 1, 0x44FFFFFF);
				Draw.centered(Draw.tr("moonvibe.cosmetics.none"), x + cardW / 2, y + CARD_H - 30, 1f, Theme.TEXT);
			}
			if (equipped) Draw.circle(x + cardW - 9, y + 9, 5, Theme.GOLD);

			if (hovered) {
				anyHover = true;
				CosmeticsManager.preview(slot, id);
			}
			if (inside) hits.add(new Hit(x, y, cardW, CARD_H, () -> equip(id, c)));
		}
		// Maus nicht über einer Karte → wieder die gespeicherte Auswahl zeigen
		if (!anyHover) CosmeticsManager.preview(slot, saved);
		int rows = (list.size() + cols - 1) / cols;
		contentHeight = rows * (CARD_H + GAP) - GAP;
	}

	/** Vorderseite eines Capes direkt aus der Textur (für Capes ohne Vorschaubild, z. B. das eigene). */
	private void drawCapeFront(String id, float cx, float cy, float hover) {
		Identifier texture = CosmeticsManager.capeTexture(id);
		float h = 44 + hover * 3;
		float w = h * 10 / 16f;
		float x = Math.round(cx - w / 2f);
		float y = Math.round(cy - h / 2f);
		Draw.rect(x - 2, y - 2, w + 4, h + 4, 3, 0x66000000);
		if (texture != null) Draw.region(texture, x, y, w, h, 1 / 64f, 1 / 32f, 11 / 64f, 17 / 32f, 0xFFFFFFFF);
	}

	private void drawUploadCard(float x, float y, float w, boolean hovered) {
		float hover = Anim.approach("cos.card.upload", hovered ? 1 : 0, 16);
		Draw.rect(x, y, w, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
		Draw.rect(x + 1, y + 1, w - 2, 46, 7, Draw.alpha(Theme.ACCENT, 0.1f + hover * 0.1f));
		Draw.outline(x, y, w, CARD_H, 8, 1, Draw.mix(Draw.alpha(Theme.ACCENT, 0.45f), Theme.ACCENT, hover));
		float size = 30 + hover * 2;
		Draw.rect(x + w / 2 - size / 2, y + 26 - size / 2, size, size, 9, Draw.mix(0xFF1B4C80, Theme.ACCENT, hover));
		Draw.centered(Draw.bold("+"), x + w / 2, y + 20, 1.6f, 0xFFFFFFFF);
		Draw.centeredFit(Draw.tr("moonvibe.cosmetics.upload"), x + w / 2, y + CARD_H - 30, w - 8, 1f, Theme.TEXT);
		Draw.centeredFit(Draw.tr("moonvibe.cosmetics.upload_click"), x + w / 2, y + CARD_H - 18, w - 6, 0.8f, Theme.MUTED);
		Draw.rect(x + 8, y + CARD_H - 4, w - 16, 2, 1, Theme.ACCENT);
	}

	private void pickCape() {
		FilePicker.openImage(Mv.tr("moonvibe.cosmetics.images"), file -> {
			if (file != null) uploadCape(file);
		});
	}

	/** Bild hochladen und als eigenes Cape anziehen. */
	public void uploadCape(File file) {
		status = Mv.tr("moonvibe.cosmetics.uploading");
		statusColor = Theme.MUTED;
		showSlot("cape");
		CosmeticsManager.uploadCape(file).whenComplete((ok, error) -> Mv.mc().submit(() -> {
			if (error == null) {
				status = Mv.tr("moonvibe.cosmetics.cape_saved");
				statusColor = Theme.SUCCESS;
			} else {
				String message = String.valueOf(error.getMessage());
				status = Mv.tr(message.contains("login") ? "moonvibe.cosmetics.error_login"
					: message.contains("IOException") ? "moonvibe.cosmetics.image_error" : "moonvibe.cosmetics.error");
				statusColor = Theme.DANGER;
			}
		}));
	}

	private void equip(String id, Cosmetic c) {
		status = Mv.tr("moonvibe.cosmetics.saving");
		statusColor = Theme.MUTED;
		CosmeticsManager.equip(slot, id).whenComplete((ok, error) -> Mv.mc().submit(() -> {
			if (error == null) {
				status = c == null ? Mv.tr("moonvibe.cosmetics.removed") : Mv.tr("moonvibe.cosmetics.equipped", c.name());
				statusColor = Theme.SUCCESS;
			} else {
				boolean login = String.valueOf(error.getMessage()).contains("login");
				status = Mv.tr(login ? "moonvibe.cosmetics.error_login" : "moonvibe.cosmetics.error");
				statusColor = Theme.DANGER;
			}
		}));
	}

	// ---------- Eingaben ----------

	@Override
	protected void onMouseClicked(int mouseX, int mouseY, int button) {
		if (searchBox.clicked(mouseX, mouseY) || button != 0) return;
		if (over(mouseX, mouseY, px + 12, py + HEADER + 10, PREVIEW_W, ph - HEADER - 22)) {
			draggingPreview = true;
			return;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (over(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
				hit.click.run();
				return;
			}
		}
	}

	@Override
	protected void onMouseDragged(int mouseX, int mouseY, int button, double dx, double dy) {
		if (draggingPreview) yaw -= (float) dx * 2.5f;
	}

	@Override
	protected void onMouseReleased(int mouseX, int mouseY, int button) {
		draggingPreview = false;
	}

	@Override
	protected void onScrolled(double amount) {
		float visible = ph - HEADER - 58;
		scrollTarget = (float) Math.max(0, Math.min(Math.max(0, contentHeight - visible), scrollTarget - amount * 24));
	}

	@Override
	protected void onKey(char character, int code) {
		searchBox.key(character, code);
	}

	@Override
	public void onClose() {
		client.setScreen(parent);
	}

	/** Bei jedem Schließen (auch wenn das Spiel selbst ein anderes Menü öffnet) die Anprobe beenden. */
	@Override
	public void removed() {
		CosmeticsManager.clearPreview();
		super.removed();
	}
}
