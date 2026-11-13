//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.minecraft.client.render.entity.LivingEntityRenderer;
//$$ import net.minecraft.client.render.entity.feature.FeatureRenderer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ @Mixin(LivingEntityRenderer.class)
//$$ public interface LivingEntityRendererAccessor {
//$$ 	@Invoker("addFeature")
//$$ 	boolean moonvibe$addFeature(FeatureRenderer<?> feature);
//$$ }
//#endif
