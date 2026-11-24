package fr.vortexsmp.vortexessentials.item;

import fr.vortexsmp.vortexessentials.Keys;
import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.mythic.MythicEffects;
import fr.vortexsmp.vortexessentials.tools.ToolOption;
import fr.vortexsmp.vortexessentials.tools.ToolSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Création, identification et tooltips des items custom. */
public final class ItemUtil {
    public static final int MAX_CHARGES = 5;
    private static final String BAR = "§8§m                                ";

    private ItemUtil() {}

    // ───────── Identification ─────────

    /** Id Vortex d'un item (nouvelle clé ou clé legacy VortexTools), ou null. */
    public static String idOf(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return null;
        PersistentDataContainer c = s.getItemMeta().getPersistentDataContainer();
        String id = c.get(Keys.VORTEX_ID, PersistentDataType.STRING);
        if (id == null && Keys.LEGACY_VORTEX_ID != null) {
            id = c.get(Keys.LEGACY_VORTEX_ID, PersistentDataType.STRING);
        }
        return id;
    }

    public static VortexItem get(ItemStack s) { return VortexItem.byId(idOf(s)); }

    public static boolean is(ItemStack s, VortexItem v) { return get(s) == v; }

    // ───────── Création ─────────

    public static ItemStack create(VortexItem v) {
        ItemStack stack = build(v);
        ItemMeta m = stack.getItemMeta();
        m.getPersistentDataContainer().set(Keys.VORTEX_ID, PersistentDataType.STRING, v.id());
        if (v == VortexItem.PANACEA) {
            m.getPersistentDataContainer().set(Keys.CHARGES, PersistentDataType.INTEGER, MAX_CHARGES);
        }
        stack.setItemMeta(m);
        return stack;
    }

