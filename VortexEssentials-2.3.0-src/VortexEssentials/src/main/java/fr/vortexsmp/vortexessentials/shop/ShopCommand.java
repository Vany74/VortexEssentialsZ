package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /shop (alias /boutique, /market) : menus, vente, annonces joueurs et gestion de la boutique du serveur. */
public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final VortexEssentials plugin;

    public ShopCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private ShopManager shop() { return plugin.shop(); }

    private static double num(String s) throws NumberFormatException { return Double.parseDouble(s.replace(',', '.')); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("sell")) { // /sell (alias /vendre) : menu de vente
            if (!s.hasPermission("vortexessentials.shop") || !plugin.getConfig().getBoolean("shop.enabled", true)) {
                Msg.send(s, "§cPermission refusée.");
            } else if (!(s instanceof Player p)) {
                Msg.send(s, "§cCommande réservée aux joueurs.");
            } else if (!plugin.money().available()) {
                Msg.send(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie.");
            } else {
                plugin.shopMenus().openSellBox(p);
            }
            return true;
        }
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("manage")) {
            if (!s.hasPermission("vortexessentials.shop.admin")) { Msg.send(s, "§cPermission refusée."); return true; }
            manage(s, Arrays.copyOfRange(a, 1, a.length));
            return true;
        }
        if (!s.hasPermission("vortexessentials.shop")) { Msg.send(s, "§cPermission refusée."); return true; }
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) { Msg.send(s, "§cLa boutique est désactivée."); return true; }
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return true; }

        switch (sub) {
            case "menu" -> plugin.shopMenus().openHub(p);
            case "admin", "server", "serveur" -> plugin.shopMenus().openCats(p);
            case "player", "joueurs" -> plugin.shopMenus().openPlayerCats(p);
            case "mine", "annonces" -> plugin.shopMenus().openMine(p);
            case "balance", "solde" -> {
                if (!plugin.money().available()) Msg.send(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie.");
                else Msg.prefixed(p, "§7Solde : §f" + plugin.money().format(plugin.money().balance(p)));
            }
            case "sell", "vendre" -> {
                if (a.length < 2) { Msg.send(p, "§e/shop sell <prix> §7: met en vente l'item tenu (tout le stack) dans la boutique des joueurs"); return true; }
                if (!p.hasPermission("vortexessentials.shop.list")) { Msg.send(p, "§cPermission refusée."); return true; }
                try {
                    String err = plugin.shopMenus().list(p, num(a[1]));
                    if (err != null) Msg.prefixed(p, "§c" + err);
                } catch (NumberFormatException e) {
                    Msg.send(p, "§cNombre invalide.");
                }
            }
            case "sellhand" -> {
                if (!plugin.money().available()) { Msg.send(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie."); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                ShopManager.Entry e = hand.getType().isAir() ? null : shop().sellIndex().get(shop().keyOf(hand));
                if (e == null || !shop().matches(e, hand)) { Msg.prefixed(p, "§cCet item n'est pas racheté (ou il est modifié)."); return true; }
                plugin.shopMenus().sell(p, e, hand.getAmount());
            }
            case "sellall" -> plugin.shopMenus().sellAll(p);
            case "add", "setprice", "remove" -> handItem(p, sub, a);
            case "editor", "editeur" -> plugin.shopEditor().open(p);
            case "box", "vente" -> { if (plugin.money().available()) plugin.shopMenus().openSellBox(p); else Msg.send(p, "§cAucune économie détectée : installez VaultUnlocked et un plugin d'économie."); }
            default -> help(s);
        }
        return true;
    }

    /** /shop add <achat> [vente] [catégorie] · /shop setprice <achat> <vente> · /shop remove : sur l'item tenu en main. */
    private void handItem(Player p, String sub, String[] a) {
        if (!p.hasPermission("vortexessentials.shop.admin")) { Msg.send(p, "§cPermission refusée."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Msg.send(p, "§cTenez l'item concerné en main."); return; }
        ShopManager sh = shop();
        String key = sh.keyOf(hand);
        String cat = sh.categoryOf(key);
        try {
            switch (sub) {
                case "add" -> {
                    if (a.length < 2) { Msg.send(p, "§e/shop add <achat> [vente] [catégorie] §7(item en main ; -1 = pas d'achat / de vente)"); return; }
                    double buy = num(a[1]);
                    double sell = a.length > 2 ? num(a[2]) : Math.round(buy * 0.3 * 100.0) / 100.0;
                    if (cat != null) {
                        ShopManager.Entry e = sh.entry(cat, key);
                        e.buy = buy <= 0 ? -1 : buy;
                        e.sell = sell <= 0 ? -1 : sell;
                        sh.save();
                        Msg.prefixed(p, "§7Prix mis à jour : §f" + sh.displayName(key) + " §7(achat §f" + e.buy + "§7 / vente §f" + e.sell + "§7)");
                        return;
                    }
                    String target = a.length > 3 ? a[3] : sh.autoCategory(hand.getType());
                    String err = target == null ? "Aucune catégorie." : sh.addEntry(target, key, buy, sell);
                    Msg.prefixed(p, err != null ? "§c" + err : "§aAjouté : §f" + sh.displayName(key) + " §7dans §f" + target + " §7(achat §f" + buy + "§7 / vente §f" + sell + "§7)");
                }
                case "setprice" -> {
                    if (a.length < 3 || cat == null) { Msg.send(p, cat == null ? "§cCet item n'est pas dans la boutique (/shop add)." : "§e/shop setprice <achat> <vente>"); return; }
                    ShopManager.Entry e = sh.entry(cat, key);
                    e.buy = num(a[1]) <= 0 ? -1 : num(a[1]);
                    e.sell = num(a[2]) <= 0 ? -1 : num(a[2]);
                    sh.save();
                    Msg.prefixed(p, "§7Prix mis à jour : achat §f" + e.buy + "§7 / vente §f" + e.sell);
                }
                default -> {
                    if (cat == null) { Msg.send(p, "§cCet item n'est pas dans la boutique."); return; }
                    Msg.prefixed(p, sh.removeEntry(cat, key) ? "§7Item retiré de la boutique." : "§cImpossible de retirer cet item.");
                }
            }
        } catch (NumberFormatException e) {
            Msg.send(p, "§cNombre invalide.");
        }
    }

    private void help(CommandSender s) {
        Msg.send(s, "§a§lShop §7— boutique du serveur et des joueurs");
        Msg.send(s, "§e/shop §7: ouvre la boutique");
        Msg.send(s, "§e/sell §7: menu de vente (déposez des items, vendus à la fermeture)");
        Msg.send(s, "§e/shop sellhand §7: vend l'item en main au serveur");
        Msg.send(s, "§e/shop sellall §7: vend tout ce qui est vendable");
        Msg.send(s, "§e/shop sell <prix> §7: met en vente l'item tenu aux joueurs");
        Msg.send(s, "§e/shop balance §7: votre solde");
        if (s.hasPermission("vortexessentials.shop.admin")) {
            Msg.send(s, "§e/shop editor §7: éditeur en jeu (item en main) · §e/shop add <achat> [vente] §7· §e/shop setprice <achat> <vente> §7· §e/shop remove");
            Msg.send(s, "§e/shop manage §7: category | add | remove | price | toggle | reload");
        }
    }

    // ═════════════ /shop manage ═════════════

    /** Clé d'entrée depuis un argument : "hand" (item tenu), nom de matériau, vortex:<id> ou citem:<id>. */
    private String key(CommandSender s, String arg) {
        if (arg.equalsIgnoreCase("hand")) {
            if (!(s instanceof Player p) || p.getInventory().getItemInMainHand().getType().isAir()) {
                Msg.send(s, "§cTenez un item en main.");
                return null;
            }
            return shop().keyOf(p.getInventory().getItemInMainHand());
        }
        String k = shop().normalizeKey(arg);
        if (k == null) Msg.send(s, "§cItem inconnu : §f" + arg);
        return k;
    }

    private void manage(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        ShopManager sh = shop();
        try {
            switch (sub) {
                case "category" -> category(s, a);
                case "add" -> {
                    if (a.length < 4) { Msg.send(s, "§e/shop manage add <catégorie> <achat> <vente> [item|hand]  §7(-1 = non achetable / non vendable)"); return; }
                    String k = key(s, a.length > 4 ? a[4] : "hand");
                    if (k == null) return;
                    String err = sh.addEntry(a[1], k, num(a[2]), num(a[3]));
                    Msg.prefixed(s, err != null ? "§c" + err : "§aAjouté : §f" + sh.displayName(k) + " §7dans §f" + a[1]);
                }
                case "remove" -> {
                    if (a.length < 3) { Msg.send(s, "§e/shop manage remove <catégorie> <item|hand>"); return; }
                    String k = key(s, a[2]);
                    if (k != null) Msg.prefixed(s, sh.removeEntry(a[1], k) ? "§7Item retiré." : "§cItem ou catégorie introuvable.");
                }
                case "price" -> {
                    if (a.length < 5) { Msg.send(s, "§e/shop manage price <catégorie> <item|hand> <buy|sell> <valeur>"); return; }
                    String k = key(s, a[2]);
                    if (k == null) return;
                    ShopManager.Entry e = sh.entry(a[1], k);
                    if (e == null) { Msg.send(s, "§cItem ou catégorie introuvable."); return; }
                    double v = num(a[4]);
                    if (a[3].equalsIgnoreCase("buy")) e.buy = v <= 0 ? -1 : v;
                    else if (a[3].equalsIgnoreCase("sell")) e.sell = v <= 0 ? -1 : v;
                    else { Msg.send(s, "§cbuy ou sell."); return; }
                    sh.save();
                    Msg.prefixed(s, "§7Prix mis à jour : achat §f" + e.buy + " §7/ vente §f" + e.sell);
                }
                case "toggle" -> {
                    if (a.length < 3) { Msg.send(s, "§e/shop manage toggle <catégorie> <item|hand>"); return; }
                    String k = key(s, a[2]);
                    if (k == null) return;
                    ShopManager.Entry e = sh.entry(a[1], k);
                    if (e == null) { Msg.send(s, "§cItem ou catégorie introuvable."); return; }
                    e.enabled = !e.enabled;
                    sh.save();
                    Msg.prefixed(s, "§f" + sh.displayName(k) + " §7: " + (e.enabled ? "§aactivé" : "§cdésactivé"));
                }
                case "import" -> {
                    int[] r = sh.mergeDefaults();
                    Msg.prefixed(s, "§aCatalogue importé : §f" + r[0] + " §acatégorie(s) et §f" + r[1] + " §aitem(s) ajoutés (vos prix sont conservés). §f" + r[2] + " §aitem(s) rendus non revendables.");
                }
                case "purge" -> {
                    if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                        Msg.send(s, "§cCeci retire de votre boutique tous les items absents du catalogue par défaut. Refaites la commande avec « confirm ».");
                        return;
                    }
                    int[] r = sh.purgeToDefaults();
                    Msg.prefixed(s, "§aBoutique purgée : §f" + r[0] + " §aitem(s) retirés, §f" + r[1] + " §acatégorie(s) vides supprimées.");
                }
                case "reset" -> {
                    if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                        Msg.send(s, "§cCeci remplace TOUTE la boutique par le catalogue par défaut. Refaites la commande avec « confirm ».");
                        return;
                    }
                    String err = sh.resetToDefault();
                    Msg.prefixed(s, err != null ? "§c" + err : "§aBoutique réinitialisée (ancienne version : shop.yml.bak).");
                }
                case "reload" -> {
                    sh.load();
                    plugin.playerShop().load();
                    Msg.prefixed(s, "§7Boutique rechargée.");
                }
                default -> {
                    Msg.send(s, "§a§l/shop manage");
                    Msg.send(s, "§e category §7create <id> <MATERIAL> <nom...> | delete <id> | toggle <id> | rename <id> <nom...> | icon <id> <MATERIAL> | list");
                    Msg.send(s, "§e add <catégorie> <achat> <vente> [item|hand] §7: ajoute un item (matériau, vortex:<id> ou citem:<id>)");
                    Msg.send(s, "§e remove|toggle <catégorie> <item|hand> §7, §e price <catégorie> <item|hand> <buy|sell> <valeur>");
                    Msg.send(s, "§e import §7: ajoute les nouveautés du catalogue sans toucher à vos prix  §8|  §e purge confirm §7: retire les items absents du catalogue  §8|  §e reset confirm §7: remplace tout");
                    Msg.send(s, "§8Édition graphique : /shop admin puis « Mode édition ». -1 = non achetable / non vendable.");
                }
            }
        } catch (NumberFormatException e) {
            Msg.send(s, "§cNombre invalide.");
        }
    }

    private void category(CommandSender s, String[] a) {
        ShopManager sh = shop();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "create" -> {
                if (a.length < 5) { Msg.send(s, "§e/shop manage category create <id> <MATERIAL> <nom...>"); return; }
                Material m = Material.matchMaterial(a[3]);
                if (m == null || !m.isItem()) { Msg.send(s, "§cMatériau inconnu : §f" + a[3]); return; }
                String err = sh.createCategory(a[2], m, String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                Msg.prefixed(s, err != null ? "§c" + err : "§aCatégorie créée.");
            }
            case "delete" -> {
                if (a.length < 3) { Msg.send(s, "§e/shop manage category delete <id>"); return; }
                Msg.prefixed(s, sh.deleteCategory(a[2]) ? "§7Catégorie supprimée." : "§cCatégorie introuvable.");
            }
            case "toggle" -> {
                ShopManager.Category c = a.length > 2 ? sh.category(a[2]) : null;
                if (c == null) { Msg.send(s, "§cCatégorie introuvable."); return; }
                c.enabled = !c.enabled;
                sh.save();
                Msg.prefixed(s, "§f" + c.name + " §7: " + (c.enabled ? "§aactivée" : "§cdésactivée"));
            }
            case "rename" -> {
                ShopManager.Category c = a.length > 3 ? sh.category(a[2]) : null;
                if (c == null) { Msg.send(s, "§e/shop manage category rename <id> <nom...>"); return; }
                c.name = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                sh.save();
                Msg.prefixed(s, "§7Catégorie renommée.");
            }
            case "icon" -> {
                ShopManager.Category c = a.length > 3 ? sh.category(a[2]) : null;
                Material m = a.length > 3 ? Material.matchMaterial(a[3]) : null;
                if (c == null || m == null || !m.isItem()) { Msg.send(s, "§e/shop manage category icon <id> <MATERIAL>"); return; }
                c.icon = m;
                sh.save();
                Msg.prefixed(s, "§7Icône mise à jour.");
            }
            default -> {
                for (ShopManager.Category c : sh.categories()) {
                    Msg.send(s, (c.enabled ? "§a" : "§c") + c.id + " §7— " + c.name + " §8(" + c.items.size() + " items)");
                }
            }
        }
    }

    // ═════════════ Complétion ═════════════

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("sell")) return List.of();
        List<String> out = new ArrayList<>();
        boolean adm = s.hasPermission("vortexessentials.shop.admin");
        if (a.length == 1) {
            out.addAll(List.of("admin", "player", "mine", "balance", "sell", "sellhand", "sellall", "help"));
            if (adm) out.add("manage");
        } else if (adm && a[0].equalsIgnoreCase("manage")) {
            if (a.length == 2) out.addAll(List.of("category", "add", "remove", "price", "toggle", "import", "purge", "reset", "reload"));
            else if (a.length == 3 && a[1].equalsIgnoreCase("category")) out.addAll(List.of("create", "delete", "toggle", "rename", "icon", "list"));
            else if (a.length == 3 || (a.length == 4 && a[1].equalsIgnoreCase("category"))) shop().categories().forEach(cat -> out.add(cat.id));
            else if (a.length == 4 && a[1].equalsIgnoreCase("price")) out.add("hand");
            else if (a.length == 5 && a[1].equalsIgnoreCase("price")) out.addAll(List.of("buy", "sell"));
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
