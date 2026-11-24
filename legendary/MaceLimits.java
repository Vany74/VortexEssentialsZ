package fr.vortexsmp.vortexessentials.legendary;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Limite des maces vanilla craftables (mace-limits dans config.yml) : max-per-server (3 par défaut) et max-per-player (1).
 * Compteurs dans mace-data.yml (/vortex mace status|reset|resetplayer). La Umbral Scythe n'est pas concernée (craft unique à part).
 */
public final class MaceLimits implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;

    public MaceLimits(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "mace-data.yml");
    }

    public int maxPerServer() { return plugin.getConfig().getInt("mace-limits.max-per-server", 3); }
    public int maxPerPlayer() { return plugin.getConfig().getInt("mace-limits.max-per-player", 1); }
    public int crafted() { return store.get().getInt("crafted"); }
    public int craftedBy(UUID id) { return store.get().getInt("players." + id); }

    public int check(Player p) {
        if (p.hasPermission("vortexessentials.admin.bypass")) return 0;
        if (maxPerServer() > 0 && crafted() >= maxPerServer()) return 1;
        if (maxPerPlayer() > 0 && craftedBy(p.getUniqueId()) >= maxPerPlayer()) return 2;
        return 0;
    }

    public void reset() { store.get().set("crafted", null); store.save(); }
    public void resetPlayer(UUID id) { store.get().set("players." + id, null); store.save(); }
    public void resetAll() { store.get().set("crafted", null); store.get().set("players", null); store.save(); }

    private static boolean vanillaMace(ItemStack s) {
        return s != null && s.getType() == Material.MACE && ItemUtil.get(s) == null;
    }

    private String denial(int r) {
        return r == 1 ? "§cLa limite de maces craftables sur le serveur est atteinte (" + maxPerServer() + ")."
                : "§cVous avez déjà craft §f" + maxPerPlayer() + " §cmace(s) (maximum par joueur).";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void prepare(PrepareItemCraftEvent e) {
        if (!vanillaMace(e.getInventory().getResult()) || !(e.getView().getPlayer() instanceof Player p)) return;
        int r = check(p);
        if (r == 0) return;
        e.getInventory().setResult(null);
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "mace_msg", 2500)) p.sendActionBar(Msg.c(denial(r)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        if (!vanillaMace(e.getCurrentItem()) || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.isShiftClick()) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cCliquez normalement sur le résultat pour fabriquer une mace.");
            return;
        }
        int r = check(p);
        if (r != 0) {
            e.setCancelled(true);
            Msg.prefixed(p, denial(r));
            return;
        }
        store.get().set("crafted", crafted() + 1);
        store.get().set("players." + p.getUniqueId(), craftedBy(p.getUniqueId()) + 1);
        store.save();
    }
}
