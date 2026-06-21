package com.github.litphoenix.tabmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.Collection;

@Mod(modid = "tabmod", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class ExampleMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderTab(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.getNetHandler() == null) return;

            Collection<NetworkPlayerInfo> players = mc.getNetHandler().getPlayerInfoMap();
            for (NetworkPlayerInfo player : players) {
                if (player.getDisplayName() != null) {
                    String text = player.getDisplayName().getFormattedText();
                    
                    // \u00A7 is the safe way to write the section sign (§)
                    if (text.contains("\u00A7kXXXX")) {
                        text = text.replace("\u00A7kXXXX", "XXXX");
                    }
                    if (text.contains("\u00A7k")) {
                        text = text.replace("\u00A7k", "");
                    }
                    
                    setPlayerDisplayName(player, text);
                }
            }
        }
    }

    private void setPlayerDisplayName(NetworkPlayerInfo player, String newName) {
        try {
            Field displayNameField = NetworkPlayerInfo.class.getDeclaredField("displayName");
            displayNameField.setAccessible(true);
            displayNameField.set(player, IChatComponent.Serializer.jsonToComponent("{\"text\":\"" + newName + "\"}"));
        } catch (Exception e) {
            // Fail silently
        }
    }
}
