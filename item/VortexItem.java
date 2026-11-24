package fr.vortexsmp.vortexessentials.item;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue des 23 items custom. L'identifiant (PDC) est le nom de l'enum en minuscules.
 * Les capacités sont décrites dans le lore ; leur logique arrive dans les étapes suivantes.
 */
public enum VortexItem {

    // ───────── Consommables (3) ─────────
    PANACEA(Category.CONSUMABLE, Material.POTION, "Panacea", e(),
            "§7Soigne §f3,5 cœurs §7par utilisation (§f5 charges§7).",
            "§7Clic droit sur une ruche avec du miel : §f+1 charge§7."),
    CRYSTALLIZATION_SHARD(Category.CONSUMABLE, Material.AMETHYST_SHARD, "Crystallization Shard", e(),
            "§7Clic droit : immobile, invulnérable et sans recul",
            "§7pendant §f8s§7."),
    AGONY(Category.CONSUMABLE, Material.SPLASH_POTION, "Agony", e(),
            "§7À l'impact : Dégâts instantanés II",
            "§7et Faiblesse §f60s §7sur les entités touchées."),

    // ───────── Outils (8) ─────────
    SMELTER_PICKAXE(Category.TOOL, Material.IRON_PICKAXE, "Smelter's Pickaxe", e("efficiency", 1, "unbreaking", 1),
            "§7Cuit les minerais automatiquement."),
    SUPER_SMELTER_PICKAXE(Category.TOOL, Material.DIAMOND_PICKAXE, "Super Smelter's Pickaxe", e("efficiency", 2, "unbreaking", 1, "fortune", 1),
            "§7Cuit les minerais automatiquement, en plus rapide."),
    NETHERITE_SMELTER_PICKAXE(Category.TOOL, Material.NETHERITE_PICKAXE, "Netherite Smelter's Pickaxe", e("efficiency", 3, "unbreaking", 2, "fortune", 2),
            "§7Cuit les minerais automatiquement.",
            "§8Amélioration de la Super Smelter's Pickaxe."),
    EXPLOSIVE_PICKAXE(Category.TOOL, Material.DIAMOND_PICKAXE, "Explosive Pickaxe", e("efficiency", 2, "unbreaking", 1, "fortune", 1),
            "§7Détruit une zone §f2×2 §7et récupère tous les drops."),
    NETHERITE_EXPLOSIVE_PICKAXE(Category.TOOL, Material.NETHERITE_PICKAXE, "Netherite Explosive Pickaxe", e("efficiency", 3, "unbreaking", 2, "fortune", 2),
            "§7Détruit une zone §f3×3 §7et récupère tous les drops.",
            "§8Amélioration de l'Explosive Pickaxe."),
    LUMBERJACK_AXE(Category.TOOL, Material.IRON_AXE, "Lumberjack's Axe", e("efficiency", 1, "unbreaking", 1),
            "§7Abat l'arbre entier en un coup."),
    SUPER_LUMBERJACK_AXE(Category.TOOL, Material.DIAMOND_AXE, "Super Lumberjack's Axe", e("efficiency", 2, "unbreaking", 1, "fortune", 1),
            "§7Abat l'arbre entier en un coup.",
            "§8Amélioration de la Lumberjack's Axe."),
    NETHERITE_LUMBERJACK_AXE(Category.TOOL, Material.NETHERITE_AXE, "Netherite Lumberjack's Axe", e("efficiency", 3, "unbreaking", 2, "fortune", 2),
            "§7Abat l'arbre entier en un coup.",
            "§8Amélioration de la Super Lumberjack's Axe."),

    // ───────── Outils mythiques (2) ─────────
    FRACTURED(Category.MYTHIC_TOOL, Material.NETHERITE_PICKAXE, "Fractured", e(),
            "§7Casse §finstantanément §7tous les blocs.",
            "§7Mine une zone cubique §f3×3 §7et cuit les minerais."),
    TERRALOOP(Category.MYTHIC_TOOL, Material.NETHERITE_AXE, "Terraloop", e(),
            "§7Casse §finstantanément §7tous les blocs.",
            "§7Zone cubique §f3×3 §7et abattage d'arbres en un coup."),
    JUGAS(Category.MYTHIC_TOOL, Material.NETHERITE_SHOVEL, "Jugas", e(),
            "§7Pelle mythique : casse §finstantanément §7tous les blocs.",
            "§7Zone cubique §f3×3 §7et autosmelt (sable, argile...)."),

