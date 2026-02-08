package ru.mgcveqbj.pearlnavigator.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mgcveqbj.pearlnavigator.PearlNavigator;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void pearlnavigator$beforeItemUse(CallbackInfo ci) {
        PearlNavigator.applyBeforeUse((MinecraftClient) (Object) this);
    }
}
