package fr.vortexsmp.vortexessentials.bounty;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Primes façon DonutSMP : /bounty <joueur> <montant> met de l'argent sur la tête d'un joueur (les primes se cumulent) ;
 * celui qui le tue (un autre joueur, qui n'a pas contribué à la prime) l'encaisse. Données : bounties.yml.
 */
public final class BountyManager implements Listener {
    public static final class Bounty {
        public UUID target;
        public String name = "?";
        public double total;
        public final Map<UUID, Double> contributors = new LinkedHashMap<>();
    }

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<UUID, Bounty> bounties = new HashMap<>();

    public BountyManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "bounties.yml");
        load();
    }

    private void load() {
        bounties.clear();
        ConfigurationSection cs = store.get().getConfigurationSection("bounties");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Bounty b = new Bounty();
                b.target = UUID.fromString(k);
                b.name = s.getString("name", "?");
                b.total = s.getDouble("total");
                ConfigurationSection c = s.getConfigurationSection("contributors");
                if (c != null) for (String u : c.getKeys(false)) b.contributors.put(UUID.fromString(u), c.getDouble(u));
                if (b.total > 0) bounties.put(b.target, b);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        store.get().set("bounties", null);
        for (Bounty b : bounties.values()) {
            String p = "bounties." + b.target;
            store.get().set(p + ".name", b.name);
            store.get().set(p + ".total", b.total);
            for (Map.Entry<UUID, Double> e : b.contributors.entrySet()) store.get().set(p + ".contributors." + e.getKey(), e.getValue());
        }
        store.save();
    }

    public Bounty get(UUID target) { return bounties.get(target); }

    public List<Bounty> top() {
        List<Bounty> l = new ArrayList<>(bounties.values());
        l.sort(Comparator.comparingDouble((Bounty b) -> b.total).reversed());
        return l;
    }

    private double cd(String path, double def) { return plugin.getConfig().getDouble("bounty." + path, def); }

    public String place(Player placer, OfflinePlayer target, double amount) {
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) return "Les primes sont désactivées.";
        if (!plugin.money().available()) return "Aucune économie détectée.";
        if (target.getUniqueId().equals(placer.getUniqueId())) return "Vous ne pouvez pas mettre une prime sur vous-même.";
        if (!target.isOnline() && !target.hasPlayedBefore()) return "Ce joueur est inconnu.";
        amount = Math.round(amount * 100.0) / 100.0;
        if (amount < cd("min-amount", 100)) return "Prime minimale : " + plugin.money().format(cd("min-amount", 100)) + ".";
        if (amount > cd("max-amount", 1_000_000_000)) return "Prime maximale dépassée.";
        if (!plugin.money().withdraw(placer, amount)) return "Fonds insuffisants.";
        Bounty b = bounties.computeIfAbsent(target.getUniqueId(), k -> new Bounty());
        b.target = target.getUniqueId();
        b.name = target.getName() == null ? "?" : target.getName();
        b.total += amount;
        b.contributors.merge(placer.getUniqueId(), amount, Double::sum);
        save();
        if (plugin.getConfig().getBoolean("bounty.broadcast", true)) {
            Bukkit.broadcast(Msg.c("§8[§cPrime§8] §f" + placer.getName() + " §7place une prime sur §c" + b.name + " §7: §6" + plugin.money().format(b.total)));
        }
        Player online = target.getPlayer();
        if (online != null) online.sendMessage(Msg.c("§cUne prime de §6" + plugin.money().format(b.total) + " §cest sur votre tête !"));
        return null;
    }

    public boolean remove(UUID target) {
        Bounty b = bounties.remove(target);
        if (b == null) return false;
        for (Map.Entry<UUID, Double> e : b.contributors.entrySet()) plugin.money().deposit(Bukkit.getOfflinePlayer(e.getKey()), e.getValue());
        save();
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        Player victim = e.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        Bounty b = bounties.get(victim.getUniqueId());
        if (b == null || !plugin.money().available()) return;
        if (b.contributors.containsKey(killer.getUniqueId())) {
            killer.sendMessage(Msg.c("§cVous avez contribué à cette prime : elle reste sur la tête de §f" + victim.getName() + "§c."));
            return;
        }
        double tax = Math.max(0, Math.min(100, cd("tax-percent", 5)));
        double reward = Math.round(b.total * (1 - tax / 100.0) * 100.0) / 100.0;
        if (!plugin.money().deposit(killer, reward)) return;
        bounties.remove(victim.getUniqueId());
        save();
        if (plugin.getConfig().getBoolean("bounty.broadcast", true)) {
            Bukkit.broadcast(Msg.c("§8[§cPrime§8] §f" + killer.getName() + " §7encaisse la prime de §c" + victim.getName() + " §7: §6" + plugin.money().format(reward)));
        } else {
            killer.sendMessage(Msg.c("§aVous encaissez la prime de §f" + victim.getName() + " §a: §6" + plugin.money().format(reward)));
        }
    }
}
