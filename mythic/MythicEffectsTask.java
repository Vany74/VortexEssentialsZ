package fr.vortexsmp.vortexessentials.mythic;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.item.ItemUtil;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import fr.vortexsmp.vortexessentials.tools.ToolOption;
import fr.vortexsmp.vortexessentials.tools.ToolSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tâche périodique (10 ticks) :
 *  - effets permanents des armes mythiques portées (configurables : MythicEffects) ;
 *  - passifs : régénération dans l'eau (Sealord) / près des villageois (Mintus) ;
 *  - bonus en main : Hâte des Smelters rapides (sauf vitesse vanilla), Magma Club, Sceptre of Arachne.
 * Les outils mythiques ne donnent plus de Hâte : ils cassent instantanément (MythicToolListener).
 */
public final class MythicEffectsTask extends BukkitRunnable {
    private static final Set<Material> RELEVANT = EnumSet.of(
            Material.TRIDENT, Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_SHOVEL, Material.DIAMOND_PICKAXE, Material.CROSSBOW, Material.BOW, Material.MACE, Material.NETHERITE_SPEAR);

    private final VortexEssentials plugin;
    private final Map<VortexItem, Map<PotionEffectType, Integer>> cache = new EnumMap<>(VortexItem.class);
    private final Map<VortexItem, Map<PotionEffectType, Integer>> waterCache = new EnumMap<>(VortexItem.class);
    private final NamespacedKey waterDamageKey;
    private int tick;

    public MythicEffectsTask(VortexEssentials plugin) {
        this.plugin = plugin;
        this.waterDamageKey = new NamespacedKey(plugin, "sealord_water_damage");
        reload();
    }

    public void reload() {
        cache.clear();
        waterCache.clear();
        for (VortexItem v : VortexItem.mythics()) {
            cache.put(v, MythicEffects.amplifiers(v));
            waterCache.put(v, MythicEffects.waterAmplifiers(v));
        }
    }

    /** Filtre rapide : évite de cloner le meta de tous les items. */
    public static boolean relevant(Material m) {
        return RELEVANT.contains(m);
    }

    /** Armes mythiques portées (inventaire, armure, main secondaire). */
    public static Set<VortexItem> carried(Player p) {
        Set<VortexItem> out = EnumSet.noneOf(VortexItem.class);
        scan(p.getInventory().getStorageContents(), out);
        scan(p.getInventory().getArmorContents(), out);
        scan(p.getInventory().getExtraContents(), out);
        return out;
    }

    private static void scan(ItemStack[] items, Set<VortexItem> out) {
        for (ItemStack s : items) {
            if (s == null || !relevant(s.getType())) continue;
            VortexItem v = ItemUtil.get(s);
            if (v != null && v.isMythicWeapon()) out.add(v);
        }
    }

    @Override
    public void run() {
        tick++;
        boolean half = tick % 2 == 0; // une fois par seconde

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;

            ItemStack main = p.getInventory().getItemInMainHand();
            if (relevant(main.getType())) {
                VortexItem held = ItemUtil.get(main);
                boolean vanilla = ToolSettings.get(main, ToolOption.VANILLA_SPEED);
                if (held == VortexItem.SUPER_SMELTER_PICKAXE || held == VortexItem.NETHERITE_SMELTER_PICKAXE) {
                    int lvl = plugin.getConfig().getInt("tools.super-smelter-haste", 1);
                    if (!vanilla && lvl > 0) give(p, PotionEffectType.HASTE, lvl - 1);
                } else if (held == VortexItem.FRACTURED || held == VortexItem.TERRALOOP || held == VortexItem.JUGAS) {
                    int lvl = plugin.getConfig().getInt("mythic-tools.haste-level", 0); // 0 = aucune Hâte
                    if (!vanilla && lvl > 0) give(p, PotionEffectType.HASTE, lvl - 1);
                } else if (held == VortexItem.MAGMA_CLUB) {
                    give(p, PotionEffectType.FIRE_RESISTANCE, 0);
                } else if (held == VortexItem.SCEPTRE_OF_ARACHNE) {
                    stripWebs(p);
                }
            }

            Set<VortexItem> c = carried(p);
            // Sealord : +dégâts d'attaque dans l'eau (attribut, remplace la Force)
            updateSealordDamage(p, c.contains(VortexItem.SEALORD) && p.isInWater()
                    && plugin.getConfig().getBoolean("mythic-weapons.sealord.regen-in-water", true));
            if (c.isEmpty()) continue;

            for (VortexItem v : c) {
                for (Map.Entry<PotionEffectType, Integer> e : cache.getOrDefault(v, Map.of()).entrySet()) {
                    give(p, e.getKey(), e.getValue());
                }
            }
            // Effets passifs dans l'eau (Sealord : respiration, grâce du dauphin, force, régénération...)
            if (p.isInWater() && plugin.getConfig().getBoolean("mythic-weapons.sealord.regen-in-water", true)) {
                for (VortexItem v : c) {
                    for (Map.Entry<PotionEffectType, Integer> e : waterCache.getOrDefault(v, Map.of()).entrySet()) {
                        give(p, e.getKey(), e.getValue());
                    }
                }
            }
            if (half && c.contains(VortexItem.MINTUS) && nearVillager(p)) {
                give(p, PotionEffectType.REGENERATION, 1);
            }
        }
    }

    /**
     * Pose l'effet (60 ticks) et le renouvelle quand il reste ≤ 20 ticks (sinon la Régénération ne se
     * déclencherait jamais). Un effet plus fort venant d'ailleurs n'est jamais écrasé.
     */
    private void give(Player p, PotionEffectType type, int amplifier) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && (cur.getAmplifier() > amplifier
                || (cur.getAmplifier() == amplifier && cur.getDuration() > 20))) return;
        p.addPotionEffect(new PotionEffect(type, 60, amplifier, true, false, true));
    }

    private void updateSealordDamage(Player p, boolean on) {
        AttributeInstance ai = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (ai == null) return;
        boolean has = ai.getModifier(waterDamageKey) != null;
        if (on && !has) {
            ai.addTransientModifier(new AttributeModifier(waterDamageKey,
                    plugin.getConfig().getDouble("mythic-weapons.sealord.water-attack-damage", 2.0), AttributeModifier.Operation.ADD_NUMBER));
        } else if (!on && has) {
            ai.removeModifier(waterDamageKey);
        }
    }

    /** Retire les toiles autour des pieds et de la tête (immunité du Sceptre of Arachne). */
    public static void stripWebs(Player p) {
        Block feet = p.getLocation().getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (feet.getType() == Material.COBWEB) feet.setType(Material.AIR);
        if (head.getType() == Material.COBWEB) head.setType(Material.AIR);
    }

    private boolean nearVillager(Player p) {
        double r = plugin.getConfig().getDouble("mythic-weapons.mintus.regen-villager-radius", 10);
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (en instanceof org.bukkit.entity.Villager) return true;
        }
        return false;
    }
}
