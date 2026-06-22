package com.github.litphoenix.tabmod.mixin;

import com.github.litphoenix.tabmod.ExampleMod;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    public void injectWlrAndClean(NetworkPlayerInfo networkPlayerInfoIn, CallbackInfoReturnable<String> cir) {
        String name = cir.getReturnValue();
        if (name == null || networkPlayerInfoIn.getGameProfile() == null) return;

        // 1. Always run deobfuscation
        if (name.contains("\u00A7kXXXX")) name = name.replace("\u00A7kXXXX", "");
        if (name.contains("\u00A7k")) name = name.replace("\u00A7k", "");
        name = name.replaceAll("O+([a-zA-Z0-9_]{3,16})O+", "$1");

        // 2. Only inject WLR tags if the mod is toggled ON
        if (ExampleMod.isModEnabled) {
            UUID playerUuid = networkPlayerInfoIn.getGameProfile().getId();
            String wlrTag = ExampleMod.getWlrTag(playerUuid);

            if (wlrTag != null) {
                name = wlrTag + " " + name;
            }
        }

        cir.setReturnValue(name);
    }
}
