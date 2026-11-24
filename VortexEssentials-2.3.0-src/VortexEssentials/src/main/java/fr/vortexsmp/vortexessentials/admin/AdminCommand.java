package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /vortex admin ... et /vortex citem ... (réservés à vortexessentials.admin). */
public final class AdminCommand {
    private final VortexEssentials plugin;

    public AdminCommand(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        Msg.send(s, "§cCommande réservée aux joueurs.");
        return null;
    }

    /** Matériau depuis un nom ou "hand". */
    private Material material(CommandSender s, String arg) {
        if (arg.equalsIgnoreCase("hand")) {
            Player p = player(s);
            if (p == null) return null;
            Material m = p.getInventory().getItemInMainHand().getType();
            if (m.isAir()) { Msg.send(s, "§cTenez un item en main."); return null; }
            return m;
        }
        Material m = Material.matchMaterial(arg);
        if (m == null) Msg.send(s, "§cMatériau inconnu : §f" + arg);
        return m;
    }

    // ═════════════ /vortex admin ═════════════

    public void admin(CommandSender s, String[] a) {
        AdminManager adm = plugin.admin();
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> { Player p = player(s); if (p != null) plugin.adminMenus().openHub(p); }
            case "disable", "enable" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex admin " + sub + " <item|craft|potion> <matériau|hand|effet>"); return; }
                boolean disable = sub.equals("disable");
                String what = a[1].toLowerCase(Locale.ROOT);
                if (what.equals("potion")) {
                    String id = a[2].toLowerCase(Locale.ROOT);
                    if (!plugin.potions().all().contains(id)) { Msg.send(s, "§cEffet inconnu : §f" + id); return; }
                    if (adm.potionDisabled(id) != disable) adm.togglePotion(id);
                    Msg.prefixed(s, "§7Brassage de §f" + id + " §7: " + (disable ? "§cbloqué" : "§aautorisé"));
                    return;
                }
                if (!what.equals("item") && !what.equals("craft")) { Msg.send(s, "§cType : item, craft ou potion."); return; }
                Material m = material(s, a[2]);
                if (m == null) return;
                var set = what.equals("item") ? adm.items() : adm.crafts();
                if (set.contains(m) != disable) adm.toggle(set, m);
                Msg.prefixed(s, "§f" + m.name().toLowerCase() + " §7(" + what + ") : " + (disable ? "§cdésactivé" : "§aréactivé"));
            }
            case "limit" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex admin limit <matériau|hand> <nombre|off>"); return; }
                Material m = material(s, a[1]);
                if (m == null) return;
                int n = 0;
                if (!a[2].equalsIgnoreCase("off")) {
                    try { n = Integer.parseInt(a[2]); } catch (NumberFormatException e) { Msg.send(s, "§cNombre invalide."); return; }
                }
                adm.setLimit(m, n);
                Msg.prefixed(s, n > 0 ? "§f" + m.name().toLowerCase() + " §7: limite §f" + n : "§f" + m.name().toLowerCase() + " §7: limite retirée");
            }
            case "craft" -> craft(s, a);
            case "defaults" -> {
                int[] r = AdminDefaults.apply(plugin, java.util.Arrays.stream(a).anyMatch(x -> x.equalsIgnoreCase("force")));
                Msg.prefixed(s, "§7Réglages par défaut appliqués : §f" + r[0] + " §7limite(s), §f" + r[1] + " §7item(s) désactivé(s), §f" + r[2] + " §7craft(s) ajouté(s).");
            }
            case "list" -> {
                Msg.send(s, "§b§lAdministration");
                Msg.send(s, "§7Items désactivés : §f" + names(adm.items().stream().map(Material::name).toList()));
                Msg.send(s, "§7Crafts désactivés : §f" + names(adm.crafts().stream().map(Material::name).toList()));
                Msg.send(s, "§7Potions bloquées : §f" + names(new ArrayList<>(adm.potions())));
                Msg.send(s, "§7Limites : §f" + names(adm.limits().entrySet().stream().map(e -> e.getKey().name() + "=" + e.getValue()).toList()));
            }
            default -> {
                Msg.send(s, "§e/vortex admin §7[menu | list | disable|enable <item|craft|potion> <x> | limit <x> <n|off> | craft create|list|delete]");
            }
        }
    }

    private static String names(List<String> l) { return l.isEmpty() ? "aucun" : String.join(", ", l); }

    private void craft(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "create" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 3) { Msg.send(s, "§e/vortex admin craft create <nom>"); return; }
                plugin.adminMenus().openEditor(p, a[2]);
            }
            case "delete" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex admin craft delete <nom>"); return; }
                Msg.prefixed(s, plugin.customRecipes().delete(a[2]) ? "§7Craft supprimé." : "§cCraft introuvable.");
            }
            default -> Msg.send(s, "§7Crafts personnalisés : §f" + names(plugin.customRecipes().names()));
        }
    }

    // ═════════════ /vortex citem ═════════════

    public void citem(CommandSender s, String[] a) {
        CustomItems ci = plugin.customItems();
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                Player p = player(s);
                if (p == null || !need(s, a, 2, "create <id>")) return;
                String err = ci.createFromHand(a[1], p.getInventory().getItemInMainHand());
                Msg.prefixed(s, err != null ? "§c" + err : "§aItem §f" + CustomItems.clean(a[1]) + " §acréé d'après l'item en main.");
            }
            case "list" -> Msg.send(s, "§d§lItems personnalisés §7: §f" + names(ci.ids()));
            case "give" -> {
                if (!need(s, a, 2, "give <id> [joueur] [qté]")) return;
                ItemStack it = ci.build(a[1]);
                if (it == null) { Msg.send(s, "§cItem introuvable."); return; }
                Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
                if (target == null) { Msg.send(s, "§cJoueur introuvable."); return; }
                int qty = 1;
                if (a.length > 3) { try { qty = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { } }
                it.setAmount(qty);
                target.getInventory().addItem(it).values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
                Msg.prefixed(s, "§a" + qty + "× §f" + CustomItems.clean(a[1]) + " §adonné à §f" + target.getName());
            }
            case "delete" -> {
                if (!need(s, a, 2, "delete <id>")) return;
                Msg.prefixed(s, ci.delete(a[1]) ? "§7Item supprimé." : "§cItem introuvable.");
            }
            case "info" -> {
                if (!need(s, a, 2, "info <id>") || !exists(s, a[1])) return;
                var sec = ci.sec(a[1]);
                Msg.send(s, "§d§l" + CustomItems.clean(a[1]) + " §7(" + sec.getString("material") + ")");
                Msg.send(s, "§7Nom : §f" + sec.getString("name", "—") + " §7Cooldown : §f" + sec.getInt("cooldown") + "s §7Consommable : §f" + sec.getBoolean("consume")
                        + " §7Permission : §f" + sec.getString("permission", "—"));
                for (String act : CustomItems.ACTIVATORS) {
                    List<String> l = ci.actions(a[1], act);
                    if (!l.isEmpty()) Msg.send(s, "§7" + act + " §8: §f" + String.join("§7 | §f", l));
                }
            }
            case "name" -> {
                if (!need(s, a, 3, "name <id> <texte avec &couleurs>") || !exists(s, a[1])) return;
                ci.setName(a[1], String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
                Msg.prefixed(s, "§7Nom mis à jour.");
            }
            case "lore" -> {
                if (!need(s, a, 3, "lore add <id> <texte> | lore clear <id>")) return;
                if (a[1].equalsIgnoreCase("clear")) { if (exists(s, a[2])) { ci.clearLore(a[2]); Msg.prefixed(s, "§7Lore vidé."); } return; }
                if (!a[1].equalsIgnoreCase("add") || a.length < 4 || !exists(s, a[2])) { Msg.send(s, "§e/vortex citem lore add <id> <texte>"); return; }
                ci.addLore(a[2], String.join(" ", Arrays.copyOfRange(a, 3, a.length)));
                Msg.prefixed(s, "§7Ligne ajoutée.");
            }
            case "set" -> {
                if (!need(s, a, 4, "set <id> <" + String.join("|", CustomItems.OPTIONS) + "> <valeur>") || !exists(s, a[1])) return;
                String err = ci.setOption(a[1], a[2], a[3]);
                Msg.prefixed(s, err != null ? "§c" + err : "§7Option mise à jour.");
            }
            case "action" -> {
                if (a.length < 4 || !exists(s, a[1])) {
                    Msg.send(s, "§e/vortex citem action <id> <" + String.join("|", CustomItems.ACTIVATORS) + "> add <action...> | clear");
                    return;
                }
                String act = a[2].toUpperCase(Locale.ROOT);
                if (!CustomItems.ACTIVATORS.contains(act)) { Msg.send(s, "§cActivateur : " + String.join(", ", CustomItems.ACTIVATORS)); return; }
                if (a[3].equalsIgnoreCase("clear")) { ci.clearActions(a[1], act); Msg.prefixed(s, "§7Actions vidées."); return; }
                if (!a[3].equalsIgnoreCase("add") || a.length < 5) { Msg.send(s, "§e... add <action>   ex. console: give {player} diamond 1"); return; }
                ci.addAction(a[1], act, String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                Msg.prefixed(s, "§7Action ajoutée à §f" + act);
            }
            case "reload" -> { ci.reload(); Msg.prefixed(s, "§7Items personnalisés rechargés."); }
            default -> {
                Msg.send(s, "§d§l/vortex citem §7(items façon ExecutableItems)");
                Msg.send(s, "§e create <id> §7: crée depuis l'item en main (nom, lore, enchantements)");
                Msg.send(s, "§e name|lore|set <id> ... §7: apparence et options (cooldown, consume, permission, glow, model...)");
                Msg.send(s, "§e action <id> <activateur> add <action> §7: console:, player:, msg:, broadcast:, heal:, effect:, sound:");
                Msg.send(s, "§e give|list|info|delete|reload");
                Msg.send(s, "§8Variables : {player} {uuid} {world} {x} {y} {z} {target} — les actions console: ont les pleins pouvoirs.");
            }
        }
    }

    private boolean need(CommandSender s, String[] a, int n, String usage) {
        if (a.length >= n) return true;
        Msg.send(s, "§e/vortex citem " + usage);
        return false;
    }

    private boolean exists(CommandSender s, String id) {
        if (plugin.customItems().exists(id)) return true;
        Msg.send(s, "§cItem introuvable : §f" + id);
        return false;
    }

    // ═════════════ Complétion ═════════════

    public List<String> completeAdmin(String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("menu", "list", "disable", "enable", "limit", "craft"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("disable") || a[0].equalsIgnoreCase("enable"))) out.addAll(List.of("item", "craft", "potion"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("craft")) out.addAll(List.of("create", "list", "delete"));
        else if (a.length == 3 && a[1].equalsIgnoreCase("potion")) out.addAll(plugin.potions().all());
        else if (a.length == 3 && a[0].equalsIgnoreCase("craft") && a[1].equalsIgnoreCase("delete")) out.addAll(plugin.customRecipes().names());
        else if (a.length == 3 || (a.length == 2 && a[0].equalsIgnoreCase("limit"))) out.add("hand");
        return filter(out, a);
    }

    public List<String> completeCitem(String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("create", "list", "give", "delete", "info", "name", "lore", "set", "action", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("lore")) out.addAll(List.of("add", "clear"));
        else if (a.length == 2) out.addAll(plugin.customItems().ids());
        else if (a.length == 3 && a[0].equalsIgnoreCase("lore")) out.addAll(plugin.customItems().ids());
        else if (a.length == 3 && a[0].equalsIgnoreCase("set")) out.addAll(CustomItems.OPTIONS);
        else if (a.length == 3 && a[0].equalsIgnoreCase("action")) out.addAll(CustomItems.ACTIVATORS);
        else if (a.length == 4 && a[0].equalsIgnoreCase("action")) out.addAll(List.of("add", "clear"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return filter(out, a);
    }

    private static List<String> filter(List<String> l, String[] a) {
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return l.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
