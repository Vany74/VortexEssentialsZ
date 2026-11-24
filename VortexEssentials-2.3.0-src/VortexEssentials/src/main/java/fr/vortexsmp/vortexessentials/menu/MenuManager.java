package fr.vortexsmp.vortexessentials.menu;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.boost.BoostManager;
import fr.vortexsmp.vortexessentials.hud.HudTask;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.Feature;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.limit.LimitManager;
import fr.vortexsmp.vortexessentials.limit.PotionManager;
import fr.vortexsmp.vortexessentials.mythic.MythicEffectsTask;
import fr.vortexsmp.vortexessentials.recipe.RecipeManager;
import fr.vortexsmp.vortexessentials.tools.ToolOption;
import fr.vortexsmp.vortexessentials.tools.ToolSettings;
import fr.vortexsmp.vortexessentials.util.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menus : accueil (profil, catégories, fonctionnalités, boosts, réglages d'outil, admin),
 * listes d'items, aperçu de recette, profil, et écrans de gestion des limites.
 * (Les menus d'administration sont dans AdminMenuManager.)
 */
public final class MenuManager implements Listener {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final Category[] CATEGORIES = {
            Category.TOOL, Category.MYTHIC_TOOL, Category.LEGENDARY_WEAPON, Category.MYTHIC_WEAPON, Category.CONSUMABLE, Category.SECRET_WEAPON};
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final Material[] BOOST_ICONS = {
            Material.EMERALD, Material.DIAMOND, Material.GOLD_INGOT, Material.NETHERITE_INGOT,
            Material.AMETHYST_SHARD, Material.LAPIS_LAZULI, Material.QUARTZ};

    private static final int PAGE_SIZE = 45;
    private static final int S_PROFILE = 4, S_FEATURES = 19, S_BOOSTS = 20, S_TOOL = 22, S_ADMIN = 24, S_LIMITS = 25, S_SHOP = 21, S_SERVICES = 23;

    private final VortexEssentials plugin;
    private Material accent = Material.PURPLE_STAINED_GLASS_PANE;

    public MenuManager(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    // ═════════════ Ouverture ═════════════

    private static String t(String s) { return "§8» §b§lVortex §8| §f" + s; }

    public void openHub(Player p) { open(p, new MenuHolder(MenuHolder.Type.HUB), 36, t("Accueil")); }

    public void openCategory(Player p, Category c) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.CATEGORY);
        h.category(c);
        open(p, h, 45, t(c.label()));
    }

    public void openFeatures(Player p) { open(p, new MenuHolder(MenuHolder.Type.FEATURES), 36, t("Fonctionnalités")); }

    public void openFeature(Player p, Feature f) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.FEATURE);
        h.feature(f);
        open(p, h, 36, t(f.label()));
    }

    public void openBoosts(Player p) { open(p, new MenuHolder(MenuHolder.Type.BOOSTS), 36, t("Boosts")); }

    public void openProfile(Player p) { open(p, new MenuHolder(MenuHolder.Type.PROFILE), 36, t("Profil")); }

    public void openToolSettings(Player p) {
        VortexItem v = ItemUtil.get(p.getInventory().getItemInMainHand());
        if (ToolSettings.available(v).isEmpty()) {
            Msg.prefixed(p, "§7Tenez un outil Vortex réglable en main (Smelter, Explosive, Lumberjack, Fractured, Terraloop, Jugas).");
            return;
        }
        MenuHolder h = new MenuHolder(MenuHolder.Type.TOOL);
        h.item(v);
        open(p, h, 27, t("Réglages — " + v.displayName()));
    }

    public void openLimits(Player p) {
        if (!canLimit(p)) return;
        open(p, new MenuHolder(MenuHolder.Type.LIMITS), 27, t("Limites"));
    }

    public void openEnchants(Player p, String query) {
        if (!canLimit(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.ENCHANTS);
        h.query(query == null ? "" : query.toLowerCase(Locale.ROOT));
        open(p, h, 54, t("Enchantements"));
    }

    public void openPotions(Player p) {
        if (!canLimit(p)) return;
        open(p, new MenuHolder(MenuHolder.Type.POTIONS), 54, t("Potions"));
    }

    public void openObjects(Player p) {
        if (!canLimit(p)) return;
        open(p, new MenuHolder(MenuHolder.Type.OBJECTS), 27, t("Objets"));
    }

    public void openRecipe(Player p, VortexItem v) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.RECIPE);
        h.item(v);
        open(p, h, 45, t("Recette — " + v.category().color() + v.displayName()));
    }

    private void open(Player p, MenuHolder h, int size, String title) {
        Inventory inv = Bukkit.createInventory(h, size, Msg.c(title));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    private boolean canLimit(Player p) {
        if (p.hasPermission("vortexessentials.limit.admin")) return true;
        Msg.prefixed(p, "§cPermission refusée.");
        return false;
    }

    // ═════════════ Dessin ═════════════

    private void draw(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        inv.clear();
        accent = switch (h.type()) {
            case HUB, PROFILE -> Material.PURPLE_STAINED_GLASS_PANE;
            case FEATURES, FEATURE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case BOOSTS -> Material.LIME_STAINED_GLASS_PANE;
            case TOOL -> Material.YELLOW_STAINED_GLASS_PANE;
            case CATEGORY -> categoryPane(h.category());
            case RECIPE -> categoryPane(h.item().category());
            default -> Material.ORANGE_STAINED_GLASS_PANE;
        };
        switch (h.type()) {
            case HUB -> drawHub(p, inv);
            case CATEGORY -> drawCategory(p, h, inv);
            case FEATURES -> drawFeatures(inv);
            case FEATURE -> drawFeature(p, h, inv);
            case BOOSTS -> drawBoosts(p, inv);
            case PROFILE -> drawProfile(p, inv);
            case TOOL -> drawTool(p, h, inv);
            case LIMITS -> drawLimits(inv);
            case ENCHANTS -> drawEnchants(h, inv);
            case POTIONS -> drawPotions(h, inv);
            case OBJECTS -> drawObjects(inv);
            case RECIPE -> drawRecipe(p, h, inv);
            default -> { }
        }
    }

    private static Material categoryPane(Category c) {
        return switch (c) {
            case TOOL -> Material.CYAN_STAINED_GLASS_PANE;
            case MYTHIC_TOOL -> Material.PURPLE_STAINED_GLASS_PANE;
            case LEGENDARY_WEAPON -> Material.ORANGE_STAINED_GLASS_PANE;
            case MYTHIC_WEAPON -> Material.RED_STAINED_GLASS_PANE;
            case CONSUMABLE -> Material.PINK_STAINED_GLASS_PANE;
            case SECRET_WEAPON -> Material.BLACK_STAINED_GLASS_PANE;
        };
    }

    private void fill(Inventory inv, int from, int to) { Ui.frame(inv, from, to, accent); }

    private ItemStack button(Material m, String name, String... lore) { return Ui.button(m, name, lore); }

    // ───────── Accueil ─────────

    private ItemStack head(Player p) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Msg.plain("§b§l" + p.getName()));
        sm.lore(lore(
                "§7Temps de jeu : §f" + playtimeText(p),
                "§7Niveau : §f" + p.getLevel() + " §8| §7Ping : §f" + p.getPing() + " ms",
                "§7Boosts actifs : §f" + plugin.boosts().active(p).size(),
                "", "§eClic §7: voir mon profil"));
        sm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        skull.setItemMeta(sm);
        return skull;
    }

    private static List<net.kyori.adventure.text.Component> lore(String... lines) {
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lines) l.add(Msg.plain(s));
        return l;
    }

    private String playtimeText(Player p) {
        return plugin.playtime().format(plugin.playtime().seconds(p.getUniqueId()));
    }

    private void drawHub(Player p, Inventory inv) {
        fill(inv, 0, inv.getSize());
        inv.setItem(S_PROFILE, head(p));
        for (int i = 0; i < CATEGORIES.length; i++) {
            Category c = CATEGORIES[i];
            int count = VortexItem.ofCategory(c).size();
            inv.setItem(CATEGORY_SLOTS[i], button(c.icon(), c.color() + "§l" + c.label(),
                    Ui.bar(), "§7" + count + " item" + (count > 1 ? "s" : ""), "", "§eClic §7pour ouvrir"));
        }
        inv.setItem(S_FEATURES, button(Material.BOOKSHELF, "§b§lFonctionnalités",
                Ui.bar(), "§7Zone, autosmelt, abattage, capacités...", "", "§eClic §7pour ouvrir"));
        inv.setItem(S_BOOSTS, button(Material.EXPERIENCE_BOTTLE, "§a§lBoosts",
                Ui.bar(), "§7Avantages de vos grades", "", "§eClic §7pour ouvrir"));
        inv.setItem(S_SHOP, button(Material.EMERALD, "§a§lBoutique",
                Ui.bar(), "§7Achetez et vendez : serveur et joueurs", "§7Réductions et bonus de vos grades", "", "§eClic §7pour ouvrir"));
        inv.setItem(S_SERVICES, button(Material.NETHER_STAR, "§d§lSystèmes du serveur",
                "§7Commandes, primes, spawners, rituels, boosts,", "§7temps de jeu et outils du staff.", "", "§eClic §7: ouvrir"));
        inv.setItem(S_TOOL, button(Material.SMITHING_TABLE, "§e§lMon outil",
                Ui.bar(), "§7Réglages de l'outil en main", "§8(ou sneak + clic droit)", "", "§eClic §7pour ouvrir"));
        if (p.hasPermission("vortexessentials.admin")) {
            inv.setItem(S_ADMIN, button(Material.COMMAND_BLOCK, "§c§lAdministration",
                    Ui.bar(), "§7Désactivations, limites, crafts, items", "", "§8Réservé aux admins"));
        }
        if (p.hasPermission("vortexessentials.limit.admin")) {
            inv.setItem(S_LIMITS, button(Material.ENCHANTED_BOOK, "§6§lLimites",
                    Ui.bar(), "§7Enchantements, potions, bypass", "", "§8Réservé aux admins"));
        }
    }

    // ───────── Catégories / fonctionnalités ─────────

    private void drawCategory(Player p, MenuHolder h, Inventory inv) {
        fill(inv, 0, inv.getSize());
        List<VortexItem> items = VortexItem.ofCategory(h.category());
        boolean admin = p.hasPermission("vortexessentials.admin");
        for (int i = 0; i < items.size() && i < 18; i++) inv.setItem(9 + i, itemIcon(items.get(i), admin));
        inv.setItem(40, button(Material.ARROW, "§7« Accueil"));
    }

    private ItemStack itemIcon(VortexItem v, boolean admin) {
        List<String> extra = new ArrayList<>();
        extra.add("");
        extra.addAll(fr.vortexsmp.vortexessentials.item.CraftInfo.lines(plugin, v));
        extra.add("§eClic gauche §7: voir la recette");
        if (admin) extra.add("§eClic droit §7: recevoir l'item §8(admin)");
        return ItemUtil.icon(v, extra.toArray(new String[0]));
    }

    private void drawFeatures(Inventory inv) {
        fill(inv, 0, inv.getSize());
        Feature[] fs = Feature.values();
        for (int i = 0; i < fs.length && i < 7; i++) {
            Feature f = fs[i];
            inv.setItem(10 + i, button(f.icon(), "§b§l" + f.label(),
                    Ui.bar(), "§7" + f.description(), "§7Items : §f" + f.items().size(), "", "§eClic §7pour voir les items"));
        }
        inv.setItem(31, button(Material.ARROW, "§7« Accueil"));
    }

    private void drawFeature(Player p, MenuHolder h, Inventory inv) {
        fill(inv, 0, inv.getSize());
        boolean admin = p.hasPermission("vortexessentials.admin");
        List<VortexItem> items = h.feature().items();
        for (int i = 0; i < items.size() && i < 9; i++) inv.setItem(9 + i, itemIcon(items.get(i), admin));
        inv.setItem(4, button(h.feature().icon(), "§b§l" + h.feature().label(), "§7" + h.feature().description()));
        inv.setItem(31, button(Material.ARROW, "§7« Fonctionnalités"));
    }

    // ───────── Boosts ─────────

    private void drawBoosts(Player p, Inventory inv) {
        fill(inv, 0, inv.getSize());
        BoostManager bm = plugin.boosts();
        List<BoostManager.Group> groups = bm.all();
        Set<String> mine = new LinkedHashSet<>();
        for (BoostManager.Group g : bm.active(p)) mine.add(g.id());

        if (groups.isEmpty()) {
            inv.setItem(13, button(Material.BARRIER, "§cAucun boost configuré", "§7Section boosts: de config.yml"));
        }
        for (int i = 0; i < groups.size() && i < 7; i++) {
            BoostManager.Group g = groups.get(i);
            boolean has = mine.contains(g.id());
            List<String> l = new ArrayList<>();
            l.add(Ui.bar());
            l.add("§7XP : §fx" + g.xp());
            l.add("§7Drops de mobs : §fx" + g.drops());
            l.add("§7Dégâts : §fx" + g.damage());
            l.add("§7Durée des potions : §fx" + g.potionDuration());
            l.add("§7Cooldowns : §f-" + Math.round(g.cooldownReduction() * 100) + "%");
            l.add("§7Vente : §f+" + Math.round(g.sellBonus() * 100) + "% §8| §7Achat : §f-" + Math.round(g.buyDiscount() * 100) + "%");
            if (!g.effects().isEmpty()) {
                List<String> fx = new ArrayList<>();
                g.effects().forEach((t, amp) -> fx.add(Effects.label(t.getKey().getKey()) + " " + Effects.roman(amp + 1)));
                l.add("§7Effets : §f" + String.join("§7, §f", fx));
            }
            l.add("");
            l.add(has ? "§a✔ Votre grade" : "§8Non débloqué");
            ItemStack it = button(BOOST_ICONS[i % BOOST_ICONS.length], (has ? "§a§l" : "§7§l") + g.id(), l.toArray(new String[0]));
            if (has) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(10 + i, it);
        }
        inv.setItem(22, button(Material.NETHER_STAR, "§d§lVos boosts totaux",
                Ui.bar(),
                "§7XP : §fx" + String.format("%.2f", bm.multiplier(p, BoostManager.Stat.XP)),
                "§7Drops : §fx" + String.format("%.2f", bm.multiplier(p, BoostManager.Stat.DROPS)),
                "§7Dégâts : §fx" + String.format("%.2f", bm.multiplier(p, BoostManager.Stat.DAMAGE)),
                "§7Potions : §fx" + String.format("%.2f", bm.multiplier(p, BoostManager.Stat.POTION)),
                "§7Cooldowns : §fx" + String.format("%.2f", bm.cooldownFactor(p)),
                "§7Prix de vente : §fx" + String.format("%.2f", bm.sellMultiplier(p)),
                "§7Prix d'achat : §fx" + String.format("%.2f", bm.buyMultiplier(p)),
                "§8Cumul : " + bm.stacking()));
        inv.setItem(31, button(Material.ARROW, "§7« Accueil"));
    }

    // ───────── Profil ─────────

    private void drawProfile(Player p, Inventory inv) {
        fill(inv, 0, inv.getSize());
        ItemStack skull = head(p);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        sm.lore(lore(
                Ui.bar(),
                "§7Temps de jeu : §f" + playtimeText(p),
                "§7Mobs tués : §f" + p.getStatistic(Statistic.MOB_KILLS),
                "§7Joueurs tués : §f" + p.getStatistic(Statistic.PLAYER_KILLS),
                "§7Morts : §f" + p.getStatistic(Statistic.DEATHS),
                "§7Niveau d'XP : §f" + p.getLevel(),
                "§7Monde : §f" + p.getWorld().getName(),
                "§7Position : §f" + p.getLocation().getBlockX() + " " + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ()));
        skull.setItemMeta(sm);
        inv.setItem(4, skull);

        // Items Vortex portés
        Set<VortexItem> seen = new LinkedHashSet<>();
        for (ItemStack s : p.getInventory().getContents()) {
            VortexItem v = ItemUtil.get(s);
            if (v != null) seen.add(v);
        }
        int slot = 10;
        for (VortexItem v : seen) {
            if (slot > 16) break;
            inv.setItem(slot++, ItemUtil.icon(v));
        }
        if (seen.isEmpty()) {
            inv.setItem(13, button(Material.BARRIER, "§7Aucun item Vortex dans votre inventaire"));
        }
        List<String> mythic = new ArrayList<>();
        for (VortexItem v : MythicEffectsTask.carried(p)) mythic.add(v.displayName());

        inv.setItem(20, button(Material.EXPERIENCE_BOTTLE, "§a§lBoosts actifs", Ui.bar(),
                "§7Grades : §f" + (plugin.boosts().active(p).isEmpty() ? "aucun"
                        : String.join("§7, §f", plugin.boosts().active(p).stream().map(BoostManager.Group::id).toList())),
                "§7XP : §fx" + String.format("%.2f", plugin.boosts().multiplier(p, BoostManager.Stat.XP))));
        inv.setItem(22, button(Material.NETHERITE_SWORD, "§c§lArme mythique", Ui.bar(),
                mythic.isEmpty() ? "§7Aucune portée" : "§f" + String.join("§7, §f", mythic)));
        boolean hud = HudTask.enabledFor(p);
        inv.setItem(24, button(hud ? Material.LIME_DYE : Material.GRAY_DYE, "§e§lHUD des cooldowns",
                Ui.bar(), hud ? "§aActivé" : "§cDésactivé", "", "§eClic §7pour basculer"));
        inv.setItem(31, button(Material.ARROW, "§7« Accueil"));
    }

    // ───────── Réglages d'outil ─────────

    private void drawTool(Player p, MenuHolder h, Inventory inv) {
        fill(inv, 0, inv.getSize());
        ItemStack hand = p.getInventory().getItemInMainHand();
        inv.setItem(4, ItemUtil.icon(h.item()));
        List<ToolOption> opts = ToolSettings.available(h.item());
        for (int i = 0; i < opts.size() && i < 4; i++) {
            ToolOption o = opts.get(i);
            boolean on = ToolSettings.get(hand, o);
            ItemStack it = button(o.icon(), (on ? "§a§l" : "§c§l") + o.label(),
                    Ui.bar(), "§7" + o.description(), "",
                    "§7État : " + (on ? "§aActivé" : "§cDésactivé"), "", "§eClic §7pour basculer");
            if (on) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(10 + 2 * i, it);
        }
        inv.setItem(22, button(Material.BARRIER, "§7Fermer"));
    }

    // ───────── Limites ─────────

    private void drawLimits(Inventory inv) {
        fill(inv, 0, inv.getSize());
        inv.setItem(11, button(Material.ENCHANTED_BOOK, "§d§lEnchantements", Ui.bar(), "§7Niveaux max et activation"));
        inv.setItem(13, button(Material.BREWING_STAND, "§b§lPotions", Ui.bar(), "§7Amplificateur et durée max"));
        inv.setItem(15, button(Material.SHIELD, "§6§lObjets", Ui.bar(), "§7Bypass individuels et permanents"));
        inv.setItem(22, button(Material.ARROW, "§7« Accueil"));
    }

    private void drawEnchants(MenuHolder h, Inventory inv) {
        String q = h.query();
        List<String> keys = new ArrayList<>();
        for (Enchantment e : plugin.limits().all()) {
            String k = e.getKey().getKey();
            if (q.isBlank() || k.contains(q)) keys.add(k);
        }
        h.entries(keys);
        int max = Math.max(0, (keys.size() - 1) / PAGE_SIZE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE_SIZE; n++) {
            int idx = h.page() * PAGE_SIZE + n;
            if (idx >= keys.size()) break;
            Enchantment e = plugin.limits().find(keys.get(idx));
            if (e != null) inv.setItem(n, enchantIcon(e));
        }
        navBar(inv, h, max, q.isBlank() ? "§7Recherche : §f/vortexlimit search <texte>" : "§7Filtre : §f" + q);
    }

    private void drawPotions(MenuHolder h, Inventory inv) {
        List<String> ids = plugin.potions().all();
        h.entries(ids);
        int max = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE_SIZE; n++) {
            int idx = h.page() * PAGE_SIZE + n;
            if (idx >= ids.size()) break;
            inv.setItem(n, potionIcon(ids.get(idx)));
        }
        navBar(inv, h, max, "§7Appliqué si §fenforce-potions §7est activé");
    }

    private void drawObjects(Inventory inv) {
        fill(inv, 0, inv.getSize());
        inv.setItem(11, button(Material.SHIELD, "§6Bypass item", "§7Item en main exempté des limites", "§f/vortexlimit bypass"));
        inv.setItem(13, button(Material.GOLD_BLOCK, "§eType permanent", "§7Tous les items de ce matériau", "§f/vortexlimit type"));
        inv.setItem(15, button(Material.NAME_TAG, "§aID objet", "§7Donne un ID à l'item et l'exempte", "§f/vortexlimit id <nom>"));
        inv.setItem(18, button(Material.BOOK, "§7Actuellement",
                "§7Matériaux exemptés : §f" + plugin.bypass().materialCount(),
                "§7IDs exemptés : §f" + plugin.bypass().objectIdCount(),
                "§8Les items Vortex sont exemptés (limit.bypass.vortex-items)."));
        inv.setItem(22, button(Material.ARROW, "§7« Limites"));
    }

    private void navBar(Inventory inv, MenuHolder h, int maxPage, String info) {
        Ui.frame(inv, 45, 54, accent);
        inv.setItem(45, button(Material.ARROW, "§e◀ Page précédente"));
        if (h.type() == MenuHolder.Type.ENCHANTS) {
            inv.setItem(46, button(Material.NAME_TAG, "§b🔎 Recherche", info));
            inv.setItem(47, button(Material.BARRIER, "§cEffacer la recherche"));
        } else {
            inv.setItem(46, button(Material.PAPER, "§7Info", info));
        }
        inv.setItem(49, button(Material.NETHER_STAR, "§d✦ Vortex", "§7Page §f" + (h.page() + 1) + "§7/§f" + (maxPage + 1)));
        inv.setItem(51, button(Material.ARROW, "§aPage suivante ▶"));
        inv.setItem(53, button(Material.COMPASS, "§7Retour", "§7Menu Limites"));
    }

    private ItemStack enchantIcon(Enchantment e) {
        LimitManager.Limit r = plugin.limits().get(e);
        return button(r.enabled() ? Material.ENCHANTED_BOOK : Material.BARRIER,
                (r.enabled() ? "§a" : "§c") + "§l" + e.getKey().getKey(),
                "§7État : " + (r.enabled() ? "§aACTIVÉ" : "§cDÉSACTIVÉ"),
                "§7Max : §e" + r.maxLevel(), "§7Vanilla : §f" + e.getMaxLevel(), "",
                "§eGauche §7: activer/désactiver", "§eDroit §7: +1", "§eShift+Droit §7: -1", "§eShift+Gauche §7: reset");
    }

    /** Vraie potion colorée selon l'effet (splash pour les effets nuisibles), nom français. */
    private ItemStack potionIcon(String id) {
        PotionManager.PotionRule r = plugin.potions().get(id);
        boolean harmful = Effects.HARMFUL.contains(id);
        ItemStack i = new ItemStack(harmful ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(id));
        if (type != null) pm.setColor(type.getColor());
        pm.displayName(Msg.plain((r.enabled() ? "§a" : "§c") + "§l" + Effects.label(id) + " §8(" + id + ")"));
        pm.lore(lore(
                "§7État : " + (r.enabled() ? "§aACTIVÉ" : "§cDÉSACTIVÉ"),
                "§7Niveau max : §e" + Effects.roman(r.maxAmplifier() + 1) + " §8(amplificateur " + r.maxAmplifier() + ")",
                "§7Durée max : §e" + r.maxDurationSeconds() + "s",
                "§7Type : " + (harmful ? "§cNuisible" : "§aBénéfique"), "",
                "§eGauche §7: activer/désactiver", "§eDroit §7: +1 niveau", "§eShift+Droit §7: -1 niveau", "§eShift+Gauche §7: reset"));
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        pm.setEnchantmentGlintOverride(r.enabled());
        i.setItemMeta(pm);
        return i;
    }

    // ───────── Recette ─────────

    private void drawRecipe(Player p, MenuHolder h, Inventory inv) {
        fill(inv, 0, inv.getSize());
        VortexItem v = h.item();
        for (int s : GRID) inv.setItem(s, null);
        List<String> info = new ArrayList<>();
        info.add("");
        info.addAll(fr.vortexsmp.vortexessentials.item.CraftInfo.lines(plugin, v));
        inv.setItem(25, ItemUtil.icon(v, info.toArray(new String[0])));
        inv.setItem(40, button(Material.ARROW, "§7« " + v.category().label()));

        RecipeManager rm = plugin.recipes();
        VortexItem src = rm.forgeSource(v);
        RecipeManager.Def d = rm.def(v);

        if (src != null) {
            inv.setItem(19, button(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "§fModèle d'amélioration en netherite"));
            inv.setItem(20, ItemUtil.icon(src, "", "§8Votre item, à poser sur la table de forge"));
            inv.setItem(21, new ItemStack(Material.NETHERITE_INGOT));
            inv.setItem(23, button(Material.SMITHING_TABLE, "§7Table de forge", "§7Conserve durabilité et enchantements"));
        } else if (d != null) {
            inv.setItem(23, button(Material.CRAFTING_TABLE, d.shaped() ? "§7Établi §8(forme fixe)" : "§7Établi §8(sans forme)",
                    d.transferFrom() != null ? "§7Conserve durabilité et enchantements" : "§7Craft classique"));
            if (d.shaped()) {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        char ch = ' ';
                        if (r < d.shape().size() && c < d.shape().get(r).length()) ch = d.shape().get(r).charAt(c);
                        if (ch != ' ') inv.setItem(GRID[r * 3 + c], ingredient(d, d.ingredients().get(ch)));
                    }
                }
            } else {
                for (int i = 0; i < d.shapeless().size() && i < 9; i++) inv.setItem(GRID[i], ingredient(d, d.shapeless().get(i)));
            }
        } else {
            inv.setItem(20, button(Material.BARRIER, "§cAucune recette", "§7Obtenu auprès d'un administrateur"));
        }
        if (p.hasPermission("vortexessentials.admin")) {
            inv.setItem(26, button(Material.ANVIL, "§b§lÉditer ce craft", "§7Recette, limites de craft...", "§eClic §7: ouvrir l'éditeur §8(admin)"));
        }
    }

    private ItemStack ingredient(RecipeManager.Def d, String token) {
        Material m = token.startsWith("#") ? null : Material.matchMaterial(token);
        if (m != null && d.requireCustom().containsKey(m)) {
            return ItemUtil.icon(d.requireCustom().get(m), "", "§8Doit être votre version de cet item");
        }
        return RecipeManager.display(token);
    }

    // ═════════════ Clics ═════════════

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h)) return;
        if (h.type().admin() || h.type().shop()) return; // gérés par AdminMenuManager / ShopMenus
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;
        handle(p, h, e);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder h && !h.type().admin() && !h.type().shop()) e.setCancelled(true);
    }

    private void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f); }

    private void handle(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        switch (h.type()) {
            case HUB -> {
                for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
                    if (slot == CATEGORY_SLOTS[i]) { click(p); openCategory(p, CATEGORIES[i]); return; }
                }
                switch (slot) {
                    case S_PROFILE -> { click(p); openProfile(p); }
                    case S_FEATURES -> { click(p); openFeatures(p); }
                    case S_BOOSTS -> { click(p); openBoosts(p); }
                    case S_TOOL -> { click(p); openToolSettings(p); }
                    case S_SHOP -> { click(p); plugin.shopMenus().openHub(p); }
                    case S_SERVICES -> { click(p); plugin.servicesMenu().open(p); }
                    case S_ADMIN -> { if (p.hasPermission("vortexessentials.admin")) { click(p); plugin.adminMenus().openHub(p); } }
                    case S_LIMITS -> { if (p.hasPermission("vortexessentials.limit.admin")) { click(p); openLimits(p); } }
                    default -> { }
                }
            }
            case CATEGORY -> {
                if (slot == 40) { openHub(p); return; }
                itemClick(p, VortexItem.ofCategory(h.category()), slot - 9, e.getClick());
            }
            case FEATURES -> {
                if (slot == 31) { openHub(p); return; }
                Feature[] fs = Feature.values();
                int idx = slot - 10;
                if (idx >= 0 && idx < fs.length && idx < 7) { click(p); openFeature(p, fs[idx]); }
            }
            case FEATURE -> {
                if (slot == 31) { openFeatures(p); return; }
                itemClick(p, h.feature().items(), slot - 9, e.getClick());
            }
            case BOOSTS -> { if (slot == 31) openHub(p); }
            case PROFILE -> {
                if (slot == 31) openHub(p);
                else if (slot == 24) { HudTask.setEnabled(p, !HudTask.enabledFor(p)); click(p); draw(p, h); }
            }
            case TOOL -> toolClick(p, h, slot);
            case LIMITS -> {
                if (!p.hasPermission("vortexessentials.limit.admin")) return;
                switch (slot) {
                    case 11 -> openEnchants(p, "");
                    case 13 -> openPotions(p);
                    case 15 -> openObjects(p);
                    case 22 -> openHub(p);
                    default -> { }
                }
            }
            case OBJECTS -> { if (slot == 22) openLimits(p); }
            case RECIPE -> {
                if (slot == 40 && h.item() != null) openCategory(p, h.item().category());
                else if (slot == 26 && h.item() != null && p.hasPermission("vortexessentials.admin")) plugin.recipeEditor().openEdit(p, h.item(), null);
            }
            case ENCHANTS, POTIONS -> handlePaged(p, h, e);
            default -> { }
        }
    }

    private void itemClick(Player p, List<VortexItem> items, int idx, ClickType click) {
        if (idx < 0 || idx >= items.size()) return;
        if (click == ClickType.LEFT) { click(p); openRecipe(p, items.get(idx)); }
        else if (click == ClickType.RIGHT && p.hasPermission("vortexessentials.admin")) give(p, items.get(idx));
    }

    private void toolClick(Player p, MenuHolder h, int slot) {
        if (slot == 22) { p.closeInventory(); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ItemUtil.get(hand) != h.item()) { p.closeInventory(); return; }
        List<ToolOption> opts = ToolSettings.available(h.item());
        int idx = (slot - 10) / 2;
        if (slot < 10 || (slot - 10) % 2 != 0 || idx >= opts.size()) return;

        ToolOption o = opts.get(idx);
        ItemStack copy = hand.clone();
        ToolSettings.set(copy, o, !ToolSettings.get(copy, o));
        p.getInventory().setItemInMainHand(copy);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, ToolSettings.get(copy, o) ? 1.6f : 0.8f);
        draw(p, h);
    }

    private void handlePaged(Player p, MenuHolder h, InventoryClickEvent e) {
        if (!p.hasPermission("vortexessentials.limit.admin")) return;
        int slot = e.getRawSlot();

        if (slot >= PAGE_SIZE) {
            switch (slot) {
                case 45 -> { h.page(h.page() - 1); draw(p, h); }
                case 51 -> { h.page(h.page() + 1); draw(p, h); }
                case 47 -> { if (h.type() == MenuHolder.Type.ENCHANTS) { h.query(""); h.page(0); draw(p, h); } }
                case 53 -> openLimits(p);
                default -> { }
            }
            return;
        }

        int idx = h.page() * PAGE_SIZE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        String key = h.entries().get(idx);
        boolean shift = e.isShiftClick(), left = e.isLeftClick(), right = e.isRightClick();

        if (h.type() == MenuHolder.Type.ENCHANTS) {
            Enchantment en = plugin.limits().find(key);
            if (en == null) return;
            int max = plugin.limits().get(en).maxLevel();
            if (shift && left) plugin.limits().reset(en);
            else if (shift && right) plugin.limits().setMax(en, max - 1);
            else if (right) plugin.limits().setMax(en, max + 1);
            else if (left) plugin.limits().toggle(en);
            else return;
            plugin.limits().save();
        } else {
            int max = plugin.potions().get(key).maxAmplifier();
            if (shift && left) plugin.potions().reset(key);
            else if (shift && right) plugin.potions().setAmplifier(key, max - 1);
            else if (right) plugin.potions().setAmplifier(key, max + 1);
            else if (left) plugin.potions().toggle(key);
            else return;
            plugin.potions().save();
        }
        draw(p, h);
    }

    /** Donne un item Vortex à un joueur (déborde au sol si l'inventaire est plein). */
    public static void give(Player p, VortexItem v) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(ItemUtil.create(v));
        left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        Msg.prefixed(p, "§aVous avez reçu §f" + v.displayName() + "§a.");
    }
}
