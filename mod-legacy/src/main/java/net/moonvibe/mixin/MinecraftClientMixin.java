package net.moonvibe.mixin;

import net.moonvibe.ui.screen.MoonTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	public ClientWorld world;

	/** Ersetzt das Minecraft-Hauptmenü durch das moonvibe-Hauptmenü. */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen moonvibe$replaceTitleScreen(Screen screen) {
		boolean title = screen instanceof TitleScreen || (screen == null && world == null);
		return title ? new MoonTitleScreen() : screen;
	}
}