    // ───────── Armes légendaires (7) ─────────
    BLAZING_CROSSBOW(Category.LEGENDARY_WEAPON, Material.CROSSBOW, "Blazing Crossbow", e(),
            "§7Tire des flèches enflammées.",
            "§7Sneak + clic gauche §8(cd 25s)§7 : §fBarrage §7— 5 flèches enflammées.",
            "§7Passif : tous les §f4 §7tirs, le carreau §fexplose §7à l'impact."),
    REAPER_SCYTHE(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Reaper Scythe", leg(),
            "§7Sneak + clic gauche §8(cd 45s)§7 : §fDrain §7— prend §f25%§7 des PV de la cible,",
            "§7vous soigne d'autant et copie ses effets de potion.",
            "§7Sneak + clic droit §8(cd 35s)§7 : §fMoisson d'âmes §7— draine les ennemis à 6 blocs."),
    EXCALIBUR(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Excalibur", leg(),
            "§7Clic droit §8(cd 45s)§7 : annule les §f8 prochains coups",
            "§7reçus pendant §f10s§7."),
    EVOKER_WAND(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Evoker Wand", leg(),
            "§7Sneak + clic gauche §8(cd 12s)§7 : 3 lignes de crocs.",
            "§7Clic droit §8(cd 25s)§7 : renvoie les projectiles à 5 blocs (8s)."),
    CHRONO_SWORD(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Chrono Sword", leg(),
            "§7Sneak + clic droit : marque un point dans le temps.",
            "§7Re-sneak + clic droit (ou mort) : rembobine position et vie.",
            "§8Cooldown 50s (100s si déclenché par la mort)."),
    SCEPTRE_OF_ARACHNE(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Sceptre of Arachne", leg(),
            "§7Sneak + clic gauche : empower le prochain tir.",
            "§7Clic droit : toiles + 6 Cave Spiders sur l'ennemi le plus proche.",
            "§7Les toiles se dissipent à votre approche (passage libre)."),
    MAGMA_CLUB(Category.LEGENDARY_WEAPON, Material.NETHERITE_SWORD, "Magma Club", leg(),
            "§7§f35%§7 de chance d'enflammer à l'attaque.",
            "§7Fire Resistance tant qu'elle est en main.",
            "§7Sneak + clic gauche §8(cd 20s)§7 : §fÉruption §7— enflamme et projette les ennemis proches.",
            "§7Sneak + clic droit §8(cd 35s)§7 : §fVague de magma §7— ligne de feu devant vous."),

