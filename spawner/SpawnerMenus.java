package fr.vortexsmp.vortexessentials.spawner;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menu d'un spawner (stockage, vente, améliorations, joueurs autorisés) et boutique des spawners. */
public final class SpawnerMenus implements Listener {
    private static final class Holder implements InventoryHolder {
        final String spawnerKey;
        final List<EntityType> shop = new ArrayList<>();
        Inventory inv;
        Holder(String key) { this.spawnerKey = key; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public SpawnerMenus(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static ItemStack button(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Msg.c(x));
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    private String money(double v) { return plugin.money().format(v); }

    public void open(Player p, SpawnerData d) {
        Holder h = new Holder(d.key);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§e" + SpawnerManager.pretty(d.type) + " Spawner §8×" + d.stack));
        h.inv = inv;
        draw(inv, d);
        p.openInventory(inv);
    }

    private void draw(Inventory inv, SpawnerData d) {
        inv.clear();
        SpawnerManager sm = plugin.spawners();
        ItemStack icon = sm.item(d.type, 1);
        ItemMeta im = icon.getItemMeta();
        im.lore(List.of(Msg.c("§7Pile : §f" + d.stack + "§7/§f" + sm.maxStack()), Msg.c("§7Propriétaire : §f" + d.ownerName),
                Msg.c("§7Par cycle : §f" + d.stack * (1 + d.qty) + " §7mobs")));
        icon.setItemMeta(im);
        inv.setItem(4, icon);
        int i = 9;
        for (Map.Entry<Material, Long> en : d.drops.entrySet()) {
            if (i > 35 || en.getValue() <= 0) continue;
            ItemStack s = new ItemStack(en.getKey(), (int) Math.min(64, Math.max(1, en.getValue())));
            ItemMeta m = s.getItemMeta();
            m.lore(List.of(Msg.c("§7Stocké : §f" + en.getValue())));
            s.setItemMeta(m);
            inv.setItem(i++, s);
        }
        inv.setItem(45, button(Material.HOPPER, "§aRécupérer les drops", "§7Stocké : §f" + d.storedItems() + " §7items"));
        inv.setItem(46, button(Material.EXPERIENCE_BOTTLE, "§aRécupérer l'expérience", "§7Stockée : §f" + d.exp));
        inv.setItem(47, button(Material.EMERALD, "§6Vendre les drops", "§7Au prix de la boutique."));
        inv.setItem(48, button(d.autoSell ? Material.LIME_DYE : Material.GRAY_DYE, "§eVente automatique : " + (d.autoSell ? "§aactivée" : "§cdésactivée"),
                "§7Les drops sont vendus dès qu'ils sont obtenus.", "§eClic §7: changer"));
        inv.setItem(50, upgrade(d, "speed", Material.SUGAR, "Vitesse", d.speed, "Moins d'attente entre deux apparitions."));
        inv.setItem(51, upgrade(d, "range", Material.ENDER_EYE, "Portée", d.range, "Distance d'activation du spawner (+4 blocs par niveau)."));
        inv.setItem(52, upgrade(d, "quantity", Material.CHEST_MINECART, "Quantité", d.qty, "Plus de mobs (donc de drops) par apparition."));
        List<String> names = new ArrayList<>();
        for (UUID u : d.trusted) names.add(String.valueOf(Bukkit.getOfflinePlayer(u).getName()));
        inv.setItem(53, button(Material.PLAYER_HEAD, "§bJoueurs autorisés", "§7" + (names.isEmpty() ? "Aucun" : String.join(", ", names)),
                "§7Regardez le spawner puis :", "§f/us trust <joueur> §7· §f/us untrust <joueur>"));
    }

    private ItemStack upgrade(SpawnerData d, String kind, Material m, String label, int level, String help) {
        List<Integer> prices = plugin.spawners().upgradePrices(kind);
        boolean max = level >= prices.size();
        if (max) return button(m, "§d" + label + " §7(niveau §f" + level + "§7/§f" + prices.size() + "§7)", "§7" + help, "§aNiveau maximal");
        Material mat = materialFor(d);
        int need = mat == null ? 0 : amountFor(kind, level);
        List<String> lore = new ArrayList<>(List.of("§7" + help, "§7Prochain niveau : §a" + money(prices.get(level))));
        if (mat != null && need > 0) {
            long have = available(null, d, mat);
            lore.add("§7Matériaux : §f" + need + "× " + niceName(mat) + " §7(stock du spawner : " + (have >= need ? "§a" : "§c") + have + "§7)");
            lore.add("§8Pris dans le stock du spawner, puis dans votre inventaire.");
        }
        lore.add("§eClic §7: améliorer");
        return button(m, "§d" + label + " §7(niveau §f" + level + "§7/§f" + prices.size() + "§7)", lore.toArray(new String[0]));
    }

    private static String niceName(Material m) {
        String n = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Matériau du mob exigé pour améliorer ce spawner (spawners.upgrade-materials.mobs), ou null. */
    private Material materialFor(SpawnerData d) {
        String name = plugin.getConfig().getString("spawners.upgrade-materials.mobs." + d.type.name());
        return name == null ? null : Material.matchMaterial(name);
    }

    private int amountFor(String kind, int level) {
        List<Integer> l = plugin.getConfig().getIntegerList("spawners.upgrade-materials.amounts." + kind);
        return level < l.size() ? l.get(level) : 0;
    }

    /** Matériaux disponibles : stock du spawner + (si p != null) inventaire du joueur. */
    private long available(Player p, SpawnerData d, Material m) {
        long have = d.drops.getOrDefault(m, 0L);
        if (p != null) for (ItemStack s : p.getInventory().getStorageContents()) if (s != null && s.getType() == m && !s.hasItemMeta()) have += s.getAmount();
        return have;
    }

    private void take(Player p, SpawnerData d, Material m, int amount) {
        long fromStock = Math.min(amount, d.drops.getOrDefault(m, 0L));
        if (fromStock > 0) {
            long left = d.drops.get(m) - fromStock;
            if (left <= 0) d.drops.remove(m); else d.drops.put(m, left);
        }
        int rest = (int) (amount - fromStock);
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && rest > 0; i++) {
            ItemStack s = inv[i];
            if (s == null || s.getType() != m || s.hasItemMeta()) continue;
            int t = Math.min(rest, s.getAmount());
            if (t >= s.getAmount()) inv[i] = null; else s.setAmount(s.getAmount() - t);
            rest -= t;
        }
        p.getInventory().setStorageContents(inv);
    }

    public void shop(Player p) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§eBoutique de spawners"));
        h.inv = inv;
        var prices = plugin.getConfig().getConfigurationSection("spawners.shop.prices");
        int i = 0;
        if (prices != null) {
            for (String k : prices.getKeys(false)) {
                if (i >= 54) break;
                try {
                    EntityType t = EntityType.valueOf(k.toUpperCase());
                    Material egg = Material.matchMaterial(t.name() + "_SPAWN_EGG");
                    h.shop.add(t);
                    inv.setItem(i++, button(egg == null ? Material.SPAWNER : egg, "§e" + SpawnerManager.pretty(t) + " Spawner",
                            "§7Prix : §a" + money(prices.getDouble(k)), "§eClic gauche §7: acheter 1", "§eClic droit §7: acheter 8"));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (h.spawnerKey == null) { shopClick(p, h, slot, e.getClick()); return; }

        SpawnerData d = plugin.spawners().all().get(h.spawnerKey);
        if (d == null) { p.closeInventory(); return; }
        if (!d.allowed(p.getUniqueId()) && !p.hasPermission("ultimatespawners.admin")) return;
        SpawnerManager sm = plugin.spawners();
        switch (slot) {
            case 45 -> {
                for (Map.Entry<Material, Long> en : new ArrayList<>(d.drops.entrySet())) sm.giveItems(p, en.getKey(), en.getValue());
                d.drops.clear();
                sm.audit(p.getName() + " récupère les drops de " + d.key);
            }
            case 46 -> {
                if (d.exp > 0) { p.giveExp((int) Math.min(Integer.MAX_VALUE, d.exp)); d.exp = 0; }
            }
            case 47 -> {
                double t = sm.sellAll(d, p);
                Msg.prefixed(p, t > 0 ? "§aVendu pour §f" + money(t) : "§7Rien de vendable.");
            }
            case 48 -> d.autoSell = !d.autoSell;
            case 50 -> upgrade(p, d, "speed");
            case 51 -> upgrade(p, d, "range");
            case 52 -> upgrade(p, d, "quantity");
            default -> { return; }
        }
        sm.markDirty();
        sm.refreshHolo(d);
        draw(h.inv, d);
    }

    private void upgrade(Player p, SpawnerData d, String kind) {
        List<Integer> prices = plugin.spawners().upgradePrices(kind);
        int level = kind.equals("speed") ? d.speed : kind.equals("range") ? d.range : d.qty;
        if (level >= prices.size()) { Msg.prefixed(p, "§7Niveau maximal."); return; }
        double price = prices.get(level);
        Material mat = materialFor(d);
        int need = mat == null ? 0 : amountFor(kind, level);
        if (need > 0 && available(p, d, mat) < need) {
            Msg.prefixed(p, "§cMatériaux insuffisants : il faut §f" + need + "× " + niceName(mat) + " §c(stock du spawner + inventaire : §f" + available(p, d, mat) + "§c).");
            return;
        }
        if (!plugin.money().available() || !plugin.money().withdraw(p, price)) { Msg.prefixed(p, "§cIl vous faut §f" + money(price) + "§c."); return; }
        if (need > 0) take(p, d, mat, need);
        if (kind.equals("speed")) d.speed++; else if (kind.equals("range")) d.range++; else d.qty++;
        plugin.spawners().audit(p.getName() + " améliore " + kind + " de " + d.key + " (niveau " + (level + 1) + ", " + price + ")");
        Msg.prefixed(p, "§aAmélioration achetée : §f" + kind + " niveau " + (level + 1));
    }

    private void shopClick(Player p, Holder h, int slot, ClickType click) {
        if (slot < 0 || slot >= h.shop.size()) return;
        if (!p.hasPermission("ultimatespawners.shop")) { Msg.prefixed(p, "§cPermission refusée."); return; }
        EntityType t = h.shop.get(slot);
        double unit = plugin.getConfig().getDouble("spawners.shop.prices." + t.name(), -1);
        if (unit < 0) return;
        int qty = click == ClickType.RIGHT ? 8 : 1;
        double total = unit * qty;
        if (!plugin.money().available() || !plugin.money().withdraw(p, total)) { Msg.prefixed(p, "§cIl vous faut §f" + money(total) + "§c."); return; }
        p.getInventory().addItem(plugin.spawners().item(t, qty)).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        plugin.spawners().audit(p.getName() + " achète " + qty + " " + t.name() + " (" + total + ")");
        Msg.prefixed(p, "§aAchat : §f" + qty + "× " + SpawnerManager.pretty(t) + " §7pour §f" + money(total));
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
