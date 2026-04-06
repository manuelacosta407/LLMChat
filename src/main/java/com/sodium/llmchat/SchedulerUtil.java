package com.sodium.llmchat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {

    private static final boolean FOLIA;
    
    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    private SchedulerUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void executeOnMain(Plugin plugin, CommandSender sender, Runnable task) {
        if (FOLIA) {
            if (sender instanceof Player player) {
                player.getScheduler().run(plugin, t -> task.run(), null);
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void executeAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
}
