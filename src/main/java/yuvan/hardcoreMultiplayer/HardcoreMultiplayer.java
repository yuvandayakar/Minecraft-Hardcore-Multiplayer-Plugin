package yuvan.hardcoreMultiplayer;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

public class HardcoreMultiplayer extends JavaPlugin implements Listener {
    private World lobbyWorld;
    private Location lobbyLoc;
    private Location platformBase;
    private Location startNPCLoc;
    private Location statsNPCLoc;
    private Villager startNPC;
    private Villager statsNPC;

    private World runWorld;
    private int timer = 0;
    private boolean running = false;

    private int totalDeaths;
    private int prevRunTime;
    private String prevDeathCause;

    private static final int PLATFORM_Y = 200;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        lobbyWorld = Bukkit.getWorld("world");
        if (lobbyWorld == null) {
            getLogger().severe("Lobby world 'world' not found! Plugin disabled.");
            return;
        }

        platformBase = new Location(lobbyWorld, 0, PLATFORM_Y, 0);
        lobbyLoc = new Location(lobbyWorld, 0.5, PLATFORM_Y + 1, 0.5);
        startNPCLoc = new Location(lobbyWorld, -2.5, PLATFORM_Y + 1, 0.5);
        statsNPCLoc = new Location(lobbyWorld, 2.5, PLATFORM_Y + 1, 0.5);

        lobbyWorld.setSpawnLocation(lobbyLoc);

        totalDeaths = getConfig().getInt("stats.deaths", 0);
        prevRunTime = getConfig().getInt("stats.prev-time", 0);
        prevDeathCause = getConfig().getString("stats.last-cause", "None");

        lobbyWorld.setGameRule(GameRule.MOB_GRIEFING, false);
        lobbyWorld.setGameRule(GameRule.DO_TILE_DROPS, false);
        lobbyWorld.setGameRule(GameRule.FALL_DAMAGE, false);
        lobbyWorld.setGameRule(GameRule.KEEP_INVENTORY, true);

        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("startrun").setExecutor((sender, cmd, label, args) -> {
            startRun();
            return true;
        });

