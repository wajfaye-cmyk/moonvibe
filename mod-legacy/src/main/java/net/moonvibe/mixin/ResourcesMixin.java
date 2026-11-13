//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.Moonvibe;
//$$ import java.io.File;
//$$ import java.nio.file.Path;
//$$ import java.util.List;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.minecraft.class_4454;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.resource.DirectoryResourcePack;
//$$ import net.minecraft.resource.ZipResourcePack;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$$
//$$ /**
//$$  * Bilder, Schriften und Übersetzungen der Mod für Minecraft einbinden. Bis 1.12 erledigt das die Legacy-Fabric-API –
//$$  * für 1.13 gibt es keine, daher kommt das Mod-Archiv hier selbst als Ressourcenpaket dazu.
//$$  */
//$$ @Mixin(MinecraftClient.class)
//$$ public class ResourcesMixin {
//$$ 	@ModifyVariable(method = "reloadResources", at = @At("STORE"), ordinal = 0)
//$$ 	private List<class_4454> moonvibe$addModPack(List<class_4454> packs) { // class_4454 = ResourcePack
//$$ 		FabricLoader.getInstance().getModContainer(Moonvibe.MOD_ID).ifPresent(mod -> {
//$$ 			for (Path root : mod.getOrigin().getPaths()) {
//$$ 				File file = root.toFile();
//$$ 				packs.add(file.isDirectory() ? new DirectoryResourcePack(file) : new ZipResourcePack(file));
//$$ 			}
//$$ 		});
//$$ 		return packs;
//$$ 	}
//$$ }
//#endif
