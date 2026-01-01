package yuvan.hardcoreMultiplayer.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class LobbyManager {
    private final JavaPlugin plugin;
    private final StatsManager statsManager;

    private World lobbyWorld;
    private Location lobbyLoc;
    private Location platformBase;
    private Location startNPCLoc;
    private Location statsNPCLoc;

    private Villager startNPC;
    private Villager statsNPC;

    // Constants
    private static final int PLATFORM_Y = 200;

    public LobbyManager(JavaPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    public void init() {
        lobbyWorld = Bukkit.getWorld("world");
        if (lobbyWorld == null) {
            plugin.getLogger().severe("Lobby world 'world' not found!");
            return;
        }

        // Setup Locations
        platformBase = new Location(lobbyWorld, 0, PLATFORM_Y, 0);
        lobbyLoc = new Location(lobbyWorld, 0.5, PLATFORM_Y + 1, 0.5);
        startNPCLoc = new Location(lobbyWorld, -2.5, PLATFORM_Y + 1, 0.5);
        statsNPCLoc = new Location(lobbyWorld, 2.5, PLATFORM_Y + 1, 0.5);

        // Setup World Settings
        lobbyWorld.setSpawnLocation(lobbyLoc);
        lobbyWorld.setGameRule(GameRules.MOB_GRIEFING, false);
        lobbyWorld.setGameRule(GameRules.BLOCK_DROPS, false);
        lobbyWorld.setGameRule(GameRules.FALL_DAMAGE, false);
        lobbyWorld.setGameRule(GameRules.KEEP_INVENTORY, true);

        // Build Platform and Spawn NPCs
        new BukkitRunnable() {
            @Override
            public void run() {
                buildSkyPlatform();
                createOrLoadLobbyNPCs();
            }
        }.runTaskLater(plugin, 20L);
    }

    public Location getLobbySpawn() {
        return lobbyLoc;
    }

    public World getLobbyWorld() {
        return lobbyWorld;
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

    public void createOrLoadLobbyNPCs() {
        if (lobbyWorld == null)
            return;

        // Clean existing entities
        for (Entity ent : lobbyWorld.getNearbyEntities(platformBase, 15, 15, 15)) {
            if (ent instanceof Villager) {
                Villager v = (Villager) ent;
                // Basic cleanup relying on type, we will re-spawn them properly.
                v.remove();
            }
        }

        // Spawn Start NPC
        startNPC = lobbyWorld.spawn(startNPCLoc, Villager.class);
        startNPC.customName(Component.text("Start Run", NamedTextColor.GREEN));
        startNPC.setCustomNameVisible(true);
        startNPC.setAI(false);
        startNPC.setInvulnerable(true);
        startNPC.setGravity(false);
        startNPC.setSilent(true);
        startNPC.setProfession(Villager.Profession.LIBRARIAN);
        startNPC.setVillagerLevel(5);
        startNPC.setPersistent(true);

        // Spawn Stats NPC
        statsNPC = lobbyWorld.spawn(statsNPCLoc, Villager.class);
        statsNPC.customName(Component.text("Stats:", NamedTextColor.YELLOW));
        //updateStatsNPC(); // Sets initial name
        statsNPC.setCustomNameVisible(true);
        statsNPC.setAI(false);
        statsNPC.setInvulnerable(true);
        statsNPC.setGravity(false);
        statsNPC.setSilent(true);
        statsNPC.setProfession(Villager.Profession.CLERIC);
        statsNPC.setVillagerLevel(5);
        statsNPC.setPersistent(true);
    }

    public void updateStartNPC(boolean running) {
        if (startNPC != null && !startNPC.isDead()) {
            startNPC.customName(running ? Component.text("Join Run", NamedTextColor.GREEN)
                    : Component.text("Start Run", NamedTextColor.GREEN));
        }
    }

    public void updateStatsNPC() {
        if (statsNPC != null && !statsNPC.isDead()) {
            statsNPC.customName(
                    Component.text("Stats:", NamedTextColor.GOLD)
                            .append(Component.text("\nDeaths: " + statsManager.getTotalDeaths(), NamedTextColor.RED))
                            .append(Component.text("\nPrev Time: " + statsManager.getPrevRunTime() + "s",
                                    NamedTextColor.GREEN))
                            .append(Component.text("\nLast Death: " + statsManager.getPrevDeathCause(),
                                    NamedTextColor.WHITE)));
        }
    }
}
