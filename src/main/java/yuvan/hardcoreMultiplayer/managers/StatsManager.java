package yuvan.hardcoreMultiplayer.managers;

import org.bukkit.plugin.java.JavaPlugin;

public class StatsManager {
    private final JavaPlugin plugin;
    private int totalDeaths;
    private int prevRunTime;
    private String prevDeathCause;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        totalDeaths = plugin.getConfig().getInt("stats.deaths", 0);
        prevRunTime = plugin.getConfig().getInt("stats.prev-time", 0);
        prevDeathCause = plugin.getConfig().getString("stats.last-cause", "None");
    }

    public void save() {
        plugin.getConfig().set("stats.deaths", totalDeaths);
        plugin.getConfig().set("stats.prev-time", prevRunTime);
        plugin.getConfig().set("stats.last-cause", prevDeathCause);
        plugin.saveConfig();
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    public void incrementDeaths() {
        this.totalDeaths++;
    }

    public int getPrevRunTime() {
        return prevRunTime;
    }

    public void setPrevRunTime(int prevRunTime) {
        this.prevRunTime = prevRunTime;
    }

    public String getPrevDeathCause() {
        return prevDeathCause;
    }

    public void setPrevDeathCause(String prevDeathCause) {
        this.prevDeathCause = prevDeathCause;
    }
}
