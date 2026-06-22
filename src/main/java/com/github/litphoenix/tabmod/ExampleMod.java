package com.github.litphoenix.tabmod;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Mod(modid = "tabmod", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class ExampleMod {

    private static boolean wlrEnabled = false;
    private static final Map<UUID, String> wlrCache = new ConcurrentHashMap<>();
    private static String apiKey = "";
    private static File configFile;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandWLR());
        
        // Sets up local config folder path safely
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        configFile = new File(configDir, "tabmod.txt");
        loadApiKey();
    }

    public static boolean isWlrEnabled() {
        return wlrEnabled;
    }

    public static String getWlrTag(UUID uuid) {
        return wlrCache.get(uuid);
    }

    public static String detectGame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return null;

        String title = EnumChatFormatting.getTextWithoutFormattingCodes(obj.getDisplayName()).toUpperCase();
        if (title.contains("BLITZ SG")) return "HungerGames";
        if (title.contains("BED WARS")) return "Bedwars";
        if (title.contains("SKYWARS")) return "SkyWars";
        return null;
    }

    public static void fetchWLR(NetworkPlayerInfo player) {
        if (player.getGameProfile() == null || apiKey.isEmpty()) return;
        UUID uuid = player.getGameProfile().getId();
        if (wlrCache.containsKey(uuid)) return;

        String game = detectGame();
        if (game == null) return;

        wlrCache.put(uuid, ""); 

        CompletableFuture.runAsync(() -> {
            try {
                String cleanUuid = uuid.toString().replace("-", "");
                URL url = new URL("https://api.hypixel.net/v2/player?uuid=" + cleanUuid);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("API-Key", apiKey);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JsonObject json = new JsonParser().parse(response.toString()).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean() && json.has("player") && !json.get("player").isJsonNull()) {
                        JsonObject playerObj = json.getAsJsonObject("player");
                        if (playerObj.has("stats")) {
                            JsonObject stats = playerObj.getAsJsonObject("stats");
                            if (stats.has(game)) {
                                JsonObject gameStats = stats.getAsJsonObject(game);
                                
                                int wins = 0;
                                int losses = 0;

                                if (game.equals("Bedwars")) {
                                    wins = gameStats.has("wins_bedwars") ? gameStats.get("wins_bedwars").getAsInt() : 0;
                                    losses = gameStats.has("losses_bedwars") ? gameStats.get("losses_bedwars").getAsInt() : 0;
                                } else {
                                    wins = gameStats.has("wins") ? gameStats.get("wins").getAsInt() : 0;
                                    losses = gameStats.has("losses") ? gameStats.get("losses").getAsInt() : 0;
                                }

                                if (wins >= 10) {
                                    double wlr = (losses == 0) ? wins : (double) wins / losses;
                                    String formattedWlr = String.format(Locale.UK, "%.2f", wlr);
                                    wlrCache.put(uuid, "\u00A77[" + formattedWlr + "] ");
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void saveApiKey(String key) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadApiKey() {
        if (!configFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line = reader.readLine();
            if (line != null) {
                apiKey = line.trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class CommandWLR extends CommandBase {
        @Override
        public String getCommandName() {
            return "wlr";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/wlr OR /wlr key <api_key>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length >= 2 && args[0].equalsIgnoreCase("key")) {
                apiKey = args[1].trim();
                saveApiKey(apiKey);
                sender.addChatMessage(new ChatComponentText("\u00A7aHypixel API key saved locally on your computer!"));
                return;
            }

            wlrEnabled = !wlrEnabled;
            if (!wlrEnabled) {
                wlrCache.clear();
            }
            
            if (wlrEnabled && apiKey.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("\u00A7cWarning: No API key set. Use /wlr key <key> first."));
            }
            
            String status = wlrEnabled ? "\u00A7aenabled" : "\u00A7cdisabled";
            sender.addChatMessage(new ChatComponentText("\u00A77Tab WLR lookup is now " + status + "\u00A77."));
        }
    }
}
