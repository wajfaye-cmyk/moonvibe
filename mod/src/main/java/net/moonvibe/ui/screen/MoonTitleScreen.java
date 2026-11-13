package net.moonvibe.ui.screen;

import net.moonvibe.Moonvibe;
import net.moonvibe.compat.Mc;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Eigenes Hauptmenü von moonvibe (ersetzt das Minecraft-Titelmenü). */
public class MoonTitleScreen extends MoonScreen {
	private static final Identifier LOGO = Moonvibe.id("textures/gui/title.png");
	private static final Component COPYRIGHT = Mc.copyright();

	private record Button(String id, float x, float y, float w, float h, Component label, boolean primary, boolean active, Runnable action) {}

	private final List<Button> buttons = new ArrayList<>();

	public MoonTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
		Anim.set("title.open", 0);
	}

	@Override
	protected void init() {
		buttons.clear();
		float bw = 210;
		float x = width / 2f - bw / 2;
		float y = height * 0.5f;
		buttons.add(new Button("single", x, y, bw, 26, Component.translatable("menu.singleplayer"), true, true,
			() -> Mc.setScreen(Mc.singleplayerScreen(this))));
		buttons.add(new Button("multi", x, y + 32, bw, 26, Component.translatable("menu.multiplayer"), false, Mc.allowsMultiplayer(),
			() -> Mc.setScreen(Mc.multiplayerScreen(this))));

		float small = (bw - 8) / 3f;
		float row = y + 64;
		buttons.add(new Button("options", x, row, small, 22, Component.translatable("menu.options"), false, true,
			() -> Mc.setScreen(Mc.optionsScreen(this))));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			buttons.add(new Button("mods", x + small + 4, row, small, 22, Component.translatable("moonvibe.title.mods"), false, true, this::openModMenu));
		} else {
			buttons.add(new Button("language", x + small + 4, row, small, 22, Component.translatable("options.language"), false, true,
				() -> Mc.setScreen(Mc.languageScreen(this))));
		}
		buttons.add(new Button("quit", x + (small + 4) * 2, row, small, 22, Component.translatable("menu.quit"), false, true, minecraft::stop));
	}

	private void openModMenu() {
		try {
			Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			Mc.setScreen((Screen) cls.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException e) {
			Moonvibe.LOGGER.warn("Mod Menu konnte nicht geöffnet werden", e);
		}
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float delta) {
		drawPanorama(g, delta);
		Draw.blur(g);
		Draw.fillGradient(g, 0, 0, width, height, 0x88060910, 0xEE060910);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float open = Anim.easeOut(Anim.approach("title.open", 1, 4));
		float slide = (1 - open) * 16;

		float logoW = Math.min(340, width * 0.55f);
		float logoH = logoW * 341f / 1024f;
		float logoY = Math.max(10, height * 0.5f - logoH - 18) - slide;
		Draw.image(g, LOGO, Math.round(width / 2f - logoW / 2), Math.round(logoY), Math.round(logoW), Math.round(logoH), 1024, 341, open);

		for (Button b : buttons) {
			boolean over = b.active && inside(mouseX, mouseY, b);
			float h = Anim.approach("title.btn." + b.id, over ? 1 : 0, 16);
			float y = b.y + slide * 0.6f;
			int alpha = b.active ? 0xFF : 0x80;
			if (b.primary) {
				Draw.gradient(g, b.x, y, b.w, b.h, 7, Draw.alpha(Draw.mix(Theme.ACCENT, Theme.ACCENT_LIGHT, h), open),
					Draw.alpha(Draw.mix(0xFF1F7FE0, Theme.ACCENT, h), open));
			} else {
				Draw.rect(g, b.x, y, b.w, b.h, 7, Draw.alpha(Draw.mix(0xCC121925, 0xEE1C2638, h), open));
				Draw.outline(g, b.x, y, b.w, b.h, 7, 1, Draw.alpha(Draw.mix(Theme.BORDER, Theme.ACCENT, h), open));
			}
			Component label = b.primary ? Draw.bold(b.label) : Draw.ui(b.label);
			int color = Draw.alpha((alpha << 24) | 0xFFFFFF, open);
			Draw.centered(g, label, b.x + b.w / 2, y + (b.h - 8) / 2f, 1f, color);
		}

		String version = "Moonvibe " + Moonvibe.VERSION + "  ·  Minecraft " + Mc.versionName();
		Draw.text(g, Draw.ui(version), 6, height - 13, Draw.alpha(0x99FFFFFF, open));
		Component copyright = Draw.ui(COPYRIGHT);
		float cw = Draw.width(copyright);
		boolean overCopyright = mouseX >= width - cw - 6 && mouseY >= height - 14;
		Draw.text(g, copyright, width - cw - 6, height - 13, Draw.alpha(overCopyright ? 0xFFFFFFFF : 0x99FFFFFF, open));

		Component user = Draw.ui(Component.translatable("moonvibe.title.logged_in", minecraft.getUser().getName()));
		Draw.text(g, user, width - Draw.width(user) - 8, 8, Draw.alpha(0xAAFFFFFF, open));
	}

	private static boolean inside(double mx, double my, Button b) {
		return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
	}

	@Override
	protected boolean onMouseClicked(double mx, double my, int button) {
		if (button != 0) return false;
		for (Button b : buttons) {
			if (b.active && inside(mx, my, b)) {
				Mc.playClickSound();
				b.action.run();
				return true;
			}
		}
		float cw = Draw.width(Draw.ui(COPYRIGHT));
		if (mx >= width - cw - 6 && my >= height - 14) {
			Mc.setScreen(Mc.creditsScreen(this));
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
