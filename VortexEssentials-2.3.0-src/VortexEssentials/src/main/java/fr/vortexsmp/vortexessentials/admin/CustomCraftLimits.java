package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Limites de craft des crafts personnalisés (pommes d'or, totem, enclume...) : nombre maximal de crafts sur tout le serveur
 * et par joueur (0 = illimité). Réglage : /vortex craftlimit <craft> <serveur> <joueur>, ou boutons de l'éditeur de recettes.
 * Données : custom-craft-limits.yml. Permission d'ignorer : vortexessentials.admin.bypass.
 */
public final class CustomCraftLimits implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;

    public CustomCraftLimits(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "custom-craft-limits.yml");
    }

    public int maxServer(String c) { return store.get().getInt("limits." + c + ".server", 0); }
    public int maxPlayer(String c) { return store.get().getInt("limits." + c + ".player", 0); }
    public boolean limited(String c) { return maxServer(c) > 0 || maxPlayer(c) > 0; }
    public int crafted(String c) { return store.get().getInt("counts." + c + ".total"); }
    public int craftedBy(String c, UUID id) { return store.get().getInt("counts." + c + ".players." + id); }

    public void set(String c, int server, int player) {
        server = Math.max(0, server);
        player = Math.max(0, player);
        store.get().set("limits." + c, null);
        if (server > 0) store.get().set("limits." + c + ".server", server);
        if (player > 0) store.get().set("limits." + c + ".player", player);
        store.save();
    }

    public void reset(String c) {
        store.get().set("counts." + c, null);
        store.save();
    }

    /** 0 = autorisé, 1 = limite du serveur, 2 = limite du joueur. */
    private int check(Player p, String c) {
        if (p.hasPermission("vortexessentials.admin.bypass")) return 0;
        if (maxServer(c) > 0 && crafted(c) >= maxServer(c)) return 1;
        if (maxPlayer(c) > 0 && craftedBy(c, p.getUniqueId()) >= maxPlayer(c)) return 2;
        return 0;
    }

    /** Lignes à afficher dans les menus : illimité ou limité (avec le nombre restant). */
    public List<String> lines(String c) {
        List<String> out = new ArrayList<>();
        if (!limited(c)) {
            out.add("§a✔ Craft illimité");
            return out;
        }
        if (maxServer(c) > 0) {
            int left = Math.max(0, maxServer(c) - crafted(c));
            out.add("§6✦ Craft limité : §f" + left + "§7/§f" + maxServer(c) + " §7restant(s) sur le serveur");
        }
        if (maxPlayer(c) > 0) out.add("§7Maximum par joueur : §f" + maxPlayer(c));
        return out;
    }

    private String craftName(Recipe r) {
        if (!(r instanceof Keyed k) || !k.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return null;
        String key = k.getKey().getKey();
        if (key.startsWith("craft_")) return "item:" + key.substring("craft_".length());   // recette d'un item Vortex
        return key.startsWith("custom_") ? key.substring("custom_".length()) : null;
    }

    private String denial(String c, int r) {
        return r == 1 ? "§cLimite atteinte : ce craft ne peut plus être fabriqué sur le serveur (" + maxServer(c) + ")."
                : "§cVous avez déjà fabriqué ce craft §f" + maxPlayer(c) + " §cfois (maximum par joueur).";
    }

    @EventHandler(priority = EventPriority.LOW)
    public void prepare(PrepareItemCraftEvent e) {
        String c = craftName(e.getRecipe());
        if (c == null || !limited(c) || !(e.getView().getPlayer() instanceof Player p)) return;
        int r = check(p, c);
        if (r == 0) return;
        e.getInventory().setResult(null);
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "ccl_msg", 2500)) p.sendActionBar(Msg.c(denial(c, r)));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        String c = craftName(e.getRecipe());
        if (c == null || !limited(c) || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.isShiftClick()) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cCliquez normalement sur le résultat : ce craft est limité.");
            return;
        }
        int r = check(p, c);
        if (r != 0) {
            e.setCancelled(true);
            Msg.prefixed(p, denial(c, r));
            return;
        }
        store.get().set("counts." + c + ".total", crafted(c) + 1);
        store.get().set("counts." + c + ".players." + p.getUniqueId(), craftedBy(c, p.getUniqueId()) + 1);
        store.save();
    }
}
