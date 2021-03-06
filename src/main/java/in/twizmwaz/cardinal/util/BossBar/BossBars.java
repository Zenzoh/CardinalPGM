package in.twizmwaz.cardinal.util.bossBar;

import in.twizmwaz.cardinal.chat.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.UUID;

public class BossBars implements Listener {

    public static HashMap<String, LocalizedBossBar> broadcastedBossBars = new HashMap<>();

    public static String addBroadcastedBossBar(ChatMessage bossBarTitle, BarColor color, BarStyle style, Boolean shown, BarFlag... flags) {
        String id = UUID.randomUUID().toString();
        LocalizedBossBar bossBar = new LocalizedBossBar(bossBarTitle, color, style, flags);
        bossBar.setVisible(shown);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        broadcastedBossBars.put(id, bossBar);
        return id;
    }

    public static void removeBroadcastedBossBar(String id) {
        if (broadcastedBossBars.containsKey(id)) {
            broadcastedBossBars.get(id).setVisible(false);
            broadcastedBossBars.get(id).removeAll();
            broadcastedBossBars.remove(id);
        }
    }

    public static void setTitle(String id, ChatMessage chat) {
        if (broadcastedBossBars.containsKey(id)) broadcastedBossBars.get(id).setTitle(chat);
    }

    public static void setProgress(String id, Double progress) {
        if (broadcastedBossBars.containsKey(id)) broadcastedBossBars.get(id).setProgress(progress);
    }

    public static void setVisible(String id, Boolean visible) {
        if (broadcastedBossBars.containsKey(id)) broadcastedBossBars.get(id).setVisible(visible);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoinEvent(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for (LocalizedBossBar bossBar : broadcastedBossBars.values()) {
            bossBar.addPlayer(player);
        }
    }

    @EventHandler
    public void onQuitEvent(PlayerQuitEvent event) {
        for (LocalizedBossBar bossBar : broadcastedBossBars.values()) {
            bossBar.removePlayer(event.getPlayer());
        }
    }


}
