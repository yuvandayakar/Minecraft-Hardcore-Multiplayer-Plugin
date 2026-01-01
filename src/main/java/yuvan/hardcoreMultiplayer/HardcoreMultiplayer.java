package yuvan.hardcoreMultiplayer;

import org.bukkit.plugin.java.JavaPlugin;
import yuvan.hardcoreMultiplayer.commands.HCCommand;
import yuvan.hardcoreMultiplayer.commands.StartRunCommand;
import yuvan.hardcoreMultiplayer.listeners.HardcoreListener;
import yuvan.hardcoreMultiplayer.managers.GameManager;
import yuvan.hardcoreMultiplayer.managers.LobbyManager;
import yuvan.hardcoreMultiplayer.managers.StatsManager;
import yuvan.hardcoreMultiplayer.managers.WorldManager;

import java.util.Objects;

public class HardcoreMultiplayer extends JavaPlugin {

    private StatsManager statsManager;
    private WorldManager worldManager;
    private LobbyManager lobbyManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize Managers
        this.statsManager = new StatsManager(this);
        this.worldManager = new WorldManager(this);
        this.lobbyManager = new LobbyManager(this, statsManager);
        this.gameManager = new GameManager(this, statsManager, worldManager, lobbyManager);

        // Initialize Logic
        lobbyManager.init();
        gameManager.init();

        // Register Commands
        Objects.requireNonNull(getCommand("startrun")).setExecutor(new StartRunCommand(gameManager));
        Objects.requireNonNull(getCommand("hc")).setExecutor(new HCCommand(statsManager, lobbyManager));

        // Register Listeners
        getServer().getPluginManager().registerEvents(
                new HardcoreListener(this, gameManager, statsManager, lobbyManager, worldManager),
                this);
    }

    @Override
    public void onDisable() {
        if (statsManager != null) {
            statsManager.save();
        }
        // Game end logic dealing with worlds is handled by managers,
        // but typically plugins don't need to force delete worlds on shutdown unless
        // desired.
        // We'll trust the state remains for next boot or manual cleanup if needed.
    }
}