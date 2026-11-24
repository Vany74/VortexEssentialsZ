package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.boost.BoostManager;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.menu.MenuHolder;
import fr.vortexsmp.vortexessentials.menu.Ui;
import fr.vortexsmp.vortexessentials.mythic.MythicRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menus de la boutique : accueil, boutique du serveur (catégories et items, prix fixes, stock illimité),
 * éditeur de prix pour les admins, boutique des joueurs (catégories automatiques) et mes annonces.
 * Clic gauche = acheter 1, Shift + gauche = acheter un stack, clic droit = vendre 1, Shift + droit = tout vendre.
 */
public final class ShopMenus implements Listener {
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final double[] STEPS = {0.1, 1, 10, 100, 1000};
    private static final int PAGE = 45;
    private static final int BOX = 45; // zone de dépôt du menu de vente

    private final VortexEssentials plugin;

    public ShopMenus(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private ShopManager shop() { return plugin.shop(); }
    private PlayerShop pshop() { return plugin.playerShop(); }
    private Money money() { return plugin.money(); }

    private static String t(String s) { return "§8» §a§lShop §8| §f" + s; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private String fmt(double v) { return money().format(v); }
    private static boolean admin(Player p) { return p.hasPermission("vortexessentials.shop.admin"); }
    private void tick(Player p, boolean good) { p.playSound(p.getLocation(), good ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO, 0.6f, good ? 1.4f : 1f); }

    private boolean needMoney(Player p) {
        if (money().available()) return true;
        Msg.prefixed(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie.");
        return false;
    }

    // ═════════════ Ouverture ═════════════

    private void open(Player p, MenuHolder h, int size, String title) {
        Inventory inv = Bukkit.createInventory(h, size, Msg.c(title));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    public void openHub(Player p) { open(p, new MenuHolder(MenuHolder.Type.SHOP_HUB), 36, t("Boutique")); }
    public void openCats(Player p) { open(p, new MenuHolder(MenuHolder.Type.SHOP_CATS), 45, t("Boutique du serveur")); }

    public void openItems(Player p, String catId, boolean edit) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.SHOP_ITEMS);
        h.name(catId);
        h.kind(edit ? "edit" : "");
        ShopManager.Category c = shop().category(catId);
        open(p, h, 54, t(c == null ? catId : c.name));
    }

    public void openEdit(Player p, String catId, String key, int step) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.SHOP_EDIT);
        h.name(catId);
        h.query(key);
        h.page(step);
        open(p, h, 36, t("Éditeur de prix"));
    }

    public void openPlayerCats(Player p) { open(p, new MenuHolder(MenuHolder.Type.PSHOP_CATS), 45, t("Boutique des joueurs")); }

