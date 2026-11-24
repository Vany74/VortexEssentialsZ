package fr.vortexsmp.vortexessentials.consumable;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consommables :
 *  - Panacea : boire = 3 cœurs soignés et -1 charge ; clic droit sur une ruche pleine = +1 charge.
 *  - Crystallization Shard : clic droit = immobile + anti-knockback + Résistance III.
 *  - Agony : aucune logique ici, ses effets (Dégâts instantanés II + Faiblesse 60 s) sont dans la potion elle-même.
 */
public final class ConsumableListener implements Listener {
    private final VortexEssentials plugin;
    private final NamespacedKey knockbackKey;
    private final Map<UUID, Long> frozen = new HashMap<>();

    public ConsumableListener(VortexEssentials plugin) {
        this.plugin = plugin;
        this.knockbackKey = new NamespacedKey(plugin, "crystallized");
    }

    // ═════════════ Panacea ═════════════

    @EventHandler(ignoreCancelled = true)
    public void drink(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (!ItemUtil.is(item, VortexItem.PANACEA)) return;
        e.setCancelled(true); // la Panacea n'est jamais consommée : elle perd une charge

        Player p = e.getPlayer();
        int charges = ItemUtil.charges(item);
        if (charges <= 0) {
            p.sendActionBar(Msg.c("§cPanacea vide §7— clic droit sur une ruche pleine de miel pour la recharger"));
            return;
        }
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20 : mh.getValue();
        if (p.getHealth() >= max - 0.01) {
            p.sendActionBar(Msg.c("§7Vous êtes déjà en pleine santé"));
            return;
        }

        double hearts = plugin.getConfig().getDouble("panacea.heal-hearts", 3.5);
        p.setHealth(Math.min(max, p.getHealth() + hearts * 2));

        ItemStack updated = ItemUtil.setCharges(item.clone(), charges - 1);
        p.getInventory().setItem(e.getHand(), updated);

        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 6, 0.4, 0.3, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
        p.sendActionBar(Msg.c("§d+" + (hearts % 1 == 0 ? String.valueOf((int) hearts) : String.valueOf(hearts)) + " ❤ §7— charges : §f" + (charges - 1) + "§7/§f" + ItemUtil.MAX_CHARGES));
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    @EventHandler
    public void hive(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getHand() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.BEEHIVE && b.getType() != Material.BEE_NEST) return;

        ItemStack item = e.getItem();
        if (!ItemUtil.is(item, VortexItem.PANACEA)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        Beehive bd = (Beehive) b.getBlockData();
        if (bd.getHoneyLevel() < bd.getMaximumHoneyLevel()) {
            p.sendActionBar(Msg.c("§7Cette ruche n'a pas assez de miel"));
            return;
        }
        int charges = ItemUtil.charges(item);
        if (charges >= ItemUtil.MAX_CHARGES) {
            p.sendActionBar(Msg.c("§7Votre Panacea est déjà pleine"));
            return;
        }

        bd.setHoneyLevel(0); // le miel est récolté, comme avec une bouteille
        b.setBlockData(bd);
        p.getInventory().setItem(e.getHand(), ItemUtil.setCharges(item.clone(), charges + 1));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1f);
        p.sendActionBar(Msg.c("§6Panacea rechargée §7— charges : §f" + (charges + 1) + "§7/§f" + ItemUtil.MAX_CHARGES));
    }

    // ═════════════ Crystallization Shard ═════════════

    @EventHandler
    public void shard(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!ItemUtil.is(hand, VortexItem.CRYSTALLIZATION_SHARD)) return;
        e.setCancelled(true);

        Long until = frozen.get(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) {
            p.sendActionBar(Msg.c("§7Vous êtes déjà cristallisé"));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        freeze(p, plugin.getConfig().getInt("crystallization-shard.seconds", 8));
    }

    private void freeze(Player p, int seconds) {
        UUID id = p.getUniqueId();
        frozen.put(id, System.currentTimeMillis() + seconds * 1000L);

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 2, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 6, true, false, true));

        AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(knockbackKey);
            kb.addTransientModifier(new AttributeModifier(knockbackKey, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        }

        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.END_ROD, l, 40, 0.5, 0.8, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.7f);
        p.sendActionBar(Msg.c("§dCristallisation §7(" + seconds + "s)"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> unfreeze(id), seconds * 20L);
    }

    private void unfreeze(UUID id) {
        frozen.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(knockbackKey);
    }

    /** Immobile : plus aucun déplacement horizontal ni saut (la chute reste possible). */
    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (frozen.isEmpty()) return;
        Long until = frozen.get(e.getPlayer().getUniqueId());
        if (until == null || System.currentTimeMillis() >= until) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        boolean horizontal = from.getX() != to.getX() || from.getZ() != to.getZ();
        boolean up = to.getY() > from.getY();
        if (!horizontal && !up) return;

        Location fixed = to.clone();
        fixed.setX(from.getX());
        fixed.setZ(from.getZ());
        if (up) fixed.setY(from.getY());
        e.setTo(fixed);
    }

    /** Cristallisé : aucun dégât (sauf vide et /kill). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void shield(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = frozen.get(p.getUniqueId());
        if (until == null || System.currentTimeMillis() >= until) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.SUICIDE
                || c == EntityDamageEvent.DamageCause.KILL) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        unfreeze(e.getPlayer().getUniqueId());
    }
}
