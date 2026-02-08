package ru.mgcveqbj.pearlnavigator.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mgcveqbj.pearlnavigator.PearlNavigator;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(
        method = "tickMovement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/input/Input;tick(ZF)V",
            shift = At.Shift.AFTER
        )
    )
    private void pearlnavigator$applyNudge(CallbackInfo ci) {
        PearlNavigator.applyNudge((ClientPlayerEntity) (Object) this);
    }
}
