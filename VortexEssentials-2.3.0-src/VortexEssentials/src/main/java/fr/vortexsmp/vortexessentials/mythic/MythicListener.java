package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Applique les 3 règles des armes mythiques : forge unique, une par joueur, jamais rangées. */
public final class MythicListener implements Listener {
    private final VortexEssentials plugin;

    public MythicListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("mythic." + key, true);
    }

    private static boolean mythic(ItemStack s) {
        return MythicRules.isMythicWeapon(s);
    }

    /** Message anti-spam (ex: debout sur un objet au sol). */
    private void warn(Player p, String msg) {
        if (plugin.cooldowns().tryUse(p.getUniqueId(), "mythic_msg", 2000)) {
            p.sendActionBar(Msg.c(msg));
        }
    }

    // ───────── Règle 1 : forge unique ─────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void prepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        VortexItem v = ItemUtil.get(result);
        if (v == null || !v.isMythicWeapon()) return;
        if (!(e.getView().getPlayer() instanceof Player p)) return;

        if (on("forge-once") && plugin.mythic().isForged(v)) {
            e.getInventory().setResult(null);
        } else if (on("one-per-player") && MythicRules.carried(p, v.isMythicArmor()) > 0) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        VortexItem v = ItemUtil.get(e.getCurrentItem());
        if (v == null || !v.isMythicWeapon()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Shift-clic / touche numérique / F : on bloque pour ne pas gâcher l'unique forge
        ClickType ct = e.getClick();
        if (e.isShiftClick() || ct == ClickType.NUMBER_KEY || ct == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cCliquez normalement sur le résultat pour forger une arme mythique.");
            return;
        }
        if (on("forge-once") && plugin.mythic().isForged(v)) {
            e.setCancelled(true);
            Msg.prefixed(p, "§c" + v.displayName() + " a déjà été forgée sur ce serveur.");
            return;
        }
        if (on("one-per-player") && MythicRules.carried(p, v.isMythicArmor()) > 0) {
            e.setCancelled(true);
            Msg.prefixed(p, v.isMythicArmor() ? "§cVous portez déjà une armure mythique." : "§cVous portez déjà une arme mythique.");
            return;
        }
        if (on("forge-once")) {
            plugin.mythic().markForged(v, p);
            Bukkit.broadcast(Msg.c("§c✦ §f" + p.getName() + " §7a forgé §c§l" + v.displayName() + " §7!"));
            plugin.discord().announce("Arme mythique forgée", "**" + p.getName() + "** a forgé **" + v.displayName() + "** !", 0xE5484D);
        }
    }

    // ───────── Règle 2 : une seule arme mythique par joueur ─────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (!mythic(e.getItem().getItemStack())) return;

        if (!(e.getEntity() instanceof Player p)) {
            // Aucun mob (zombie, renard, allay...) ne doit ramasser une arme mythique
            e.setCancelled(true);
            return;
        }
        VortexItem pv = ItemUtil.get(e.getItem().getItemStack());
        if (on("one-per-player") && pv != null && MythicRules.carried(p, pv.isMythicArmor()) > 0) {
            e.setCancelled(true);
            warn(p, pv.isMythicArmor() ? "§cVous portez déjà une armure mythique." : "§cVous portez déjà une arme mythique.");
        }
    }

    // ───────── Règle 3 : impossible à ranger ─────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!on("block-containers")) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean bad = false;

        // Bundles : ni mettre une arme mythique dedans, ni l'en sortir/y entrer par clic
        if ((MythicRules.isBundle(cursor) && mythic(cur)) || (MythicRules.isBundle(cur) && mythic(cursor))) {
            bad = true;
        }

        Inventory top = e.getView().getTopInventory();
        if (!bad && MythicRules.restricted(top)) {
            int raw = e.getRawSlot();
            int topSize = top.getSize();
            if (raw >= 0 && raw < topSize) {
                // Clic dans le conteneur : dépôt avec le curseur, touche numérique ou F
                if (mythic(cursor)) bad = true;
                if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                        && mythic(p.getInventory().getItem(e.getHotbarButton()))) bad = true;
                if (e.getClick() == ClickType.SWAP_OFFHAND
                        && mythic(p.getInventory().getItemInOffHand())) bad = true;
            } else if (raw >= topSize && e.isShiftClick() && mythic(cur)) {
                // Shift-clic depuis l'inventaire du joueur vers le conteneur
                bad = true;
            }
        }

        if (bad) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cUne arme mythique ne peut pas être rangée.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!on("block-containers")) return;
        Inventory top = e.getView().getTopInventory();
        if (!MythicRules.restricted(top)) return;
        if (!mythic(e.getOldCursor())) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player p) {
                    Msg.prefixed(p, "§cUne arme mythique ne peut pas être rangée.");
                }
                return;
            }
        }
    }

    /** Entonnoirs, droppers, dispensers... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void move(InventoryMoveItemEvent e) {
        if (on("block-containers") && mythic(e.getItem())) e.setCancelled(true);
    }

    /** Entonnoir (ou minecart-entonnoir) qui aspire un objet au sol. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hopperPickup(InventoryPickupItemEvent e) {
        if (on("block-containers") && mythic(e.getItem().getItemStack())) e.setCancelled(true);
    }

    /** Cadres, armor stands et allays : autres façons de "ranger" un objet. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent e) {
        if (!on("block-containers")) return;
        if (!(e.getRightClicked() instanceof ItemFrame
                || e.getRightClicked() instanceof ArmorStand
                || e.getRightClicked() instanceof Allay)) return;

        ItemStack used = e.getHand() == EquipmentSlot.OFF_HAND
                ? e.getPlayer().getInventory().getItemInOffHand()
                : e.getPlayer().getInventory().getItemInMainHand();
        if (mythic(used)) {
            e.setCancelled(true);
            Msg.prefixed(e.getPlayer(), "§cUne arme mythique ne peut pas être rangée.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent e) {
        if (on("block-containers") && mythic(e.getPlayerItem())) {
            e.setCancelled(true);
            Msg.prefixed(e.getPlayer(), "§cUne arme mythique ne peut pas être rangée.");
        }
    }
}
