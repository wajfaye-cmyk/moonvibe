package net.moonvibe.feature;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zählt Treffer-Combos und misst die Reichweite des letzten Schlags (nur Anzeige, keine Änderung am Spiel). */
public final class CombatTracker {
	private static int combo;
	private static long lastHit;
	private static double lastReach = -1;
	private static long lastReachTime;
	private static int lastHurtTime;

	private CombatTracker() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) onAttack((LocalPlayer) player, entity);
			return InteractionResult.PASS;
		});
	}

	private static void onAttack(LocalPlayer player, Entity target) {
		long now = System.currentTimeMillis();
		combo = now - lastHit < 2000 ? combo + 1 : 1;
		lastHit = now;
		// Abstand vom Auge zum nächsten Punkt der Hitbox – so messen es auch Lunar und Badlion
		Vec3 eye = player.getEyePosition(1.0F);
		AABB box = target.getBoundingBox();
		double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
		double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
		lastReach = eye.distanceTo(new Vec3(x, y, z));
		lastReachTime = now;
	}

	/** Jeden Tick: Combo endet, wenn man getroffen wird oder 2 s nichts trifft. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) return;
		if (player.hurtTime > 0 && lastHurtTime == 0) combo = 0;
		lastHurtTime = player.hurtTime;
		if (System.currentTimeMillis() - lastHit > 2000) combo = 0;
	}

	public static int combo() {
		return combo;
	}

	/** Reichweite des letzten Schlags oder -1, wenn er länger als 3 s her ist. */
	public static double reach() {
		return System.currentTimeMillis() - lastReachTime < 3000 ? lastReach : -1;
	}
}
