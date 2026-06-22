package com.github.litphoenix.tabmod.mixin;

import com.github.litphoenix.tabmod.ExampleMod;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    public void injectWlrAndClean(NetworkPlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        String name = cir.getReturnValue();
        if (name == null) return;

        if (ExampleMod.isWlrEnabled()) {
            ExampleMod.fetchWLR(networkPlayerInfoIn);
            String wlrTag = ExampleMod.getWlrTag(networkPlayerInfoIn.getGameProfile().getId());
            if (wlrTag != null) {
                name = wlrTag + name;
            }
        }

        cir.setReturnValue(name);
    }
}
