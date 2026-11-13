package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

/** Kompass-Leiste oben in der Mitte (wie bei Lunar „Direction HUD“). */
public final class CompassModule extends HudModule {
	private static final int W = 160;
	private static final int H = 18;
	private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	public final BoolSetting background = add(new BoolSetting("background", true));
	public final BoolSetting showDegrees = add(new BoolSetting("show_degrees", true));

	public CompassModule() {
		super("compass", new ItemStack(Items.COMPASS), false, CENTER, 22);
	}

	@Override
	public int width(MinecraftClient mc) {
		return W;
	}

	@Override
	public int height(MinecraftClient mc) {
		return H + (showDegrees.on() ? 10 : 0);
	}

	@Override
	public void render(MinecraftClient mc) {
		float yaw = mc.player != null ? MathHelper.wrapDegrees(mc.player.yaw) : 0;
		if (background.on()) Draw.rect(0, 0, W, H, 4, 0x8C0B0F17);
		// 1 Grad = 1 Pixel; Markierungen alle 15°, Himmelsrichtungen alle 45°; zum Rand hin ausblenden
		for (int deg = -180; deg <= 540; deg += 15) {
			float x = W / 2f + (deg - yaw);
			if (x < 4 || x > W - 4) continue;
			float fade = Math.min(1, Math.min(x - 4, W - 4 - x) / 14f);
			int norm = Math.floorMod(deg, 360);
			if (norm % 45 == 0) {
				String label = POINTS[norm / 45];
				int color = label.equals("N") ? 0xFFFF6B6B : label.length() == 1 ? 0xFFFFFFFF : 0xFFB8C2D6;
				Draw.mcText(label, Math.round(x - Draw.mcWidth(label) / 2f), 5, Draw.alpha(color, fade), true);
			} else {
				Draw.fill(Math.round(x), 7, Math.round(x) + 1, 11, Draw.alpha(0x88FFFFFF, fade));
			}
		}
		Draw.fill(W / 2, 1, W / 2 + 1, 4, Theme.GOLD);
		if (showDegrees.on()) {
			String dir = mc.player != null ? mc.player.getHorizontalDirection().getName() : "north";
			String text = Math.floorMod((int) yaw + 180, 360) + "°  " + Mv.tr("moonvibe.direction." + dir);
			Draw.mcText(text, (W - Draw.mcWidth(text)) / 2, H + 1, 0xFFDDE3EE, true);
		}
	}
}