    // ───────── Armes mythiques (3) ─────────
    SEALORD(Category.MYTHIC_WEAPON, Material.TRIDENT, "Sealord",
            e("riptide", 3, "sharpness", 6, "breach", 2, "sweeping_edge", 5, "fire_aspect", 3, "looting", 5),
            "§7Passif : puissant dans l'eau (effets ci-dessous).",
            "§7Sneak + clic gauche §8(cd 20s)§7 : §fTidal Slam",
            "§7Sneak + clic droit §8(cd 60s)§7 : §fMaelstrom"),
    LIGHTSPEED(Category.MYTHIC_WEAPON, Material.NETHERITE_SWORD, "Lightspeed",
            e("sharpness", 6, "breach", 2, "sweeping_edge", 5, "fire_aspect", 3, "looting", 5, "efficiency", 10),
            "§7Passif : immunité aux dégâts de chute.",
            "§7Clic droit §8(cd 8s)§7 : §fDash",
            "§7Sneak + clic droit §8(cd 45s)§7 : §fSonic Slash"),
    MINTUS(Category.MYTHIC_WEAPON, Material.NETHERITE_AXE, "Mintus",
            e("sharpness", 6, "breach", 2, "fire_aspect", 2, "fortune", 5, "looting", 5, "efficiency", 10),
            "§7Passif : régénère près des villageois.",
            "§7Sneak + clic gauche §8(cd 30s)§7 : §fRallying Cheer",
            "§7Sneak + clic droit §8(cd 60s)§7 : §fVillage's Wrath"),
    SKYFALL(Category.MYTHIC_WEAPON, Material.BOW, "Skyfall",
            e("power", 8, "punch", 3, "flame", 1, "infinity", 1),
            "§7Passif : flèches pleine puissance perforantes (3 cibles) et plus rapides.",
            "§7Sneak + clic gauche §8(cd 40s)§7 : §fArrow Rain §7— pluie de flèches sur la cible visée."),
    VOIDRENDER(Category.MYTHIC_WEAPON, Material.CROSSBOW, "Voidrender",
            e("quick_charge", 5, "piercing", 5),
            "§7Passif : chargement quasi instantané, carreaux perforants.",
            "§7Sneak + clic gauche §8(cd 30s)§7 : §fRail Shot §7— rayon perforant qui brise les boucliers.",
            "§7Sneak + tir §8(cd 20s)§7 : §fBlink Bolt §7— vous vous téléportez à l'impact."),
    UMBRAL_SCYTHE(Category.SECRET_WEAPON, Material.MACE, "Umbral Scythe",
            e("density", 5, "breach", 4, "wind_burst", 1, "fire_aspect", 2, "looting", 5),
            "§7Brise les boucliers comme une hache.",
            "§7Passif : aucun dégât de chute, smash attacks de mace.",
            "§7Sneak + clic droit §8(cd 16s)§7 : §fEclipse Leap §7— bond immense : éclipse les ennemis au départ, écrase le sol à l'atterrissage.",
            "§7Sneak + clic gauche §8(3 charges, cd 20s)§7 : §fReaper Chains §7— 3 charges libres : blessent l'ennemi accroché, onde de choc au sol.",
            "§7Touche d'échange de main §8(cd 25s)§7 : §fShatterer §7— onde de choc dévastatrice : dégâts, projection en l'air et paralysie."),
    COLOSSUS_PLATE(Category.MYTHIC_WEAPON, Material.NETHERITE_CHESTPLATE, "Colossus Plate",
            e("protection", 6, "projectile_protection", 6, "thorns", 3),
            "§7Passif : renvoie §f20% §7des dégâts de mêlée à l'attaquant.",
            "§7Double sneak §8(cd 60s)§7 : §fFortress §7— Résistance IV, sans recul et onde de choc."),
    PHANTOM_TREADS(Category.MYTHIC_WEAPON, Material.NETHERITE_BOOTS, "Phantom Treads",
            e("protection", 6, "feather_falling", 8, "depth_strider", 4, "soul_speed", 5),
            "§7Passif : aucun dégât de chute.",
            "§7Double sneak §8(cd 25s)§7 : §fShadow Step §7— dash en avant et invisibilité 3s."),
    RADIANT_CROWN(Category.MYTHIC_WEAPON, Material.NETHERITE_HELMET, "Radiant Crown",
            e("protection", 6, "respiration", 5, "aqua_affinity", 1, "projectile_protection", 6),
            "§7Passif : immunisé à la cécité, l'obscurité et la nausée.",
            "§7Double sneak §8(cd 45s)§7 : §fSolar Flare §7— aveugle et révèle les ennemis, vous soigne."),
    EMBER_GREAVES(Category.MYTHIC_WEAPON, Material.NETHERITE_LEGGINGS, "Ember Greaves",
            e("protection", 6, "fire_protection", 10, "swift_sneak", 5),
            "§7Passif : immunisé au feu, les ennemis qui vous frappent prennent feu.",
            "§7Double sneak §8(cd 40s)§7 : §fFlame Nova §7— enflamme et repousse les ennemis proches."),
    STORMPIERCER(Category.MYTHIC_WEAPON, Material.NETHERITE_SPEAR, "Stormpiercer",
            e("sharpness", 6, "fire_aspect", 2, "looting", 5, "lunge", 3, "breach", 2),
            "§7Passif : brise les boucliers ; tous les 4 coups, la foudre jaillit en chaîne.",
            "§7Sneak + clic droit §8(cd 14s)§7 : §fThunder Javelin §7— javelot de foudre perforant qui traverse tous les ennemis.",
            "§7Touche d'échange de main §8(cd 18s)§7 : §fSkewer Dash §7— charge fulgurante qui empale et projette tout sur votre passage."),
    ALL_IN_ONE(Category.CONSUMABLE, Material.SPLASH_POTION, "All in One", e(),
            "§7Potion jetable : §fForce I§7, §fVitesse I§7 et §fRésistance au feu I§7 pendant §f8 min§7."),
    HOLY_MASTER(Category.CONSUMABLE, Material.SPLASH_POTION, "Holy Master", e(),
            "§7Potion jetable : §fSoin instantané II§7, puis §fRésistance V§7 et §fLenteur V§7 pendant §f30 s§7 (Maître tortue).");

