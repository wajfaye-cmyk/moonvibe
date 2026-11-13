//#if MC < 1.13
// (1.13: MoonTitleScreen zeichnet das Panorama selbst)
package net.moonvibe.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Das drehende Panorama des Minecraft-Hauptmenüs für das moonvibe-Hauptmenü. */
@Mixin(TitleScreen.class)
public interface TitleScreenAccessor {
	@Invoker("renderBackground")
	void moonvibe$renderBackground(int mouseX, int mouseY, float tickDelta);
}
//#endif
