package net.moonvibe.module.mechanic;

import net.moonvibe.module.Module;
import net.moonvibe.module.setting.BoolSetting;
import net.minecraft.world.item.Items;

/** Cosmetics (Capes, Hüte, Flügel) von moonvibe-Spielern anzeigen. */
public final class CosmeticsModule extends Module {
	public final BoolSetting showOwn = add(new BoolSetting("show_own", true));
	public final BoolSetting showOthers = add(new BoolSetting("show_others", true));
	public final BoolSetting animations = add(new BoolSetting("animations", true));

	public CosmeticsModule() {
		super("cosmetics", Category.CLIENT, Items.ELYTRA, true);
	}
}