    private final Category category;
    private final Material base;
    private final String displayName;
    private final Map<String, Integer> enchants;
    private final List<String> lore;

    VortexItem(Category category, Material base, String displayName,
               Map<String, Integer> enchants, String... lore) {
        this.category = category;
        this.base = base;
        this.displayName = displayName;
        this.enchants = enchants;
        this.lore = List.of(lore);
    }

    /** Construit une map d'enchantements à partir de paires clé, niveau. */
    private static Map<String, Integer> e(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    /** Enchantements des armes légendaires. */
    private static Map<String, Integer> leg() {
        return e("sharpness", 5, "breach", 1, "sweeping_edge", 3, "fire_aspect", 2, "looting", 3, "efficiency", 4);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public Category category() { return category; }
    public Material base() { return base; }
    public String displayName() { return displayName; }
    public Map<String, Integer> enchants() { return enchants; }
    public List<String> lore() { return lore; }

    public boolean isMythicWeapon() { return category == Category.MYTHIC_WEAPON || category == Category.SECRET_WEAPON; }

    /** Toutes les armes et armures mythiques (catégories mythique et secrète). */
    public static List<VortexItem> mythics() {
        List<VortexItem> out = new ArrayList<>();
        for (VortexItem v : values()) if (v.isMythicWeapon()) out.add(v);
        return out;
    }

    /** Arme secrète : cachée des menus, des listes, de l'aperçu de recette et du wiki. */
    public boolean isSecret() { return false; }

    /** Ni enchantement, ni enclume, ni meule, ni forge : l'item reste tel quel. */
    public boolean isUnmodifiable() { return this == SKYFALL || this == VOIDRENDER || this == UMBRAL_SCYTHE || isMythicArmor(); }

    /** Armures mythiques : portées (effets, passif) et capacité par double sneak (ArmorListener). */
    public boolean isMythicArmor() { return this == COLOSSUS_PLATE || this == PHANTOM_TREADS || this == RADIANT_CROWN || this == EMBER_GREAVES; }

    /** Capacités gérées par MythicRangedListener / SecretWeaponListener (et non par MythicAbilities). */
    public boolean customAbilities() { return this == SKYFALL || this == VOIDRENDER || this == UMBRAL_SCYTHE || this == STORMPIERCER || isMythicArmor(); }

    /** Modèle dans le resource pack (les armes ajoutées en 1.9 gardent l'apparence vanilla). */
    public boolean hasPackModel() { return true; }

    public static VortexItem byId(String raw) {
        if (raw == null) return null;
        String n = raw.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (VortexItem v : values()) if (v.id().equals(n)) return v;
        return null;
    }

    public static List<VortexItem> ofCategory(Category c) {
        List<VortexItem> out = new ArrayList<>();
        for (VortexItem v : values()) if (v.category == c && !v.isSecret()) out.add(v);
        return out;
    }
}
