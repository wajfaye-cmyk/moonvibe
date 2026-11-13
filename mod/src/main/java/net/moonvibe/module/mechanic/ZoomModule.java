package net.moonvibe.module.mechanic;

import net.moonvibe.compat.Mc;
import net.moonvibe.Moonvibe;
import net.moonvibe.module.Module;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Zoom auf Taste C (wie OptiFine/Lunar). Mausrad ändert die Stärke, Kamera wird optional weich. */
public final class ZoomModule extends Module {
	public final SliderSetting level = add(new SliderSetting("zoom_level", 4f, 1.5f, 20f, 0.5f, v -> String.format("%.1fx", v)));
	public final BoolSetting smoothCamera = add(new BoolSetting("smooth_camera", true));
	public final BoolSetting scrollToZoom = add(new BoolSetting("scroll_to_zoom", true));
	public final BoolSetting smoothTransition = add(new BoolSetting("smooth_transition", true));

	private float current = 1f;
	private float scrollFactor = 1f;
	private long lastFrame = System.nanoTime();
	private boolean active;
	private boolean previousSmoothCamera;

	public ZoomModule() {
		super("zoom", Category.MECHANIC, Items.SPYGLASS, true);
	}

	public boolean isActive() {
		return active;
	}

	public void tick(Minecraft mc) {
		boolean wanted = enabled() && Moonvibe.zoomKey.isDown() && Mc.screen() == null && mc.player != null;
		if (wanted && !active) {
			previousSmoothCamera = mc.options.smoothCamera;
			if (smoothCamera.on()) mc.options.smoothCamera = true;
			scrollFactor = 1f;
		} else if (!wanted && active) {
			mc.options.smoothCamera = previousSmoothCamera;
		}
		active = wanted;
	}

	public boolean onScroll(double amount) {
		if (!active || !scrollToZoom.on()) return false;
		scrollFactor = Math.max(0.25f, Math.min(5f, scrollFactor * (amount > 0 ? 1.15f : 1 / 1.15f)));
		return true;
	}

	/** Pro Frame mit dem normalen FOV aufgerufen. */
	public float apply(float fov) {
		long now = System.nanoTime();
		float seconds = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		float target = active ? Math.max(1f, level.value() * scrollFactor) : 1f;
		if (smoothTransition.on()) current += (target - current) * Math.min(1f, seconds * 14f);
		else current = target;
		return fov / current;
	}
}
