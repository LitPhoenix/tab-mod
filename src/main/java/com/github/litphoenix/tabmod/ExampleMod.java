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
import java.util.concurrent.CompletableFuture;

@Mod(modid = "tabmod", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class ExampleMod {

    public static boolean isModEnabled = true;

    private static final Map<UUID, Map<String, String>> wlrCache = new ConcurrentHashMap<>();
    private static final Set<UUID> failedCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Map<String, UUID> nickMap = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<UUID> fetchQueue = new ConcurrentLinkedQueue<>();
    
    private static String apiKey = "";
    private static File configDir;
    private static File keyFile;
    private static File cacheFile;
    private static File nicksFile;
    private static File thresholdFile;

    private static String currentGame = null;
    private static long lastGameCheck = 0;
    private static int fetchCountSinceSave = 0;

    // Threshold configurations (Game name -> [Sweat Threshold, Extreme Threshold])
    public static final Map<String, double[]> thresholds = new ConcurrentHashMap<>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandWLR());
        
        configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        
        keyFile = new File(configDir, "tabmod_key.txt");
        cacheFile = new File(configDir, "tabmod_cache.json");
        nicksFile = new File(configDir, "tabmod_nicks.json");
        thresholdFile = new File(configDir, "tabmod_thresholds.json");
        
        initDefaultThresholds();
        loadApiKey();
        loadCache();
        loadNicks();
        loadThresholds();
        startBackgroundWorker();
    }

    private void initDefaultThresholds() {
        thresholds.put("HungerGames", new double[]{0.8, 1.6});
        thresholds.put("Bedwars", new double[]{0.8, 1.6});
        thresholds.put("SkyWars", new double[]{0.8, 1.6});
        thresholds.put("BuildBattle", new double[]{0.8, 1.6});
        thresholds.put("MCGO", new double[]{0.8, 1.6});
        thresholds.put("UHC", new double[]{0.8, 1.6});
        thresholds.put("Battleground", new double[]{1.5, 2.0});
        thresholds.put("Walls", new double[]{0.8, 1.6});
        thresholds.put("Quake", new double[]{0.8, 1.6});
        thresholds.put("Arena", new double[]{0.8, 1.6});
    }

    private void startBackgroundWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    UUID target = fetchQueue.poll();
                    if (target != null && !apiKey.isEmpty() && !wlrCache.containsKey(target) && !failedCache.contains(target)) {
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
                String rawName = player.getGameProfile().getName().toLowerCase();
                UUID targetUuid = nickMap.containsKey(rawName) ? nickMap.get(rawName) : player.getGameProfile().getId();
                queueFetch(targetUuid);
            }
        }
    }

    public static String getWlrTag(UUID uuid) {
        String game = detectGameCached();
        if (game == null) return null;
        if (wlrCache.containsKey(uuid)) return wlrCache.get(uuid).get(game);
        return null;
    }
    
    public static boolean isFailed(UUID uuid) {
        return failedCache.contains(uuid);
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
                JsonObject json = new JsonParser().parse(in).getAsJsonObject();
                in.close();

                if (json.has("success") && json.get("success").getAsBoolean() && json.has("player") && !json.get("player").isJsonNull()) {
                    JsonObject playerObj = json.getAsJsonObject("player");
                    JsonObject stats = playerObj.has("stats") ? playerObj.getAsJsonObject("stats") : new JsonObject();
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
        int wins = 0;
        int losses = 0;

        if (stats.has(game)) {
            JsonObject gameObj = stats.getAsJsonObject(game);
            wins = gameObj.has(winKey) ? gameObj.get(winKey).getAsInt() : 0;
            losses = gameObj.has(lossKey) ? gameObj.get(lossKey).getAsInt() : 0;
            if (game.equals("BuildBattle")) losses = Math.max(0, losses - wins);
        }

        if (wins >= 10) {
            double wlr = (losses == 0) ? wins : (double) wins / losses;
            
            String bColor = "\u00A78"; 
            if (wins >= 5000) bColor = "\u00A74";      
            else if (wins >= 2500) bColor = "\u00A7c"; 
            else if (wins >= 1000) bColor = "\u00A76"; 
            else if (wins >= 500) bColor = "\u00A7e";  
            else if (wins >= 100) bColor = "\u00A77";  

            double[] limits = thresholds.getOrDefault(game, new double[]{0.8, 1.6});
            String nColor = "\u00A77"; 
            if (wlr >= limits[1]) nColor = "\u00A74";       
            else if (wlr >= limits[0]) nColor = "\u00A7c";  
            else if (wlr >= limits[0] * 0.8) nColor = "\u00A76"; 
            else if (wlr >= limits[0] * 0.5) nColor = "\u00A7e"; 

            String formattedWlr = String.format(Locale.UK, "%.2f", wlr);
            playerStats.put(game, bColor + "[" + nColor + formattedWlr + bColor + "]\u00A7f");
        } else {
            playerStats.put(game, "\u00A78[---]\u00A7f");
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
    
    public static synchronized void saveNicks() {
        Map<String, UUID> clone = new HashMap<>(nickMap);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(nicksFile))) {
            new Gson().toJson(clone, writer);
        } catch (IOException e) { }
    }

    private static void loadNicks() {
        if (!nicksFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(nicksFile))) {
            Map<String, UUID> loaded = new Gson().fromJson(reader, new TypeToken<Map<String, UUID>>(){}.getType());
            if (loaded != null) nickMap.putAll(loaded);
        } catch (IOException e) { }
    }

    public static synchronized void saveThresholds() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(thresholdFile))) {
            new Gson().toJson(thresholds, writer);
        } catch (IOException e) { }
    }

    private static void loadThresholds() {
        if (!thresholdFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(thresholdFile))) {
            Map<String, double[]> loaded = new Gson().fromJson(reader, new TypeToken<Map<String, double[]>>(){}.getType());
            if (loaded != null) thresholds.putAll(loaded);
        } catch (IOException e) { }
    }

    private static class CommandWLR extends CommandBase {
        @Override
        public String getCommandName() { return "wlr"; }
        
        @Override
        public String getCommandUsage(ICommandSender sender) { return "/wlr | /wlr toggle | /wlr key <api_key> | /wlr nick <fake> <real> | /
