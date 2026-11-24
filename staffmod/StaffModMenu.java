package fr.vortexsmp.vortexessentials.staffmod;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
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
import java.util.List;

/** Menu de configuration des paliers du mode staff : créer, régler commandes, vanish, vol, mode de jeu, poids. */
public final class StaffModMenu implements Listener {
    private static final String[] MODES = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};

    private static final class Holder implements InventoryHolder {
        final String tier;
        Inventory inv;
        final List<String> shown = new ArrayList<>();
        Holder(String tier) { this.tier = tier; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VortexEssentials plugin;

    public StaffModMenu(VortexEssentials plugin) {
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

    private static Material helmet(int weight) {
        return switch (Math.min(weight, 5)) {
            case 1 -> Material.LEATHER_HELMET;
            case 2 -> Material.IRON_HELMET;
            case 3 -> Material.DIAMOND_HELMET;
            case 4 -> Material.NETHERITE_HELMET;
            default -> Material.DRAGON_HEAD;
        };
    }

    public void openList(Player p) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§bPaliers du mode staff"));
        h.inv = inv;
        int i = 0;
        for (StaffMod.Tier t : plugin.staffMod().tiers()) {
            if (i >= 45) break;
            h.shown.add(t.name);
            inv.setItem(i++, button(helmet(t.weight), "§b" + t.name, "§7Poids : §f" + t.weight, "§7Commandes : §f" + t.features.size() + " groupe(s)",
                    "§7Vanish : " + (t.vanish ? "§aoui" : "§cnon") + " §8| §7Vol : " + (t.fly ? "§aoui" : "§cnon") + " §8| §7Mode : §f" + t.gamemode,
                    "§eClic §7: modifier"));
        }
        inv.setItem(49, button(Material.ANVIL, "§aCréer un palier", "§7Vous saisirez le nom dans le chat."));
        p.openInventory(inv);
    }

    public void openEdit(Player p, StaffMod.Tier t) {
        Holder h = new Holder(t.name);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("§bPalier : " + t.name));
        h.inv = inv;
        int i = 0;
        for (StaffCatalog.Entry e : StaffCatalog.ENTRIES) {
            boolean on = t.features.contains(e.key());
            inv.setItem(i++, button(e.icon(), (on ? "§a✔ " : "§c✘ ") + e.name(), "§7" + String.join(", ", e.nodes()), "§eClic §7: " + (on ? "retirer" : "ajouter")));
        }
        inv.setItem(45, button(t.vanish ? Material.ENDER_EYE : Material.ENDER_PEARL, "§bVanish : " + (t.vanish ? "§aactivé" : "§cdésactivé"), "§7Invisible pour les joueurs sans staffmod.", "§eClic §7: changer"));
        inv.setItem(46, button(Material.FEATHER, "§bVol : " + (t.fly ? "§aactivé" : "§cdésactivé"), "§eClic §7: changer"));
        inv.setItem(47, button(Material.GRASS_BLOCK, "§bMode de jeu : §f" + t.gamemode, "§eClic §7: suivant"));
        inv.setItem(48, button(Material.RED_DYE, "§cPoids -1"));
        inv.setItem(49, button(helmet(t.weight), "§bPoids : §f" + t.weight, "§7Le plus haut palier gagne si plusieurs conviennent."));
        inv.setItem(50, button(Material.LIME_DYE, "§aPoids +1"));
        inv.setItem(52, button(Material.TNT, "§cSupprimer le palier", "§cShift + clic §7pour confirmer."));
        inv.setItem(53, button(Material.ARROW, "§7« Retour"));
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("vortexessentials.staffmod.admin")) return;
        int slot = e.getRawSlot();
        StaffMod sm = plugin.staffMod();
        if (h.tier == null) {
            if (slot == 49) { p.closeInventory(); sm.prompt(p, "create"); return; }
            if (slot >= 0 && slot < h.shown.size()) { StaffMod.Tier t = sm.tier(h.shown.get(slot)); if (t != null) openEdit(p, t); }
            return;
        }
        StaffMod.Tier t = sm.tier(h.tier);
        if (t == null) { openList(p); return; }
        if (slot >= 0 && slot < StaffCatalog.ENTRIES.size()) {
            String key = StaffCatalog.ENTRIES.get(slot).key();
            if (!t.features.remove(key)) t.features.add(key);
        } else {
            switch (slot) {
                case 45 -> t.vanish = !t.vanish;
                case 46 -> t.fly = !t.fly;
                case 47 -> {
                    int idx = 0;
                    for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(t.gamemode)) idx = i;
                    t.gamemode = MODES[(idx + 1) % MODES.length];
                }
                case 48 -> t.weight = Math.max(1, t.weight - 1);
                case 50 -> t.weight++;
                case 52 -> {
                    if (!e.isShiftClick()) { Msg.prefixed(p, "§cShift + clic pour confirmer la suppression."); return; }
                    sm.delete(t.name);
                    openList(p);
                    return;
                }
                case 53 -> { openList(p); return; }
                default -> { return; }
            }
        }
        sm.save();
        sm.refresh(t.name);
        openEdit(p, t);
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
