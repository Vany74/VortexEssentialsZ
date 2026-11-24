package fr.vortexsmp.vortexessentials.recipe;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.admin.AdminDefaults;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Éditeur de recettes en jeu (/vortex recipes edit) pour les items Vortex ET pour les crafts personnalisés (pommes d'or,
 * trident, totem...) : on choisit une recette, puis on compose la grille 3×3 en cliquant sur les items de son propre
 * inventaire (copiés, rien n'est consommé). Items Vortex : écrit dans recipes-custom.yml (appliqué par-dessus
 * recipes.yml, jamais écrasé). Crafts personnalisés : enregistrés directement dans leur fichier.
 */
public final class RecipeEditor implements Listener {
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private static final class Holder implements InventoryHolder {
        final VortexItem item;     // item Vortex en cours d'édition
        final String custom;       // craft personnalisé en cours d'édition
        final boolean list;
        final int page;
        final ItemStack[] grid = new ItemStack[9];
        final List<Object> shown = new ArrayList<>();
        Inventory inv;
        Holder(boolean list, VortexItem item, String custom, int page) { this.list = list; this.item = item; this.custom = custom; this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public RecipeEditor(VortexEssentials plugin) {
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

    // ═════════════ Liste : items Vortex puis crafts personnalisés ═════════════

    public void openList(Player p, int page) {
        Holder h = new Holder(true, null, null, Math.max(0, page));
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§bÉditeur de recettes"));
        h.inv = inv;
        List<Object> all = new ArrayList<>(List.of(VortexItem.values()));
        all.addAll(plugin.customRecipes().names());
        int from = h.page * 45;
        RecipeManager rm = plugin.recipes();
        for (int i = 0; i < 45 && from + i < all.size(); i++) {
            Object o = all.get(from + i);
            h.shown.add(o);
            if (o instanceof VortexItem v) {
                String state = rm.isOverridden(v) ? "§eRecette modifiée en jeu" : rm.def(v) != null ? "§7Recette par défaut" : rm.forgeSource(v) != null ? "§7Obtenu par forge" : "§cAucune recette";
                inv.setItem(i, ItemUtil.icon(v, "", state, "§eClic §7: modifier la recette"));
            } else {
                String name = (String) o;
                ItemStack res = plugin.customRecipes().result(name);
                ItemStack icon = res == null ? new ItemStack(Material.CRAFTING_TABLE) : res.clone();
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(Msg.c("§bCraft personnalisé : §f" + name));
                for (String l : plugin.craftLimits().lines(name)) lore.add(Msg.c(l));
                lore.add(Msg.c("§eClic §7: modifier la recette"));
                meta.lore(lore);
                icon.setItemMeta(meta);
                inv.setItem(i, icon);
            }
        }
        inv.setItem(45, h.page > 0 ? button(Material.ARROW, "§7« Page précédente") : null);
        inv.setItem(49, button(Material.BARRIER, "§7Fermer"));
        inv.setItem(53, from + 45 < all.size() ? button(Material.ARROW, "§7Page suivante »") : null);
        p.openInventory(inv);
    }

    // ═════════════ Édition ═════════════

    private void prefill(Holder h) {
        if (h.custom != null) {
            List<String> g = plugin.customRecipes().grid(h.custom);
            for (int i = 0; i < 9 && i < g.size(); i++) {
                Material m = g.get(i).isEmpty() ? null : Material.matchMaterial(g.get(i));
                if (m != null) h.grid[i] = new ItemStack(m);
            }
            return;
        }
        RecipeManager.Def d = plugin.recipes().def(h.item);
        if (d == null) return;
        if (d.shaped()) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    char ch = ' ';
                    if (r < d.shape().size() && c < d.shape().get(r).length()) ch = d.shape().get(r).charAt(c);
                    if (ch != ' ') h.grid[r * 3 + c] = token(d, d.ingredients().get(ch));
                }
            }
        } else {
            for (int i = 0; i < d.shapeless().size() && i < 9; i++) h.grid[i] = token(d, d.shapeless().get(i));
        }
    }

    private static ItemStack token(RecipeManager.Def d, String tok) {
        Material m = tok.startsWith("#") ? null : Material.matchMaterial(tok);
        if (m != null && d.requireCustom().containsKey(m)) return ItemUtil.create(d.requireCustom().get(m));
        return RecipeManager.display(tok);
    }

    public void openEdit(Player p, VortexItem v, ItemStack[] grid) { openEditor(p, new Holder(false, v, null, 0), grid); }