    public void openPlayerList(Player p, PlayerCategory c) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.PSHOP_LIST);
        h.kind(c.name());
        open(p, h, 54, t(c.label()));
    }

    /** Menu de vente façon DonutSMP : on dépose des items, ils sont vendus à la fermeture. */
    public void openSellBox(Player p) { open(p, new MenuHolder(MenuHolder.Type.SELL_BOX), 54, t("Menu de vente")); }

    public void openMine(Player p) { open(p, new MenuHolder(MenuHolder.Type.PSHOP_MINE), 54, t("Mes annonces")); }

    // ═════════════ Dessin ═════════════

    private void draw(Player p, MenuHolder h) {
        h.getInventory().clear();
        switch (h.type()) {
            case SHOP_HUB -> drawHub(p, h.getInventory());
            case SHOP_CATS -> drawCats(p, h);
            case SHOP_ITEMS -> drawItems(p, h);
            case SHOP_EDIT -> drawEdit(h);
            case PSHOP_CATS -> drawPlayerCats(h);
            case PSHOP_LIST -> drawPlayerList(p, h);
            case PSHOP_MINE -> drawMine(p, h);
            case SELL_BOX -> drawSellBox(p, h);
            default -> { }
        }
    }

    private ItemStack balanceItem(Player p) {
        BoostManager bm = plugin.boosts();
        List<String> l = new ArrayList<>();
        l.add(Ui.bar());
        l.add(money().available() ? "§7Solde : §f" + fmt(money().balance(p)) : "§cAucune économie détectée");
        l.add("§7Prix de vente : §fx" + String.format(Locale.US, "%.2f", bm.sellMultiplier(p)) + " §8(bonus de grade)");
        l.add("§7Prix d'achat : §fx" + String.format(Locale.US, "%.2f", bm.buyMultiplier(p)) + " §8(réduction de grade)");
        for (BoostManager.Event e : bm.events()) {
            if (e.stat().equals("sell") || e.stat().equals("buy")) {
                l.add("§a⚡ Événement " + e.stat() + " x" + e.multiplier() + " §7(" + Math.max(0, (e.until() - System.currentTimeMillis()) / 60_000L) + " min)");
            }
        }
        return Ui.button(Material.GOLD_INGOT, "§6§lVotre portefeuille", l.toArray(new String[0]));
    }

    private void drawHub(Player p, Inventory inv) {
        Ui.frame(inv, 0, inv.getSize(), Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(4, balanceItem(p));
        inv.setItem(11, Ui.button(Material.EMERALD, "§a§lBoutique du serveur", Ui.bar(),
                "§7" + shop().categories().stream().filter(c -> c.enabled).count() + " catégories", "§7Stock illimité, prix fixes", "", "§eClic §7pour ouvrir"));
        inv.setItem(13, Ui.button(Material.CHEST, "§e§lBoutique des joueurs", Ui.bar(),
                "§7Achetez et vendez entre joueurs", "§7Commande : §f/shop sell <prix>", "", "§eClic §7pour ouvrir"));
        inv.setItem(15, Ui.button(Material.NAME_TAG, "§b§lMes annonces", Ui.bar(),
                "§7" + pshop().count(p.getUniqueId()) + " annonce(s) en cours", "", "§eClic §7pour gérer"));
        inv.setItem(20, Ui.button(Material.BARREL, "§c§lMenu de vente", Ui.bar(),
                "§7Déposez les items à vendre :", "§7ils sont vendus à la fermeture", "§8Commande : /sell", "", "§eClic §7pour ouvrir"));
        inv.setItem(22, Ui.button(Material.HOPPER, "§c§lVendre tout", Ui.bar(),
                "§7Vend tout ce qui est vendable dans", "§7votre inventaire (hors barre rapide)", "", "§eClic §7pour vendre"));
        inv.setItem(31, Ui.button(Material.ARROW, "§7« Accueil"));
    }

    private void drawCats(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        Ui.frame(inv, 0, inv.getSize(), Material.GREEN_STAINED_GLASS_PANE);
        List<String> ids = new ArrayList<>();
        for (ShopManager.Category c : shop().categories()) if (c.enabled || admin(p)) ids.add(c.id);
        h.entries(ids);
        for (int i = 0; i < ids.size() && i < GRID.length; i++) {
            ShopManager.Category c = shop().category(ids.get(i));
            long n = c.items.values().stream().filter(e -> e.enabled).count();
            inv.setItem(GRID[i], Ui.button(c.icon, (c.enabled ? "§a§l" : "§c§l") + c.name, Ui.bar(),
                    "§7" + n + " item(s)", c.enabled ? "" : "§c✖ Catégorie désactivée", "§eClic §7pour ouvrir"));
        }
        inv.setItem(40, Ui.button(Material.ARROW, "§7« Boutique"));
    }

    private ItemStack withLines(ItemStack base, String... lines) {
        ItemStack s = base.clone();
        s.setAmount(1);
        ItemMeta m = s.getItemMeta();
        List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String x : lines) l.add(Msg.plain(x));
        m.lore(l);
        s.setItemMeta(m);
        return s;
    }

    private void drawItems(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        ShopManager.Category c = shop().category(h.name());
        boolean edit = h.kind().equals("edit") && admin(p);
        if (c == null) { Ui.frame(inv, 0, inv.getSize(), Material.RED_STAINED_GLASS_PANE); return; }

        List<String> keys = new ArrayList<>();
        for (ShopManager.Entry e : c.items.values()) if (e.enabled || edit) keys.add(e.key);
        h.entries(keys);
        int max = Math.max(0, (keys.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));

        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= keys.size()) break;
            ShopManager.Entry e = c.items.get(keys.get(idx));
            ItemStack base = shop().stack(e.key, 1);
            if (base == null) continue;
            double buy = shop().buyUnit(p, e), sell = shop().sellUnit(p, e);
            List<String> lines = new ArrayList<>();
            lines.add("");
            lines.add(Ui.bar());
            lines.add(buy < 0 ? "§7Achat : §8non disponible" : "§7Achat : §a" + fmt(buy) + discountTag(e.buy, buy));
            lines.add(sell < 0 ? "§7Vente : §8non rachetable" : "§7Vente : §6" + fmt(sell) + bonusTag(e.sell, sell));
            lines.add("");
            if (edit) {
                lines.add(e.enabled ? "§a✔ Actif" : "§c✖ Désactivé");
                lines.add("§eClic §7: modifier");
            } else {
                if (buy >= 0) { lines.add("§eClic gauche §7: acheter 1"); lines.add("§eShift + gauche §7: acheter un stack"); }
                if (sell >= 0) { lines.add("§eClic droit §7: vendre 1"); lines.add("§eShift + droit §7: tout vendre"); }
            }
            inv.setItem(n, withLines(base, lines.toArray(new String[0])));
        }

        Ui.frame(inv, 45, 54, Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(45, Ui.button(Material.ARROW, "§e◀ Page précédente"));
        inv.setItem(49, balanceItem(p));
        inv.setItem(51, Ui.button(Material.ARROW, "§aPage suivante ▶"));
        inv.setItem(53, Ui.button(Material.COMPASS, "§7« Catégories"));
        if (admin(p)) {
            inv.setItem(47, Ui.button(edit ? Material.LIME_DYE : Material.GRAY_DYE, "§e§lMode édition : " + (edit ? "§aON" : "§cOFF"),
                    "§7Modifier prix, activer/désactiver, supprimer", "", "§eClic §7pour basculer"));
            if (edit) {
                inv.setItem(48, Ui.button(Material.HOPPER, "§a§lAjouter un item", "§7Prenez un item avec le curseur,", "§7puis cliquez ici.",
                        "§8Prix de départ : achat 10 / vente 3"));
            }
        }
    }

    private String discountTag(double base, double actual) {
        if (base <= 0 || Math.abs(base - actual) < 0.005) return "";
        return " §8(-" + Math.round((1 - actual / base) * 100) + "%)";
    }

    private String bonusTag(double base, double actual) {
        if (base <= 0 || Math.abs(base - actual) < 0.005) return "";
        return " §8(+" + Math.round((actual / base - 1) * 100) + "%)";
    }

    private void drawEdit(MenuHolder h) {
        Inventory inv = h.getInventory();
        Ui.frame(inv, 0, inv.getSize(), Material.YELLOW_STAINED_GLASS_PANE);
        ShopManager.Entry e = shop().entry(h.name(), h.query());
        if (e == null) { inv.setItem(13, Ui.button(Material.BARRIER, "§cEntrée introuvable")); return; }
        double step = STEPS[h.page() % STEPS.length];
        ItemStack base = shop().stack(e.key, 1);
        if (base != null) inv.setItem(4, withLines(base, "", "§8Clé : " + e.key));
        inv.setItem(10, Ui.button(Material.EMERALD, "§a§lPrix d'achat : " + (e.buy < 0 ? "§8non disponible" : "§f" + e.buy), Ui.bar(),
                "§eClic gauche §7: +" + step, "§eClic droit §7: -" + step, "§8Sous 0 : l'item n'est plus achetable"));
        inv.setItem(12, Ui.button(Material.GOLD_INGOT, "§6§lPrix de vente : " + (e.sell < 0 ? "§8non rachetable" : "§f" + e.sell), Ui.bar(),
                "§eClic gauche §7: +" + step, "§eClic droit §7: -" + step, "§8Sous 0 : l'item n'est plus rachetable"));
        inv.setItem(14, Ui.button(Material.REPEATER, "§b§lPas : §f" + step, "§eClic §7pour changer (0.1 / 1 / 10 / 100 / 1000)"));
        inv.setItem(16, Ui.button(e.enabled ? Material.LIME_DYE : Material.GRAY_DYE, e.enabled ? "§a§lActif" : "§c§lDésactivé",
                "§eClic §7pour basculer"));
        inv.setItem(22, Ui.button(Material.TNT, "§c§lSupprimer de la boutique", "§eShift + clic §7pour confirmer"));
        inv.setItem(31, Ui.button(Material.ARROW, "§7« Items"));
    }

    private void drawPlayerCats(MenuHolder h) {
        Inventory inv = h.getInventory();
        Ui.frame(inv, 0, inv.getSize(), Material.YELLOW_STAINED_GLASS_PANE);
        PlayerCategory[] cs = PlayerCategory.values();
        for (int i = 0; i < cs.length && i < GRID.length; i++) {
            inv.setItem(GRID[i], Ui.button(cs[i].icon(), "§e§l" + cs[i].label(), Ui.bar(),
                    "§7" + pshop().countIn(cs[i]) + " annonce(s)", "", "§eClic §7pour ouvrir"));
        }
        inv.setItem(40, Ui.button(Material.ARROW, "§7« Boutique"));
    }

    private void listingNav(Inventory inv, MenuHolder h, int maxPage, Material backIcon, String backName) {
        Ui.frame(inv, 45, 54, Material.YELLOW_STAINED_GLASS_PANE);
        inv.setItem(45, Ui.button(Material.ARROW, "§e◀ Page précédente"));
        inv.setItem(49, Ui.button(Material.NETHER_STAR, "§d✦ Page " + (h.page() + 1) + "/" + (maxPage + 1)));
        inv.setItem(51, Ui.button(Material.ARROW, "§aPage suivante ▶"));
        inv.setItem(53, Ui.button(backIcon, backName));
    }

    private ItemStack listingIcon(Player viewer, PlayerShop.Listing l, boolean mine) {
        double tax = plugin.getConfig().getDouble("shop.player-shop.tax-percent", 5);
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(Ui.bar());
        lines.add("§7Vendeur : §f" + l.sellerName());
        lines.add("§7Quantité : §f" + l.item().getAmount());
        lines.add("§7Prix : §a" + fmt(l.price()));
        lines.add("");
        if (mine) {
            lines.add("§eClic §7: retirer l'annonce");
        } else {
            lines.add("§eClic gauche §7: acheter");
            if (l.seller().equals(viewer.getUniqueId()) || admin(viewer)) lines.add("§eClic droit §7: retirer l'annonce");
            lines.add("§8Taxe vendeur : " + tax + "%");
        }
        ItemStack s = l.item().clone();
        ItemMeta m = s.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String x : lines) lore.add(Msg.plain(x));
        m.lore(lore);
        s.setItemMeta(m);
        return s;
    }

    private void drawPlayerList(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        List<PlayerShop.Listing> ls = pshop().in(PlayerCategory.valueOf(h.kind()));
        List<String> ids = new ArrayList<>();
        for (PlayerShop.Listing l : ls) ids.add(String.valueOf(l.id()));
        h.entries(ids);
        int max = Math.max(0, (ids.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= ls.size()) break;
            inv.setItem(n, listingIcon(p, ls.get(idx), false));
        }
        listingNav(inv, h, max, Material.COMPASS, "§7« Catégories");
    }

    private void drawMine(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        List<PlayerShop.Listing> ls = pshop().of(p.getUniqueId());
        List<String> ids = new ArrayList<>();
        for (PlayerShop.Listing l : ls) ids.add(String.valueOf(l.id()));
        h.entries(ids);
        int max = Math.max(0, (ids.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= ls.size()) break;
            inv.setItem(n, listingIcon(p, ls.get(idx), true));
        }
        listingNav(inv, h, max, Material.COMPASS, "§7« Boutique");
    }

    // ═════════════ Clics ═════════════

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h) || !h.type().shop()) return;
        if (!(e.getWhoClicked() instanceof Player p)) { e.setCancelled(true); return; }
        if (h.type() == MenuHolder.Type.SELL_BOX) { sellBoxClick(p, h, e); return; }
        if (e.getClickedInventory() != top) {
            // Inventaire du joueur : on laisse prendre un item au curseur (ajout d'items), sans shift-clic ni double-clic
            if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        switch (h.type()) {
            case SHOP_HUB -> hubClick(p, slot);
            case SHOP_CATS -> {
                if (slot == 40) { openHub(p); return; }
                for (int i = 0; i < GRID.length; i++) {
                    if (GRID[i] == slot && i < h.entries().size()) { openItems(p, h.entries().get(i), false); return; }
                }
            }
            case SHOP_ITEMS -> itemsClick(p, h, e);
            case SHOP_EDIT -> editClick(p, h, e);
            case PSHOP_CATS -> {
                if (slot == 40) { openHub(p); return; }
                PlayerCategory[] cs = PlayerCategory.values();
                for (int i = 0; i < cs.length && i < GRID.length; i++) if (GRID[i] == slot) { openPlayerList(p, cs[i]); return; }
            }
            case PSHOP_LIST, PSHOP_MINE -> listingClick(p, h, e);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h) || !h.type().shop()) return;
        int from = h.type() == MenuHolder.Type.SELL_BOX ? BOX : 0; // zone de dépôt du menu de vente : slots 0..44
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && raw >= from) { e.setCancelled(true); return; }
        }
        if (h.type() == MenuHolder.Type.SELL_BOX && e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshBox(p, h));
        }
    }

    private void hubClick(Player p, int slot) {
        switch (slot) {
            case 11 -> openCats(p);
            case 13 -> openPlayerCats(p);
            case 15 -> openMine(p);
            case 20 -> { if (needMoney(p)) openSellBox(p); }
            case 22 -> { sellAll(p); openHub(p); }
            case 31 -> plugin.menu().openHub(p);
            default -> { }
        }
    }

    private void itemsClick(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        boolean edit = h.kind().equals("edit") && admin(p);
        if (slot >= PAGE) {
            switch (slot) {
                case 45 -> { h.page(h.page() - 1); draw(p, h); }
                case 51 -> { h.page(h.page() + 1); draw(p, h); }
                case 53 -> openCats(p);
                case 47 -> { if (admin(p)) { h.kind(edit ? "" : "edit"); h.page(0); draw(p, h); } }
                case 48 -> { if (edit) addFromCursor(p, h, e.getCursor()); }
                default -> { }
            }
            return;
        }
        int idx = h.page() * PAGE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        ShopManager.Entry en = shop().entry(h.name(), h.entries().get(idx));
        if (en == null) return;

        if (edit) { openEdit(p, h.name(), en.key, 1); return; }
        if (!needMoney(p)) return;
        ClickType c = e.getClick();
        if (c == ClickType.LEFT) buy(p, en, 1);
        else if (c == ClickType.SHIFT_LEFT) { ItemStack s = shop().stack(en.key, 64); buy(p, en, s == null ? 1 : s.getMaxStackSize()); }
        else if (c == ClickType.RIGHT) sell(p, en, 1);
        else if (c == ClickType.SHIFT_RIGHT) sell(p, en, Integer.MAX_VALUE);
        draw(p, h);
    }

    private void addFromCursor(Player p, MenuHolder h, ItemStack cursor) {
        if (cursor == null || cursor.getType().isAir()) cursor = p.getInventory().getItemInMainHand(); // à défaut : l'item en main
        if (cursor == null || cursor.getType().isAir()) {
            Msg.prefixed(p, "§7Prenez un item avec le curseur (ou tenez-le en main), puis cliquez sur le bouton d'ajout.");
            return;
        }
        String key = shop().keyOf(cursor);
        ShopManager.Category c = shop().category(h.name());
        if (c != null && c.items.containsKey(key)) { Msg.prefixed(p, "§cCet item est déjà dans la catégorie."); return; }
        String err = shop().addEntry(h.name(), key, 10, 3);
        if (err != null) { Msg.prefixed(p, "§c" + err); return; }
        openEdit(p, h.name(), key, 1);
    }

    private void editClick(Player p, MenuHolder h, InventoryClickEvent e) {
        if (!admin(p)) return;
        ShopManager.Entry en = shop().entry(h.name(), h.query());
        int slot = e.getRawSlot();
        if (slot == 31) { openItems(p, h.name(), true); return; }
        if (en == null) return;
        double step = STEPS[h.page() % STEPS.length];
        double dir = e.isLeftClick() ? step : e.isRightClick() ? -step : 0;

        switch (slot) {
            case 10 -> { if (dir != 0) en.buy = adjust(en.buy, dir, step); }
            case 12 -> { if (dir != 0) en.sell = adjust(en.sell, dir, step); }
            case 14 -> h.page((h.page() + 1) % STEPS.length);
            case 16 -> en.enabled = !en.enabled;
            case 22 -> {
                if (e.isShiftClick()) {
                    shop().removeEntry(h.name(), en.key);
                    Msg.prefixed(p, "§7Item retiré de la boutique.");
                    openItems(p, h.name(), true);
                }
                return;
            }
            default -> { return; }
        }
        shop().save();
        draw(p, h);
    }

    /** Applique un pas à un prix ; en dessous de zéro l'achat/la vente est désactivé(e) (-1). */
    private static double adjust(double cur, double delta, double step) {
        if (cur < 0) return delta > 0 ? round2(step) : -1;
        double next = round2(cur + delta);
        return next <= 0 ? -1 : next;
    }

    private void listingClick(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot >= PAGE) {
            switch (slot) {
                case 45 -> { h.page(h.page() - 1); draw(p, h); }
                case 51 -> { h.page(h.page() + 1); draw(p, h); }
                case 53 -> { if (h.type() == MenuHolder.Type.PSHOP_MINE) openHub(p); else openPlayerCats(p); }
                default -> { }
            }
            return;
        }
        int idx = h.page() * PAGE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        long id = Long.parseLong(h.entries().get(idx));
        PlayerShop.Listing l = pshop().get(id);
        if (l == null) { draw(p, h); return; }

        boolean mine = h.type() == MenuHolder.Type.PSHOP_MINE;
        if (mine || (e.isRightClick() && (l.seller().equals(p.getUniqueId()) || admin(p)))) {
            cancelListing(p, l);
        } else if (e.isLeftClick()) {
            if (needMoney(p)) buyListing(p, l);
        }
        draw(p, h);
    }

    // ═════════════ Menu de vente (dépôt) ═════════════

    private boolean autoSell() { return plugin.getConfig().getBoolean("shop.sell-menu.auto-sell-on-close", true); }

    private void drawSellBox(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        Ui.frame(inv, BOX, 54, Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(45, Ui.button(Material.HOPPER, "§e§lTout reprendre", Ui.bar(), "§7Renvoie tous les items déposés", "§7dans votre inventaire"));
        inv.setItem(53, Ui.button(Material.EMERALD, "§a§lVendre maintenant", Ui.bar(), "§7Vend les items vendables déposés", "§7(le menu reste ouvert)"));
        refreshBox(p, h);
    }

    /** Met à jour l'estimation du total (slot 49). */
    private void refreshBox(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        Map<String, ShopManager.Entry> idx = shop().sellIndex();
        double total = 0;
        int sellable = 0, other = 0;
        for (int i = 0; i < BOX; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            ShopManager.Entry en = idx.get(shop().keyOf(s));
            double unit = en != null && shop().matches(en, s) ? shop().sellUnit(p, en) : -1;
            if (unit >= 0) { total += unit * s.getAmount(); sellable += s.getAmount(); } else other += s.getAmount();
        }
        inv.setItem(49, Ui.button(Material.GOLD_INGOT, "§6§lTotal estimé : §f" + fmt(round2(total)), Ui.bar(),
                "§7Items vendables : §f" + sellable,
                "§7Items non vendables : §f" + other + " §8(rendus à la fermeture)",
                "§7Prix de vente : §fx" + String.format(Locale.US, "%.2f", plugin.boosts().sellMultiplier(p)) + " §8(bonus de grade)", "",
                autoSell() ? "§aFermer le menu vend les items déposés" : "§eCliquez sur « Vendre maintenant »"));
    }

    private void sellBoxClick(Player p, MenuHolder h, InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        if (e.getClick() == ClickType.DOUBLE_CLICK) { e.setCancelled(true); return; }
        if (inTop && raw >= BOX) {
            e.setCancelled(true);
            if (raw == 45) { returnBox(p, top); refreshBox(p, h); }
            else if (raw == 53) { sellBox(p, top); refreshBox(p, h); }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshBox(p, h)); // dépôt / retrait libre dans les slots 0..44
    }

    /** Vend les items vendables déposés. Les autres restent dans le menu. Renvoie le total gagné. */
    private double sellBox(Player p, Inventory inv) {
        if (!needMoney(p)) return 0;
        Map<String, ShopManager.Entry> idx = shop().sellIndex();
        List<Integer> slots = new ArrayList<>();
        Map<String, Integer> sold = new LinkedHashMap<>();
        double total = 0;
        for (int i = 0; i < BOX; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            ShopManager.Entry en = idx.get(shop().keyOf(s));
            if (en == null || !shop().matches(en, s)) continue;
            double unit = shop().sellUnit(p, en);
            if (unit < 0) continue;
            total += unit * s.getAmount();
            sold.merge(shop().displayName(en.key), s.getAmount(), Integer::sum);
            slots.add(i);
        }
        total = round2(total);
        if (total <= 0) return 0;
        if (!money().deposit(p, total)) { Msg.prefixed(p, "§cL'économie a refusé le paiement."); return 0; }
        for (int i : slots) inv.setItem(i, null); // retrait seulement après paiement
        StringBuilder sb = new StringBuilder();
        sold.forEach((k, v) -> { if (sb.length() < 180) sb.append(v).append("× ").append(k).append(", "); });
        Msg.prefixed(p, "§aVendu pour §f" + fmt(total) + " §7: " + sb.toString().replaceAll(", $", ""));
        tick(p, true);
        return total;
    }

    /** Rend les items restant dans la zone de dépôt. Renvoie le nombre d'items rendus. */
    private int returnBox(Player p, Inventory inv) {
        int n = 0;
        for (int i = 0; i < BOX; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            inv.setItem(i, null);
            n += s.getAmount();
            giveBack(p, s);
        }
        return n;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h) || h.type() != MenuHolder.Type.SELL_BOX) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        if (autoSell() && money().available()) sellBox(p, inv); // façon DonutSMP : fermer = vendre
        int back = returnBox(p, inv);
        if (back > 0) Msg.prefixed(p, "§7" + back + " item(s) non vendables rendus.");
    }

    // ═════════════ Opérations : boutique du serveur ═════════════

    private void give(Player p, ItemStack template, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack s = template.clone();
            s.setAmount(Math.min(left, s.getMaxStackSize()));
            left -= s.getAmount();
            p.getInventory().addItem(s).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        }
    }

    public void buy(Player p, ShopManager.Entry e, int amount) {
        double unit = shop().buyUnit(p, e);
        if (unit < 0 || !e.enabled) { Msg.prefixed(p, "§cCet item n'est pas en vente."); tick(p, false); return; }
        double total = round2(unit * amount);
        if (!money().has(p, total)) { Msg.prefixed(p, "§cFonds insuffisants : il vous faut §f" + fmt(total) + "§c."); tick(p, false); return; }
        ItemStack tpl = shop().stack(e.key, 1);
        if (tpl == null) return;
        if (!money().withdraw(p, total)) { Msg.prefixed(p, "§cPaiement refusé par l'économie."); tick(p, false); return; }
        give(p, tpl, amount);
        Msg.prefixed(p, "§aAchat : §f" + amount + "× " + shop().displayName(e.key) + " §apour §f" + fmt(total));
        tick(p, true);
    }

    private int count(Player p, ShopManager.Entry e) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (shop().matches(e, s)) n += s.getAmount();
        return n;
    }

    private void remove(Player p, ShopManager.Entry e, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < c.length && left > 0; i++) {
            if (!shop().matches(e, c[i])) continue;
            int take = Math.min(left, c[i].getAmount());
            c[i].setAmount(c[i].getAmount() - take);
            inv.setItem(i, c[i].getAmount() <= 0 ? null : c[i]);
            left -= take;
        }
    }

    public void sell(Player p, ShopManager.Entry e, int amount) {
        double unit = shop().sellUnit(p, e);
        if (unit < 0 || !e.enabled) { Msg.prefixed(p, "§cCet item n'est pas racheté."); tick(p, false); return; }
        int n = Math.min(count(p, e), amount);
        if (n <= 0) { Msg.prefixed(p, "§cVous n'avez pas cet item (ou il est modifié : nom, enchantement, usure)."); tick(p, false); return; }
        double total = round2(unit * n);
        if (!money().deposit(p, total)) { Msg.prefixed(p, "§cL'économie a refusé le paiement."); tick(p, false); return; }
        remove(p, e, n);
        Msg.prefixed(p, "§aVente : §f" + n + "× " + shop().displayName(e.key) + " §apour §f" + fmt(total));
        tick(p, true);
    }

    /** Vend tout ce qui est vendable (hors barre rapide par défaut). Renvoie le total gagné. */
    public double sellAll(Player p) {
        if (!needMoney(p)) return 0;
        Map<String, ShopManager.Entry> idx = shop().sellIndex();
        boolean hotbar = plugin.getConfig().getBoolean("shop.sell-all-hotbar", false);
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        Map<String, Integer> sold = new LinkedHashMap<>();
        double total = 0;
        for (int i = hotbar ? 0 : 9; i < c.length; i++) {
            ItemStack s = c[i];
            if (s == null || s.getType().isAir()) continue;
            ShopManager.Entry e = idx.get(shop().keyOf(s));
            if (e == null || !shop().matches(e, s)) continue;
            total += shop().sellUnit(p, e) * s.getAmount();
            sold.merge(shop().displayName(e.key), s.getAmount(), Integer::sum);
            inv.setItem(i, null);
        }
        total = round2(total);
        if (total <= 0 || !money().deposit(p, total)) {
            if (total > 0) Msg.prefixed(p, "§cL'économie a refusé le paiement.");
            else Msg.prefixed(p, "§7Rien de vendable dans votre inventaire (la barre rapide est ignorée).");
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sold.forEach((k, v) -> { if (sb.length() < 180) sb.append(v).append("× ").append(k).append(", "); });
        Msg.prefixed(p, "§aVendu pour §f" + fmt(total) + " §7: " + sb.toString().replaceAll(", $", ""));
        tick(p, true);
        return total;
    }

    // ═════════════ Opérations : boutique des joueurs ═════════════

    private void giveBack(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
    }

    public void cancelListing(Player p, PlayerShop.Listing l) {
        PlayerShop.Listing removed = pshop().remove(l.id());
        if (removed == null) { Msg.prefixed(p, "§cCette annonce n'existe plus."); return; }
        giveBack(p, removed.item());
        Msg.prefixed(p, "§7Annonce retirée : l'item est de retour dans votre inventaire.");
    }

    public void buyListing(Player p, PlayerShop.Listing l) {
        if (l.seller().equals(p.getUniqueId())) { Msg.prefixed(p, "§cC'est votre propre annonce (clic droit pour la retirer)."); return; }
        if (!money().has(p, l.price())) { Msg.prefixed(p, "§cFonds insuffisants : il vous faut §f" + fmt(l.price()) + "§c."); tick(p, false); return; }
        PlayerShop.Listing taken = pshop().remove(l.id()); // retiré d'abord : impossible de l'acheter deux fois
        if (taken == null) { Msg.prefixed(p, "§cCette annonce vient d'être vendue."); return; }
        if (!money().withdraw(p, taken.price())) {
            pshop().add(Bukkit.getPlayer(taken.seller()) != null ? Bukkit.getPlayer(taken.seller()) : p, taken.item(), taken.price()); // remise en vente
            Msg.prefixed(p, "§cPaiement refusé par l'économie.");
            return;
        }
        giveBack(p, taken.item());

        double tax = Math.max(0, Math.min(100, plugin.getConfig().getDouble("shop.player-shop.tax-percent", 5)));
        double net = round2(taken.price() * (1 - tax / 100.0));
        money().deposit(Bukkit.getOfflinePlayer(taken.seller()), net);
        Msg.prefixed(p, "§aAchat : §f" + taken.item().getAmount() + "× " + plainName(taken.item()) + " §apour §f" + fmt(taken.price()));
        Player seller = Bukkit.getPlayer(taken.seller());
        if (seller != null) {
            Msg.prefixed(seller, "§a" + p.getName() + " a acheté §f" + taken.item().getAmount() + "× " + plainName(taken.item())
                    + " §a: vous recevez §f" + fmt(net) + " §7(taxe " + tax + "%)");
        }
        tick(p, true);
    }

    private String plainName(ItemStack s) {
        if (s.hasItemMeta() && s.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(s.getItemMeta().displayName());
        }
        return s.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Met en vente l'item tenu (tout le stack). Renvoie un message d'erreur ou null. */
    public String list(Player p, double price) {
        if (!plugin.getConfig().getBoolean("shop.player-shop.enabled", true)) return "La boutique des joueurs est désactivée.";
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return "Tenez l'item à vendre en main.";
        if (MythicRules.isMythicWeapon(hand)) return "Une arme mythique ne peut pas être mise en vente.";
        double min = plugin.getConfig().getDouble("shop.player-shop.min-price", 1);
        double max = plugin.getConfig().getDouble("shop.player-shop.max-price", 1_000_000_000);
        if (price < min || price > max) return "Prix invalide (entre " + fmt(min) + " et " + fmt(max) + ").";
        if (pshop().count(p.getUniqueId()) >= plugin.getConfig().getInt("shop.player-shop.max-listings", 10)) return "Limite d'annonces atteinte.";
        ItemStack copy = hand.clone();
        p.getInventory().setItemInMainHand(null);
        PlayerShop.Listing l = pshop().add(p, copy, round2(price));
        Msg.prefixed(p, "§aAnnonce créée : §f" + copy.getAmount() + "× " + plainName(copy) + " §apour §f" + fmt(l.price()));
        return null;
    }

}
