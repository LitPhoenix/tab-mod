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
        // Registers our event so Minecraft actually listens to it
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
                    
                    if (text.contains("§kXXXX")) {
                        text = text.replace("§kXXXX", "XXXX");
                    }
                    if (text.contains("§k")) {
                        text = text.replace("§k", "");
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
