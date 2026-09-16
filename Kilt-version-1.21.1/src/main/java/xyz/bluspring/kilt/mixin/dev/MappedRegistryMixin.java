package xyz.bluspring.kilt.mixin.dev;

import com.moulberry.mixinconstraints.annotations.IfDevEnvironment;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfDevEnvironment
@Mixin(MappedRegistry.class)
public class MappedRegistryMixin<T> {
    @Shadow @Final private ResourceKey<? extends Registry<T>> key;

    @Inject(method = "freeze", at = @At("HEAD"))
    private void kilt$checkFreezers(CallbackInfoReturnable<Registry<T>> cir) {
        //System.out.println("Registry " + this.key + " was frozen");
        //(new Exception("frozen")).printStackTrace();
    }
}
