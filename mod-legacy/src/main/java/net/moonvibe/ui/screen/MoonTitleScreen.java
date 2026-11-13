package net.moonvibe.ui.screen;

import net.moonvibe.Mv;
import net.moonvibe.Moonvibe;
//#if MC < 1.13
import net.moonvibe.mixin.TitleScreenAccessor;
//#endif
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
//#if MC >= 1.9
//$$ import net.minecraft.sound.Sounds;
//#endif
//#if MC >= 1.13
//$$ import net.minecraft.class_4216;
//$$ import net.minecraft.class_4227;
//#endif
import net.minecraft.util.Identifier;

/** Eigenes Hauptmenü von moonvibe (ersetzt das Minecraft-Titelmenü) – mit dem drehenden Minecraft-Panorama dahinter. */
public class MoonTitleScreen extends MoonScreen {
	private static final Identifier LOGO = Mv.id("textures/gui/title.png");

	private static final class Button {
		final String id;
		final float x, y, w, h;
		final String label;
		final boolean primary;
		final Runnable action;

		Button(String id, float x, float y, float w, float h, String label, boolean primary, Runnable action) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.label = label;
			this.primary = primary;
			this.action = action;
		}
	}

	private final List<Button> buttons = new ArrayList<Button>();
	//#if MC >= 1.13
	//$$ /** Drehendes Panorama (RotatingCubeMapRenderer – in den 1.13-Namen unbenannt) */
	//$$ private final class_4227 panorama = new class_4227(new class_4216(new Identifier("textures/gui/title/background/panorama")));
	//#else
	/** Unsichtbares Minecraft-Hauptmenü – nur für das Panorama im Hintergrund */
	private final TitleScreen panorama = new TitleScreen();
	//#endif

	public MoonTitleScreen() {
		Anim.set("title.open", 0);
	}

	@Override
	public void init() {
		//#if MC < 1.13
		panorama.init(client, width, height);
		//#endif
		buttons.clear();
		float bw = 210;
		float x = width / 2f - bw / 2;
		float y = height * 0.5f;
		buttons.add(new Button("single", x, y, bw, 26, Mv.tr("menu.singleplayer"), true, () -> client.setScreen(new SelectWorldScreen(this))));
		buttons.add(new Button("multi", x, y + 32, bw, 26, Mv.tr("menu.multiplayer"), false, () -> client.setScreen(new MultiplayerScreen(this))));
		float small = (bw - 8) / 3f;
		float row = y + 64;
		buttons.add(new Button("options", x, row, small, 22, Mv.tr("menu.options"), false, () -> client.setScreen(new SettingsScreen(this, client.options))));
		buttons.add(new Button("language", x + small + 4, row, small, 22, Mv.tr("options.language"), false,
			() -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager()))));
		buttons.add(new Button("quit", x + (small + 4) * 2, row, small, 22, Mv.tr("menu.quit"), false, client::scheduleStop));
	}

	//#if MC < 1.13
	@Override
	public void tick() {
		panorama.tick();
	}
	//#endif

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		//#if MC >= 1.13
		//$$ panorama.method_19176(delta); // render
		//#else
		((TitleScreenAccessor) panorama).moonvibe$renderBackground(mouseX, mouseY, delta);
		//#endif
		Draw.fillGradient(0, 0, width, height, 0x88060910, 0xEE060910);
		float open = Anim.easeOut(Anim.approach("title.open", 1, 4));
		float slide = (1 - open) * 16;

		float logoW = Math.min(340, width * 0.55f);
		float logoH = logoW * 341f / 1024f;
		float logoY = Math.max(10, height * 0.5f - logoH - 18) - slide;
		Draw.image(LOGO, Math.round(width / 2f - logoW / 2), Math.round(logoY), Math.round(logoW), Math.round(logoH), open);

		for (Button b : buttons) {
			boolean over = inside(mouseX, mouseY, b);
			float h = Anim.approach("title.btn." + b.id, over ? 1 : 0, 16);
			float y = b.y + slide * 0.6f;
			if (b.primary) {
				Draw.gradient(b.x, y, b.w, b.h, 7, Draw.alpha(Draw.mix(Theme.ACCENT, Theme.ACCENT_LIGHT, h), open),
					Draw.alpha(Draw.mix(0xFF1F7FE0, Theme.ACCENT, h), open));
			} else {
				Draw.rect(b.x, y, b.w, b.h, 7, Draw.alpha(Draw.mix(0xCC121925, 0xEE1C2638, h), open));
				Draw.outline(b.x, y, b.w, b.h, 7, 1, Draw.alpha(Draw.mix(Theme.BORDER, Theme.ACCENT, h), open));
			}
			Draw.Txt label = b.primary ? Draw.bold(b.label) : Draw.ui(b.label);
			Draw.centered(label, b.x + b.w / 2, y + (b.h - 8) / 2f, 1f, Draw.alpha(0xFFFFFFFF, open));
		}

		String version = "Moonvibe " + Moonvibe.VERSION + "  ·  Minecraft " + Mv.minecraftVersion();
		Draw.text(Draw.ui(version), 6, height - 13, Draw.alpha(0x99FFFFFF, open));
		Draw.Txt copyright = Draw.ui("Copyright Mojang AB. Do not distribute!");
		float cw = Draw.width(copyright);
		boolean overCopyright = mouseX >= width - cw - 6 && mouseY >= height - 14;
		Draw.text(copyright, width - cw - 6, height - 13, Draw.alpha(overCopyright ? 0xFFFFFFFF : 0x99FFFFFF, open));

		Draw.Txt user = Draw.tr("moonvibe.title.logged_in", client.getSession().getUsername());
		Draw.text(user, width - Draw.width(user) - 8, 8, Draw.alpha(0xAAFFFFFF, open));
	}

	private static boolean inside(double mx, double my, Button b) {
		return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
	}

	@Override
	protected void onMouseClicked(int mouseX, int mouseY, int button) {
		if (button != 0) return;
		for (Button b : buttons) {
			if (inside(mouseX, mouseY, b)) {
				//#if MC >= 1.9
				//$$ client.getSoundManager().play(PositionedSoundInstance.method_12521(Sounds.UI_BUTTON_CLICK, 1.0F)); // master
				//#else
				client.getSoundManager().play(PositionedSoundInstance.master(new Identifier("gui.button.press"), 1.0F));
				//#endif
				b.action.run();
				return;
			}
		}
		float cw = Draw.width(Draw.ui("Copyright Mojang AB. Do not distribute!"));
		if (mouseX >= width - cw - 6 && mouseY >= height - 14) {
			//#if MC >= 1.12
			//$$ client.setScreen(new CreditsScreen(false, () -> client.setScreen(this)));
			//#else
			client.setScreen(new CreditsScreen());
			//#endif
		}
	}

	@Override
	public void onClose() {
		// Hauptmenü bleibt offen (ESC schließt es nicht)
	}
}
