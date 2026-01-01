package yuvan.hardcoreMultiplayer.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import yuvan.hardcoreMultiplayer.managers.GameManager;

public class StartRunCommand implements CommandExecutor {
    private final GameManager gameManager;

    public StartRunCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        gameManager.startRun();
        return true;
    }
}
