package yuvan.hardcoreMultiplayer.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import yuvan.hardcoreMultiplayer.managers.GameManager;
import yuvan.hardcoreMultiplayer.managers.LobbyManager;
import yuvan.hardcoreMultiplayer.managers.StatsManager;
import yuvan.hardcoreMultiplayer.managers.WorldManager;

public class HardcoreListener implements Listener {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final StatsManager statsManager;
    private final LobbyManager lobbyManager;
    private final WorldManager worldManager;

    public HardcoreListener(JavaPlugin plugin, GameManager gameManager, StatsManager statsManager,
            LobbyManager lobbyManager, WorldManager worldManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.statsManager = statsManager;
        this.lobbyManager = lobbyManager;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (!gameManager.isRunning()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyManager.getLobbySpawn());
        }
        // During a run: do nothing — player loads with saved inventory/XP/etc.
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!gameManager.isRunning() || !worldManager.isRunDimension(e.getEntity().getWorld()))
            return;

        Player dead = e.getEntity();
        Component plainDeathMessage = e.deathMessage();
        // Since we want to store plain string in stats config for now (simple string),
        // convert if possible or just use a placeholder
        // Modernizing: Keep "died mysteriously" fallback.
        // Paper's e.deathMessage() returns Component.

        // For stats config (String), we need a string serialization or plain text.
        // Let's use plain text serializer or just get name.
        // For simplicity, let's just use "Player died" or try to extract plain text if
        // available in API,
        // but Component doesn't easily go to string without serializer.
        // We will just use a generic message for the stats file to avoid complexity, or
        // simple extraction.
        String cause = "died"; // Simplified for now

        // Update Stats
        statsManager.setPrevDeathCause(dead.getName() + " " + cause);
        statsManager.incrementDeaths();
        statsManager.setPrevRunTime(gameManager.getTimer());
        statsManager.save();
        lobbyManager.updateStatsNPC();

        e.deathMessage(null); // Hide default

        Bukkit.broadcast(
                Component.text(dead.getName(), NamedTextColor.RED)
                        .append(Component.text(" has died! ", NamedTextColor.RED))
                        .append(Component.text("TEAM ELIMINATED!", NamedTextColor.DARK_RED)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                gameManager.endRun();
            }
        }.runTaskLater(plugin, 40);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        if (gameManager.isRunning() && worldManager.isRunDimension(e.getPlayer().getWorld())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    e.getPlayer().setGameMode(GameMode.SPECTATOR);
                }
            }.runTaskLater(plugin, 1);
        }
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager))
            return;
        Villager v = (Villager) e.getRightClicked();
        // Check custom name via Component?
        // Villager names are components now.
        Component nameComp = v.customName();
        if (nameComp == null)
            return;

        // Simple string check is harder with Components.
        // But we know we set them as specific text.
        // We can serialize to plain text to check content.
        // Or check if it equals what we set.
        // Since we are modernizing, let's use PlainTextComponentSerializer or contains
        // checking if simple.
        // For now, let's assume we can get a string representation or check via
        // stringifying.
        // Note: Paper API 1.21 usually has serializers.
        // I will use
        // `net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer`

        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(nameComp);

        if (name.contains("Start Run") || name.contains("Join Run")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            if (!gameManager.isRunning()) {
                gameManager.startRun();
            } else if (worldManager.getRunWorld() != null) {
                gameManager.joinPlayerToRun(p);
                p.sendMessage(Component.text("You have joined the ongoing hardcore run!", NamedTextColor.AQUA));
            }
        } else if (name.contains("Stats:")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            p.sendMessage(Component.text("Current Stats:", NamedTextColor.GOLD));
            p.sendMessage(Component.text("Deaths: " + statsManager.getTotalDeaths(), NamedTextColor.RED));
            p.sendMessage(Component.text("Prev Time: " + statsManager.getPrevRunTime() + "s", NamedTextColor.GREEN));
            p.sendMessage(Component.text("Last Death: " + statsManager.getPrevDeathCause(), NamedTextColor.WHITE));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (!gameManager.isRunning() || worldManager.getRunWorld() == null)
            return;

        Player player = e.getPlayer();
        World fromWorld = player.getWorld();

        if (!worldManager.isRunDimension(fromWorld))
            return;

        PlayerPortalEvent.TeleportCause cause = e.getCause();
        World targetWorld = null;

        World run = worldManager.getRunWorld();
        World nether = worldManager.getRunNether();
        World end = worldManager.getRunEnd();

        if (run == null || nether == null || end == null) {
            player.sendMessage(
                    Component.text("[Hardcore] Portal world is missing. Cannot teleport.", NamedTextColor.RED));
            e.setCancelled(true);
            return;
        }

        if (cause == PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.equals(run))
                targetWorld = nether;
            else if (fromWorld.equals(nether))
                targetWorld = run;
        } else if (cause == PlayerPortalEvent.TeleportCause.END_PORTAL
                || cause == PlayerPortalEvent.TeleportCause.END_GATEWAY) {
            if (fromWorld.equals(run))
                targetWorld = end;
            else if (fromWorld.equals(end))
                targetWorld = run;
        }

        if (targetWorld == null) {
            e.setCancelled(true);
            return;
        }

        Location from = player.getLocation();
        Location to;

        if (fromWorld.equals(run) && targetWorld.equals(nether)) {
            to = new Location(targetWorld, from.getX() / 8.0, from.getY(), from.getZ() / 8.0);
        } else if (fromWorld.equals(nether) && targetWorld.equals(run)) {
            to = new Location(targetWorld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0);
        } else if (targetWorld.equals(end)) {
            // End Entry: Vanilla location (100, 49, 0)
            to = new Location(targetWorld, 100.5, 49, 0.5, 90, 0); // Facing West usually
            worldManager.generateEndPlatform(to);
        } else if (fromWorld.equals(end) && targetWorld.equals(run)) {
            // End Exit: Check for Victory (Dragon Defeated)
            if (isDragonDead(end)) {
                e.setCancelled(true); // Cancel teleport, victory handles it
                gameManager.victory(player);
                return;
            }
            // Otherwise just spawn point
            to = run.getSpawnLocation();
        } else {
            to = new Location(targetWorld, from.getX(), from.getY(), from.getZ());
        }

        if (to.getY() < 5 && !targetWorld.equals(end))
            to.setY(70);

        e.setTo(to);
    }

    private boolean isDragonDead(World endWorld) {
        // If the dragon is alive, it should be in the entity list
        return endWorld.getEntitiesByClass(org.bukkit.entity.EnderDragon.class).isEmpty();
        // Note: This is a simple check. If the chunk isn't loaded, it might return
        // empty.
        // But the player is IN the end, so dragon *should* be loaded if alive.
    }
}
