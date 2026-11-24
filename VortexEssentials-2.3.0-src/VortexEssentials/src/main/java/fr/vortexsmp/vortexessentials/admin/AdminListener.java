package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.Map;

/**
 * Applique les règles d'administration : items désactivés, crafts désactivés (établi, forge, four),
 * potions non brassables, limites de quantité. Les items Vortex et les items personnalisés sont toujours exemptés,
 * ainsi que les joueurs avec vortexessentials.admin.bypass.
 */
public final class AdminListener implements Listener {
    private static final String BYPASS = "vortexessentials.admin.bypass";

    private final VortexEssentials plugin;

    public AdminListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private AdminManager adm() { return plugin.admin(); }

    /** Items Vortex et items personnalisés : jamais touchés. */
    private static boolean exempt(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        return ItemUtil.idOf(s) != null
                || s.getItemMeta().getPersistentDataContainer().has(Keys.CUSTOM_ITEM, PersistentDataType.STRING);
    }

    private boolean blocked(Player p, ItemStack s) {
        return s != null && !s.getType().isAir() && adm().itemDisabled(s.getType())
                && !exempt(s) && !p.hasPermission(BYPASS);
    }

    private void deny(Player p) {
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "admin_msg", 2000)) {
            p.sendActionBar(Msg.c("§cCet objet est désactivé sur ce serveur."));
        }
    }

    // ═════════════ Items désactivés ═════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (blocked(e.getPlayer(), e.getItem())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getItemInHand())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        if (blocked(e.getPlayer(), e.getItem())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.hasPermission(BYPASS)) return;
        ItemStack s = e.getItem().getItemStack();
        if (blocked(p, s)) {
            e.setCancelled(true);
            return;
        }
        int max = adm().limitOf(s.getType());
        if (max > 0 && !exempt(s) && count(p, s.getType()) + s.getAmount() > max) {
            e.setCancelled(true);
            if (plugin.cooldowns().tryUse(p.getUniqueId(), "limit_msg", 2000)) {
                p.sendActionBar(Msg.c("§cLimite atteinte : §f" + max + " §cmax pour cet objet."));
            }
        }
    }

    // ═════════════ Crafts désactivés ═════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void craft(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null) return;
        CraftingInventory inv = e.getInventory();
        ItemStack res = inv.getResult();
        if (res == null || res.getType().isAir() || exempt(res)) return;
        if (e.getRecipe() instanceof Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return; // nos recettes
        if (e.getView().getPlayer().hasPermission(BYPASS)) return;
        if (adm().craftDisabled(res.getType()) || adm().itemDisabled(res.getType())) inv.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void smithing(PrepareSmithingEvent e) {
        ItemStack res = e.getResult();
        if (res == null || res.getType().isAir() || exempt(res)) return;
        if (e.getView().getPlayer().hasPermission(BYPASS)) return;
        if (adm().craftDisabled(res.getType()) || adm().itemDisabled(res.getType())) e.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void smelt(FurnaceSmeltEvent e) {
        Material r = e.getResult().getType();
        if (adm().craftDisabled(r) || adm().itemDisabled(r)) e.setCancelled(true);
    }

    // ═════════════ Potions non brassables ═════════════

    /** Clé d'effet d'un type de potion (strong_/long_ retirés, noms d'items traduits). */
    private static String effectId(PotionType t) {
        String k = t.getKey().getKey();
        if (k.startsWith("strong_")) k = k.substring(7);
        else if (k.startsWith("long_")) k = k.substring(5);
        return switch (k) {
            case "healing" -> "instant_health";
            case "harming" -> "instant_damage";
            case "swiftness" -> "speed";
            case "leaping" -> "jump_boost";
            case "turtle_master" -> "slowness";
            default -> k;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void brew(BrewEvent e) {
        if (adm().potions().isEmpty()) return;
        for (ItemStack r : e.getResults()) {
            if (r != null && r.hasItemMeta() && r.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null
                    && adm().potionDisabled(effectId(pm.getBasePotionType()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ═════════════ Nettoyage et limites de quantité ═════════════

    private int count(Player p, Material m) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getStorageContents()) if (s != null && s.getType() == m && !exempt(s)) n += s.getAmount();
        for (ItemStack s : inv.getArmorContents()) if (s != null && s.getType() == m && !exempt(s)) n += s.getAmount();
        for (ItemStack s : inv.getExtraContents()) if (s != null && s.getType() == m && !exempt(s)) n += s.getAmount();
        return n;
    }

    private void sweep(Player p) {
        if (!p.isOnline() || p.hasPermission(BYPASS)) return;
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();

        // Items désactivés : retirés
        boolean removed = false;
        for (int i = 0; i < c.length; i++) {
            if (c[i] != null && adm().itemDisabled(c[i].getType()) && !exempt(c[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (removed) p.sendActionBar(Msg.c("§cDes objets désactivés ont été retirés de votre inventaire."));

        // Limites : l'excédent tombe au sol
        for (Map.Entry<Material, Integer> en : adm().limits().entrySet()) {
            int excess = count(p, en.getKey()) - en.getValue();
            if (excess <= 0) continue;
            ItemStack[] cur = inv.getContents();
            int dropped = 0;
            for (int i = cur.length - 1; i >= 0 && excess > 0; i--) {
                ItemStack s = cur[i];
                if (s == null || s.getType() != en.getKey() || exempt(s)) continue;
                int take = Math.min(excess, s.getAmount());
                s.setAmount(s.getAmount() - take);
                inv.setItem(i, s.getAmount() <= 0 ? null : s);
                excess -= take;
                dropped += take;
            }
            if (dropped > 0) {
                p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(en.getKey(), dropped));
                p.sendActionBar(Msg.c("§cLimite de §f" + en.getValue() + " §c" + en.getKey().name().toLowerCase() + " : excédent jeté."));
            }
        }
    }

    private void sweepNextTick(Player p) {
        if (adm().items().isEmpty() && adm().limits().isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> sweep(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) sweepNextTick(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) sweepNextTick(p);
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        sweepNextTick(e.getPlayer());
    }
}
