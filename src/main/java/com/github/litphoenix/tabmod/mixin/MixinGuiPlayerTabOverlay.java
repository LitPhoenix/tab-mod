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

        // 2. Process and inject tags if mod is active
        if (ExampleMod.isModEnabled) {
            String rawName = networkPlayerInfoIn.getGameProfile().getName().toLowerCase();
            
            // Use real UUID if user has mapped this nickname, otherwise use default
            UUID playerUuid = ExampleMod.nickMap.containsKey(rawName) 
                ? ExampleMod.nickMap.get(rawName) 
                : networkPlayerInfoIn.getGameProfile().getId();

            String wlrTag = ExampleMod.getWlrTag(playerUuid);

            if (wlrTag != null) {
                // Success or Under 10 wins (from cache)
                name = wlrTag + " " + name;
            } else if (ExampleMod.isFailed(playerUuid)) {
                // API check failed or player is nicked (Not in local mapping)
                name = "\u00A78[\u00A7c?\u00A78]\u00A7f " + name;
            } else {
                // Data is queued to be fetched
                name = "\u00A78[...]\u00A7f " + name;
                ExampleMod.queueFetch(playerUuid);
            }
        }

        cir.setReturnValue(name);
    }
}
