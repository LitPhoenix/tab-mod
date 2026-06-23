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
import com.google.gson.GsonBuilder;
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
    private static File gamesFile;

    private static String currentGame = null;
    private static long lastGameCheck = 0;
    private static int fetchCountSinceSave = 0;

    public static final Map<String, GameProfile> gameProfiles = new ConcurrentHashMap<>();

    public static class GameProfile {
        public boolean enabled = true;
        public String stat1 = "wins";
        public String stat2 = "losses";
        public String prefix = "[";
        public String suffix = "]";
        public int decimals = 2;
        public double swt = 0.8;
        public double ext = 1.6;

        public GameProfile() {}
        public GameProfile(String s1, String s2, double swt, double ext) {
            this.stat1 = s1; this.stat2 = s2; this.swt = swt; this.ext = ext;
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandWLR());
        
        configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!configDir.exists()) configDir.mkdirs();
        
        keyFile = new File(configDir, "tabmod_key.txt");
        cacheFile = new File(configDir, "tabmod_cache.json");
        nicksFile = new File(configDir, "tabmod_nicks.json");
        gamesFile = new File(configDir, "tabmod_games.json");
        
        initDefaultProfiles();
        loadApiKey();
        loadCache();
        loadNicks();
        loadProfiles();
        startBackgroundWorker();
    }

    private void initDefaultProfiles() {
        gameProfiles.put("HungerGames", new GameProfile("wins", "deaths", 0.8, 1.6));
        gameProfiles.put("Bedwars", new GameProfile("wins_bedwars", "losses_bedwars", 0.8, 1.6));
        gameProfiles.put("SkyWars", new GameProfile("wins", "losses", 0.8, 1.6));
        gameProfiles.put("BuildBattle", new GameProfile("wins", "games_played", 0.8, 1.6));
        gameProfiles.put("MCGO", new GameProfile("game_wins", "deaths", 0.8, 1.6));
        gameProfiles.put("UHC", new GameProfile("wins", "deaths", 0.8, 1.6));
        gameProfiles.put("Battleground", new GameProfile("wins", "losses", 1.5, 2.0));
        gameProfiles.put("Walls", new GameProfile("wins", "losses", 0.8, 1.6));
        gameProfiles.put("Quake", new GameProfile("wins", "deaths", 0.8, 1.6));
        gameProfiles.put("Arena", new GameProfile("wins", "losses", 0.8, 1.6));
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
        
        GameProfile profile = gameProfiles.get(game);
        if (profile == null || !profile.enabled) return null;

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
        for (String game : gameProfiles.keySet()) {
            if (title.contains(game.toUpperCase())) return game;
        }
        
        if (title.contains("BLITZ") || title.contains("SG")) return "HungerGames";
        if (title.contains("BED WARS")) return "Bedwars";
        if (title.contains("SKY WARS")) return "SkyWars";
        if (title.contains("COPS AND CRIMS") || title.contains("CVSC")) return "MCGO";
        if (title.contains("WARLORDS")) return "Battleground";
        if (title.contains("THE WALLS")) return "Walls";
        if (title.contains("QUAKECRAFT")) return "Quake";
        if (title.contains("ARENA BRAWL")) return "Arena";
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

                    for (Map.Entry<String, GameProfile> entry : gameProfiles.entrySet()) {
                        parseDynamicStat(stats, playerStats, entry.getKey(), entry.getValue());
                    }

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

    private static void parseDynamicStat(JsonObject stats, Map<String, String> playerStats, String gameName, GameProfile profile) {
        int v1 = 0;
        int v2 = 0;

        if (stats.has(gameName)) {
            JsonObject gameObj = stats.getAsJsonObject(gameName);
            v1 = gameObj.has(profile.stat1) ? gameObj.get(profile.stat1).getAsInt() : 0;
            v2 = gameObj.has(profile.stat2) ? gameObj.get(profile.stat2).getAsInt() : 0;
            if (gameName.equals("BuildBattle") && profile.stat2.equals("games_played")) {
                v2 = Math.max(0, v2 - v1);
            }
        }

        if (v1 >= 10) {
            double ratio = (v2 == 0) ? v1 : (double) v1 / v2;
            
            String bColor = "\u00A78"; 
            if (v1 >= 5000) bColor = "\u00A74";      
            else if (v1 >= 2500) bColor = "\u00A7c"; 
            else if (v1 >= 1000) bColor = "\u00A76"; 
            else if (v1 >= 500) bColor = "\u00A7e";  
            else if (v1 >= 100) bColor = "\u00A77";  

            String nColor = "\u00A77"; 
            if (ratio >= profile.ext) nColor = "\u00A74";       
            else if (ratio >= profile.swt) nColor = "\u00A7c";  
            else if (ratio >= profile.swt * 0.8) nColor = "\u00A76"; 
            else if (ratio >= profile.swt * 0.5) nColor = "\u00A7e"; 

            String formatStr = "%." + Math.max(0, profile.decimals) + "f";
            String formattedRatio = String.format(Locale.UK, formatStr, ratio);
            
            String pre = profile.prefix != null ? profile.prefix : "";
            String suf = profile.suffix != null ? profile.suffix : "";
            
            playerStats.put(gameName, bColor + pre + nColor + formattedRatio + bColor + suf + "\u00A7f");
        } else {
            String pre = profile.prefix != null ? profile.prefix : "";
            String suf = profile.suffix != null ? profile.suffix : "";
            playerStats.put(gameName, "\u00A78" + pre + "---" + suf + "\u00A7f");
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

    public static synchronized void saveProfiles() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(gamesFile))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(gameProfiles, writer);
        } catch (IOException e) { }
    }

    private static void loadProfiles() {
        if (!gamesFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(gamesFile))) {
            Map<String, GameProfile> loaded = new Gson().fromJson(reader, new TypeToken<Map<String, GameProfile>>(){}.getType());
            if (loaded != null) gameProfiles.putAll(loaded);
        } catch (IOException e) { }
    }

    private static class CommandWLR extends CommandBase {
        @Override
        public String getCommandName() { return "wlr"; }
        
        @Override
        public String getCommandUsage(ICommandSender sender) { return "/wlr | /wlr toggle | /wlr key <api_key> | /wlr nick <fake> <real> | /wlr config"; }
        
        @Override
        public int getRequiredPermissionLevel() { return 0; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length >= 2 && args[0].equalsIgnoreCase("key")) {
                apiKey = args[1].trim();
                saveApiKey(apiKey);
                sender.addChatMessage(new ChatComponentText("\u00A7aHypixel API key saved!"));
                return;
            }
            if (args.length >= 3 && args[0].equalsIgnoreCase("nick")) {
                String fakeName = args[1];
                String realName = args[2];
                sender.addChatMessage(new ChatComponentText("\u00A7eResolving real UUID for " + realName + "..."));
                CompletableFuture.runAsync(() -> {
                    try {
                        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + realName);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        if (conn.getResponseCode() == 200) {
                            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                            reader.close();
                            String rawId = json.get("id").getAsString();
                            StringBuilder sb = new StringBuilder(rawId);
                            sb.insert(8, "-").insert(13, "-").insert(18, "-").insert(23, "-");
                            nickMap.put(fakeName.toLowerCase(), UUID.fromString(sb.toString()));
                            saveNicks();
                            Minecraft.getMinecraft().addScheduledTask(() -> sender.addChatMessage(new ChatComponentText("\u00A7aSuccess! Linked " + fakeName + " to " + realName)));
                        } else {
                            Minecraft.getMinecraft().addScheduledTask(() -> sender.addChatMessage(new ChatComponentText("\u00A7cCould not find real player: " + realName)));
                        }
                    } catch(Exception e) {
                        Minecraft.getMinecraft().addScheduledTask(() -> sender.addChatMessage(new ChatComponentText("\u00A7cError mapping nick.")));
                    }
                });
                return;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("toggle")) {
                isModEnabled = !isModEnabled;
                sender.addChatMessage(new ChatComponentText("\u00A7eTabMod is now " + (isModEnabled ? "\u00A7aENABLED" : "\u00A7cDISABLED")));
                return;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
                Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiConfig()));
                return;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("run")) {
                if (apiKey.isEmpty()) {
                    sender.addChatMessage(new ChatComponentText("\u00A7cNo API key set. Use /wlr key <key>"));
                    return;
                }
                if (!isModEnabled) return;
                sender.addChatMessage(new ChatComponentText("\u00A7aQueueing stats..."));
                queueCurrentTabList();
                return;
            }
        }
    }
}
