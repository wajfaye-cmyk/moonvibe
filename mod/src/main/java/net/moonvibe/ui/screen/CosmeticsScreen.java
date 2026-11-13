package net.moonvibe.ui.screen;

import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.cosmetics.CosmeticsManager.Cosmetic;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.FilePicker;
import net.moonvibe.ui.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Cosmetics direkt im Spiel anprobieren und anziehen (Rechte Shift → Cosmetics). */
public class CosmeticsScreen extends MoonScreen {
	private static final Identifier MARK = Moonvibe.id("textures/gui/mark.png");
	private static final Map<String, Integer> RARITY = Map.of(
		"common", 0xFFA3AEC2, "rare", 0xFF3FA9FF, "epic", 0xFFB77CFF, "legendary", 0xFFFFB627, "custom", 0xFFFF6BD5);
	/** Platzhalter-Karte "Eigenes Cape hochladen" */
	private static final Cosmetic UPLOAD = new Cosmetic("upload", "cape", "upload", Map.of(), null);
	private static final int HEADER = 38;
	private static final int PREVIEW_W = 136;
	private static final int CARD_W = 78;
	private static final int CARD_H = 92;
	private static final int GAP = 6;

	private final Screen parent;
	private String slot = "cape";
	private String search = "";
	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private float yaw = 200;
	private long lastFrame = System.nanoTime();
	private boolean draggingPreview;
	private @Nullable Component status;
	private int statusColor = Theme.MUTED;
	private boolean loadFailed;
	private EditBox searchBox;

	private record Hit(float x, float y, float w, float h, Runnable click) {}

	private final List<Hit> hits = new ArrayList<>();
	private float px, py, pw, ph;

