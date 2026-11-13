package net.moonvibe.module.hud;

import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Kompass-Leiste oben in der Mitte (wie bei Lunar „Direction HUD“). */
public final class CompassModule extends HudModule {
	private static final int W = 160;
	private static final int H = 18;
	private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	public final BoolSetting background = add(new BoolSetting("background", true));
	public final BoolSetting showDegrees = add(new BoolSetting("show_degrees", true));

	public CompassModule() {
		super("compass", Items.RECOVERY_COMPASS, false, CENTER, 22);
	}

	@Override
	public int width(Minecraft mc) {
		return W;
	}

	@Override
	public int height(Minecraft mc) {
		return H + (showDegrees.on() ? 10 : 0);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Minecraft mc) {
		float yaw = mc.player != null ? Mth.wrapDegrees(mc.player.getYRot()) : 0;
		if (background.on()) Draw.rect(g, 0, 0, W, H, 4, 0x8C0B0F17);
		Draw.scissor(g, 0, 0, W, H);
		// 1 Grad = 1 Pixel; Markierungen alle 15°, Himmelsrichtungen alle 45°
		for (int deg = -180; deg <= 540; deg += 15) {
			float x = W / 2f + (deg - yaw);
			if (x < -10 || x > W + 10) continue;
			int norm = Math.floorMod(deg, 360);
			if (norm % 45 == 0) {
				String label = POINTS[norm / 45];
				int color = label.equals("N") ? 0xFFFF6B6B : label.length() == 1 ? 0xFFFFFFFF : 0xFFB8C2D6;
				Draw.mcText(g, label, Math.round(x - mc.font.width(label) / 2f), 5, color, true);
			} else {
				Draw.fill(g, Math.round(x), 7, Math.round(x) + 1, 11, 0x88FFFFFF);
			}
		}
		Draw.endScissor(g);
		Draw.fill(g, W / 2, 1, W / 2 + 1, 4, Theme.GOLD);
		if (showDegrees.on()) {
			String text = Math.round(Math.floorMod((int) yaw + 180, 360)) + "°  " + I18n.get("moonvibe.direction." + (mc.player != null ? mc.player.getDirection().getSerializedName() : "north"));
			Draw.mcText(g, text, (W - mc.font.width(text)) / 2, H + 1, 0xFFDDE3EE, true);
		}
	}
}
