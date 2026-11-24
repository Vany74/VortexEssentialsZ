package fr.vortexsmp.vortexessentials.legendary;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

import java.util.UUID;

/**
 * Armes légendaires : nombre d'exemplaires craftables limité (legendary-limits dans config.yml).
 *  - max-per-weapon : par arme, sur tout le serveur (3 par défaut) ;
 *  - max-per-player : par joueur, toutes armes légendaires confondues (2 par défaut).
 * Les compteurs sont dans legendary-data.yml (/vortex legendary status|reset|resetplayer).
 */
public final class LegendaryLimits implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;

    public LegendaryLimits(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "legendary-data.yml");
    }

    public int maxPerWeapon() { return plugin.getConfig().getInt("legendary-limits.max-per-weapon", 3); }
    public int maxPerPlayer() { return plugin.getConfig().getInt("legendary-limits.max-per-player", 2); }

    public int crafted(VortexItem v) { return store.get().getInt("crafted." + v.id()); }
    public int craftedBy(UUID id) { return store.get().getInt("players." + id + ".total"); }

    /** 0 = autorisé, 1 = arme épuisée sur le serveur, 2 = limite du joueur atteinte. */
    public int check(Player p, VortexItem v) {
        if (p.hasPermission("vortexessentials.admin.bypass")) return 0;
        if (maxPerWeapon() > 0 && crafted(v) >= maxPerWeapon()) return 1;
        if (maxPerPlayer() > 0 && craftedBy(p.getUniqueId()) >= maxPerPlayer()) return 2;
        return 0;
    }

    public void record(Player p, VortexItem v) {
        store.get().set("crafted." + v.id(), crafted(v) + 1);
        store.get().set("players." + p.getUniqueId() + ".total", craftedBy(p.getUniqueId()) + 1);
        store.get().set("players." + p.getUniqueId() + ".name", p.getName());
        store.save();
    }

    public void reset(VortexItem v) {
        store.get().set("crafted." + v.id(), null);
        store.save();
    }

    public void resetAll() {
        store.get().set("crafted", null);
        store.get().set("players", null);
        store.save();
    }

    public void resetPlayer(UUID id) {
        store.get().set("players." + id, null);
        store.save();
    }

    private String denial(int reason) {
        return reason == 1
                ? "§cCette arme légendaire a déjà été craftée §f" + maxPerWeapon() + " §cfois sur le serveur."
                : "§cVous avez déjà craft §f" + maxPerPlayer() + " §carmes légendaires (maximum par joueur).";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void prepare(PrepareItemCraftEvent e) {
        VortexItem v = ItemUtil.get(e.getInventory().getResult());
        if (v == null || v.category() != Category.LEGENDARY_WEAPON) return;
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        int r = check(p, v);
        if (r == 0) return;
        e.getInventory().setResult(null);
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "legend_msg", 2500)) p.sendActionBar(Msg.c(denial(r)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        VortexItem v = ItemUtil.get(e.getCurrentItem());
        if (v == null || v.category() != Category.LEGENDARY_WEAPON) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ClickType ct = e.getClick();
        if (e.isShiftClick() || ct == ClickType.NUMBER_KEY || ct == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cCliquez normalement sur le résultat pour forger une arme légendaire.");
            return;
        }
        int r = check(p, v);
        if (r != 0) {
            e.setCancelled(true);
            Msg.prefixed(p, denial(r));
            return;
        }
        record(p, v);
        int max = maxPerWeapon();
        Bukkit.broadcast(Msg.c("§6✦ §f" + p.getName() + " §7a forgé §6§l" + v.displayName() + " §7(" + crafted(v)
                + (max > 0 ? "/" + max : "") + ")"));
        plugin.discord().announce("Arme légendaire forgée", "**" + p.getName() + "** a forgé **" + v.displayName() + "** !", 0xF1C40F);
    }
}