	public CosmeticsScreen(Screen parent) {
		super(Component.translatable("moonvibe.cosmetics.title"));
		this.parent = parent;
		Anim.set("cos.open", 0);
		Anim.set("cosmetics.scroll", 0);
		if (CosmeticsManager.catalog() == null && CosmeticsManager.serverConfigured()) {
			CosmeticsManager.loadCatalog().exceptionally(e -> {
				loadFailed = true;
				return null;
			});
		}
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 560);
		ph = Math.min(height - 24, 330);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		searchBox = searchBox(Math.round(px + pw - 196), Math.round(py + 13), 150, "moonvibe.cosmetics.search", search, s -> {
			search = s;
			scrollTarget = 0;
		});
	}

	private boolean over(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		hits.clear();
		float open = Anim.easeOut(Anim.approach("cos.open", 1, 12));
		Draw.push(g);
		Draw.translate(g, 0, (1 - open) * 10);

		Draw.rect(g, px, py, pw, ph, 10, Theme.PANEL);
		Draw.outline(g, px, py, pw, ph, 10, 1, Theme.BORDER);

		// Kopfzeile
		Draw.image(g, MARK, Math.round(px + 12), Math.round(py + 9), 20, 20, 128, 128, 1);
		Draw.text(g, Draw.bold("Moonvibe"), px + 38, py + 11, 1.15f, Theme.TEXT);
		Draw.text(g, Draw.ui(Component.translatable("moonvibe.cosmetics.title")), px + 40 + Draw.width(Draw.bold("Moonvibe")) * 1.15f + 6, py + 13, Theme.MUTED);
		Draw.rect(g, px + pw - 202, py + 9, 170, 20, 6, Theme.CONTROL);
		drawSearchHint(g, searchBox, "moonvibe.cosmetics.search");
		float close = Anim.approach("cos.close", over(mouseX, mouseY, px + pw - 28, py + 9, 20, 20) ? 1 : 0, 16);
		Draw.rect(g, px + pw - 28, py + 9, 20, 20, 6, Draw.mix(Theme.CONTROL, Theme.DANGER, close));
		Draw.centered(g, Draw.bold("×"), px + pw - 18, py + 13, 1.4f, Theme.TEXT);
		hits.add(new Hit(px + pw - 28, py + 9, 20, 20, this::onClose));
		Draw.fill(g, Math.round(px), Math.round(py + HEADER), Math.round(px + pw), Math.round(py + HEADER + 1), Theme.BORDER);

		drawPreview(g, mouseX, mouseY);

		float cx = px + PREVIEW_W + 22;
		float cy = py + HEADER + 10;
		float cw = pw - PREVIEW_W - 34;
		drawTabs(g, mouseX, mouseY, cx, cy, cw);
		float gridY = cy + 26;
		float gridH = py + ph - 12 - gridY;
		scroll = Anim.approach("cosmetics.scroll", scrollTarget, 26);
		Draw.scissor(g, Math.round(cx - 2), Math.round(gridY - 2), Math.round(cx + cw + 2), Math.round(gridY + gridH + 2));
		drawGrid(g, mouseX, mouseY, cx, gridY - scroll, cw, gridY, gridH);
		Draw.endScissor(g);

		Draw.pop(g);
	}

	// ---------- Vorschau (eigener Spieler, mit der Maus drehbar) ----------

	private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float x = px + 12;
		float y = py + HEADER + 10;
		float w = PREVIEW_W;
		float h = ph - HEADER - 22;
		Draw.gradient(g, x, y, w, h, 8, 0xFF16243A, 0xFF0B111B);
		Draw.outline(g, x, y, w, h, 8, 1, Theme.BORDER);
		Draw.rect(g, x + w / 2 - 40, y + h - 34, 80, 10, 5, 0x5532A0FF);

		LivingEntity player = minecraft.player;
		if (player != null) {
			// langsam weiterdrehen – gleich schnell, egal wie viele Bilder pro Sekunde
			long now = System.nanoTime();
			float seconds = Math.min(0.1f, (now - lastFrame) / 1e9f);
			lastFrame = now;
			if (!draggingPreview) yaw += 9f * seconds;
			Draw.entity(g, player, yaw, Math.round(x), Math.round(y + 6), Math.round(x + w), Math.round(y + h - 26), (int) (h * 0.33f));
		}
		if (status != null) Draw.centered(g, Draw.ui(status), x + w / 2, y + h - 16, 1f, statusColor);
		else Draw.centered(g, Draw.ui(Component.translatable("moonvibe.cosmetics.drag")), x + w / 2, y + h - 16, 1f, Theme.MUTED);
	}

	// ---------- Kategorien ----------

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w) {
		List<String> slots = CosmeticsManager.SLOTS;
		// Breite je nach Text ("Begleiter" braucht mehr Platz als "Hüte"), der Rest gleichmäßig verteilt;
		// erst wenn das nicht reicht, wird die Schrift kleiner – abgeschnitten wird nichts
		Component[] labels = new Component[slots.size()];
		float textW = 0;
		for (int i = 0; i < slots.size(); i++) {
			labels[i] = Draw.ui(Component.translatable("moonvibe.cosmetics.category." + slots.get(i)));
			textW += Draw.width(labels[i]);
		}
		float free = w - (slots.size() - 1) * 4;
		float scale = Math.max(0.5f, Math.min(1f, (free - slots.size() * 8) / textW));
		float pad = (free - textW * scale) / slots.size();
		float tx = x;
		for (int i = 0; i < slots.size(); i++) {
			String s = slots.get(i);
			float tabW = Draw.width(labels[i]) * scale + pad;
			boolean active = s.equals(slot);
			float hover = Anim.approach("cos.tab." + s, active ? 1 : over(mouseX, mouseY, tx, y, tabW, 20) ? 0.4f : 0, 16);
			Draw.rect(g, tx, y, tabW, 20, 6, Draw.mix(Theme.CONTROL, Theme.ACCENT, hover * 0.85f));
			Draw.centered(g, labels[i], tx + tabW / 2, y + 6 + (1 - scale) * 4, scale, active ? 0xFFFFFFFF : Draw.mix(Theme.MUTED, Theme.TEXT, hover));
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

	private List<Cosmetic> items() {
		List<Cosmetic> catalog = CosmeticsManager.catalog();
		if (catalog == null) return List.of();
		String q = search.trim().toLowerCase(Locale.ROOT);
		List<Cosmetic> result = new ArrayList<>();
		Cosmetic custom = CosmeticsManager.ownCustomCape();
		if (slot.equals("cape") && q.isEmpty()) {
			result.add(UPLOAD);
			if (custom != null) result.add(custom);
		}
		catalog.stream()
			.filter(c -> c.type().equals(slot))
			.filter(c -> q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q))
			.sorted((a, b) -> rank(a.rarity()) - rank(b.rarity()))
			.forEach(result::add);
		return result;
	}

	private static int rank(String rarity) {
		return switch (rarity) {
			case "legendary" -> 0;
			case "epic" -> 1;
			case "rare" -> 2;
			default -> 3;
		};
	}

	private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, float x0, float y0, float cw, float clipY, float clipH) {
		if (!CosmeticsManager.serverConfigured() || loadFailed) {
			Draw.centered(g, Draw.ui(Component.translatable("moonvibe.cosmetics.offline")), x0 + cw / 2, y0 + 50, 1f, Theme.MUTED);
			return;
		}
		if (CosmeticsManager.catalog() == null) {
			Draw.centered(g, Draw.ui(Component.translatable("moonvibe.cosmetics.loading")), x0 + cw / 2, y0 + 50, 1f, Theme.MUTED);
			return;
		}
		List<Cosmetic> list = new ArrayList<>(items());
		list.add(0, null); // "Keins"
		int cols = Math.max(2, (int) ((cw + GAP) / (CARD_W + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		boolean inside = mouseY >= clipY && mouseY < clipY + clipH;
		String saved = CosmeticsManager.ownSaved(slot);
		boolean anyHover = false;

		for (int i = 0; i < list.size(); i++) {
			Cosmetic c = list.get(i);
			float x = x0 + (i % cols) * (cardW + GAP);
			float y = y0 + (i / cols) * (CARD_H + GAP);
			boolean hovered = inside && over(mouseX, mouseY, x, y, cardW, CARD_H);
			if (c == UPLOAD) {
				drawUploadCard(g, x, y, cardW, hovered);
				if (inside) hits.add(new Hit(x, y, cardW, CARD_H, this::pickCape));
				continue;
			}
			String id = c == null ? null : c.id();
			int rarity = c == null ? Theme.MUTED : RARITY.getOrDefault(c.rarity(), 0xFFA3AEC2);
			float hover = Anim.approach("cos.card." + slot + "." + id, hovered ? 1 : 0, 16);
			boolean equipped = Objects.equals(saved, id);

			Draw.rect(g, x, y, cardW, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
			Draw.rect(g, x + 1, y + 1, cardW - 2, 46, 7, Draw.alpha(rarity, 0.08f + hover * 0.08f));
			Draw.outline(g, x, y, cardW, CARD_H, 8, equipped ? 1.5f : 1, equipped ? Theme.GOLD : Draw.mix(Theme.BORDER, Draw.alpha(rarity, 0.7f), hover));
			if (c != null) {
				Identifier thumb = CosmeticsManager.thumbnail(c);
				float size = 54 + hover * 4;
				if (thumb != null) Draw.image(g, thumb, Math.round(x + cardW / 2 - size / 2), Math.round(y + 26 - size / 2), Math.round(size), Math.round(size), 256, 256, 1);
				else if (c.type().equals("cape")) drawCapeFront(g, c.id(), x + cardW / 2, y + 26, hover);
				Draw.centeredFit(g, Draw.ui(c.name()), x + cardW / 2, y + CARD_H - 30, cardW - 8, 1f, Theme.TEXT);
				Component rarityName = Draw.bold(Component.translatable(c.custom() ? "moonvibe.rarity.custom" : "moonvibe.rarity." + c.rarity()));
				Draw.centered(g, rarityName, x + cardW / 2, y + CARD_H - 18, 0.8f, rarity);
				Draw.rect(g, x + 8, y + CARD_H - 4, cardW - 16, 2, 1, rarity);
			} else {
				Draw.outline(g, x + cardW / 2 - 16, y + 10, 32, 32, 16, 1, 0x44FFFFFF);
				Draw.centered(g, Draw.ui(Component.translatable("moonvibe.cosmetics.none")), x + cardW / 2, y + CARD_H - 30, 1f, Theme.TEXT);
			}
			if (equipped) Draw.circle(g, x + cardW - 9, y + 9, 5, Theme.GOLD);

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
	private void drawCapeFront(GuiGraphicsExtractor g, String id, float cx, float cy, float hover) {
		Identifier texture = CosmeticsManager.capeTexture(id);
		int h = Math.round(44 + hover * 3);
		int w = Math.round(h * 10 / 16f);
		int x = Math.round(cx - w / 2f);
		int y = Math.round(cy - h / 2f);
		Draw.rect(g, x - 2, y - 2, w + 4, h + 4, 3, 0x66000000);
		if (texture != null) Draw.region(g, texture, x, y, w, h, 1, 1, 10, 16, 64, 32);
	}

	private void drawUploadCard(GuiGraphicsExtractor g, float x, float y, float w, boolean hovered) {
		float hover = Anim.approach("cos.card.upload", hovered ? 1 : 0, 16);
		Draw.rect(g, x, y, w, CARD_H, 8, Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover));
		Draw.rect(g, x + 1, y + 1, w - 2, 46, 7, Draw.alpha(Theme.ACCENT, 0.1f + hover * 0.1f));
		Draw.outline(g, x, y, w, CARD_H, 8, 1, Draw.mix(Draw.alpha(Theme.ACCENT, 0.45f), Theme.ACCENT, hover));
		float size = 30 + hover * 2;
		Draw.rect(g, x + w / 2 - size / 2, y + 26 - size / 2, size, size, 9, Draw.mix(0xFF1B4C80, Theme.ACCENT, hover));
		Draw.centered(g, Draw.bold("+"), x + w / 2, y + 22, 1.6f, 0xFFFFFFFF);
		Draw.centeredFit(g, Draw.ui(Component.translatable("moonvibe.cosmetics.upload")), x + w / 2, y + CARD_H - 30, w - 8, 1f, Theme.TEXT);
		Draw.centeredFit(g, Draw.ui(Component.translatable("moonvibe.cosmetics.upload_hint")), x + w / 2, y + CARD_H - 18, w - 6, 0.8f, Theme.MUTED);
		Draw.rect(g, x + 8, y + CARD_H - 4, w - 16, 2, 1, Theme.ACCENT);
	}

	private void pickCape() {
		FilePicker.openImage(I18n.get("moonvibe.cosmetics.images"), path -> {
			if (path != null) uploadCape(path);
		});
	}

	/** Bild hochladen (Klick auf die Karte oder Datei ins Fenster ziehen). */
	public void uploadCape(Path file) {
		status = Component.translatable("moonvibe.cosmetics.uploading");
		statusColor = Theme.MUTED;
		showSlot("cape");
		CosmeticsManager.uploadCape(file).whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
			if (error == null) {
				status = Component.translatable("moonvibe.cosmetics.cape_saved");
				statusColor = Theme.SUCCESS;
			} else {
				String message = String.valueOf(error.getMessage());
				status = Component.translatable(message.contains("login") ? "moonvibe.cosmetics.error_login"
					: message.contains("IOException") ? "moonvibe.cosmetics.image_error" : "moonvibe.cosmetics.error");
				statusColor = Theme.DANGER;
			}
		}));
	}

	// Bild ins Fenster ziehen (Minecraft meldet das erst ab 1.16)
	//#if MC >= 1.16
	@Override
	//#endif
	public void onFilesDrop(List<Path> files) {
		files.stream()
			.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)$"))
			.findFirst()
			.ifPresentOrElse(this::uploadCape, () -> {
				status = Component.translatable("moonvibe.cosmetics.image_error");
				statusColor = Theme.DANGER;
			});
	}

	private void equip(@Nullable String id, @Nullable Cosmetic c) {
		status = Component.translatable("moonvibe.cosmetics.saving");
		statusColor = Theme.MUTED;
		String target = slot;
		CosmeticsManager.equip(target, id).whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
			if (error == null) {
				status = c == null ? Component.translatable("moonvibe.cosmetics.removed") : Component.translatable("moonvibe.cosmetics.equipped", c.name());
				statusColor = Theme.SUCCESS;
			} else {
				boolean login = String.valueOf(error.getMessage()).contains("login");
				status = Component.translatable(login ? "moonvibe.cosmetics.error_login" : "moonvibe.cosmetics.error");
				statusColor = Theme.DANGER;
			}
		}));
	}

	// ---------- Eingaben ----------

	@Override
	protected boolean onMouseClicked(double mx, double my, int button) {
		if (button != 0) return false;
		if (over(mx, my, px + 12, py + HEADER + 10, PREVIEW_W, ph - HEADER - 22)) {
			draggingPreview = true;
			return true;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (over(mx, my, hit.x, hit.y, hit.w, hit.h)) {
				hit.click.run();
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!draggingPreview) return false;
		yaw -= (float) dx * 2.5f;
		return true;
	}

	@Override
	protected boolean onMouseReleased(double mx, double my, int button) {
		draggingPreview = false;
		return false;
	}

	@Override
	protected boolean onScrolled(double amount) {
		float visible = ph - HEADER - 58;
		scrollTarget = (float) Math.max(0, Math.min(Math.max(0, contentHeight - visible), scrollTarget - amount * 24));
		return true;
	}

	@Override
	public void onClose() {
		Mc.setScreen(parent);
	}

	/** Wird bei jedem Schließen aufgerufen (auch wenn das Spiel selbst ein anderes Menü öffnet) – Anprobe beenden. */
	@Override
	public void removed() {
		CosmeticsManager.clearPreview();
		super.removed();
	}
}
