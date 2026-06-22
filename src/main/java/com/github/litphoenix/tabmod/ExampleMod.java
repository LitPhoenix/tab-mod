package com.github.litphoenix.tabmod;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod(modid = "tabmod", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class ExampleMod {

    public static boolean isModEnabled = true;

    private static final Map<UUID, Map<String, String>> wlrCache = new ConcurrentHashMap<>();
    private static final Set<UUID> failedCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentLinkedQueue<UUID> fetchQueue = new ConcurrentLinkedQueue<>();
    
    private static String apiKey = "";
    private static File configDir;
    private static File keyFile;
    private static File cacheFile;

    private static String currentGame = null;
    private static long lastGameCheck = 0;
    private static int fetchCountSinceSave = 0;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandWLR());
        
        configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        
        keyFile = new File(configDir, "tabmod_key.txt");
        cacheFile = new File(configDir, "tabmod_cache.json");
        
        loadApiKey();
        loadCache();
        startBackgroundWorker();
    }

    private void startBackgroundWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    UUID target = fetchQueue.poll();
                    if (target != null && !apiKey.isEmpty() && !wlrCache.containsKey(target)) {
                        fetchFromAPI(target);
                        Thread.sleep(600); 
                    } else {
                        Thread.sleep(500); 
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        worker.setName("TabMod-API-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public static void queueFetch(UUID uuid) {
        if (apiKey.isEmpty() || wlrCache.containsKey(uuid) || failedCache.contains(uuid) || fetchQueue.contains(uuid)) return;
        fetchQueue.add(uuid);
    }

    public static void queueCurrentTabList() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;
        
        for (NetworkPlayerInfo player : mc.getNetHandler().getPlayerInfoMap()) {
            if (player.getGameProfile() != null) {
                queueFetch(player.getGameProfile().getId());
            }
        }
    }

    public static String getWlrTag(UUID uuid) {
        String game = detectGameCached();
        if (game == null) return null;
        if (wlrCache.containsKey(uuid)) {
            return wlrCache.get(uuid).get(game);
        }
        return null;
    }

    public static String detectGameCached() {
        long now = System.currentTimeMillis();
        if (now - lastGameCheck > 5000) { 
            currentGame = detectGame();
            lastGameCheck = now;
        }
        return currentGame;
    }

    public static String detectGame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return null;
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return null;

        String title = EnumChatFormatting.getTextWithoutFormattingCodes(obj.getDisplayName()).toUpperCase();
        if (title.contains("BLITZ") || title.contains("SG")) return "HungerGames";
        if (title.contains("BED WARS") || title.contains("BEDWARS")) return "Bedwars";
        if (title.contains("SKYWARS") || title.contains("SKY WARS")) return "SkyWars";
        if (title.contains("BUILD BATTLE")) return "BuildBattle";
        if (title.contains("COPS AND CRIMS") || title.contains("CVSC")) return "MCGO";
        if (title.contains("UHC")) return "UHC";
        if (title.contains("WARLORDS")) return "Battleground";
        if (title.contains("THE WALLS") || title.contains("WALLS")) return "Walls";
        if (title.contains("QUAKECRAFT") || title.contains("QUAKE")) return "Quake";
        if (title.contains("ARENA BRAWL") || title.contains("ARENA")) return "Arena";
        return null;
    }

    private static void fetchFromAPI(UUID uuid) {
        try {
            String cleanUuid = uuid.toString().replace("-", "");
            URL url = new URL("https://api.hypixel.net/player?uuid=" + cleanUuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("API-Key", apiKey);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();

                JsonObject json = new JsonParser().parse(response.toString()).getAsJsonObject();
                if (json.has("success") && json.get("success").getAsBoolean() && json.has("player") && !json.get("player").isJsonNull()) {
                    JsonObject playerObj = json.getAsJsonObject("player");
                    if (playerObj.has("stats")) {
                        JsonObject stats = playerObj.getAsJsonObject("stats");
                        Map<String, String> playerStats = new ConcurrentHashMap<>();

                        parseGameStat(stats, playerStats, "HungerGames", "wins", "deaths");
                        parseGameStat(stats, playerStats, "Bedwars", "wins_bedwars", "losses_bedwars");
                        parseGameStat(stats, playerStats, "SkyWars", "wins", "losses");
                        parseGameStat(stats, playerStats, "BuildBattle", "wins", "games_played");
                        parseGameStat(stats, playerStats, "MCGO", "game_wins", "deaths");
                        parseGameStat(stats, playerStats, "UHC", "wins", "deaths");
                        parseGameStat(stats, playerStats, "Battleground", "wins", "losses");
                        parseGameStat(stats, playerStats, "Walls", "wins", "losses");
                        parseGameStat(stats, playerStats, "Quake", "wins", "deaths");
                        parseGameStat(stats, playerStats, "Arena", "wins", "losses");

                        wlrCache.put(uuid, playerStats);
                    }
                } else {
                    failedCache.add(uuid); 
                }
            } else if (conn.getResponseCode() == 429) {
                fetchQueue.add(uuid);
                Thread.sleep(5000); 
            } else {
                failedCache.add(uuid);
            }

            fetchCountSinceSave++;
            if (fetchCountSinceSave > 15) {
                saveCache();
                fetchCountSinceSave = 0;
            }
        } catch (Exception e) {
            failedCache.add(uuid);
        }
    }

    private static void parseGameStat(JsonObject stats, Map<String, String> playerStats, String game, String winKey, String lossKey) {
        if (!stats.has(game)) return;
        JsonObject gameObj = stats.getAsJsonObject(game);

        int wins = gameObj.has(winKey) ? gameObj.get(winKey).getAsInt() : 0;
        int losses = gameObj.has(lossKey) ? gameObj.get(lossKey).getAsInt() : 0;

        if (game.equals("BuildBattle")) {
            losses = Math.max(0, losses - wins);
        }

        if (wins >= 10) {
            double wlr = (losses == 0) ? wins : (double) wins / losses;
            
            String colour = "\u00A77"; 
            if (wlr >= 4.0) colour = "\u00A74";      
            else if (wlr >= 2.0) colour = "\u00A7c"; 
            else if (wlr >= 1.0) colour = "\u00A76"; 
            else if (wlr >= 0.5) colour = "\u00A7e"; 

            String formattedWlr = String.format(Locale.UK, "%.2f", wlr);
            playerStats.put(game, colour + "[" + formattedWlr + "]\u00A7f");
        }
    }

    private static void saveApiKey(String key) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(keyFile))) {
            writer.write(key);
        } catch (IOException e) { }
    }

    private static void loadApiKey() {
        if (!keyFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(keyFile))) {
            String line = reader.readLine();
            if (line != null) apiKey = line.trim();
        } catch (IOException e) { }
    }

    private static synchronized void saveCache() {
        Map<UUID, Map<String, String>> clone = new HashMap<>(wlrCache);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
            new Gson().toJson(clone, writer);
        } catch (IOException e) { }
    }

    private static void loadCache() {
        if (!cacheFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            Map<UUID, Map<String, String>> loaded = new Gson().fromJson(reader, new TypeToken<Map<UUID, Map<String, String>>>(){}.getType());
            if (loaded != null) wlrCache.putAll(loaded);
        } catch (IOException e) { }
    }

    private static class CommandWLR extends CommandBase {
        @Override
        public String getCommandName() { return "wlr"; }
        
        @Override
        public String getCommandUsage(ICommandSender sender) { return "/wlr | /wlr toggle | /wlr key <api_key>"; }
        
        @Override
        public int getRequiredPermissionLevel() { return 0; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length >= 2 && args[0].equalsIgnoreCase("key")) {
                apiKey = args[1].trim();
                saveApiKey(apiKey);
                sender.addChatMessage(new ChatComponentText("\u00A7aHypixel API key saved! TabMod is ready."));
                return;
            }
            
            if (args.length >= 1 && args[0].equalsIgnoreCase("toggle")) {
                isModEnabled = !isModEnabled;
                String state = isModEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED";
                sender.addChatMessage(new ChatComponentText("\u00A7eTabMod is now " + state));
                return;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("run")) {
                if (apiKey.isEmpty()) {
                    sender.addChatMessage(new ChatComponentText("\u00A7cNo API key set. Use /wlr key <key>"));
                    return;
                }
                
                if (!isModEnabled) {
                    sender.addChatMessage(new ChatComponentText("\u00A7cMod is currently disabled. Use /wlr toggle first."));
                    return;
                }

                sender.addChatMessage(new ChatComponentText("\u00A7aQueueing stats for current tab list..."));
                queueCurrentTabList();
                return;
            }

            sender.addChatMessage(new ChatComponentText("\u00A7cUsage: /wlr | /wlr toggle | /wlr key <key>"));
        }
    }
}