        getCommand("hc").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player) || !sender.hasPermission("hardcoremultiplayer.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "/hc deaths <n> | time <s> | cause <msg...> | reset");
                return true;
            }
            try {
                switch (args[0].toLowerCase()) {
                    case "deaths": totalDeaths = Integer.parseInt(args[1]); break;
                    case "time": prevRunTime = Integer.parseInt(args[1]); break;
                    case "cause": prevDeathCause = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); break;
                    case "reset": totalDeaths = 0; prevRunTime = 0; prevDeathCause = "None"; break;
                    default: sender.sendMessage(ChatColor.RED + "Invalid subcommand!"); return true;
                }
                saveStats();
                updateStatsNPC();
                sender.sendMessage(ChatColor.GREEN + "Stats updated!");
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Invalid arguments!");
            }
            return true;
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                buildSkyPlatform();
                createOrLoadLobbyNPCs();
            }
        }.runTaskLater(this, 20L);

        startTimerTask();
    }

    @Override
    public void onDisable() {
        saveStats();
        saveConfig();
    }

    private void saveStats() {
        getConfig().set("stats.deaths", totalDeaths);
        getConfig().set("stats.prev-time", prevRunTime);
        getConfig().set("stats.last-cause", prevDeathCause);
        saveConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (!running) {
            // Lobby mode when no run is active
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyLoc);
        }
        // During active run: do nothing → player loads with saved inventory, XP, effects, etc.
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!running || runWorld == null || !e.getEntity().getWorld().equals(runWorld)) return;

        Player dead = e.getEntity();
        String cause = e.getDeathMessage() != null ? ChatColor.stripColor(e.getDeathMessage()) : "died mysteriously";
        prevDeathCause = cause;
        totalDeaths++;
        prevRunTime = timer;
        saveStats();
        updateStatsNPC();

        e.setDeathMessage(null);
        Bukkit.broadcastMessage(ChatColor.RED + dead.getName() + " has died! " + ChatColor.DARK_RED + "TEAM ELIMINATED!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                endRun();
            }
        }.runTaskLater(this, 40);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        if (running && runWorld != null && e.getPlayer().getWorld().equals(runWorld)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    e.getPlayer().setGameMode(GameMode.SPECTATOR);
                }
            }.runTaskLater(this, 1);
        }
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager)) return;
        Villager v = (Villager) e.getRightClicked();
        e.setCancelled(true);

        String name = v.getCustomName();
        if (name == null) return;

        if (name.contains("Start Run") || name.contains("Join Run")) {
            Player p = e.getPlayer();
            if (!running) {
                startRun();  // Clears everyone and starts fresh
            } else if (runWorld != null) {
                // Late joiner → clear and spawn fresh
                clearPlayer(p);
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(runWorld.getSpawnLocation());
                p.sendMessage(ChatColor.AQUA + "You have joined the ongoing hardcore run!");
            }
        } else if (name.contains("Stats:")) {
            Player p = e.getPlayer();
            p.sendMessage(ChatColor.GOLD + "Current Stats:");
            p.sendMessage(ChatColor.RED + "Deaths: " + totalDeaths);
            p.sendMessage(ChatColor.GREEN + "Prev Time: " + prevRunTime + "s");
            p.sendMessage(ChatColor.WHITE + "Last Death: " + prevDeathCause);
        }
    }

    private void createOrLoadLobbyNPCs() {
        for (Entity ent : lobbyWorld.getNearbyEntities(platformBase, 15, 15, 15)) {
            if (ent instanceof Villager) {
                Villager v = (Villager) ent;
                String name = v.getCustomName();
                if (name != null && (name.contains("Start Run") || name.contains("Join Run") || name.contains("Stats:"))) {
                    v.remove();
                }
            }
        }

        startNPC = lobbyWorld.spawn(startNPCLoc, Villager.class);
        startNPC.setCustomName(ChatColor.GREEN + "Start Run");
        startNPC.setCustomNameVisible(true);
        startNPC.setAI(false);
        startNPC.setInvulnerable(true);
        startNPC.setGravity(false);
        startNPC.setSilent(true);
        startNPC.setProfession(Villager.Profession.LIBRARIAN);
        startNPC.setVillagerLevel(5);
        startNPC.setPersistent(true);

        statsNPC = lobbyWorld.spawn(statsNPCLoc, Villager.class);
        updateStatsNPC();
        statsNPC.setCustomNameVisible(true);
        statsNPC.setAI(false);
        statsNPC.setInvulnerable(true);
        statsNPC.setGravity(false);
        statsNPC.setSilent(true);
        statsNPC.setProfession(Villager.Profession.CLERIC);
        statsNPC.setVillagerLevel(5);
        statsNPC.setPersistent(true);

        updateStartNPCName();
    }

    private void buildSkyPlatform() {
        Material material = Material.DEEPSLATE_BRICKS;
        Material border = Material.POLISHED_DEEPSLATE;

        for (int x = -5; x <= 4; x++) {
            for (int z = -5; z <= 4; z++) {
                Location blockLoc = platformBase.clone().add(x, 0, z);
                blockLoc.getBlock().setType((x == -5 || x == 4 || z == -5 || z == 4) ? border : material);
            }
        }
    }

    private void updateStartNPCName() {
        if (startNPC != null && !startNPC.isDead()) {
            startNPC.setCustomName(running ? ChatColor.GREEN + "Join Run" : ChatColor.GREEN + "Start Run");
        }
    }

    private void updateStatsNPC() {
        if (statsNPC != null && !statsNPC.isDead()) {
            statsNPC.setCustomName(ChatColor.GOLD + "Stats:\n" +
                    ChatColor.RED + "Deaths: " + totalDeaths + "\n" +
                    ChatColor.GREEN + "Prev Time: " + prevRunTime + "s\n" +
                    ChatColor.WHITE + "Last Death: " + prevDeathCause);
        }
    }

    private void startRun() {
        if (running) return;

        running = true;
        updateStartNPCName();
        timer = 0;

        // Force unload and delete old runworld
        World oldRun = Bukkit.getWorld("runworld");
        if (oldRun != null) {
            Bukkit.unloadWorld(oldRun, false);
        }
        File runFolder = new File(Bukkit.getWorldContainer(), "runworld");
        if (runFolder.exists()) {
            deleteFolder(runFolder);
        }

        // Create fresh with new seed
        long seed = new Random().nextLong();
        getLogger().info("Creating new runworld with seed: " + seed);
        WorldCreator wc = new WorldCreator("runworld");
        wc.seed(seed);
        runWorld = Bukkit.createWorld(wc);

        if (runWorld == null) {
            getLogger().severe("Failed to create runworld!");
            running = false;
            updateStartNPCName();
            return;
        }

        runWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        runWorld.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        runWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        runWorld.setDifficulty(Difficulty.HARD);

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayer(p);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(runWorld.getSpawnLocation());
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "New Run Started! Seed: " + seed + " | Good luck!");
    }

    private void endRun() {
        running = false;
        updateStartNPCName();

        if (runWorld != null) {
            Bukkit.unloadWorld(runWorld, false);
            final File runFolder = new File(Bukkit.getWorldContainer(), "runworld");
            new BukkitRunnable() {
                @Override
                public void run() {
                    deleteFolder(runFolder);
                    getLogger().info("Deleted runworld folder after endRun.");
                }
            }.runTaskLater(this, 40); // Delay 2 seconds
            runWorld = null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayer(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyLoc);
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Returned to Lobby.");
    }

    private void clearPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20.0f);
        p.setExp(0);
        p.setLevel(0);
        p.setTotalExperience(0);
        p.getActivePotionEffects().forEach(eff -> p.removePotionEffect(eff.getType()));
    }

    private void startTimerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) return;
                timer++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setPlayerListFooter(ChatColor.YELLOW + "Run Time: " + timer + "s");
                }
            }
        }.runTaskTimer(this, 20, 20);
    }

    private void deleteFolder(File folder) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
        }
        folder.delete();
    }
}