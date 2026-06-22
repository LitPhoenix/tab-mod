package com.github.litphoenix.tabmod.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FontRenderer.class)
public class MixinFontRenderer {
    
    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true)
    private String clearScrambling(String text) {
        if (text == null) return null;
        
        if (text.contains("\u00A7kXXXX")) {
            text = text.replace("\u00A7kXXXX", "");
        }
        if (text.contains("\u00A7k")) {
            text = text.replace("\u00A7k", "");
        }
        
        text = text.replaceAll("O+([a-zA-Z0-9_]{3,16})O+", "$1");
        
        return text;
    }
}
