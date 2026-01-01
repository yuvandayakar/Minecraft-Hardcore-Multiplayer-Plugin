package yuvan.hardcoreMultiplayer.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import yuvan.hardcoreMultiplayer.managers.LobbyManager;
import yuvan.hardcoreMultiplayer.managers.StatsManager;

import java.util.Arrays;

public class HCCommand implements CommandExecutor {
    private final StatsManager statsManager;
    private final LobbyManager lobbyManager;

    public HCCommand(StatsManager statsManager, LobbyManager lobbyManager) {
        this.statsManager = statsManager;
        this.lobbyManager = lobbyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (sender instanceof Player && !sender.hasPermission("hardcoremultiplayer.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(
                    Component.text("/hc deaths <n> | time <s> | cause <msg...> | reset", NamedTextColor.YELLOW));
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "deaths":
                    statsManager.setTotalDeaths(Integer.parseInt(args[1]));
                    break;
                case "time":
                    statsManager.setPrevRunTime(Integer.parseInt(args[1]));
                    break;
                case "cause":
                    statsManager.setPrevDeathCause(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    break;
                case "reset":
                    statsManager.setTotalDeaths(0);
                    statsManager.setPrevRunTime(0);
                    statsManager.setPrevDeathCause("None");
                    break;
                default:
                    sender.sendMessage(Component.text("Invalid subcommand!", NamedTextColor.RED));
                    return true;
            }
            statsManager.save();
            lobbyManager.updateStatsNPC();
            sender.sendMessage(Component.text("Stats updated!", NamedTextColor.GREEN));
        } catch (Exception ex) {
            sender.sendMessage(Component.text("Invalid arguments!", NamedTextColor.RED));
        }
        return true;
    }
}
