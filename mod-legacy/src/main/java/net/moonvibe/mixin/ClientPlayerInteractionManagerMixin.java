package net.moonvibe.mixin;

import net.moonvibe.feature.CombatTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Treffer für Combo- und Reichweiten-Anzeige zählen (nur Anzeige, ändert nichts am Spiel). */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void moonvibe$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
		CombatTracker.onAttack(player, target);
	}
}
