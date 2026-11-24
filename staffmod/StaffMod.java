package fr.vortexsmp.vortexessentials.staffmod;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mode staff à paliers (/staffmod). Chaque palier (helper, modo, admin, owner...) débloque, tant que le staff est en mode
 * staff, un ensemble de commandes de gestion (catalogue StaffCatalog) et peut activer vanish, vol et un mode de jeu.
 * Un joueur reçoit son palier par assignation (/staffmod assign) ou par la permission vortexessentials.staffmod.tier.<palier>.
 * Les paliers se créent et se modifient en jeu avec /staffmod menu. Données : staffmod.yml.
 */
public final class StaffMod implements Listener {
    public static final class Tier {
        public String name;
        public int weight = 1;
        public final Set<String> features = new LinkedHashSet<>();
        public boolean vanish, fly;
        public String gamemode = "SURVIVAL";
        Tier(String name) { this.name = name; }
    }

    private static final class Active {
        PermissionAttachment att;
        GameMode prevGm;
        boolean prevFlight;
        String tier;
        boolean vanished;
    }

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final Map<UUID, String> assigned = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, String> prompts = new HashMap<>();

    public StaffMod(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "staffmod.yml");
        load();
    }

    private void load() {
        tiers.clear();
        assigned.clear();
        ConfigurationSection cs = store.get().getConfigurationSection("tiers");
        if (cs == null || cs.getKeys(false).isEmpty()) {
            defaults();
            return;
        }
        for (String k : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(k);
            if (s == null) continue;
            Tier t = new Tier(k);
            t.weight = s.getInt("weight", 1);
            t.features.addAll(s.getStringList("features"));
            t.vanish = s.getBoolean("vanish");
            t.fly = s.getBoolean("fly");
            t.gamemode = s.getString("gamemode", "SURVIVAL");
            tiers.put(k, t);
        }
        ConfigurationSection ps = store.get().getConfigurationSection("players");
        if (ps != null) for (String k : ps.getKeys(false)) {
            try { assigned.put(UUID.fromString(k), ps.getString(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void defaults() {
        add("helper", 1, List.of("freeze", "xray"), false, true, "SURVIVAL");
        add("modo", 2, List.of("freeze", "xray", "jail", "announce", "bypass"), true, true, "SURVIVAL");
        add("admin", 3, List.of("freeze", "xray", "jail", "announce", "bypass", "maintenance", "servermenu", "orders", "shop", "bounty", "spawners"), true, true, "CREATIVE");
        add("owner", 4, List.of("freeze", "xray", "jail", "announce", "bypass", "maintenance", "servermenu", "orders", "shop", "bounty", "spawners", "smp", "admin"), true, true, "CREATIVE");
        save();
    }

    private void add(String name, int weight, List<String> features, boolean vanish, boolean fly, String gm) {
        Tier t = new Tier(name);
        t.weight = weight;
        t.features.addAll(features);
        t.vanish = vanish;
        t.fly = fly;
        t.gamemode = gm;
        tiers.put(name, t);
    }

    public void save() {
        store.get().set("tiers", null);
        store.get().set("players", null);
        for (Tier t : tiers.values()) {
            String p = "tiers." + t.name;
            store.get().set(p + ".weight", t.weight);
            store.get().set(p + ".features", new ArrayList<>(t.features));
            store.get().set(p + ".vanish", t.vanish);
            store.get().set(p + ".fly", t.fly);
            store.get().set(p + ".gamemode", t.gamemode);
        }
        for (Map.Entry<UUID, String> e : assigned.entrySet()) store.get().set("players." + e.getKey(), e.getValue());
        store.save();
    }

    public List<Tier> tiers() {
        List<Tier> l = new ArrayList<>(tiers.values());
        l.sort(Comparator.comparingInt((Tier t) -> t.weight).thenComparing(t -> t.name));
        return l;
    }

    public Tier tier(String name) { return name == null ? null : tiers.get(name.toLowerCase(Locale.ROOT)); }

    public String create(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (n.isBlank() || tiers.containsKey(n)) return "Nom invalide ou déjà utilisé.";
        Tier t = new Tier(n);
        t.weight = tiers.values().stream().mapToInt(x -> x.weight).max().orElse(0) + 1;
        tiers.put(n, t);
        save();
        return null;
    }

    public boolean delete(String name) {
        if (tiers.remove(name) == null) return false;
        assigned.values().removeIf(v -> v.equals(name));
        save();
        return true;
    }

    public void assign(UUID id, String tier) { assigned.put(id, tier); save(); }
    public boolean unassign(UUID id) { boolean ok = assigned.remove(id) != null; if (ok) save(); return ok; }
    public String assignedTier(UUID id) { return assigned.get(id); }

    public Tier tierOf(Player p) {
        Tier t = tier(assigned.get(p.getUniqueId()));
        if (t != null) return t;
        Tier best = null;
        for (Tier c : tiers.values()) {
            if (p.hasPermission("vortexessentials.staffmod.tier." + c.name) && (best == null || c.weight > best.weight)) best = c;
        }
        return best;
    }

    public boolean isActive(UUID id) { return active.containsKey(id); }
    public List<String> activeNames() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Active> e : active.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) out.add(p.getName() + " (" + e.getValue().tier + ")");
        }
        return out;
    }

    public String toggle(Player p) {
        if (active.containsKey(p.getUniqueId())) { leave(p, true); return null; }
        Tier t = tierOf(p);
        if (t == null) return "Aucun palier de staff ne vous est attribué.";
        enter(p, t);
        return null;
    }

    private void enter(Player p, Tier t) {
        Active a = new Active();
        a.tier = t.name;
        a.att = p.addAttachment(plugin);
        for (String f : t.features) {
            StaffCatalog.Entry e = StaffCatalog.get(f);
            if (e != null) for (String node : e.nodes()) a.att.setPermission(node, true);
        }
        a.prevGm = p.getGameMode();
        a.prevFlight = p.getAllowFlight();
        try {
            GameMode gm = GameMode.valueOf(t.gamemode);
            if (gm != a.prevGm) p.setGameMode(gm);
        } catch (IllegalArgumentException ignored) { }
        if (t.fly) p.setAllowFlight(true);
        if (t.vanish) {
            a.vanished = true;
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && !o.hasPermission("vortexessentials.staffmod")) o.hidePlayer(plugin, p);
        }
        active.put(p.getUniqueId(), a);
        p.sendMessage(Msg.c("§8[§bStaff§8] §aMode staff activé §7— palier §f" + t.name + " §7(" + t.features.size() + " groupe(s) de commandes)"));
        p.updateCommands();
    }

    public void leave(Player p, boolean notify) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        if (a.att != null) p.removeAttachment(a.att);
        if (a.prevGm != null) p.setGameMode(a.prevGm);
        p.setAllowFlight(a.prevFlight || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
        if (a.vanished) for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
        if (notify) p.sendMessage(Msg.c("§8[§bStaff§8] §7Mode staff désactivé."));
        p.updateCommands();
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p, false);
        }
    }

    public void refresh(String tierName) {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Active a = active.get(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null || !a.tier.equals(tierName)) continue;
            leave(p, false);
            Tier t = tier(tierName);
            if (t != null) enter(p, t);
        }
    }

    public void prompt(Player p, String what) {
        prompts.put(p.getUniqueId(), what);
        p.sendMessage(Msg.c("§bTapez le nom du nouveau palier dans le chat §7(ou §fannuler§7)."));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        String what = prompts.remove(e.getPlayer().getUniqueId());
        if (what == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("annuler")) { p.sendMessage(Msg.c("§7Création annulée.")); }
            else {
                String err = create(msg);
                p.sendMessage(Msg.c(err != null ? "§c" + err : "§aPalier §f" + msg.toLowerCase(Locale.ROOT) + " §acréé."));
            }
            plugin.staffModMenu().openList(p);
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
        leave(e.getPlayer(), false);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.getConfig().getBoolean("staffmod.hint-on-join", true) && tierOf(p) != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.sendMessage(Msg.c("§8[§bStaff§8] §7Votre palier : §f" + tierOf(p).name + " §7— §f/staffmod §7pour le mode staff.")); }, 40L);
        }
    }
}