    /** Version "décor" pour les menus : même apparence, sans id Vortex. */
    public static ItemStack icon(VortexItem v, String... extraLore) {
        ItemStack stack = build(v);
        ItemMeta m = stack.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String l : extraLore) lore.add(Msg.plain(l));
        m.lore(lore);
        stack.setItemMeta(m);
        return stack;
    }

    private static ItemStack build(VortexItem v) {
        ItemStack stack = new ItemStack(v.base());
        ItemMeta m = stack.getItemMeta();

        m.displayName(Msg.plain(v.category().color() + "§l" + v.displayName()));
        m.lore(loreFor(v, null));

        VortexEssentials pl = VortexEssentials.get();

        for (Map.Entry<String, Integer> en : v.enchants().entrySet()) {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(en.getKey()));
            if (e != null) m.addEnchant(e, en.getValue(), true);
        }
        // Surcharges : item-enchants.<id>.<enchant>: niveau  (0 = retirer)
        if (pl != null) {
            ConfigurationSection ov = pl.getConfig().getConfigurationSection("item-enchants." + v.id());
            if (ov != null) {
                for (String k : ov.getKeys(false)) {
                    Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k));
                    if (e == null) continue;
                    int lvl = ov.getInt(k, 0);
                    if (lvl <= 0) m.removeEnchant(e);
                    else m.addEnchant(e, lvl, true);
                }
            }
            // Modèles du resource pack (désactivé par défaut)
            boolean crossbow = v == VortexItem.BLAZING_CROSSBOW || v == VortexItem.VOIDRENDER;
            if (pl.getConfig().getBoolean("use-custom-models", false) && v.hasPackModel()
                    && (!crossbow || pl.getConfig().getBoolean("crossbow-models", true))) {
                m.setItemModel(NamespacedKey.fromString("vortex:" + v.id()));
                if (v.isMythicArmor()) { // l'armure portée utilise aussi la texture du pack
                    org.bukkit.inventory.meta.components.EquippableComponent eq = m.getEquippable();
                    eq.setModel(NamespacedKey.fromString("vortex:" + v.id()));
                    m.setEquippable(eq);
                }
            }
        }

        if (v.isMythicWeapon() || v.category() == Category.LEGENDARY_WEAPON) {
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        if (m instanceof PotionMeta pm) {
            Color c = potionColor(v);
            if (c != null) pm.setColor(c);
            if (v == VortexItem.ALL_IN_ONE) { // Force I, Vitesse I, Résistance au feu I : 8 minutes
                pm.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, 9600, 0), true);
                pm.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 9600, 0), true);
                pm.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 9600, 0), true);
            }
            if (v == VortexItem.HOLY_MASTER) { // Soin II + Maître tortue (Résistance V, Lenteur V) : 30 s
                pm.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1), true);
                pm.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 4), true);
                pm.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 4), true);
            }
            if (v == VortexItem.AGONY) {
                pm.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1), true);
                pm.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 0), true);
            }
        }
        if (m.getEnchants().isEmpty()) m.setEnchantmentGlintOverride(true);

        stack.setItemMeta(m);
        return stack;
    }

    // ───────── Tooltips ─────────

    /** Lore complet d'un item. {@code state} = PDC de l'item (réglages, charges) ou null pour les valeurs par défaut. */
    public static List<Component> loreFor(VortexItem v, PersistentDataContainer state) {
        String col = v.category().color();
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.plain(col + "✦ §l" + v.category().label().toUpperCase() + " §r" + col + "✦"));
        lore.add(Msg.plain(BAR));
        for (String line : v.lore()) lore.add(Msg.plain(decorate(line)));

        if (v.isMythicWeapon()) {
            String fx = MythicEffects.describe(v);
            if (!fx.isEmpty()) lore.add(Msg.plain("§b❖ §7Dans l'inventaire : §f" + fx));
            String water = MythicEffects.describeWater(v);
            if (!water.isEmpty()) lore.add(Msg.plain("§3≈ §7Dans l'eau : §f" + water));
            String worn = fr.vortexsmp.vortexessentials.mythic.ArmorListener.describe(v);
            if (!worn.isEmpty()) lore.add(Msg.plain("§b❖ §7Porté : §f" + worn));
            VortexEssentials pl = VortexEssentials.get();
            double wd = pl == null ? 2.0 : pl.getConfig().getDouble("mythic-weapons.sealord.water-attack-damage", 2.0);
            if (v == VortexItem.SEALORD && wd > 0) lore.add(Msg.plain("§3≈ §7Dans l'eau : §f+" + (wd % 1 == 0 ? String.valueOf((int) wd) : String.valueOf(wd)) + " dégâts d'attaque"));
        }
        if (v == VortexItem.PANACEA) {
            Integer n = state == null ? null : state.get(Keys.CHARGES, PersistentDataType.INTEGER);
            lore.add(Component.empty());
            lore.add(Msg.plain("§d❤ §7Charges : §f" + (n == null ? MAX_CHARGES : n) + "§7/§f" + MAX_CHARGES));
        }

        List<ToolOption> opts = ToolSettings.available(v);
        if (!opts.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Msg.plain("§e⚙ §7Réglages §8(sneak + clic droit)"));
            for (ToolOption o : opts) {
                boolean on = ToolSettings.read(state, o);
                lore.add(Msg.plain("  §8▪ §7" + o.label() + " §8: " + (on ? "§aActivé" : "§cDésactivé")));
            }
        }
        if (v.isMythicWeapon()) {
            lore.add(Component.empty());
            lore.add(Msg.plain("§c✦ Forgeable une seule fois par serveur"));
            if (v.isUnmodifiable()) lore.add(Msg.plain("§8Ne peut être ni enchantée ni modifiée."));
        }
        lore.add(Msg.plain(BAR));
        lore.add(Msg.plain("§8VortexEssentials"));
        return lore;
    }

    private static String decorate(String line) {
        String plain = line.replaceAll("§.", "");
        if (plain.startsWith("Passif")) return "§a✚ " + line;
        if (plain.contains("clic") || plain.contains("Sneak") || plain.startsWith("Attaque")) return "§6⚔ " + line;
        if (line.startsWith("§8")) return "§8↑ " + line;
        return "§8▪ " + line;
    }

    /** Recalcule le lore d'un item Vortex d'après son état (réglages, charges). */
    public static void refreshLore(ItemStack s) {
        VortexItem v = get(s);
        if (v == null) return;
        ItemMeta m = s.getItemMeta();
        m.lore(loreFor(v, m.getPersistentDataContainer()));
        s.setItemMeta(m);
    }

    // ───────── Charges de la Panacea ─────────

    public static int charges(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return 0;
        PersistentDataContainer c = s.getItemMeta().getPersistentDataContainer();
        Integer n = c.get(Keys.CHARGES, PersistentDataType.INTEGER);
        if (n == null && Keys.LEGACY_CHARGES != null) {
            try {
                n = c.get(Keys.LEGACY_CHARGES, PersistentDataType.INTEGER);
            } catch (IllegalArgumentException ignored) {
                n = null;
            }
        }
        return n == null ? MAX_CHARGES : n;
    }

    /** Fixe les charges (0 à MAX) et met à jour le tooltip. */
    public static ItemStack setCharges(ItemStack s, int n) {
        n = Math.max(0, Math.min(MAX_CHARGES, n));
        ItemMeta m = s.getItemMeta();
        m.getPersistentDataContainer().set(Keys.CHARGES, PersistentDataType.INTEGER, n);
        m.lore(loreFor(VortexItem.PANACEA, m.getPersistentDataContainer()));
        s.setItemMeta(m);
        return s;
    }

    private static Color potionColor(VortexItem v) {
        return switch (v) {
            case PANACEA -> Color.fromRGB(0xFF69B4);
            case AGONY -> Color.fromRGB(0x5B0A7A);
            case ALL_IN_ONE -> Color.fromRGB(0xFFA826);
            case HOLY_MASTER -> Color.fromRGB(0xFFF3A0);
            default -> null;
        };
    }
}
