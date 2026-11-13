package net.moonvibe.module.mechanic;

import net.moonvibe.Mv;
import net.moonvibe.module.Module;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.ChoiceSetting;
import net.moonvibe.module.setting.ColorSetting;
import net.moonvibe.module.setting.SliderSetting;
import net.moonvibe.ui.Draw;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Eigenes Fadenkreuz: Form, Farbe, Größe, Abstand und Umrandung frei einstellbar. */
public final class CrosshairModule extends Module {
	public final ChoiceSetting style = add(new ChoiceSetting("crosshair_style", "cross", Arrays.asList("cross", "dot", "circle", "cross_dot")));
	public final ColorSetting color = add(new ColorSetting("color", 0xFF4FD1FF));
	public final SliderSetting size = add(new SliderSetting("crosshair_size", 5, 2, 12, 1, v -> String.valueOf(Math.round(v))));
	public final SliderSetting gap = add(new SliderSetting("crosshair_gap", 2, 0, 6, 1, v -> String.valueOf(Math.round(v))));
	public final SliderSetting thickness = add(new SliderSetting("crosshair_thickness", 1, 1, 3, 1, v -> String.valueOf(Math.round(v))));
	public final BoolSetting outline = add(new BoolSetting("outline", true));

	public CrosshairModule() {
		super("crosshair", Category.MECHANIC, new ItemStack(Items.BOW), false);
	}

	/** Soll das eigene statt des normalen Fadenkreuzes gezeichnet werden? */
	public boolean active(MinecraftClient mc) {
		return enabled() && mc.options.perspective == 0 && !Mv.hudHidden();
	}

	public void render(MinecraftClient mc) {
		if (!active(mc)) return;
		float cx = Mv.width() / 2f;
		float cy = Mv.height() / 2f;
		float s = size.value();
		float gp = gap.value();
		float th = thickness.value();
		int c = color.color();
		int shadow = 0xC0000000;

		if (style.is("cross") || style.is("cross_dot")) {
			float[][] bars = {
				{cx - gp - s, cy - th / 2, s, th},
				{cx + gp, cy - th / 2, s, th},
				{cx - th / 2, cy - gp - s, th, s},
				{cx - th / 2, cy + gp, th, s},
			};
			if (outline.on()) for (float[] b : bars) Draw.rect(b[0] - 0.5f, b[1] - 0.5f, b[2] + 1, b[3] + 1, 0, shadow);
			for (float[] b : bars) Draw.rect(b[0], b[1], b[2], b[3], 0, c);
		}
		if (style.is("dot") || style.is("cross_dot")) {
			float r = Math.max(1, th);
			if (outline.on()) Draw.circle(cx, cy, r + 0.6f, shadow);
			Draw.circle(cx, cy, r, c);
		}
		if (style.is("circle")) {
			float r = s + gp;
			if (outline.on()) Draw.outline(cx - r - 0.5f, cy - r - 0.5f, (r + 0.5f) * 2, (r + 0.5f) * 2, r + 0.5f, th + 1, shadow);
			Draw.outline(cx - r, cy - r, r * 2, r * 2, r, th, c);
			Draw.circle(cx, cy, 0.8f, c);
		}
	}
}
