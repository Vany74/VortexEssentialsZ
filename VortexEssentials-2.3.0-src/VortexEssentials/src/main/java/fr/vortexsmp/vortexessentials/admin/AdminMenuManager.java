package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.boost.BoostManager;
import fr.vortexsmp.vortexessentials.menu.MenuHolder;
import fr.vortexsmp.vortexessentials.menu.Ui;
import fr.vortexsmp.vortexessentials.util.Effects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menus d'administration : hub, listes (items/crafts désactivés, limites, crafts perso, items perso),
 * potions non brassables et éditeur de craft.
 */
public final class AdminMenuManager implements Listener {
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 25, PAGE = 45;

    private final VortexEssentials plugin;

    public AdminMenuManager(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static String t(String s) { return "§8» §c§lAdmin §8| §f" + s; }

    private static String pretty(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean allowed(Player p) {
        if (p.hasPermission("vortexessentials.admin")) return true;
        Msg.prefixed(p, "§cPermission refusée.");
        return false;
    }

    // ═════════════ Ouverture ═════════════

    public void openHub(Player p) {
        if (!allowed(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.ADMIN);
        Inventory inv = Bukkit.createInventory(h, 36, Msg.c(t("Administration")));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    /** kind : items, crafts, limits, recipes. */
    public void openList(Player p, String kind) {
        if (!allowed(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.ADMIN_LIST);
        h.kind(kind);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(t(switch (kind) {
            case "items" -> "Items désactivés";
            case "crafts" -> "Crafts désactivés";
            case "limits" -> "Limites d'objets";
            case "boosts" -> "Boosts de grade";
            default -> "Crafts personnalisés";
        })));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    public void openPotions(Player p) {
        if (!allowed(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.ADMIN_POTIONS);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(t("Potions non brassables")));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    public void openCustomItems(Player p) {
        if (!allowed(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.CITEMS);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(t("Items personnalisés")));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    public void openEditor(Player p, String name) {
        if (!allowed(p)) return;
        MenuHolder h = new MenuHolder(MenuHolder.Type.CRAFT_EDIT);
        h.name(CustomRecipes.clean(name));
        Inventory inv = Bukkit.createInventory(h, 45, Msg.c(t("Craft « " + h.name() + " »")));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    // ═════════════ Dessin ═════════════

    private void draw(Player p, MenuHolder h) {
        Inventory inv = h.getInventory();
        switch (h.type()) {
            case ADMIN -> drawHub(inv);
            case ADMIN_LIST -> drawList(h, inv);
            case ADMIN_POTIONS -> drawPotions(h, inv);
            case CITEMS -> drawCustomItems(h, inv);
            case BOOST_EDIT -> drawBoostEditor(h, inv);
            case CRAFT_EDIT -> drawEditor(h, inv);
            default -> { }
        }
    }

    private void drawHub(Inventory inv) {
        Ui.frame(inv, 0, inv.getSize(), Material.RED_STAINED_GLASS_PANE);
        AdminManager a = plugin.admin();
        inv.setItem(10, Ui.button(Material.BARRIER, "§c§lItems désactivés", Ui.bar(),
                "§7" + a.items().size() + " item(s)", "§8Interdits : usage, pose, ramassage, craft", "", "§eClic §7pour gérer"));
        inv.setItem(11, Ui.button(Material.CRAFTING_TABLE, "§6§lCrafts désactivés", Ui.bar(),
                "§7" + a.crafts().size() + " craft(s)", "§8Établi, forge, four", "", "§eClic §7pour gérer"));
        inv.setItem(12, Ui.button(Material.BREWING_STAND, "§b§lPotions non brassables", Ui.bar(),
                "§7" + a.potions().size() + " potion(s) bloquée(s)", "", "§eClic §7pour gérer"));
        inv.setItem(13, Ui.button(Material.HOPPER, "§e§lLimites d'objets", Ui.bar(),
                "§7" + a.limits().size() + " limite(s)", "§8Quantité max dans l'inventaire", "", "§eClic §7pour gérer"));
        inv.setItem(14, Ui.button(Material.KNOWLEDGE_BOOK, "§a§lCrafts personnalisés", Ui.bar(),
                "§7" + plugin.customRecipes().names().size() + " craft(s)", "§8Créez vos recettes en jeu", "", "§eClic §7pour gérer"));
        inv.setItem(15, Ui.button(Material.COMMAND_BLOCK, "§d§lItems personnalisés", Ui.bar(),
                "§7" + plugin.customItems().ids().size() + " item(s)", "§8Actions au clic, façon ExecutableItems", "", "§eClic §7pour gérer"));
        inv.setItem(16, Ui.button(Material.EXPERIENCE_BOTTLE, "§a§lBoosts de grade", Ui.bar(),
                "§7" + plugin.boosts().all().size() + " groupe(s)", "§8Créez et réglez les avantages des grades", "", "§eClic §7pour gérer"));
        inv.setItem(31, Ui.button(Material.ARROW, "§7« Accueil"));
    }

    private List<String> entriesOf(String kind) {
        List<String> out = new ArrayList<>();
        switch (kind) {
            case "items" -> plugin.admin().items().forEach(m -> out.add(m.name()));
            case "crafts" -> plugin.admin().crafts().forEach(m -> out.add(m.name()));
            case "limits" -> plugin.admin().limits().keySet().forEach(m -> out.add(m.name()));
            case "boosts" -> plugin.boosts().all().forEach(g -> out.add(g.id()));
            default -> out.addAll(plugin.customRecipes().names());
        }
        return out;
    }

    private void navBar(Inventory inv, MenuHolder h, int maxPage, Material addIcon, String addName, String... addLore) {
        Ui.frame(inv, 45, 54, Material.RED_STAINED_GLASS_PANE);
        inv.setItem(45, Ui.button(Material.ARROW, "§e◀ Page précédente"));
        inv.setItem(47, Ui.button(Material.NETHER_STAR, "§d✦ Page " + (h.page() + 1) + "/" + (maxPage + 1)));
        inv.setItem(49, Ui.button(addIcon, addName, addLore));
        inv.setItem(51, Ui.button(Material.ARROW, "§aPage suivante ▶"));
        inv.setItem(53, Ui.button(Material.COMPASS, "§7« Administration"));
    }

    private static ItemStack withLore(ItemStack s, String... lines) {
        ItemMeta m = s.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String l : lines) lore.add(Msg.plain(l));
        m.lore(lore);
        s.setItemMeta(m);
        return s;
    }

    private void drawList(MenuHolder h, Inventory inv) {
        inv.clear();
        List<String> entries = entriesOf(h.kind());
        h.entries(entries);
        int max = Math.max(0, (entries.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));

        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= entries.size()) break;
            String key = entries.get(idx);
            switch (h.kind()) {
                case "items" -> inv.setItem(n, Ui.button(mat(key), "§c" + pretty(key), "§7Désactivé", "", "§eClic §7: réactiver"));
                case "crafts" -> inv.setItem(n, Ui.button(mat(key), "§6" + pretty(key), "§7Craft désactivé", "", "§eClic §7: réactiver"));
                case "limits" -> inv.setItem(n, Ui.button(mat(key), "§e" + pretty(key),
                        "§7Limite : §f" + plugin.admin().limits().get(mat(key)),
                        "", "§eClic gauche §7: +1", "§eClic droit §7: -1", "§eShift §7: supprimer"));
                case "boosts" -> inv.setItem(n, boostIcon(key));
                default -> {
                    ItemStack res = plugin.customRecipes().result(key);
                    ItemStack icon = res == null ? new ItemStack(Material.BARRIER) : res.clone();
                    inv.setItem(n, withLore(icon, "", "§8Craft : §f" + key, "§eClic gauche §7: recevoir le résultat", "§eShift + droit §7: supprimer"));
                }
            }
        }
        switch (h.kind()) {
            case "recipes" -> navBar(inv, h, max, Material.LIME_DYE, "§a§lCréer un craft", "§7Ouvre l'éditeur graphique");
            case "boosts" -> navBar(inv, h, max, Material.LIME_DYE, "§a§lCréer un boost", "§7Crée un groupe de boost", "§7puis réglez-le dans l'éditeur.");
            case "limits" -> navBar(inv, h, max, Material.LIME_DYE, "§a§lAjouter une limite",
                    "§7Prenez un item avec le curseur,", "§7puis cliquez ici.", "§8La limite = la taille du stack tenu.");
            default -> navBar(inv, h, max, Material.LIME_DYE, "§a§lAjouter",
                    "§7Prenez un item avec le curseur,", "§7puis cliquez ici.");
        }
    }

    private static Material mat(String name) {
        Material m = Material.matchMaterial(name);
        return m == null ? Material.BARRIER : m;
    }

    private ItemStack potionIcon(String id) {
        boolean blocked = plugin.admin().potionDisabled(id);
        boolean harmful = Effects.HARMFUL.contains(id);
        ItemStack i = new ItemStack(harmful ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(id));
        if (type != null) pm.setColor(type.getColor());
        pm.displayName(Msg.plain((blocked ? "§c§l" : "§a§l") + Effects.label(id) + " §8(" + id + ")"));
        pm.lore(List.of(Msg.plain(blocked ? "§c✖ Brassage bloqué" : "§a✔ Brassage autorisé"), Msg.plain(""), Msg.plain("§eClic §7: basculer")));
        pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        pm.setEnchantmentGlintOverride(!blocked);
        i.setItemMeta(pm);
        return i;
    }

    private void drawPotions(MenuHolder h, Inventory inv) {
        inv.clear();
        List<String> ids = plugin.potions().all();
        h.entries(ids);
        int max = Math.max(0, (ids.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= ids.size()) break;
            inv.setItem(n, potionIcon(ids.get(idx)));
        }
        navBar(inv, h, max, Material.PAPER, "§7Info", "§7Bloque le brassage des potions", "§7de l'effet cliqué (toutes variantes).");
    }

    private void drawCustomItems(MenuHolder h, Inventory inv) {
        inv.clear();
        List<String> ids = plugin.customItems().ids();
        h.entries(ids);
        int max = Math.max(0, (ids.size() - 1) / PAGE);
        h.page(Math.max(0, Math.min(h.page(), max)));
        for (int n = 0; n < PAGE; n++) {
            int idx = h.page() * PAGE + n;
            if (idx >= ids.size()) break;
            ItemStack it = plugin.customItems().build(ids.get(idx));
            if (it != null) inv.setItem(n, withLore(it, "", "§8ID : §f" + ids.get(idx),
                    "§eClic gauche §7: recevoir", "§eClic droit §7: détails dans le chat", "§eShift + droit §7: supprimer"));
        }
        navBar(inv, h, max, Material.PAPER, "§d§lCréer un item",
                "§7Tenez un item en main puis :", "§f/vortex citem create <id>", "§7Voir /vortex citem help");
    }

    private void drawEditor(MenuHolder h, Inventory inv) {
        Ui.frame(inv, 0, inv.getSize(), Material.GREEN_STAINED_GLASS_PANE);
        for (int s : GRID) inv.setItem(s, null);
        inv.setItem(RESULT, null);
        inv.setItem(23, Ui.button(Material.ARROW, "§7Résultat →", "§7Posez l'item à obtenir à droite"));
        inv.setItem(37, Ui.button(Material.BARRIER, "§c§lAnnuler"));
        inv.setItem(40, Ui.button(h.shapeless() ? Material.CHEST : Material.CRAFTING_TABLE,
                h.shapeless() ? "§e§lSans forme" : "§e§lForme fixe", "§7Clic pour changer", "",
                "§8Les ingrédients comptent par type de bloc/item."));
        inv.setItem(43, Ui.button(Material.LIME_DYE, "§a§lSauvegarder", "§7Enregistre le craft « " + h.name() + " »"));
    }

    // ═════════════ Clics ═════════════

    private static boolean editable(int raw) {
        if (raw == RESULT) return true;
        for (int g : GRID) if (g == raw) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h) || !h.type().admin()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();

        if (h.type() == MenuHolder.Type.CRAFT_EDIT) {
            boolean inTop = raw >= 0 && raw < top.getSize();
            if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) { e.setCancelled(true); return; }
            if (inTop && !editable(raw)) {
                e.setCancelled(true);
                editorButton(p, h, raw);
            }
            return; // cases éditables et inventaire du joueur : comportement normal
        }

        if (e.getClickedInventory() != top) {
            // Inventaire du joueur : on laisse prendre un item avec le curseur (ajout aux listes), sans shift-clic ni double-clic
            if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        switch (h.type()) {
            case ADMIN -> hubClick(p, raw);
            case ADMIN_LIST -> listClick(p, h, e);
            case ADMIN_POTIONS -> potionClick(p, h, raw);
            case CITEMS -> customItemClick(p, h, e);
            case BOOST_EDIT -> boostEditClick(p, h, e);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h) || !h.type().admin()) return;
        if (h.type() == MenuHolder.Type.CRAFT_EDIT) {
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize() && !editable(raw)) { e.setCancelled(true); return; }
            }
        } else {
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize()) { e.setCancelled(true); return; }
            }
        }
    }

    private void hubClick(Player p, int slot) {
        switch (slot) {
            case 10 -> openList(p, "items");
            case 11 -> openList(p, "crafts");
            case 12 -> openPotions(p);
            case 13 -> openList(p, "limits");
            case 14 -> openList(p, "recipes");
            case 15 -> openCustomItems(p);
            case 16 -> openList(p, "boosts");
            case 31 -> plugin.menu().openHub(p);
            default -> { }
        }
    }

    /** Navigation commune (45 = précédent, 51 = suivant, 53 = retour). Renvoie true si traitée. */
    private boolean nav(Player p, MenuHolder h, int slot) {
        switch (slot) {
            case 45 -> { h.page(h.page() - 1); draw(p, h); return true; }
            case 51 -> { h.page(h.page() + 1); draw(p, h); return true; }
            case 53 -> { openHub(p); return true; }
            default -> { return false; }
        }
    }

    private void listClick(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot >= PAGE) {
            if (nav(p, h, slot)) return;
            if (slot == 49) addClick(p, h, e.getCursor());
            return;
        }
        int idx = h.page() * PAGE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        String key = h.entries().get(idx);
        AdminManager a = plugin.admin();

        switch (h.kind()) {
            case "items" -> { a.toggle(a.items(), mat(key)); draw(p, h); }
            case "crafts" -> { a.toggle(a.crafts(), mat(key)); draw(p, h); }
            case "boosts" -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    plugin.boosts().delete(key);
                    Msg.prefixed(p, "§7Boost §f" + key + " §7supprimé.");
                    draw(p, h);
                } else {
                    openBoostEditor(p, key);
                }
            }
            case "limits" -> {
                Material m = mat(key);
                int cur = a.limitOf(m);
                if (e.isShiftClick()) a.setLimit(m, 0);
                else if (e.isLeftClick()) a.setLimit(m, cur + 1);
                else if (e.isRightClick()) a.setLimit(m, Math.max(1, cur - 1));
                draw(p, h);
            }
            default -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    plugin.customRecipes().delete(key);
                    Msg.prefixed(p, "§7Craft §f" + key + " §7supprimé.");
                    draw(p, h);
                } else if (e.isLeftClick() && plugin.customRecipes().result(key) != null) {
                    p.getInventory().addItem(plugin.customRecipes().result(key).clone())
                            .values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
                }
            }
        }
    }

    private void addClick(Player p, MenuHolder h, ItemStack cursor) {
        if (h.kind().equals("boosts")) {
            int n = plugin.boosts().all().size() + 1;
            while (plugin.boosts().group("boost_" + n) != null) n++;
            plugin.boosts().create("boost_" + n);
            openBoostEditor(p, "boost_" + n);
            return;
        }
        if (h.kind().equals("recipes")) {
            openEditor(p, "craft_" + (plugin.customRecipes().names().size() + 1));
            return;
        }
        if (cursor == null || cursor.getType().isAir()) cursor = p.getInventory().getItemInMainHand(); // à défaut : l'item en main
        if (cursor == null || cursor.getType().isAir()) {
            Msg.prefixed(p, "§7Prenez un item avec le curseur (ou tenez-le en main), puis cliquez sur le bouton d'ajout.");
            return;
        }
        AdminManager a = plugin.admin();
        Material m = cursor.getType();
        switch (h.kind()) {
            case "items" -> { if (!a.items().contains(m)) a.toggle(a.items(), m); }
            case "crafts" -> { if (!a.crafts().contains(m)) a.toggle(a.crafts(), m); }
            case "limits" -> a.setLimit(m, Math.max(1, cursor.getAmount()));
            default -> { }
        }
        draw(p, h);
    }

    private void potionClick(Player p, MenuHolder h, int slot) {
        if (slot >= PAGE) { nav(p, h, slot); return; }
        int idx = h.page() * PAGE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        plugin.admin().togglePotion(h.entries().get(idx));
        draw(p, h);
    }

    private void customItemClick(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot >= PAGE) { nav(p, h, slot); return; }
        int idx = h.page() * PAGE + slot;
        if (idx < 0 || idx >= h.entries().size()) return;
        String id = h.entries().get(idx);

        if (e.isShiftClick() && e.isRightClick()) {
            plugin.customItems().delete(id);
            Msg.prefixed(p, "§7Item §f" + id + " §7supprimé.");
            draw(p, h);
        } else if (e.isRightClick()) {
            p.closeInventory();
            Msg.send(p, "§d§l" + id);
            for (String act : CustomItems.ACTIVATORS) {
                List<String> l = plugin.customItems().actions(id, act);
                if (!l.isEmpty()) Msg.send(p, "§7" + act + " §8: §f" + String.join("§7 | §f", l));
            }
        } else if (e.isLeftClick()) {
            ItemStack it = plugin.customItems().build(id);
            if (it != null) p.getInventory().addItem(it).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }

    private void editorButton(Player p, MenuHolder h, int slot) {
        Inventory inv = h.getInventory();
        switch (slot) {
            case 37 -> p.closeInventory();
            case 40 -> { h.shapeless(!h.shapeless()); drawEditorButtons(h, inv); }
            case 43 -> {
                Material[] grid = new Material[9];
                for (int i = 0; i < 9; i++) {
                    ItemStack s = inv.getItem(GRID[i]);
                    grid[i] = s == null || s.getType().isAir() ? null : s.getType();
                }
                String err = plugin.customRecipes().save(h.name(), grid, inv.getItem(RESULT), h.shapeless());
                if (err != null) { Msg.prefixed(p, "§c" + err); return; }
                Msg.prefixed(p, "§aCraft §f" + h.name() + " §asauvegardé.");
                p.closeInventory();
            }
            default -> { }
        }
    }

    // ═════════════ Éditeur de boost ═════════════

    public void openBoostEditor(Player p, String id) {
        if (!allowed(p)) return;
        if (plugin.boosts().group(id) == null) { Msg.prefixed(p, "§cBoost introuvable."); return; }
        MenuHolder h = new MenuHolder(MenuHolder.Type.BOOST_EDIT);
        h.name(id);
        Inventory inv = Bukkit.createInventory(h, 36, Msg.c(t("Boost « " + id + " »")));
        h.bind(inv);
        draw(p, h);
        p.openInventory(inv);
    }

    private ItemStack boostIcon(String id) {
        BoostManager.Group g = plugin.boosts().group(id);
        if (g == null) return Ui.button(Material.BARRIER, "§c" + id);
        return Ui.button(Material.EXPERIENCE_BOTTLE, "§a§l" + g.id(), Ui.bar(),
                "§7Permission : §f" + g.permission(),
                "§7XP §fx" + g.xp() + " §8| §7Drops §fx" + g.drops() + " §8| §7Dégâts §fx" + g.damage(),
                "§7Potions §fx" + g.potionDuration() + " §8| §7Cooldowns §f-" + Math.round(g.cooldownReduction() * 100) + "%",
                "§7Vente §f+" + Math.round(g.sellBonus() * 100) + "% §8| §7Achat §f-" + Math.round(g.buyDiscount() * 100) + "%",
                "§7Effets : §f" + g.effects().size(), "",
                "§eClic §7: modifier", "§eShift + droit §7: supprimer");
    }

    private static String statKey(int slot) {
        return switch (slot) {
            case 10 -> "xp";
            case 11 -> "drops";
            case 12 -> "damage";
            case 13 -> "potion";
            case 14 -> "cooldown";
            case 19 -> "sell";
            case 20 -> "discount";
            default -> null;
        };
    }

    private void drawBoostEditor(MenuHolder h, Inventory inv) {
        Ui.frame(inv, 0, inv.getSize(), Material.LIME_STAINED_GLASS_PANE);
        BoostManager.Group g = plugin.boosts().group(h.name());
        if (g == null) { inv.setItem(13, Ui.button(Material.BARRIER, "§cBoost introuvable")); return; }
        String hint1 = "§eClic gauche §7: +0.05", hint2 = "§eClic droit §7: -0.05", hint3 = "§eShift §7: pas de 0.25";
        inv.setItem(10, Ui.button(Material.EXPERIENCE_BOTTLE, "§a§lXP §7: §fx" + g.xp(), Ui.bar(), "§7Multiplicateur d'expérience", "", hint1, hint2, hint3));
        inv.setItem(11, Ui.button(Material.ROTTEN_FLESH, "§a§lDrops de mobs §7: §fx" + g.drops(), Ui.bar(), "§7Multiplicateur de butin", "", hint1, hint2, hint3));
        inv.setItem(12, Ui.button(Material.IRON_SWORD, "§a§lDégâts §7: §fx" + g.damage(), Ui.bar(), "§7Multiplicateur de dégâts infligés", "", hint1, hint2, hint3));
        inv.setItem(13, Ui.button(Material.POTION, "§a§lDurée des potions §7: §fx" + g.potionDuration(), Ui.bar(), "§7Potions bénéfiques uniquement", "", hint1, hint2, hint3));
        inv.setItem(14, Ui.button(Material.CLOCK, "§a§lCooldowns §7: §f-" + Math.round(g.cooldownReduction() * 100) + "%", Ui.bar(),
                "§7Réduction des cooldowns (max 90%)", "", hint1, hint2, hint3));
        inv.setItem(19, Ui.button(Material.EMERALD, "§a§lBonus de vente §7: §f+" + Math.round(g.sellBonus() * 100) + "%", Ui.bar(),
                "§7Majore les prix de vente à la boutique", "", hint1, hint2, hint3));
        inv.setItem(20, Ui.button(Material.GOLD_NUGGET, "§a§lRéduction d'achat §7: §f-" + Math.round(g.buyDiscount() * 100) + "%", Ui.bar(),
                "§7Baisse les prix d'achat à la boutique (max 90%)", "", hint1, hint2, hint3));
        inv.setItem(16, Ui.button(Material.NAME_TAG, "§e§lPermission", Ui.bar(), "§f" + g.permission(), "",
                "§8/vortex boost perm " + g.id() + " <permission>"));
        inv.setItem(22, Ui.button(Material.BREWING_STAND, "§b§lEffets permanents", Ui.bar(),
                g.effects().isEmpty() ? "§7Aucun" : "§f" + g.effects().size() + " effet(s)", "",
                "§8/vortex boost effect " + g.id() + " add <effet> <niveau>"));
        inv.setItem(31, Ui.button(Material.ARROW, "§7« Boosts"));
    }

    private void boostEditClick(Player p, MenuHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 31) { openList(p, "boosts"); return; }
        String key = statKey(slot);
        BoostManager.Group g = plugin.boosts().group(h.name());
        if (key == null || g == null) return;
        double cur = switch (key) {
            case "xp" -> g.xp();
            case "drops" -> g.drops();
            case "damage" -> g.damage();
            case "potion" -> g.potionDuration();
            case "sell" -> g.sellBonus();
            case "discount" -> g.buyDiscount();
            default -> g.cooldownReduction();
        };
        double step = e.isShiftClick() ? 0.25 : 0.05;
        double delta = e.isLeftClick() ? step : e.isRightClick() ? -step : 0;
        if (delta == 0) return;
        plugin.boosts().setStat(h.name(), key, Math.round(Math.max(0, cur + delta) * 100.0) / 100.0);
        draw(p, h);
    }

    private void drawEditorButtons(MenuHolder h, Inventory inv) {
        inv.setItem(40, Ui.button(h.shapeless() ? Material.CHEST : Material.CRAFTING_TABLE,
                h.shapeless() ? "§e§lSans forme" : "§e§lForme fixe", "§7Clic pour changer", "",
                "§8Les ingrédients comptent par type de bloc/item."));
    }

    /** Rend les items posés dans l'éditeur. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h) || h.type() != MenuHolder.Type.CRAFT_EDIT) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = h.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int g : GRID) slots.add(g);
        slots.add(RESULT);
        for (int s : slots) {
            ItemStack it = inv.getItem(s);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(s, null);
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }
}
