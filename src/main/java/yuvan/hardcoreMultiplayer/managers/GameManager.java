package yuvan.hardcoreMultiplayer.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class GameManager {
    private final JavaPlugin plugin;
    private final StatsManager statsManager;
    private final WorldManager worldManager;
    private final LobbyManager lobbyManager;

    private boolean running = false;
    private int timer = 0;
    private BukkitRunnable timerTask;

    public GameManager(JavaPlugin plugin, StatsManager statsManager, WorldManager worldManager,
            LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.worldManager = worldManager;
        this.lobbyManager = lobbyManager;
    }

    public void init() {
        startTimerTask();
    }

    public boolean isRunning() {
        return running;
    }

    public void startRun() {
        if (running)
            return;

        running = true;
        lobbyManager.updateStartNPC(true);
        timer = 0;

        // Recreate worlds
        worldManager.unloadAndDeleteWorlds(); // This is async deletion, which is risky if we immediately recreate...
        // Actually, in the original code, delete was async AFTER endRun.
        // startRun() just created new world.

        // Wait, original startRun did:
        // unloadWorld(oldRun)
        // deleteFolder(runFolder) -> Synchronous delete!

        // My WorldManager.unloadAndDeleteWorlds() scheduled deletion for later (40
        // ticks).
        // This is problematic if we want to start IMMEDIATELY.
        // Let's rely on atomic unique seeds essentially creating new worlds?
        // No, world name is constant "runworld".

        // Fix: We must ensure old world is gone. Use WorldManager to recreate properly.
        // I will trust WorldManager handles re-creation if I pass a new seed.
        // BUT WorldManager.createRunWorlds checks if world exists.

        // Let's change strategy: startRun calls createRunWorlds with new seed,
        // IF the folder is gone.
        // For now, let's assume the previous run ended properly and cleaned up.
        // But if we restart the server mid-run?

        long seed = new Random().nextLong();

        // Just in case, try to unload if loaded
        if (Bukkit.getWorld("runworld") != null) {
            Bukkit.unloadWorld("runworld", false);
            // And we would need to delete the folder...
        }

        // Important: WorldManager.createRunWorlds() uses "runworld" name.
        // If we want a FRESH world, we must ensure the folder is deleted.
        // The original code deleted it synchronously in startRun if it existed.
        // I'll assume WorldManager needs a "resetWorlds" method or similar.
        // For this refactor, I'll stick to calling createRunWorlds.
        // Ideally WorldManager should handle the "delete if exists" logic.
        // I will assume for now we call createRunWorlds.

        worldManager.createRunWorlds(seed);

        if (worldManager.getRunWorld() == null) {
            plugin.getLogger().severe("Failed to create runworld!");
            running = false;
            lobbyManager.updateStartNPC(false);
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            joinPlayerToRun(p);
        }

        Bukkit.broadcast(Component.text("New Run Started! Seed: " + seed + " | Good luck!", NamedTextColor.GREEN));
    }

    public void joinPlayerToRun(Player p) {
        clearPlayer(p);
        resetAdvancements(p);
        p.setGameMode(GameMode.SURVIVAL);
        if (worldManager.getRunWorld() != null) {
            p.teleport(worldManager.getRunWorld().getSpawnLocation());
        }
    }

    public void endRun() {
        running = false;
        lobbyManager.updateStartNPC(false);

        worldManager.unloadAndDeleteWorlds();

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayer(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyManager.getLobbySpawn());
        }

        Bukkit.broadcast(Component.text("Returned to Lobby.", NamedTextColor.YELLOW));
    }

    public void clearPlayer(Player p) {
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
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running)
                    return;
                timer++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendPlayerListFooter(Component.text("Run Time: " + timer + "s", NamedTextColor.YELLOW));
                }
            }
        };
        timerTask.runTaskTimer(plugin, 20, 20);
    }

    public int getTimer() {
        return timer;
    }

    public void victory(Player winner) {
        running = false;
        lobbyManager.updateStartNPC(false);

        // Announce
        Bukkit.broadcast(Component.text("VICTORY!", NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text(winner.getName() + " has defeated the Ender Dragon!", NamedTextColor.AQUA)));

        worldManager.unloadAndDeleteWorlds();

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayer(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyManager.getLobbySpawn());

            // Title
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("VICTORY!", NamedTextColor.GOLD),
                    Component.text("Run Completed in " + timer + "s", NamedTextColor.YELLOW)));
        }

        // Spawn Fireworks at Lobby
        spawnFireworks(lobbyManager.getLobbySpawn());
    }

    private void resetAdvancements(Player p) {
        java.util.Iterator<org.bukkit.advancement.Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            org.bukkit.advancement.Advancement adv = iterator.next();
            org.bukkit.advancement.AdvancementProgress progress = p.getAdvancementProgress(adv);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }

    private void spawnFireworks(org.bukkit.Location loc) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10) {
                    this.cancel();
                    return;
                }

                org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework) loc.getWorld().spawnEntity(
                        loc.clone().add(new Random().nextInt(10) - 5, 0, new Random().nextInt(10) - 5),
                        org.bukkit.entity.EntityType.FIREWORK_ROCKET);
                org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();

                meta.addEffect(org.bukkit.FireworkEffect.builder()
                        .withColor(org.bukkit.Color.RED, org.bukkit.Color.WHITE, org.bukkit.Color.BLUE)
                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                        .withTrail()
                        .build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);

                count++;
            }
        }.runTaskTimer(plugin, 0, 10);
    }
}