    public void openEditCustom(Player p, String name, ItemStack[] grid) { openEditor(p, new Holder(false, null, name, 0), grid); }

    private void openEditor(Player p, Holder h, ItemStack[] grid) {
        if (grid != null) System.arraycopy(grid, 0, h.grid, 0, 9); else prefill(h);
        String title = h.custom != null ? "§bCraft : " + h.custom : "§bRecette : " + h.item.displayName();
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(title));
        h.inv = inv;
        draw(h);
        p.openInventory(inv);
    }

    private void draw(Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < 9; i++) inv.setItem(GRID[i], h.grid[i] == null ? null : h.grid[i].clone());
        inv.setItem(4, button(Material.BOOK, "§bComment faire", "§7Cliquez un item de §fvotre inventaire §7pour le copier",
                "§7dans la première case libre de la grille.", "§7Cliquez une case de la grille pour la vider.",
                h.custom != null ? "§7Le résultat du craft ne change pas." : "§7Les items Vortex posés comptent comme « votre version »."));
        if (h.custom != null) {
            ItemStack res = plugin.customRecipes().result(h.custom);
            inv.setItem(24, res == null ? button(Material.BARRIER, "§cRésultat introuvable") : res.clone());
            inv.setItem(39, button(Material.ORANGE_DYE, "§6Réappliquer les crafts par défaut", "§7Recrée les crafts fournis par le plugin", "§7(pommes d'or, trident, totem...).", "§cÉcrase vos changements sur ces crafts."));
            inv.setItem(41, button(Material.RED_DYE, "§cSupprimer ce craft", "§cShift + clic §7pour confirmer."));
        } else {
            inv.setItem(24, ItemUtil.icon(h.item));
            inv.setItem(39, button(Material.ORANGE_DYE, "§6Revenir à la recette par défaut", "§7Supprime votre modification pour cet item."));
            inv.setItem(41, button(Material.RED_DYE, "§cDésactiver cette recette", "§7L'item ne se craftera plus (reste donnable)."));
        }
        var cl = plugin.craftLimits();
        String lk = limitKey(h);
        inv.setItem(32, button(Material.EXPERIENCE_BOTTLE, "§6Limite sur le serveur : §f" + (cl.maxServer(lk) == 0 ? "illimitée" : String.valueOf(cl.maxServer(lk))),
                "§7Crafts possibles sur tout le serveur.", "§eClic gauche §7: +1  §eClic droit §7: -1", "§eShift §7: ±10  (0 = illimité)"));
        inv.setItem(34, button(Material.PLAYER_HEAD, "§6Limite par joueur : §f" + (cl.maxPlayer(lk) == 0 ? "illimitée" : String.valueOf(cl.maxPlayer(lk))),
                "§7Crafts possibles par joueur.", "§eClic gauche §7: +1  §eClic droit §7: -1", "§eShift §7: ±10  (0 = illimité)"));
        inv.setItem(35, button(Material.BARRIER, "§7Remettre le compteur à zéro", "§7Fabrications déjà comptées : §f" + cl.crafted(lk)));
        if (h.custom != null) {
            int secs = plugin.rituals().seconds(h.custom);
            inv.setItem(33, button(Material.ENCHANTING_TABLE, "§5Rituel : §f" + (secs == 0 ? "désactivé" : secs + " s"),
                    "§7Après le craft, l'item apparaît à la fin du rituel.", "§eClic gauche §7: +1 s  §eClic droit §7: -1 s", "§eShift §7: ±10  (0 = pas de rituel)"));
        }
        inv.setItem(37, button(Material.LIME_DYE, "§aEnregistrer la recette", h.custom != null ? "§7Met à jour le craft et le recharge." : "§7Écrit dans recipes-custom.yml et recharge."));
        inv.setItem(43, button(Material.ARROW, "§7« Liste"));
    }

    private static String limitKey(Holder h) { return h.custom != null ? h.custom : "item:" + h.item.id(); }

    private void saveCustom(Player p, Holder h) {
        Material[] mats = new Material[9];
        for (int i = 0; i < 9; i++) mats[i] = h.grid[i] == null ? null : h.grid[i].getType();
        String err = plugin.customRecipes().save(h.custom, mats, plugin.customRecipes().result(h.custom), plugin.customRecipes().shapeless(h.custom));
        Msg.prefixed(p, err != null ? "§c" + err : "§aCraft §f" + h.custom + " §amis à jour.");
    }

    private void save(Player p, Holder h) {
        if (h.custom != null) { saveCustom(p, h); return; }
        Map<Material, Character> letters = new LinkedHashMap<>();
        Map<String, String> custom = new LinkedHashMap<>();
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            if (h.grid[i] == null) continue;
            int r = i / 3, c = i % 3;
            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
            minC = Math.min(minC, c); maxC = Math.max(maxC, c);
        }
        if (maxR < 0) { Msg.prefixed(p, "§cLa grille est vide."); return; }
        List<String> shape = new ArrayList<>();
        Map<Character, String> ingredients = new LinkedHashMap<>();
        for (int r = minR; r <= maxR; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = minC; c <= maxC; c++) {
                ItemStack s = h.grid[r * 3 + c];
                if (s == null) { row.append(' '); continue; }
                Material m = s.getType();
                Character ch = letters.get(m);
                if (ch == null) {
                    ch = (char) ('A' + letters.size());
                    letters.put(m, ch);
                    ingredients.put(ch, m.name());
                }
                VortexItem cv = ItemUtil.get(s);
                if (cv != null) custom.put(m.name(), cv.id());
                row.append(ch);
            }
            shape.add(row.toString());
        }
        RecipeManager.Def old = plugin.recipes().def(h.item);
        String transfer = old != null && old.transferFrom() != null ? old.transferFrom().name() : null;
        try {
            plugin.recipes().writeOverride(h.item, shape, ingredients, custom, transfer);
            plugin.recipes().load();
            Msg.prefixed(p, "§aRecette de §f" + h.item.displayName() + " §aenregistrée.");
        } catch (Exception e) {
            Msg.prefixed(p, "§cErreur : " + e.getMessage());
        }
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("vortexessentials.admin")) return;
        int slot = e.getRawSlot();
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();

        if (h.list) {
            if (!top) return;
            if (slot == 45 && h.page > 0) { openList(p, h.page - 1); return; }
            if (slot == 53 && e.getCurrentItem() != null) { openList(p, h.page + 1); return; }
            if (slot == 49) { p.closeInventory(); return; }
            if (slot >= 0 && slot < h.shown.size()) {
                Object o = h.shown.get(slot);
                if (o instanceof VortexItem v) openEdit(p, v, null); else openEditCustom(p, (String) o, null);
            }
            return;
        }
        if (!top) {
            ItemStack s = e.getCurrentItem();
            if (s == null || s.getType().isAir()) return;
            for (int i = 0; i < 9; i++) {
                if (h.grid[i] == null) {
                    ItemStack copy = s.clone();
                    copy.setAmount(1);
                    h.grid[i] = copy;
                    draw(h);
                    return;
                }
            }
            Msg.prefixed(p, "§cLa grille est pleine : videz une case.");
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (GRID[i] == slot) { h.grid[i] = null; draw(h); return; }
        }
        RecipeManager rm = plugin.recipes();
        try {
            switch (slot) {
                case 37 -> save(p, h);
                case 39 -> {
                    if (h.custom != null) { AdminDefaults.apply(plugin, true); Msg.prefixed(p, "§7Crafts par défaut réappliqués."); openList(p, 0); }
                    else { rm.clearOverride(h.item); rm.load(); Msg.prefixed(p, "§7Recette par défaut rétablie."); openEdit(p, h.item, null); }
                }
                case 41 -> {
                    if (h.custom != null) {
                        if (!e.isShiftClick()) { Msg.prefixed(p, "§cShift + clic pour confirmer la suppression."); return; }
                        plugin.customRecipes().delete(h.custom);
                        Msg.prefixed(p, "§7Craft supprimé.");
                    } else {
                        rm.disableRecipe(h.item);
                        rm.load();
                        Msg.prefixed(p, "§7Recette désactivée.");
                    }
                    openList(p, 0);
                }
                case 43 -> openList(p, 0);
                case 32, 34, 35, 33 -> {
                    var cl = plugin.craftLimits();
                    String lk = limitKey(h);
                    int step = (e.isShiftClick() ? 10 : 1) * (e.getClick().isRightClick() ? -1 : 1);
                    if (slot == 32) cl.set(lk, cl.maxServer(lk) + step, cl.maxPlayer(lk));
                    else if (slot == 34) cl.set(lk, cl.maxServer(lk), cl.maxPlayer(lk) + step);
                    else if (slot == 35) cl.reset(lk);
                    else if (h.custom != null) plugin.rituals().set(h.custom, Math.max(0, plugin.rituals().seconds(h.custom) + step));
                    draw(h);
                }
                default -> { }
            }
        } catch (Exception ex) {
            Msg.prefixed(p, "§cErreur : " + ex.getMessage());
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
