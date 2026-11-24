package fr.vortexsmp.vortexessentials.command;

import fr.vortexsmp.vortexessentials.admin.CustomRecipes;
import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /vortex — menu, catalogue, give, mythic, limites, reload. */
public final class VortexCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("menu", "list", "preview", "tool", "hud", "recipes", "give", "mythic", "boost", "admin", "citem", "lang", "pack", "discord", "legendary", "mace", "ritual", "rituals", "services", "craftlimit", "crafts", "limit", "reload", "help");

    private final VortexEssentials plugin;
    private final LimitCommand limit;
    private final fr.vortexsmp.vortexessentials.admin.AdminCommand adminCmd;
    private final fr.vortexsmp.vortexessentials.boost.BoostCommand boostCmd;

    public VortexCommand(VortexEssentials plugin, LimitCommand limit) {
        this.plugin = plugin;
        this.limit = limit;
        this.adminCmd = new fr.vortexsmp.vortexessentials.admin.AdminCommand(plugin);
        this.boostCmd = new fr.vortexsmp.vortexessentials.boost.BoostCommand(plugin);
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("vortexessentials.admin")) return true;
        Msg.send(s, "§cPermission refusée.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);

        if (!sub.equals("help") && !s.hasPermission("vortexessentials.use")) {
            Msg.send(s, "§cPermission refusée.");
            return true;
        }

        switch (sub) {
            case "menu" -> {
                if (s instanceof Player p) plugin.menu().openHub(p);
                else Msg.send(s, "§cCommande réservée aux joueurs. Essayez §f/vortex list§c.");
            }
            case "list" -> list(s);
            case "preview" -> preview(s, a);
            case "tool" -> { if (s instanceof Player p) plugin.menu().openToolSettings(p); else Msg.send(s, "§cCommande réservée aux joueurs."); }
            case "hud" -> {
                if (s instanceof Player p) {
                    boolean on = !fr.vortexsmp.vortexessentials.hud.HudTask.enabledFor(p);
                    fr.vortexsmp.vortexessentials.hud.HudTask.setEnabled(p, on);
                    Msg.prefixed(p, "§7HUD des cooldowns : " + (on ? "§aactivé" : "§cdésactivé"));
                } else Msg.send(s, "§cCommande réservée aux joueurs.");
            }
            case "admin" -> { if (admin(s)) adminCmd.admin(s, Arrays.copyOfRange(a, 1, a.length)); }
            case "citem" -> { if (admin(s)) adminCmd.citem(s, Arrays.copyOfRange(a, 1, a.length)); }
            case "recipes" -> {
                if (admin(s)) {
                    if (a.length >= 3 && a[1].equalsIgnoreCase("preset")) preset(s, a[2]);
                    else if (a.length >= 2 && a[1].equalsIgnoreCase("edit")) {
                        if (s instanceof Player pl) plugin.recipeEditor().openList(pl, 0); else Msg.send(s, "§cCommande réservée aux joueurs.");
                    } else recipes(s);
                }
            }
            case "lang" -> lang(s, a);
            case "pack" -> { if (admin(s)) plugin.pack().command(s, a); }
            case "discord" -> { if (admin(s)) discord(s, a); }
            case "legendary" -> { if (admin(s)) legendary(s, a); }
            case "mace" -> { if (admin(s)) mace(s, a); }
            case "ritual" -> { if (admin(s)) ritual(s, a); }
            case "craftlimit" -> { if (admin(s)) craftLimit(s, a); }
            case "crafts" -> { if (s instanceof Player pl) plugin.ritualMenu().openCrafts(pl); else Msg.send(s, "§cCommande réservée aux joueurs."); }
            case "services", "systemes" -> { if (s instanceof Player pl) plugin.servicesMenu().open(pl); else Msg.send(s, "§cCommande réservée aux joueurs."); }
            case "rituals" -> { if (s instanceof Player pl) plugin.ritualMenu().openList(pl); else Msg.send(s, "§cCommande réservée aux joueurs."); }
            case "give" -> { if (admin(s)) give(s, a); }
            case "mythic" -> { if (admin(s)) mythic(s, a); }
            case "boost" -> boost(s, a);
            case "limit" -> limit.run(s, Arrays.copyOfRange(a, 1, a.length));
            case "reload" -> {
                if (admin(s)) {
                    plugin.reloadAll();
                    Msg.prefixed(s, "§aConfiguration, limites et registre mythique rechargés.");
                }
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.send(s, "§b§lVortexEssentials §7— par ItsVany");
        Msg.send(s, "§e/vortex §7: ouvre le menu");
        Msg.send(s, "§e/vortex list §7: liste des items");
        Msg.send(s, "§e/vortex preview <item> §7: affiche la recette");
        if (s.hasPermission("vortexessentials.admin")) Msg.send(s, "§e/vortex recipes §7: diagnostic des recettes");
        Msg.send(s, "§e/vortex boost [list|info [joueur]] §7: boosts de groupe");
        Msg.send(s, "§e/vortex tool §7: réglages de l'outil en main §8(ou sneak + clic droit)");
        Msg.send(s, "§e/vortex hud §7: active/désactive l'affichage des cooldowns");
        Msg.send(s, "§e/vortex lang [code] §7: langue du plugin");
        if (s.hasPermission("vortexessentials.admin")) {
            Msg.send(s, "§e/vortex admin §7: désactivations, limites, crafts personnalisés");
            Msg.send(s, "§e/vortex citem §7: items personnalisés (façon ExecutableItems)");
            Msg.send(s, "§e/vortex pack §7: resource pack (diagnostic, envoi)");
            Msg.send(s, "§e/vortex discord §7[test | reload] : notifications Discord");
            Msg.send(s, "§e/vortex boost create|set|effect|event|edit §7: créer et gérer des boosts");
        }
        if (s.hasPermission("vortexessentials.admin")) {
            Msg.send(s, "§e/vortex give <item> [joueur] [qté] §7: donne un item");
            Msg.send(s, "§e/vortex mythic <status|reset> [item] §7: registre des armes mythiques");
            Msg.send(s, "§e/vortex reload §7: recharge tout");
        }
        if (s.hasPermission("vortexessentials.limit.admin")) {
            Msg.send(s, "§e/vortex limit ... §7(ou §e/vortexlimit§7) : limites et bypass");
        }
    }

    /** /vortex craftlimit <craft> <max serveur> <max par joueur> (0 = illimité) · list · reset <craft>. */
    private void craftLimit(CommandSender s, String[] a) {
        var cl = plugin.craftLimits();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("list")) {
            Msg.send(s, "§6Limites des crafts personnalisés");
            for (String n : plugin.customRecipes().names()) {
                s.sendMessage(Msg.c("§8 - §f" + n + " §7: " + (cl.limited(n) ? "serveur §f" + cl.maxServer(n) + " §7(fait : §f" + cl.crafted(n) + "§7), par joueur §f" + cl.maxPlayer(n) : "§aillimité")));
            }
            return;
        }
        if (sub.equals("reset")) {
            if (a.length < 3) { Msg.send(s, "§e/vortex craftlimit reset <craft>"); return; }
            cl.reset(CustomRecipes.clean(a[2]));
            Msg.prefixed(s, "§7Compteur remis à zéro.");
            return;
        }
        String craft = CustomRecipes.clean(a[1]);
        boolean isItem = a.length > 1 && a[1].toLowerCase(Locale.ROOT).startsWith("item:");
        if (isItem) craft = a[1].toLowerCase(Locale.ROOT);
        String itemId = isItem ? craft.substring(5) : null;
        boolean itemExists = isItem && java.util.Arrays.stream(fr.vortexsmp.vortexessentials.item.VortexItem.values()).anyMatch(x -> x.id().equals(itemId));
        if (a.length < 4 || (!itemExists && !plugin.customRecipes().names().contains(craft))) {
            Msg.send(s, "§e/vortex craftlimit <craft|item:<id>> <max serveur> <max par joueur> §7(0 = illimité) · list · reset <craft>");
            return;
        }
        try {
            cl.set(craft, Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            Msg.prefixed(s, "§f" + craft + " §7: " + String.join(" §8| ", cl.lines(craft)));
        } catch (NumberFormatException e) {
            Msg.send(s, "§cNombres invalides.");
        }
    }

    private void mace(CommandSender s, String[] a) {
        var ml = plugin.maceLimits();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reset" -> { ml.reset(); Msg.prefixed(s, "§7Compteur de maces remis à zéro."); }
            case "resetplayer" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex mace resetplayer <joueur>"); return; }
                ml.resetPlayer(Bukkit.getOfflinePlayer(a[2]).getUniqueId());
                Msg.prefixed(s, "§7Compteur du joueur remis à zéro.");
            }
            default -> Msg.send(s, "§6Maces craftées : §f" + ml.crafted() + "§7/§f" + (ml.maxPerServer() == 0 ? "∞" : ml.maxPerServer())
                    + " §7(par joueur : §f" + (ml.maxPerPlayer() == 0 ? "∞" : ml.maxPerPlayer()) + "§7)");
        }
    }

    private void ritual(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "set" -> {
                if (a.length < 4) { Msg.send(s, "§e/vortex ritual set <craft> <secondes> §7(craft créé avec /vortex admin craft)"); return; }
                String craft = fr.vortexsmp.vortexessentials.admin.CustomRecipes.clean(a[2]);
                if (!plugin.customRecipes().names().contains(craft)) { Msg.send(s, "§cCraft personnalisé introuvable : §f" + craft); return; }
                try {
                    int sec = Math.max(1, Integer.parseInt(a[3]));
                    plugin.rituals().set(craft, sec);
                    Msg.prefixed(s, "§5" + craft + " §7devient un craft à rituel de §f" + sec + "s§7.");
                } catch (NumberFormatException e) {
                    Msg.send(s, "§cNombre invalide.");
                }
            }
            case "off" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex ritual off <craft>"); return; }
                plugin.rituals().set(fr.vortexsmp.vortexessentials.admin.CustomRecipes.clean(a[2]), 0);
                Msg.prefixed(s, "§7Rituel retiré (le craft redevient normal).");
            }
            default -> {
                Msg.send(s, "§5Crafts à rituel §7(" + plugin.rituals().names().size() + ")");
                for (String n : plugin.rituals().names()) s.sendMessage(Msg.c("§8 - §f" + n + " §7: " + plugin.rituals().seconds(n) + "s"));
            }
        }
    }

    private void legendary(CommandSender s, String[] a) {
        var ll = plugin.legendaryLimits();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reset" -> {
                VortexItem v = a.length > 2 ? VortexItem.byId(a[2]) : null;
                if (v == null || v.category() != Category.LEGENDARY_WEAPON) { Msg.send(s, "§e/vortex legendary reset <arme>"); return; }
                ll.reset(v);
                Msg.prefixed(s, "§7Compteur de §f" + v.displayName() + " §7remis à zéro.");
            }
            case "resetplayer" -> {
                if (a.length < 3) { Msg.send(s, "§e/vortex legendary resetplayer <joueur>"); return; }
                ll.resetPlayer(Bukkit.getOfflinePlayer(a[2]).getUniqueId());
                Msg.prefixed(s, "§7Compteur du joueur remis à zéro.");
            }
            default -> {
                Msg.send(s, "§6§lArmes légendaires §7(max §f" + ll.maxPerWeapon() + " §7par arme, §f" + ll.maxPerPlayer() + " §7par joueur)");
                for (VortexItem v : VortexItem.ofCategory(Category.LEGENDARY_WEAPON)) {
                    Msg.send(s, "§7" + v.displayName() + " §f" + ll.crafted(v) + "§7/§f" + ll.maxPerWeapon());
                }
            }
        }
    }

    private void preset(CommandSender s, String name) {
        String err = plugin.recipes().applyPreset(name);
        Msg.prefixed(s, err != null ? "§c" + err : "§aRecettes passées en mode §f" + name.toLowerCase() + " §7(ancien fichier : recipes.yml.bak).");
    }

    private void lang(CommandSender s, String[] a) {
        var langs = fr.vortexsmp.vortexessentials.lang.Lang.available(plugin);
        if (a.length < 2) {
            Msg.send(s, "§7Langue : §f" + fr.vortexsmp.vortexessentials.lang.Lang.current() + " §8| §7disponibles : §f" + String.join(", ", langs));
            Msg.send(s, "§e/vortex lang <code> §7(admin)");
            return;
        }
        if (!admin(s)) return;
        String code = a[1].toLowerCase();
        if (!langs.contains(code)) { Msg.send(s, "§cLangue inconnue. Disponibles : §f" + String.join(", ", langs)); return; }
        plugin.getConfig().set("language", code);
        plugin.saveConfig();
        fr.vortexsmp.vortexessentials.lang.Lang.load(plugin);
        plugin.recipes().load();
        Msg.prefixed(s, "§aLangue : §f" + code + " §7(les items déjà donnés gardent leur texte : redonnez-les).");
    }

    private void discord(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "status";
        switch (sub) {
            case "test" -> {
                String err = plugin.discord().test();
                Msg.prefixed(s, err != null ? "§c" + err : "§7Message de test envoyé.");
            }
            case "reload" -> { plugin.discord().reload(); Msg.prefixed(s, "§7Discord rechargé : " + (plugin.discord().active() ? "§aactif" : "§cinactif")); }
            default -> Msg.send(s, "§7Discord : " + (plugin.discord().active() ? "§aactif" : "§cinactif")
                    + " §8| §e/vortex discord §7[test | reload | status]");
        }
    }

    /** Diagnostic des recettes : chargées, en erreur, et items sans recette. */
    private void recipes(CommandSender s) {
        var rm = plugin.recipes();
        Msg.send(s, "§b§lRecettes §7: §f" + rm.craftCount() + " craft §7+ §f" + rm.upgradeCount() + " forge");
        if (rm.problems().isEmpty()) {
            Msg.send(s, "§aAucune erreur dans recipes.yml.");
        } else {
            Msg.send(s, "§cErreurs dans recipes.yml (" + rm.problems().size() + ") :");
            for (String p : rm.problems()) Msg.send(s, "§c- §7" + p);
        }
        List<String> missing = new ArrayList<>();
        for (VortexItem v : VortexItem.values()) {
            if (rm.def(v) == null && rm.forgeSource(v) == null) missing.add(v.id());
        }
        Msg.send(s, missing.isEmpty() ? "§aLes " + VortexItem.values().length + " items ont une recette." : "§eItems sans recette : §f" + String.join("§7, §f", missing));
    }

    private void preview(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        VortexItem v = a.length > 1 ? VortexItem.byId(a[1]) : null;
        if (v == null || (v.isSecret() && !s.hasPermission("vortexessentials.admin"))) {
            Msg.send(s, "§e/vortex preview <item> §7(voir /vortex list)");
            return;
        }
        plugin.menu().openRecipe(p, v);
    }

    private void list(CommandSender s) {
        for (Category c : Category.values()) {
            Msg.send(s, c.color() + "§l" + c.label());
            StringBuilder sb = new StringBuilder("  §7");
            List<VortexItem> items = VortexItem.ofCategory(c);
            for (int i = 0; i < items.size(); i++) {
                sb.append(items.get(i).id());
                if (i < items.size() - 1) sb.append("§8, §7");
            }
            Msg.send(s, sb.toString());
        }
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) { Msg.send(s, "§e/vortex give <item> [joueur] [qté]"); return; }
        VortexItem v = VortexItem.byId(a[1]);
        if (v == null) { Msg.send(s, "§cItem inconnu : §f" + a[1] + " §7(voir /vortex list)"); return; }

        Player target = null;
        if (a.length > 2) target = Bukkit.getPlayerExact(a[2]);
        else if (s instanceof Player p) target = p;
        if (target == null) { Msg.send(s, "§cJoueur introuvable."); return; }

        int qty = 1;
        if (a.length > 3) {
            try { qty = Integer.parseInt(a[3]); }
            catch (NumberFormatException ex) { Msg.send(s, "§cQuantité invalide."); return; }
        }
        qty = v.isMythicWeapon() ? 1 : Math.max(1, Math.min(qty, 64));

        for (int i = 0; i < qty; i++) {
            var left = target.getInventory().addItem(ItemUtil.create(v));
            for (var it : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), it);
        }
        Msg.prefixed(s, "§a" + qty + "× §f" + v.displayName() + " §adonné à §f" + target.getName() + "§a.");
        if (!target.equals(s)) Msg.prefixed(target, "§aVous avez reçu §f" + qty + "× " + v.displayName() + "§a.");
    }

    private void boost(CommandSender s, String[] a) {
        if (boostCmd.run(s, a)) return;
        var bm = plugin.boosts();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "info";

        if (sub.equals("list")) {
            if (bm.all().isEmpty()) { Msg.send(s, "§7Aucun groupe de boost configuré (section boosts: de config.yml)."); return; }
            Msg.send(s, "§b§lBoosts de groupe §7(cumul : §f" + bm.stacking() + "§7)" + (bm.enabled() ? "" : " §c[désactivés]"));
            for (var g : bm.all()) {
                Msg.send(s, "§e" + g.id() + " §8(" + g.permission() + ") §7XP §fx" + g.xp() + " §7Drops §fx" + g.drops()
                        + " §7Dégâts §fx" + g.damage() + " §7Potions §fx" + g.potionDuration() + " §7Cooldowns §f-" + Math.round(g.cooldownReduction() * 100) + "%"
                        + (g.effects().isEmpty() ? "" : " §7Effets §f" + g.effects().size()));
            }
            return;
        }

        Player target = null;
        if (a.length > 2) {
            if (!s.hasPermission("vortexessentials.admin")) { Msg.send(s, "§cPermission refusée."); return; }
            target = Bukkit.getPlayerExact(a[2]);
        } else if (s instanceof Player p) target = p;
        if (target == null) { Msg.send(s, "§cJoueur introuvable."); return; }

        var act = bm.active(target);
        if (act.isEmpty()) { Msg.send(s, "§7" + target.getName() + " n'a aucun boost actif."); return; }
        Msg.send(s, "§b§lBoosts de " + target.getName() + " §7(groupes : §f"
                + String.join("§7, §f", act.stream().map(g -> g.id()).toList()) + "§7)");
        Msg.send(s, "§7XP §fx" + String.format("%.2f", bm.multiplier(target, fr.vortexsmp.vortexessentials.boost.BoostManager.Stat.XP))
                + " §7Drops §fx" + String.format("%.2f", bm.multiplier(target, fr.vortexsmp.vortexessentials.boost.BoostManager.Stat.DROPS))
                + " §7Dégâts §fx" + String.format("%.2f", bm.multiplier(target, fr.vortexsmp.vortexessentials.boost.BoostManager.Stat.DAMAGE))
                + " §7Potions §fx" + String.format("%.2f", bm.multiplier(target, fr.vortexsmp.vortexessentials.boost.BoostManager.Stat.POTION))
                + " §7Cooldowns §fx" + String.format("%.2f", bm.cooldownFactor(target)));
        for (var g : act) {
            if (!g.effects().isEmpty()) {
                Msg.send(s, "§7Effets permanents §8(" + g.id() + ") §f" + String.join("§7, §f",
                        g.effects().entrySet().stream().map(en -> en.getKey().getKey().getKey() + " " + (en.getValue() + 1)).toList()));
            }
        }
    }

    private void mythic(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> {
                for (VortexItem v : VortexItem.mythics()) {
                    boolean forged = plugin.mythic().isForged(v);
                    String by = plugin.mythic().forgedBy(v);
                    Msg.send(s, "§c" + v.displayName() + " §7: "
                            + (forged ? "§eforgée" + (by != null ? " §7par §f" + by : "") : "§adisponible"));
                }
            }
            case "reset" -> {
                VortexItem v = a.length > 2 ? VortexItem.byId(a[2]) : null;
                if (v == null || !v.isMythicWeapon()) {
                    Msg.send(s, "§e/vortex mythic reset <sealord|lightspeed|mintus>");
                    return;
                }
                if (plugin.mythic().reset(v)) Msg.prefixed(s, "§a" + v.displayName() + " peut de nouveau être forgée.");
                else Msg.prefixed(s, "§7" + v.displayName() + " n'était pas marquée comme forgée.");
            }
            default -> Msg.send(s, "§e/vortex mythic <status|reset> [item]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) {
                boolean ok = switch (x) {
                    case "give", "mythic", "reload" -> s.hasPermission("vortexessentials.admin");
                    case "limit" -> s.hasPermission("vortexessentials.limit.admin");
                    default -> true;
                };
                if (ok) out.add(x);
            }
        } else {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "give" -> {
                    if (!s.hasPermission("vortexessentials.admin")) break;
                    if (a.length == 2) for (VortexItem v : VortexItem.values()) out.add(v.id());
                    else if (a.length == 3) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                }
                case "mythic" -> {
                    if (!s.hasPermission("vortexessentials.admin")) break;
                    if (a.length == 2) out.addAll(List.of("status", "reset"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("reset"))
                        for (VortexItem v : VortexItem.mythics()) out.add(v.id());
                }
                case "admin" -> {
                    if (s.hasPermission("vortexessentials.admin")) out.addAll(adminCmd.completeAdmin(Arrays.copyOfRange(a, 1, a.length)));
                    return out.stream().toList();
                }
                case "citem" -> {
                    if (s.hasPermission("vortexessentials.admin")) out.addAll(adminCmd.completeCitem(Arrays.copyOfRange(a, 1, a.length)));
                    return out.stream().toList();
                }
                case "preview" -> {
                    if (a.length == 2) for (VortexItem v : VortexItem.values()) out.add(v.id());
                }
                case "boost" -> { return boostCmd.complete(a); }
                case "craftlimit" -> {
                    if (a.length == 2) { out.addAll(List.of("list", "reset")); out.addAll(plugin.customRecipes().names()); }
                    else if (a.length == 3 && a[1].equalsIgnoreCase("reset")) out.addAll(plugin.customRecipes().names());
                }
                case "mace" -> { if (a.length == 2) out.addAll(List.of("status", "reset", "resetplayer")); }
                case "ritual" -> {
                    if (a.length == 2) out.addAll(List.of("set", "off", "list"));
                    else if (a.length == 3) out.addAll(plugin.customRecipes().names());
                }
                case "legendary" -> {
                    if (a.length == 2) out.addAll(List.of("status", "reset", "resetplayer"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("reset"))
                        for (VortexItem v : VortexItem.ofCategory(Category.LEGENDARY_WEAPON)) out.add(v.id());
                }
                case "pack" -> { if (s.hasPermission("vortexessentials.admin")) return plugin.pack().complete(a); }
                case "lang" -> { if (a.length == 2) out.addAll(fr.vortexsmp.vortexessentials.lang.Lang.available(plugin)); }
                case "discord" -> { if (a.length == 2) out.addAll(List.of("test", "reload", "status")); }
                case "recipes" -> {
                    if (a.length == 2) out.add("preset");
                    else if (a.length == 3 && a[1].equalsIgnoreCase("preset")) out.addAll(List.of("hard", "normal"));
                }
                case "limit" -> {
                    if (s.hasPermission("vortexessentials.limit.admin"))
                        out.addAll(limit.complete(Arrays.copyOfRange(a, 1, a.length)));
                }
                default -> { }
            }
        }
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
