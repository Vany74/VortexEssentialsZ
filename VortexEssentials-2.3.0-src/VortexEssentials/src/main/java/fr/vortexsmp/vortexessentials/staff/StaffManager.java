package fr.vortexsmp.vortexessentials.staff;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outils staff : prison (/jail, /unjail, /setjail...) et gel (/freeze).
 * Un joueur emprisonné reste en prison après une déconnexion ; la peine s'écoule en temps réel (hors ligne comprise).
 */
public final class StaffManager implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore jails;
    private final YamlStore jailed;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public StaffManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.jails = new YamlStore(plugin, "jails.yml");
        this.jailed = new YamlStore(plugin, "jailed.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, this::release, 100L, 100L);
    }

    // ═════════════ Prisons ═════════════

    public Set<String> jailNames() {
        ConfigurationSection cs = jails.get().getConfigurationSection("jails");
        return cs == null ? new TreeSet<>() : new TreeSet<>(cs.getKeys(false));
    }

    public Location jailLocation(String name) { return jails.get().getLocation("jails." + name); }

    public void setJail(String name, Location l) {
        jails.get().set("jails." + name.toLowerCase(Locale.ROOT), l);
        jails.save();
    }

    public boolean delJail(String name) {
        if (!jails.get().contains("jails." + name.toLowerCase(Locale.ROOT))) return false;
        jails.get().set("jails." + name.toLowerCase(Locale.ROOT), null);
        jails.save();
        return true;
    }

    /** Prison par défaut : staff.default-jail, sinon la première définie. */
    public String defaultJail() {
        String d = plugin.getConfig().getString("staff.default-jail", "");
        if (d != null && !d.isBlank() && jailLocation(d.toLowerCase(Locale.ROOT)) != null) return d.toLowerCase(Locale.ROOT);
        Set<String> n = jailNames();
        return n.isEmpty() ? null : n.iterator().next();
    }

    public boolean isJailed(UUID id) { return jailed.get().contains(id.toString()); }
    public boolean isFrozen(UUID id) { return frozen.contains(id); }

    private Location jailOf(UUID id) {
        String name = jailed.get().getString(id + ".jail");
        return name == null ? null : jailLocation(name);
    }

    /** Emprisonne. minutes <= 0 : jusqu'à /unjail. Renvoie un message d'erreur ou null. */
    public String jail(Player t, long minutes, String reason, String by) {
        String name = defaultJail();
        if (name == null) return "Aucune prison définie (/setjail <nom>).";
        Location jl = jailLocation(name);
        if (jl == null || jl.getWorld() == null) return "Prison invalide.";
        String p = t.getUniqueId().toString();
        if (!jailed.get().contains(p)) jailed.get().set(p + ".previous", t.getLocation());
        jailed.get().set(p + ".name", t.getName());
        jailed.get().set(p + ".jail", name);
        jailed.get().set(p + ".until", minutes <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L);
        jailed.get().set(p + ".reason", reason);
        jailed.get().set(p + ".by", by);
        jailed.save();
        t.teleport(jl);
        t.sendMessage(Msg.c("§cVous avez été emprisonné" + (minutes > 0 ? " pour §f" + minutes + " min" : "") + "§c. Raison : §f" + reason));
        return null;
    }

    public boolean unjail(UUID id, boolean notify) {
        if (!isJailed(id)) return false;
        Location back = jailed.get().getLocation(id + ".previous");
        jailed.get().set(id.toString(), null);
        jailed.save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            Location dest = back != null && back.getWorld() != null ? back : p.getWorld().getSpawnLocation();
            p.teleport(dest);
            if (notify) p.sendMessage(Msg.c("§aVous êtes libre."));
        }
        return true;
    }

    public List<String> jailedNames() {
        List<String> out = new ArrayList<>();
        for (String k : jailed.get().getKeys(false)) out.add(jailed.get().getString(k + ".name", k));
        return out;
    }

    private void release() {
        long now = System.currentTimeMillis();
        for (String k : new ArrayList<>(jailed.get().getKeys(false))) {
            long until = jailed.get().getLong(k + ".until", 0L);
            if (until > 0 && now >= until) {
                try { unjail(UUID.fromString(k), true); } catch (IllegalArgumentException ignored) { }
            }
        }
        for (UUID id : frozen) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(Msg.c("§bVous êtes gelé par le staff — ne vous déconnectez pas."));
        }
    }

    // ═════════════ Gel ═════════════

    public boolean toggleFreeze(UUID id) {
        if (frozen.remove(id)) return false;
        frozen.add(id);
        return true;
    }

    // ═════════════ Événements ═════════════

    private boolean locked(Player p) { return isJailed(p.getUniqueId()) || isFrozen(p.getUniqueId()); }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (frozen.contains(id)) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location nt = to.clone();
                nt.setX(from.getX());
                nt.setY(from.getY());
                nt.setZ(from.getZ());
                e.setTo(nt);
            }
            return;
        }
        if (!isJailed(id) || !e.hasChangedBlock()) return;
        Location jl = jailOf(id);
        if (jl == null || jl.getWorld() == null) return;
        double radius = plugin.getConfig().getDouble("staff.jail-radius", 6.0);
        if (to.getWorld() != jl.getWorld() || to.distanceSquared(jl) > radius * radius) e.setTo(jl);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!isJailed(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location jl = jailOf(p.getUniqueId());
            if (p.isOnline() && jl != null) {
                p.teleport(jl);
                long until = jailed.get().getLong(p.getUniqueId() + ".until", 0L);
                p.sendMessage(Msg.c("§cVous êtes toujours en prison" + (until > 0 ? " (encore §f" + Math.max(1, (until - System.currentTimeMillis()) / 60_000L) + " min§c)" : "") + "."));
            }
        }, 10L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId()) && plugin.getConfig().getBoolean("staff.freeze-quit-alert", true)) {
            String msg = "§c" + e.getPlayer().getName() + " s'est déconnecté en étant gelé !";
            for (Player s : Bukkit.getOnlinePlayers()) if (s.hasPermission("vortexessentials.staff.freeze")) s.sendMessage(Msg.c(msg));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void attack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!locked(p) || p.hasPermission("vortexessentials.staff.bypass")) return;
        String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        for (String ok : plugin.getConfig().getStringList("staff.allowed-commands")) if (ok.equalsIgnoreCase(cmd)) return;
        e.setCancelled(true);
        p.sendMessage(Msg.c("§cCommande interdite : vous êtes " + (isJailed(p.getUniqueId()) ? "en prison" : "gelé") + "."));
    }
}
