package com.github.litphoenix.tabmod.mixin;

import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {

    // This intercepts the text right before the tab menu draws it
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    public void removeTabObfuscation(NetworkPlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        String name = cir.getReturnValue();
        
        if (name != null) {
            // \u00A7 is the section symbol for Minecraft formatting codes
            if (name.contains("\u00A7kXXXX")) {
                name = name.replace("\u00A7kXXXX", "XXXX");
            }
            if (name.contains("\u00A7k")) {
                name = name.replace("\u00A7k", "");
            }
            
            // Replaces the rendered text with our cleaned version
            cir.setReturnValue(name);
        }
    }
}
