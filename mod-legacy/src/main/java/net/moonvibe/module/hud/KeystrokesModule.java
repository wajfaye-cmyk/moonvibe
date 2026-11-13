package net.moonvibe.module.hud;

import net.moonvibe.feature.CpsTracker;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.ColorSetting;
import net.moonvibe.ui.Anim;
import net.moonvibe.ui.Draw;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;

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
		super("keystrokes", new ItemStack(Blocks.TRIPWIRE_HOOK), true, 4, 64);
	}

	@Override
	public int width(MinecraftClient mc) {
		return KEY * 3 + GAP * 2;
	}

	@Override
	public int height(MinecraftClient mc) {
		int h = KEY * 2 + GAP;
		if (showMouse.on()) h += GAP + MOUSE_H;
		if (showSpace.on()) h += GAP + SPACE_H;
		return h;
	}

	@Override
	public void render(MinecraftClient mc) {
		GameOptions o = mc.options;
		int w = width(mc);
		key("w", o.forwardKey, "W", KEY + GAP, 0, KEY, KEY);
		key("a", o.leftKey, "A", 0, KEY + GAP, KEY, KEY);
		key("s", o.backKey, "S", KEY + GAP, KEY + GAP, KEY, KEY);
		key("d", o.rightKey, "D", (KEY + GAP) * 2, KEY + GAP, KEY, KEY);

		int y = KEY * 2 + GAP * 2;
		if (showMouse.on()) {
			int half = (w - GAP) / 2;
			int left = CpsTracker.left();
			int right = CpsTracker.right();
			key("lmb", o.attackKey, left > 0 ? left + " CPS" : "LMB", 0, y, half, MOUSE_H);
			key("rmb", o.useKey, right > 0 ? right + " CPS" : "RMB", half + GAP, y, half, MOUSE_H);
			y += MOUSE_H + GAP;
		}
		if (showSpace.on()) {
			float t = press("space", o.jumpKey.isPressed());
			Draw.rect(0, y, w, SPACE_H, 3, Draw.mix(0x8C0B0F17, withAlpha(pressedColor.color(), 0xE0), t));
			int bar = Draw.mix(textColor.color(), 0xFF111111, t);
			Draw.fill(w / 2 - 12, y + 5, w / 2 + 12, y + 6, bar);
		}
	}

	private float press(String id, boolean down) {
		return Anim.approach("keystrokes." + id, down ? 1f : 0f, 18f);
	}

	private void key(String id, KeyBinding binding, String label, int x, int y, int w, int h) {
		float t = press(id, binding.isPressed());
		Draw.rect(x, y, w, h, 3, Draw.mix(0x8C0B0F17, withAlpha(pressedColor.color(), 0xE0), t));
		int color = Draw.mix(textColor.color(), 0xFF111111, t);
		Draw.mcText(label, x + (w - Draw.mcWidth(label)) / 2, y + (h - 8) / 2, color, t < 0.5f);
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0xFFFFFF) | (alpha << 24);
	}
}
