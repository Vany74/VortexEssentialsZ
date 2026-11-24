package fr.vortexsmp.vortexessentials.maintenance;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mode maintenance : seuls entrent les joueurs avec la permission vortexessentials.maintenance.bypass, les opérateurs,
 * la liste de maintenance (/maintenance add) et, si maintenance.use-server-whitelist est vrai, la whitelist du serveur.
 * L'état est conservé dans maintenance.yml (il survit aux redémarrages).
 */
public final class MaintenanceManager implements Listener {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<UUID, String> allowed = new LinkedHashMap<>();

    public MaintenanceManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "maintenance.yml");
        ConfigurationSection cs = store.get().getConfigurationSection("allowed");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                try { allowed.put(UUID.fromString(k), cs.getString(k, k)); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public boolean enabled() { return store.get().getBoolean("enabled", false); }

    public void setEnabled(boolean on) {
        store.get().set("enabled", on);
        store.save();
        if (on && plugin.getConfig().getBoolean("maintenance.kick-online", true)) kickOnline();
    }

    public Map<UUID, String> allowed() { return allowed; }

    public void add(OfflinePlayer p) {
        allowed.put(p.getUniqueId(), p.getName() == null ? p.getUniqueId().toString() : p.getName());
        persist();
    }

    public boolean remove(UUID id) {
        boolean ok = allowed.remove(id) != null;
        if (ok) persist();
        return ok;
    }

    private void persist() {
        store.get().set("allowed", null);
        for (Map.Entry<UUID, String> e : allowed.entrySet()) store.get().set("allowed." + e.getKey(), e.getValue());
        store.save();
    }

    public boolean canJoin(Player p) {
        if (p.isOp() || p.hasPermission("vortexessentials.maintenance.bypass") || allowed.containsKey(p.getUniqueId())) return true;
        if (plugin.getConfig().getBoolean("maintenance.use-server-whitelist", true)) {
            for (OfflinePlayer o : Bukkit.getWhitelistedPlayers()) if (o.getUniqueId().equals(p.getUniqueId())) return true;
        }
        return false;
    }

    private Component kickMessage() {
        return AMP.deserialize(plugin.getConfig().getString("maintenance.kick-message", "&c&lServeur en maintenance\n&7Revenez un peu plus tard !"));
    }

    public void kickOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) if (!canJoin(p)) p.kick(kickMessage());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void login(PlayerLoginEvent e) {
        if (!enabled() || canJoin(e.getPlayer())) return;
        e.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage());
    }

    @EventHandler
    public void ping(ServerListPingEvent e) {
        String motd = plugin.getConfig().getString("maintenance.motd", "");
        if (enabled() && motd != null && !motd.isBlank()) e.motd(AMP.deserialize(motd));
    }
}
