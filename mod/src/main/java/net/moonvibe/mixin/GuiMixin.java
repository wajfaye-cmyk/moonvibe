package net.moonvibe.mixin;

import net.moonvibe.ui.screen.MoonTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//#if MC >= 26.2
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Final;
//#endif

/** Ersetzt das Minecraft-Hauptmenü durch das moonvibe-Hauptmenü (setScreen liegt ab 26.2 in Gui, davor in Minecraft). */
//#if MC >= 26.2
@Mixin(Gui.class)
//#else
//$$ @Mixin(Minecraft.class)
//#endif
public class GuiMixin {
	//#if MC >= 26.2
	@Shadow
	@Final
	private Minecraft minecraft;
	//#endif

	//#if MC >= 1.20
	@Shadow
	private boolean clientLevelTeardownInProgress;
	//#endif

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen moonvibe$replaceTitleScreen(Screen screen) {
		//#if MC >= 26.2
		Minecraft mc = minecraft;
		//#else
		//$$ Minecraft mc = (Minecraft) (Object) this;
		//#endif
		if (mc.isDemo()) return screen;
		// Minecraft öffnet das Hauptmenü entweder direkt oder über setScreen(null) ohne Welt
		//#if MC >= 1.20
		boolean title = screen instanceof TitleScreen || (screen == null && mc.level == null && !clientLevelTeardownInProgress);
		//#else
		//$$ boolean title = screen instanceof TitleScreen || (screen == null && mc.level == null);
		//#endif
		return title ? new MoonTitleScreen() : screen;
	}
}
