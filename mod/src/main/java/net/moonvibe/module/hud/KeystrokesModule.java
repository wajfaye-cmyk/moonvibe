package net.moonvibe.module.hud;

import net.moonvibe.feature.CpsTracker;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.ColorSetting;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

/** WASD, Maustasten (mit CPS) und Leertaste – gedrückte Tasten leuchten weich auf. */
public final class KeystrokesModule extends HudModule {
	private static final int KEY = 22;
	private static final int GAP = 2;
	private static final int MOUSE_H = 18;
	private static final int SPACE_H = 11;

	public final BoolSetting showMouse = add(new BoolSetting("show_mouse", true));
	public final BoolSetting showSpace = add(new BoolSetting("show_space", true));
	public final ColorSetting pressedColor = add(new ColorSetting("pressed_color", 0xFFFFFFFF));
	public final ColorSetting textColor = add(new ColorSetting("text_color", 0xFFFFFFFF));

	public KeystrokesModule() {
		super("keystrokes", Items.TRIAL_KEY, true, 4, 64);
	}

	@Override
	public int width(Minecraft mc) {
		return KEY * 3 + GAP * 2;
	}

	@Override
	public int height(Minecraft mc) {
		int h = KEY * 2 + GAP;
		if (showMouse.on()) h += GAP + MOUSE_H;
		if (showSpace.on()) h += GAP + SPACE_H;
		return h;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Minecraft mc) {
		var o = mc.options;
		int w = width(mc);
		key(g, mc, "w", o.keyUp, "W", KEY + GAP, 0, KEY, KEY);
		key(g, mc, "a", o.keyLeft, "A", 0, KEY + GAP, KEY, KEY);
		key(g, mc, "s", o.keyDown, "S", KEY + GAP, KEY + GAP, KEY, KEY);
		key(g, mc, "d", o.keyRight, "D", (KEY + GAP) * 2, KEY + GAP, KEY, KEY);

		int y = KEY * 2 + GAP * 2;
		if (showMouse.on()) {
			int half = (w - GAP) / 2;
			int left = CpsTracker.left();
			int right = CpsTracker.right();
			key(g, mc, "lmb", o.keyAttack, left > 0 ? left + " CPS" : "LMB", 0, y, half, MOUSE_H);
			key(g, mc, "rmb", o.keyUse, right > 0 ? right + " CPS" : "RMB", half + GAP, y, half, MOUSE_H);
			y += MOUSE_H + GAP;
		}
		if (showSpace.on()) {
			float t = press("space", o.keyJump.isDown());
			Draw.rect(g, 0, y, w, SPACE_H, 3, Draw.mix(0x8C0B0F17, withAlpha(pressedColor.color(), 0xE0), t));
			int bar = Draw.mix(textColor.color(), 0xFF111111, t);
			Draw.fill(g, w / 2 - 12, y + 5, w / 2 + 12, y + 6, bar);
		}
	}

	private float press(String id, boolean down) {
		return Anim.approach("keystrokes." + id, down ? 1f : 0f, 18f);
	}

	private void key(GuiGraphicsExtractor g, Minecraft mc, String id, KeyMapping mapping, String label, int x, int y, int w, int h) {
		float t = press(id, mapping.isDown());
		Draw.rect(g, x, y, w, h, 3, Draw.mix(0x8C0B0F17, withAlpha(pressedColor.color(), 0xE0), t));
		int color = Draw.mix(textColor.color(), 0xFF111111, t);
		Draw.mcText(g, label, x + (w - mc.font.width(label)) / 2, y + (h - 8) / 2, color, t < 0.5f);
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0xFFFFFF) | (alpha << 24);
	}
}
