package fr.vortexsmp.vortexessentials.staff;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * X-ray pour le staff (/staffxray) : les minerais proches (liste staff.xray.ores) sont entourés d'un halo coloré visible
 * à travers les blocs, uniquement par le staff qui l'active. Sert à vérifier les galeries d'un joueur suspect.
 */
public final class StaffXray implements Listener {
    private static final class State {
        World world;
        int radius;
        final Map<Long, BlockDisplay> displays = new HashMap<>();
    }

    private final VortexEssentials plugin;
    private final Map<UUID, State> active = new HashMap<>();
    private BukkitTask task;

    public StaffXray(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private Set<Material> ores() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String n : plugin.getConfig().getStringList("staff.xray.ores")) {
            Material m = Material.matchMaterial(n);
            if (m != null) set.add(m);
        }
        if (set.isEmpty()) {
            set.add(Material.DIAMOND_ORE);
            set.add(Material.DEEPSLATE_DIAMOND_ORE);
            set.add(Material.ANCIENT_DEBRIS);
        }
        return set;
    }

    private static Color color(Material m) {
        String n = m.name();
        if (n.contains("DIAMOND")) return Color.AQUA;
        if (n.contains("EMERALD")) return Color.LIME;
        if (n.contains("DEBRIS")) return Color.fromRGB(0xC0703A);
        if (n.contains("GOLD")) return Color.YELLOW;
        if (n.contains("LAPIS")) return Color.BLUE;
        if (n.contains("REDSTONE") || n.contains("SPAWNER")) return Color.RED;
        return Color.WHITE;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    /** Active ou désactive. Renvoie true si le x-ray est maintenant actif. */
    public boolean toggle(Player p, int radius) {
        State st = active.get(p.getUniqueId());
        if (st != null) {
            clear(st);
            active.remove(p.getUniqueId());
            return false;
        }
        st = new State();
        st.world = p.getWorld();
        st.radius = Math.max(4, Math.min((int) plugin.getConfig().getDouble("staff.xray.max-radius", 16), radius));
        active.put(p.getUniqueId(), st);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 20L);
        return true;
    }

    private void clear(State st) {
        for (BlockDisplay d : st.displays.values()) d.remove();
        st.displays.clear();
    }

    public void clearAll() {
        for (State st : active.values()) clear(st);
        active.clear();
        if (task != null) { task.cancel(); task = null; }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        State st = active.remove(e.getPlayer().getUniqueId());
        if (st != null) clear(st);
    }

    private void tick() {
        if (active.isEmpty()) {
            if (task != null) { task.cancel(); task = null; }
            return;
        }
        Set<Material> ores = ores();
        int max = (int) plugin.getConfig().getDouble("staff.xray.max-displays", 300);
        for (Map.Entry<UUID, State> en : new HashMap<>(active).entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            State st = en.getValue();
            if (p == null || !p.isOnline()) { clear(st); active.remove(en.getKey()); continue; }
            World w = p.getWorld();
            if (w != st.world) { clear(st); st.world = w; }

            Location c = p.getLocation();
            int r = st.radius;
            Set<Long> seen = new HashSet<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int dy = -r; dy <= r; dy++) {
                        int y = c.getBlockY() + dy;
                        if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                        Material m = w.getBlockAt(x, y, z).getType();
                        if (!ores.contains(m)) continue;
                        long k = key(x, y, z);
                        seen.add(k);
                        if (st.displays.containsKey(k) || st.displays.size() >= max) continue;
                        final int fx = x, fy = y, fz = z;
                        final Material fm = m;
                        BlockDisplay bd = w.spawn(new Location(w, fx, fy, fz), BlockDisplay.class, d -> {
                            d.setBlock(fm.createBlockData());
                            d.setGlowing(true);
                            d.setGlowColorOverride(color(fm));
                            d.setVisibleByDefault(false);
                            d.setPersistent(false);
                            d.setBrightness(new Display.Brightness(15, 15));
                        });
                        p.showEntity(plugin, bd);
                        st.displays.put(k, bd);
                    }
                }
            }
            st.displays.entrySet().removeIf(d -> {
                if (seen.contains(d.getKey())) return false;
                d.getValue().remove();
                return true;
            });
        }
    }
}
