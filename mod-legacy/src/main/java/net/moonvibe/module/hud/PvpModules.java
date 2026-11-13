package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.feature.CombatTracker;
import net.moonvibe.module.setting.BoolSetting;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** PvP-Anzeigen: Toggle Sprint/Sneak, Combo, Reichweite, Geschwindigkeit. */
public final class PvpModules {
	private PvpModules() {}

	private static List<String> one(String line) {
		return Collections.singletonList(line);
	}

	/** Dauer-Sprinten und Schleichen per Umschalten (1.8.9 hat das noch nicht eingebaut). */
	public static final class ToggleSprint extends TextHudModule {
		public final BoolSetting toggleSprint = add(new BoolSetting("toggle_sprint", true));
		public final BoolSetting toggleSneak = add(new BoolSetting("toggle_sneak", false));

		private boolean sneakToggled;
		private boolean sneakWasDown;

		public ToggleSprint() {
			super("togglesprint", new ItemStack(Items.LEATHER_BOOTS), false, 4, -40);
		}

		/** Jeden Tick: Sprint-Taste gedrückt halten bzw. Schleichen umschalten. */
		public void apply(MinecraftClient mc) {
			if (!enabled() || mc.player == null || mc.currentScreen != null) return;
			GameOptions o = mc.options;
			if (toggleSprint.on()) Mv.setPressed(o.sprintKey, true);
			if (toggleSneak.on()) {
				boolean down = Mv.physicallyDown(o.sneakKey);
				if (down && !sneakWasDown) sneakToggled = !sneakToggled;
				sneakWasDown = down;
				Mv.setPressed(o.sneakKey, sneakToggled);
			}
		}

		@Override
		protected void onDisable() {
			GameOptions o = Mv.mc().options;
			sneakToggled = false;
			Mv.setPressed(o.sprintKey, Mv.physicallyDown(o.sprintKey));
			Mv.setPressed(o.sneakKey, Mv.physicallyDown(o.sneakKey));
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			ClientPlayerEntity p = mc.player;
			boolean sneaking = p != null && p.isSneaking();
			boolean sprinting = p != null && p.isSprinting();
			if (sneaking) return one(Mv.tr(toggleSneak.on() && sneakToggled ? "moonvibe.hud.sneaking_toggled" : "moonvibe.hud.sneaking"));
			if (sprinting || editing()) return one(Mv.tr(toggleSprint.on() ? "moonvibe.hud.sprinting_toggled" : "moonvibe.hud.sprinting"));
			return one(Mv.tr("moonvibe.hud.walking"));
		}
	}

	public static final class Combo extends TextHudModule {
		public Combo() {
			super("combo", new ItemStack(Items.IRON_SWORD), false, 4, 180);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			return one(Mv.tr("moonvibe.hud.combo", editing() && CombatTracker.combo() == 0 ? 3 : CombatTracker.combo()));
		}
	}

	public static final class Reach extends TextHudModule {
		public Reach() {
			super("reach", new ItemStack(Items.STICK), false, 4, 198);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			double reach = CombatTracker.reach();
			if (reach < 0) return one(editing() ? "2.87 " + Mv.tr("moonvibe.hud.blocks") : Mv.tr("moonvibe.hud.reach_none"));
			return one(String.format("%.2f %s", reach, Mv.tr("moonvibe.hud.blocks")));
		}
	}

	public static final class Speed extends TextHudModule {
		private double lastX;
		private double lastZ;
		private long lastTick;
		private double speed;

		public Speed() {
			super("speed", new ItemStack(Items.FEATHER), false, 4, 216);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			ClientPlayerEntity p = mc.player;
			if (p != null && p.ticksAlive != lastTick) {
				// Blöcke pro Sekunde aus der Bewegung seit dem letzten Tick (20 Ticks = 1 s)
				speed = speed * 0.7 + Math.hypot(p.x - lastX, p.z - lastZ) * 20 * 0.3;
				lastX = p.x;
				lastZ = p.z;
				lastTick = p.ticksAlive;
			}
			return one(String.format("%.1f %s", editing() && speed < 0.05 ? 5.6 : speed, Mv.tr("moonvibe.hud.bps")));
		}
	}
}
