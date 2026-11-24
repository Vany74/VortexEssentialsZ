package fr.vortexsmp.vortexessentials.playtime;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Temps de jeu actif (playtime.yml) : le temps ne compte plus quand le joueur est inactif
 * (playtime.afk-seconds). À la première connexion, le compteur part du temps de jeu vanilla du joueur.
 * Récompenses par palier dans config.yml (playtime.rewards).
 */
public final class PlaytimeManager implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<UUID, Long> seconds = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> lastActive = new HashMap<>();
    private BukkitTask tickTask, saveTask;

    public PlaytimeManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "playtime.yml");
        load();
        start();
    }

    private void load() {
        store.reload();
        seconds.clear();
        names.clear();
        ConfigurationSection ps = store.get().getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                seconds.put(id, ps.getLong(k + ".seconds"));
                names.put(id, ps.getString(k + ".name", k));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    /** (Re)démarre les tâches périodiques d'après la config. */
    public void start() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        tickTask = saveTask = null;
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) return;

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long afk = Math.max(30, plugin.getConfig().getInt("playtime.afk-seconds", 300)) * 1000L;
            for (Player p : Bukkit.getOnlinePlayers()) {
                Long last = lastActive.get(p.getUniqueId());
                if (last == null || now - last < afk) add(p, 5);
            }
        }, 100L, 100L);
        long every = Math.max(60, plugin.getConfig().getInt("playtime.save-interval-seconds", 300)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, every, every);
    }

    public void reload() { start(); }

    public long seconds(UUID id) { return seconds.getOrDefault(id, 0L); }

    public static String format(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "j " + h + "h " + m + "min";
        if (h > 0) return h + "h " + m + "min";
        return m + "min";
    }

    /** UUID d'un joueur connu par son pseudo (insensible à la casse), ou null. */
    public UUID find(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    public String name(UUID id) { return names.getOrDefault(id, id.toString()); }

    public List<Map.Entry<UUID, Long>> top(int n) {
        List<Map.Entry<UUID, Long>> l = new ArrayList<>(seconds.entrySet());
        l.sort(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()));
        return l.subList(0, Math.min(n, l.size()));
    }

    public int rank(UUID id) {
        long mine = seconds(id);
        int r = 1;
        for (long v : seconds.values()) if (v > mine) r++;
        return r;
    }

    private void add(Player p, long s) {
        long total = seconds.merge(p.getUniqueId(), s, Long::sum);
        names.put(p.getUniqueId(), p.getName());
        checkRewards(p, total);
    }

    private void checkRewards(Player p, long total) {
        List<Map<?, ?>> rewards = plugin.getConfig().getMapList("playtime.rewards");
        if (rewards.isEmpty()) return;
        String path = "players." + p.getUniqueId() + ".claimed";
        List<Integer> claimed = new ArrayList<>(store.get().getIntegerList(path));
        boolean changed = false;
        for (Map<?, ?> r : rewards) {
            Object h = r.get("hours");
            if (!(h instanceof Number num)) continue;
            int hours = num.intValue();
            if (total < hours * 3600L || claimed.contains(hours)) continue;
            claimed.add(hours);
            changed = true;
            Object cmds = r.get("commands");
            if (cmds instanceof List<?> l) {
                for (Object c : l) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.valueOf(c).replace("{player}", p.getName()));
                }
            }
            Object msg = r.get("message");
            if (msg != null) p.sendMessage(Msg.raw(String.valueOf(msg).replace("{player}", p.getName()).replace('&', '§')));
        }
        if (changed) {
            store.get().set(path, claimed);
            save();
        }
    }

    public void save() {
        for (Map.Entry<UUID, Long> e : seconds.entrySet()) {
            store.get().set("players." + e.getKey() + ".seconds", e.getValue());
            store.get().set("players." + e.getKey() + ".name", names.getOrDefault(e.getKey(), e.getKey().toString()));
        }
        store.save();
    }

    // ═════════════ Activité ═════════════

    private void active(Player p) {
        long now = System.currentTimeMillis();
        Long l = lastActive.get(p.getUniqueId());
        if (l == null || now - l > 1000) lastActive.put(p.getUniqueId(), now);
    }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (e.hasChangedPosition() || e.hasChangedOrientation()) active(e.getPlayer());
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) { active(e.getPlayer()); }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent e) { active(e.getPlayer()); }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        lastActive.put(id, System.currentTimeMillis());
        names.put(id, p.getName());
        if (!seconds.containsKey(id)) seconds.put(id, p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L); // reprise du temps vanilla
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        lastActive.remove(e.getPlayer().getUniqueId());
        save();
    }
}
