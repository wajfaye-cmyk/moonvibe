package net.moonvibe.module.hud;

import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.ColorSetting;
import net.moonvibe.ui.Draw;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/** HUD-Modul mit einer oder mehreren Textzeilen – mit Hintergrund, Farbe, Schatten und Klammern einstellbar. */
public abstract class TextHudModule extends HudModule {
	private static final int PAD_X = 5;
	private static final int PAD_Y = 4;
	private static final int LINE = 10;

	public final BoolSetting background = add(new BoolSetting("background", true));
	public final ColorSetting textColor = add(new ColorSetting("text_color", 0xFFFFFFFF));
	public final BoolSetting shadow = add(new BoolSetting("text_shadow", true));
	public final BoolSetting brackets = add(new BoolSetting("brackets", false));

	protected TextHudModule(String id, ItemStack icon, boolean enabledByDefault, float defaultX, float defaultY) {
		super(id, icon, enabledByDefault, defaultX, defaultY);
	}

	protected abstract List<String> lines(MinecraftClient mc);

	private String decorate(String line) {
		return brackets.on() ? "[" + line + "]" : line;
	}

	@Override
	public int width(MinecraftClient mc) {
		int widest = 0;
		for (String line : lines(mc)) widest = Math.max(widest, Draw.mcWidth(decorate(line)));
		return Math.max(widest + PAD_X * 2, 56);
	}

	@Override
	public int height(MinecraftClient mc) {
		return lines(mc).size() * LINE + PAD_Y * 2 - 2;
	}

	@Override
	public void render(MinecraftClient mc) {
		List<String> lines = lines(mc);
		int w = width(mc);
		int h = lines.size() * LINE + PAD_Y * 2 - 2;
		if (background.on()) Draw.rect(0, 0, w, h, 3, 0x8C0B0F17);
		for (int i = 0; i < lines.size(); i++) {
			String line = decorate(lines.get(i));
			int x = (w - Draw.mcWidth(line)) / 2;
			Draw.mcText(line, x, PAD_Y + i * LINE, textColor.color(), shadow.on());
		}
	}
}
