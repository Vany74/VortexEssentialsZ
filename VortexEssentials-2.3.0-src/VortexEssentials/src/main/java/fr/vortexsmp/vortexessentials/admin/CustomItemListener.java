package fr.vortexsmp.vortexessentials.admin;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/**
 * Exécute les actions des items personnalisés.
 * Actions : console: / player: / msg: / broadcast: / heal: / effect: <effet> <niveau> <secondes> / sound: <clé>.
 * Variables : {player} {uuid} {world} {x} {y} {z} {target}.
 */
public final class CustomItemListener implements Listener {
    private final VortexEssentials plugin;

    public CustomItemListener(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = plugin.customItems().idOf(hand);
        if (id == null) return;

        String base = right ? "RIGHT_CLICK" : "LEFT_CLICK";
        String act = p.isSneaking() && !plugin.customItems().actions(id, "SNEAK_" + base).isEmpty() ? "SNEAK_" + base : base;
        List<String> actions = plugin.customItems().actions(id, act);
        if (actions.isEmpty()) return;

        e.setCancelled(true); // pas de pose de bloc ni d'usage vanilla
        trigger(p, hand, id, actions, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = plugin.customItems().idOf(hand);
        if (id == null) return;
        List<String> actions = plugin.customItems().actions(id, "HIT");
        if (actions.isEmpty()) return;
        trigger(p, hand, id, actions, e.getEntity() instanceof LivingEntity le ? le.getName() : e.getEntity().getName());
    }

    private void trigger(Player p, ItemStack hand, String id, List<String> actions, String target) {
        ConfigurationSection s = plugin.customItems().sec(id);
        if (s == null) return;

        String perm = s.getString("permission");
        if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) {
            p.sendActionBar(Msg.c("§cVous n'avez pas la permission d'utiliser cet objet."));
            return;
        }
        int cd = s.getInt("cooldown", 0);
        if (cd > 0 && !plugin.cooldowns().tryUse(p.getUniqueId(), "citem_" + id, cd * 1000L)) {
            long left = plugin.cooldowns().remainingMs(p.getUniqueId(), "citem_" + id);
            p.sendActionBar(Msg.c("§cCooldown : §f" + (int) Math.ceil(left / 1000.0) + "s"));
            return;
        }
        for (String action : actions) run(p, action, target);

        if (s.getBoolean("consume")) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        }
    }

    private void run(Player p, String raw, String target) {
        Location l = p.getLocation();
        String a = raw.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString())
                .replace("{world}", p.getWorld().getName())
                .replace("{x}", String.valueOf(l.getBlockX())).replace("{y}", String.valueOf(l.getBlockY()))
                .replace("{z}", String.valueOf(l.getBlockZ())).replace("{target}", target == null ? "" : target);
        int i = a.indexOf(':');
        if (i < 0) return;
        String type = a.substring(0, i).trim().toLowerCase(Locale.ROOT);
        String arg = a.substring(i + 1).trim();

        switch (type) {
            case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg);
            case "player" -> p.performCommand(arg.startsWith("/") ? arg.substring(1) : arg);
            case "msg" -> p.sendMessage(CustomItems.color(arg));
            case "broadcast" -> Bukkit.broadcast(CustomItems.color(arg));
            case "heal" -> {
                try {
                    AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
                    double max = mh == null ? 20 : mh.getValue();
                    p.setHealth(Math.min(max, p.getHealth() + Double.parseDouble(arg)));
                } catch (NumberFormatException ignored) { }
            }
            case "effect" -> {
                String[] t = arg.split("\\s+");
                if (t.length < 3) return;
                PotionEffectType pt = PotionEffectType.getByKey(NamespacedKey.minecraft(t[0].toLowerCase(Locale.ROOT)));
                try {
                    if (pt != null) p.addPotionEffect(new PotionEffect(pt, Integer.parseInt(t[2]) * 20, Math.max(0, Integer.parseInt(t[1]) - 1)));
                } catch (NumberFormatException ignored) { }
            }
            case "sound" -> p.playSound(p.getLocation(), arg, 1f, 1f);
            default -> plugin.getLogger().warning("Action inconnue sur un item personnalisé : " + type);
        }
    }
}
