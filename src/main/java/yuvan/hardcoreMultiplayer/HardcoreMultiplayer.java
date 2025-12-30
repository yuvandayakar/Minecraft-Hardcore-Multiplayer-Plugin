package yuvan.hardcoreMultiplayer;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Random;

public class HardcoreMultiplayer extends JavaPlugin implements Listener {

    private int timer = 0;
    private boolean running = false;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("startrun").setExecutor((sender, cmd, label, args) -> {
            startRun();
            return true;
        });
        createLobbyNPC();
        startTimerTask();
    }

    // =========================
    // Shared Death
    // =========================
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!running) return;

        Bukkit.broadcastMessage(ChatColor.RED + "A player has died! TEAM ELIMINATED!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
        }

        // End run and return to lobby
        new BukkitRunnable() {
            @Override
            public void run() {
                endRun();
            }
        }.runTaskLater(this, 40);
    }

    // =========================
    // NPC Interaction
    // =========================
    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Villager) {
            Villager v = (Villager) e.getRightClicked();
            if ("Start Run".equals(v.getCustomName())) {
                startRun();
            }
        }
    }

    private void createLobbyNPC() {
        World lobby = Bukkit.getWorld("world");

        Location loc = new Location(lobby, 0.5, 65, 0.5);
        if (loc.getWorld().getNearbyEntities(loc, 2, 2, 2).stream()
                .anyMatch(ent -> ent instanceof Villager)) {
            return; // NPC already exists
        }

        Villager npc = lobby.spawn(loc, Villager.class);
        npc.setCustomName("Start Run");
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setGravity(false);
    }

    // =========================
    // Start Run
    // =========================
    private void startRun() {
        if (running) return;
        running = true;

        timer = 0;

        // Delete old world
        File runWorldFolder = new File("runworld");
        deleteFolder(runWorldFolder);

        // Create new seed
        long seed = new Random().nextLong();

        WorldCreator wc = new WorldCreator("runworld");
        wc.seed(seed);
        World runWorld = Bukkit.createWorld(wc);

        runWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        runWorld.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        runWorld.setDifficulty(Difficulty.HARD);

        // Teleport players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(runWorld.getSpawnLocation());
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "New Run Started! Good luck!");
    }

    // =========================
    // End Run
    // =========================
    private void endRun() {
        running = false;

        World lobby = Bukkit.getWorld("world");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(new Location(lobby, 0.5, 65, 0.5));
        }

        Bukkit.unloadWorld("runworld", false);
        deleteFolder(new File("runworld"));

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Returned to Lobby.");
    }

    // =========================
    // Timer Scoreboard
    // =========================
    private void startTimerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) return;

                timer++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setPlayerListFooter("Run Time: " + timer + "s");
                }
            }
        }.runTaskTimer(this, 20, 20);
    }

    // =========================
    // Delete Folder
    // =========================
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
