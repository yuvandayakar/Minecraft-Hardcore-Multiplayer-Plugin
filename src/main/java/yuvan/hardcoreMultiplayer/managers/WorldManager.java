package yuvan.hardcoreMultiplayer.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Random;

public class WorldManager {
    private final JavaPlugin plugin;
    private World runWorld;
    private World runNether;
    private World runEnd;

    public WorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void createRunWorlds(long seed) {
        // Create run overworld
        if (Bukkit.getWorld("runworld") == null) {
            plugin.getLogger().info("Creating runworld with seed: " + seed);
            WorldCreator wc = new WorldCreator("runworld");
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.NORMAL);
            wc.seed(seed);
            wc.generateStructures(true);
            runWorld = Bukkit.createWorld(wc);
        } else {
            runWorld = Bukkit.getWorld("runworld");
        }

        if (runWorld != null) {
            runWorld.setGameRule(GameRules.ADVANCE_TIME, true);
            runWorld.setGameRule(GameRules.SPAWN_MOBS, true);
            runWorld.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            runWorld.setDifficulty(Difficulty.HARD);
            runWorld.setHardcore(true);
        }

        // Create run nether
        if (Bukkit.getWorld("runworld_nether") == null) {
            WorldCreator wc = new WorldCreator("runworld_nether");
            wc.environment(World.Environment.NETHER);
            wc.type(WorldType.NORMAL);
            wc.seed(seed);
            wc.hardcore(true);
            wc.generateStructures(true);
            runNether = Bukkit.createWorld(wc);
        } else {
            runNether = Bukkit.getWorld("runworld_nether");
        }

        if (runNether != null) {
            runNether.setDifficulty(Difficulty.HARD);
            runNether.setHardcore(true);
        }

        // Create run end
        if (Bukkit.getWorld("runworld_the_end") == null) {
            WorldCreator wc = new WorldCreator("runworld_the_end");
            wc.environment(World.Environment.THE_END);
            wc.type(WorldType.NORMAL);
            wc.seed(seed);
            wc.hardcore(true);
            wc.generateStructures(true);
            runEnd = Bukkit.createWorld(wc);
        } else {
            runEnd = Bukkit.getWorld("runworld_the_end");
        }

        if (runEnd != null) {
            runEnd.setDifficulty(Difficulty.HARD);
            runEnd.setHardcore(true);
        }
    }

    public void unloadAndDeleteWorlds() {
        if (runWorld != null)
            Bukkit.unloadWorld(runWorld, false);
        if (runNether != null)
            Bukkit.unloadWorld(runNether, false);
        if (runEnd != null)
            Bukkit.unloadWorld(runEnd, false);

        runWorld = null;
        runNether = null;
        runEnd = null;

        // Schedule deletion
        new BukkitRunnable() {
            @Override
            public void run() {
                deleteFolder(new File(Bukkit.getWorldContainer(), "runworld"));
                deleteFolder(new File(Bukkit.getWorldContainer(), "runworld_nether"));
                deleteFolder(new File(Bukkit.getWorldContainer(), "runworld_the_end"));
                plugin.getLogger().info("Deleted runworld folders.");
            }
        }.runTaskLater(plugin, 40L);
    }

    private void deleteFolder(File folder) {
        if (!folder.exists())
            return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    public boolean isRunDimension(World w) {
        if (w == null)
            return false;
        String name = w.getName();
        return name.equals("runworld") ||
                name.equals("runworld_nether") ||
                name.equals("runworld_the_end");
    }

    public World getRunWorld() {
        return runWorld;
    }

    public World getRunNether() {
        return runNether;
    }

    public World getRunEnd() {
        return runEnd;
    }

    // Helper to get target world for portals
    public World getTargetRunWorld(World.Environment targetEnv) {
        switch (targetEnv) {
            case NORMAL:
                return runWorld;
            case NETHER:
                return runNether;
            case THE_END:
                return runEnd;
            default:
                return null;
        }
    }

    public void generateEndPlatform(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY() - 1; // Platform is under the player
        int z = loc.getBlockZ();
        World world = loc.getWorld();

        // 5x5 Obsidian Platform
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.OBSIDIAN);

                // Clear 3 blocks air above
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
    }
}
