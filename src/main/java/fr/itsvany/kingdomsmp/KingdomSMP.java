package fr.itsvany.kingdomsmp;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class KingdomSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final List<String> normal = List.of("Nature", "Wrath", "Heaven", "Gl1tched", "Adaptation", "Wealth", "Fractionate", "Void", "Parade");
    private final List<String> legendary = List.of("King", "Tyrannus", "Hériclés");
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<String, Long> killTimes = new HashMap<>();
    private final Map<UUID, Integer> adaptationHits = new HashMap<>();
    private final Map<UUID, Long> adaptationEffectEnds = new HashMap<>();
    private final Map<UUID, Long> glitchTimes = new HashMap<>();
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    // Ne retire que les effets posés par KingdomSMP, jamais les potions normales du joueur.
    private final Map<UUID, Set<PotionEffectType>> managedEffects = new HashMap<>();
    private final Map<UUID, String> pendingAdminCrowns = new HashMap<>();
    private final Map<UUID, Integer> eventProgress = new HashMap<>();
    // Un joueur ne peut recevoir la récompense qu’une fois par événement actif.
    private final Set<UUID> eventCompleted = new HashSet<>();
    private final Set<UUID> ritualInProgress = new HashSet<>();
    private final Set<UUID> ritualInterrupted = new HashSet<>();
    private boolean crownEventActive = false;
    private long crownEventEndsAt = 0L;
    private long nextCrownEventAt = 0L;
    private String crownEventType = "MONSTERS";
    private NamespacedKey crownKey, materialKey, upgraderKey, repairerKey, crownChangerKey, ritualTokenKey, menuActionKey;
    private File dataFile;
    private YamlConfiguration storage;

    private static final class Profile {
        String crown = "", language = "fr";
        int kills, deaths;
        final Set<String> unlocked = new HashSet<>();
        final Set<UUID> trusted = new HashSet<>();
        final Map<Integer, Long> cooldowns = new HashMap<>();
        int crownMaterials = 10;
        boolean crownBroken = false, crownShattered = false;
        boolean kingAbsorbed = false, pactActive = false, pactApproved = false;
        int pactKills = 0;
        long emptySince = 0L;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages_fr.yml"); saveResourceIfMissing("messages_en.yml"); saveResourceIfMissing("messages_es.yml");
        crownKey = new NamespacedKey(this, "kingdom_crown"); materialKey = new NamespacedKey(this, "crown_material"); upgraderKey = new NamespacedKey(this, "crown_upgrader"); repairerKey = new NamespacedKey(this, "crown_repairer"); crownChangerKey = new NamespacedKey(this, "crown_changer"); ritualTokenKey = new NamespacedKey(this, "ritual_token"); menuActionKey = new NamespacedKey(this, "menu_action");
        dataFile = new File(getDataFolder(), "players.yml"); loadStorage();
        getServer().getPluginManager().registerEvents(this, this);
        registerCraftingRecipes();
        Objects.requireNonNull(getCommand("kingdom")).setExecutor(this);
        Objects.requireNonNull(getCommand("kingdom")).setTabCompleter(this);
        new BukkitRunnable() { @Override public void run() { for (Player p : Bukkit.getOnlinePlayers()) { applyPassives(p); tickCrownDurability(p); } } }.runTaskTimer(this, 20L, 20L);
        long interval = Math.max(15, getConfig().getLong("settings.intervalle-sauvegarde-secondes", 60));
        new BukkitRunnable() { @Override public void run() { saveStorage(); } }.runTaskTimer(this, interval * 20L, interval * 20L);
        startCrownEvents();
        getLogger().info("KingdomSMP 0.5.1 activé — cinq événements, extraction extrême et cycle de trois heures.");
    }
    @Override public void onDisable() { saveStorage(); }
    private void saveResourceIfMissing(String name) { if (!new File(getDataFolder(), name).exists()) saveResource(name, false); }
    private Profile profile(UUID id) { return profiles.computeIfAbsent(id, ignored -> new Profile()); }
    private boolean isLegendary(String name) { return legendary.contains(name); }
    private List<String> allCrowns() { List<String> all = new ArrayList<>(normal); all.addAll(legendary); return all; }

    private void loadStorage() {
        storage = dataFile.exists() ? YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();
        ConfigurationSection root = storage.getConfigurationSection("players"); if (root == null) return;
        for (String idText : root.getKeys(false)) try {
            UUID id = UUID.fromString(idText); ConfigurationSection s = root.getConfigurationSection(idText); if (s == null) continue;
            Profile p = new Profile(); p.crown = s.getString("couronne", ""); if (p.crown.equals("Heavenly")) p.crown = "Heaven"; if (p.crown.equals("Sovereign")) p.crown = "Void"; p.kills = s.getInt("eliminations", 0); p.deaths = s.getInt("morts", 0); p.language = normalizeLanguage(s.getString("langue", "fr"));
            p.unlocked.addAll(s.getStringList("debloques")); p.crownMaterials = Math.max(0, s.getInt("materiaux-couronne", getConfig().getInt("couronne-durabilite.vies-initiales", 10))); p.crownBroken = s.getBoolean("couronne-cassee", false); p.crownShattered = s.getBoolean("couronne-brisee", false); p.kingAbsorbed = s.getBoolean("couronne-absorbee-par-king", false); p.pactActive = s.getBoolean("pacte-actif", false); p.pactApproved = s.getBoolean("pacte-approuve", false); p.pactKills = Math.max(0,s.getInt("pacte-eliminations",0)); p.emptySince = s.getLong("couronne-vide-depuis", 0L); for (String trusted : s.getStringList("trusted")) try { p.trusted.add(UUID.fromString(trusted)); } catch (IllegalArgumentException ignored) {}
            ConfigurationSection cds = s.getConfigurationSection("cooldowns"); if (cds != null) for (String k : cds.getKeys(false)) try { p.cooldowns.put(Integer.parseInt(k), cds.getLong(k)); } catch (NumberFormatException ignored) {}
            profiles.put(id, p);
        } catch (IllegalArgumentException e) { getLogger().warning("UUID invalide dans players.yml : " + idText); }
    }
    private synchronized void saveStorage() {
        if (storage == null) storage = new YamlConfiguration(); storage.set("players", null);
        for (var e : profiles.entrySet()) { String path = "players." + e.getKey(); Profile p = e.getValue(); storage.set(path+".couronne", p.crown); storage.set(path+".eliminations", p.kills); storage.set(path+".morts", p.deaths); storage.set(path+".langue", p.language); storage.set(path+".debloques", new ArrayList<>(p.unlocked)); storage.set(path+".materiaux-couronne", p.crownMaterials); storage.set(path+".couronne-cassee", p.crownBroken); storage.set(path+".couronne-brisee", p.crownShattered); storage.set(path+".couronne-absorbee-par-king", p.kingAbsorbed); storage.set(path+".pacte-actif", p.pactActive); storage.set(path+".pacte-approuve", p.pactApproved); storage.set(path+".pacte-eliminations", p.pactKills); storage.set(path+".couronne-vide-depuis", p.emptySince); storage.set(path+".trusted", p.trusted.stream().map(UUID::toString).toList());
            for (var cd : p.cooldowns.entrySet()) storage.set(path+".cooldowns."+cd.getKey(), cd.getValue()); }
        try { getDataFolder().mkdirs(); storage.save(dataFile); } catch (IOException e) { getLogger().severe("Sauvegarde impossible : " + e.getMessage()); }
    }
    private String normalizeLanguage(String lang) { return List.of("fr","en","es").contains(lang == null ? "" : lang.toLowerCase(Locale.ROOT)) ? lang.toLowerCase(Locale.ROOT) : "fr"; }
    private String tr(CommandSender sender, String key) { String lang = sender instanceof Player p ? profile(p.getUniqueId()).language : getConfig().getString("settings.langue-console", "fr"); return tr(lang, key); }
    private String tr(String lang, String key) {
        File f = new File(getDataFolder(), "messages_" + normalizeLanguage(lang) + ".yml"); YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String value = y.getString(key); if (value == null) value = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages_fr.yml")).getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', value == null ? key : value);
    }
    private void msg(CommandSender s, String key) { s.sendMessage(tr(s,"prefix") + tr(s,key)); }
    private void msg(CommandSender s, String key, Map<String,String> vars) { String value = tr(s,"prefix") + tr(s,key); for (var e : vars.entrySet()) value = value.replace("{"+e.getKey()+"}", e.getValue()); s.sendMessage(value); }
    private String crownName(String name) { return name; }

    private ArmorTrim crownTrim(String crown) {
        TrimPattern pattern;
        TrimMaterial material;
        switch (crown) {
            case "Nature" -> { pattern = TrimPattern.RAISER; material = TrimMaterial.EMERALD; }
            case "Wrath" -> { pattern = TrimPattern.RIB; material = TrimMaterial.REDSTONE; }
            case "Heaven" -> { pattern = TrimPattern.TIDE; material = TrimMaterial.LAPIS; }
            case "Gl1tched" -> { pattern = TrimPattern.WAYFINDER; material = TrimMaterial.DIAMOND; }
            case "Adaptation" -> { pattern = TrimPattern.SHAPER; material = TrimMaterial.NETHERITE; }
            case "Wealth" -> { pattern = TrimPattern.BOLT; material = TrimMaterial.COPPER; }
            case "Fractionate" -> { pattern = TrimPattern.COAST; material = TrimMaterial.AMETHYST; }
            case "Void" -> { pattern = TrimPattern.DUNE; material = TrimMaterial.IRON; }
            case "Parade" -> { pattern = TrimPattern.VEX; material = TrimMaterial.GOLD; }
            case "King" -> { pattern = TrimPattern.SILENCE; material = TrimMaterial.RESIN; }
            case "Tyrannus" -> { pattern = TrimPattern.WARD; material = TrimMaterial.QUARTZ; }
            case "Hériclés" -> { pattern = TrimPattern.FLOW; material = TrimMaterial.AMETHYST; }
            default -> { return null; }
        }
        return new ArmorTrim(material, pattern);
    }
    /** Applique explicitement le trim au meta d'armure et vérifie qu'il est conservé. */
    private void applyCrownTrim(ItemMeta meta, String crown) {
        if (!(meta instanceof ArmorMeta armorMeta)) {
            getLogger().warning("Impossible d'appliquer le trim de la couronne " + crown + " : le casque ne fournit pas ArmorMeta.");
            return;
        }
        ArmorTrim trim = crownTrim(crown);
        if (trim == null) return;
        armorMeta.setTrim(trim);
    }
    private void applyCrownTrim(ItemStack item, String crown) {
        if (item == null || item.getType() != Material.NETHERITE_HELMET) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        applyCrownTrim(meta, crown);
        item.setItemMeta(meta);
    }
    private String trimDescription(Player p, String crown) {
        String pattern = switch(crown) {
            case "Nature" -> "Raiser"; case "Wrath" -> "Rib"; case "Heaven" -> "Tide"; case "Gl1tched" -> "Wayfinder";
            case "Adaptation" -> "Shaper"; case "Wealth" -> "Bolt"; case "Fractionate" -> "Coast"; case "Void" -> "Dune";
            case "Parade" -> "Vex"; case "King" -> "Silence"; case "Tyrannus" -> "Ward"; case "Hériclés" -> "Flow"; default -> "None";
        };
        String material = switch(crown) {
            case "Nature" -> "Emerald"; case "Wrath" -> "Redstone"; case "Heaven" -> "Lapis Lazuli"; case "Gl1tched" -> "Diamond";
            case "Adaptation" -> "Netherite"; case "Wealth" -> "Copper"; case "Fractionate", "Hériclés" -> "Amethyst Shard"; case "Void" -> "Iron Ingot";
            case "Parade" -> "Gold Ingot"; case "King" -> "Resin Brick"; case "Tyrannus" -> "Nether Quartz"; default -> "Unknown";
        };
        return localized(p, "Trim : ", "Trim: ", "Trim: ") + pattern + " (" + material + ")";
    }
    private String crownMenuTitle(Player p) { return localized(p, "Couronnes KingdomSMP", "KingdomSMP Crowns", "Coronas KingdomSMP"); }
    private ItemStack createCrown(String name) {
        ItemStack item = new ItemStack(Material.NETHERITE_HELMET); ItemMeta m = item.getItemMeta(); if (m == null) return item;
        m.setDisplayName(ChatColor.GOLD + "✦ " + ChatColor.LIGHT_PURPLE + name + " Crown");
        m.setLore(List.of(ChatColor.GRAY + (isLegendary(name) ? "Legendary Crown" : "Crown"), ChatColor.DARK_PURPLE + "KingdomSMP", ChatColor.GRAY + "Bound crown item", ChatColor.RED + "Curse of Binding")); m.setUnbreakable(true);
        m.addEnchant(Enchantment.PROTECTION,4,true); m.addEnchant(Enchantment.AQUA_AFFINITY,1,true); m.addEnchant(Enchantment.RESPIRATION,3,true);
        m.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        m.setCustomModelData(1000 + allCrowns().indexOf(name));
        applyCrownTrim(m, name);
        m.getPersistentDataContainer().set(crownKey, PersistentDataType.STRING, name);
        item.setItemMeta(m);
        // Reapplique après setItemMeta pour éviter qu'un meta custom ou un autre traitement écrase le trim.
        applyCrownTrim(item, name);
        return item;
    }
    private boolean isCrownItem(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(crownKey, PersistentDataType.STRING); }
    private String itemCrown(ItemStack item) { return isCrownItem(item) ? item.getItemMeta().getPersistentDataContainer().get(crownKey, PersistentDataType.STRING) : ""; }
    private void removeCrownItems(Player p) { for (int i=0;i<p.getInventory().getSize();i++) if (isCrownItem(p.getInventory().getItem(i))) p.getInventory().setItem(i,null); if (isCrownItem(p.getInventory().getHelmet())) p.getInventory().setHelmet(null); }
    private void giveCrown(Player p, String crown) { removeCrownItems(p); Profile d=profile(p.getUniqueId()); d.unlocked.removeIf(key -> key.startsWith("power:")); d.crown=crown; d.crownMaterials=Math.max(1,getConfig().getInt("couronne-durabilite.vies-initiales",10)); d.crownBroken=false; d.crownShattered=false; d.kingAbsorbed=false; d.pactActive=false; d.pactApproved=false; d.pactKills=0; d.emptySince=0L; Map<Integer,ItemStack> overflow=p.getInventory().addItem(createCrown(crown)); overflow.values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i)); saveStorage(); }

    private boolean isCustomItem(ItemStack item, NamespacedKey key) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE); }
    private ItemStack namedItem(Material type, String name, String lore, NamespacedKey key) {
        ItemStack item = new ItemStack(type); ItemMeta meta = item.getItemMeta(); if (meta == null) return item;
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name); meta.setLore(List.of(ChatColor.GRAY + lore, ChatColor.DARK_PURPLE + "KingdomSMP"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1); item.setItemMeta(meta); return item;
    }
    private ItemStack createMaterial(int amount) { ItemStack item=namedItem(Material.AMETHYST_SHARD, "Crown Material", "Material de vie pour les couronnes", materialKey); item.setAmount(Math.max(1, Math.min(64, amount))); return item; }
    private ItemStack createUpgrader() { return namedItem(Material.NETHER_STAR, "Crown Upgrader", "Débloque une capacité supplémentaire", upgraderKey); }
    private ItemStack createRepairer() { return namedItem(Material.ANVIL, "Crown Repairer", "Répare et restaure une couronne", repairerKey); }
    private ItemStack createCrownChanger() { return namedItem(Material.ENDER_EYE, "Crown Changer", "Consomme cet objet pour changer de couronne basique aléatoirement. Ne donne pas de couronne légendaire.", crownChangerKey); }
    private ItemStack createRitualToken(String crown) {
        Material material = switch (crown) {
            case "King" -> Material.NETHER_STAR;
            case "Tyrannus" -> Material.BLAZE_ROD;
            default -> Material.HEART_OF_THE_SEA;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + switch (crown) {
            case "King" -> "Sceau royal";
            case "Tyrannus" -> "Noyau du Tyran";
            default -> "Cœur d’Hériclés";
        });
        meta.setLore(List.of(ChatColor.GRAY + "Catalyseur de rituel légendaire", ChatColor.DARK_PURPLE + "Couronne : " + crown, ChatColor.RED + "Consommé au terme du rituel"));
        meta.getPersistentDataContainer().set(ritualTokenKey, PersistentDataType.STRING, crown);
        item.setItemMeta(meta);
        return item;
    }
    private boolean hasRitualToken(Player p, String crown) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (crown.equals(item.getItemMeta().getPersistentDataContainer().get(ritualTokenKey, PersistentDataType.STRING))) return true;
        }
        return false;
    }
    private boolean consumeRitualToken(Player p, String crown) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;
            if (crown.equals(item.getItemMeta().getPersistentDataContainer().get(ritualTokenKey, PersistentDataType.STRING))) {
                if (item.getAmount() <= 1) p.getInventory().setItem(i, null); else item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }
    private void registerCraftingRecipes() {
        ShapedRecipe material = new ShapedRecipe(new NamespacedKey(this, "crown_material_recipe"), createMaterial(1)); material.shape("AGA", "GEG", "AGA"); material.setIngredient('A', Material.AMETHYST_SHARD); material.setIngredient('G', Material.GOLD_INGOT); material.setIngredient('E', Material.ECHO_SHARD); Bukkit.addRecipe(material);
        ShapedRecipe upgrader = new ShapedRecipe(new NamespacedKey(this, "crown_upgrader_recipe"), createUpgrader()); upgrader.shape("DND", "AEA", "DND"); upgrader.setIngredient('D', Material.DIAMOND); upgrader.setIngredient('N', Material.NETHERITE_INGOT); upgrader.setIngredient('A', Material.AMETHYST_SHARD); upgrader.setIngredient('E', Material.EXPERIENCE_BOTTLE); Bukkit.addRecipe(upgrader);
        ShapedRecipe repairer = new ShapedRecipe(new NamespacedKey(this, "crown_repairer_recipe"), createRepairer()); repairer.shape("DAD", "ANA", "DAD"); repairer.setIngredient('D', Material.DIAMOND); repairer.setIngredient('A', Material.AMETHYST_SHARD); repairer.setIngredient('N', Material.NETHERITE_INGOT); Bukkit.addRecipe(repairer);
        ShapedRecipe changer = new ShapedRecipe(new NamespacedKey(this, "crown_changer_recipe"), createCrownChanger()); changer.shape("EAE", "ANA", "EAE"); changer.setIngredient('E', Material.ENDER_PEARL); changer.setIngredient('A', Material.AMETHYST_SHARD); changer.setIngredient('N', Material.NETHER_STAR); Bukkit.addRecipe(changer);
        ShapedRecipe kingToken = new ShapedRecipe(new NamespacedKey(this, "ritual_king_token"), createRitualToken("King")); kingToken.shape("EEE", "ENE", "EEE"); kingToken.setIngredient('E', Material.ECHO_SHARD); kingToken.setIngredient('N', Material.NETHER_STAR); Bukkit.addRecipe(kingToken);
        ShapedRecipe tyrannusToken = new ShapedRecipe(new NamespacedKey(this, "ritual_tyrannus_token"), createRitualToken("Tyrannus")); tyrannusToken.shape("BDB", "DND", "BDB"); tyrannusToken.setIngredient('B', Material.BLAZE_ROD); tyrannusToken.setIngredient('D', Material.DIAMOND); tyrannusToken.setIngredient('N', Material.NETHERITE_INGOT); Bukkit.addRecipe(tyrannusToken);
        ShapedRecipe heraclesToken = new ShapedRecipe(new NamespacedKey(this, "ritual_hericles_token"), createRitualToken("Hériclés")); heraclesToken.shape("ASA", "SHS", "ASA"); heraclesToken.setIngredient('A', Material.AMETHYST_SHARD); heraclesToken.setIngredient('S', Material.HEART_OF_THE_SEA); heraclesToken.setIngredient('H', Material.HEAVY_CORE); Bukkit.addRecipe(heraclesToken);
    }
    private void consumeHand(Player p) { ItemStack hand=p.getInventory().getItemInMainHand(); if(hand.getAmount()<=1)p.getInventory().setItemInMainHand(null); else hand.setAmount(hand.getAmount()-1); }
    private void useUpgrader(Player p, ItemStack hand) {
        Profile d=profile(p.getUniqueId()); if(d.crown.isBlank() || d.crownBroken || d.crownShattered){msg(p,"crown-broken");return;}
        int max=isLegendary(d.crown)?5:3; for(int i=1;i<=max;i++) if(!d.unlocked.contains("power:"+i)) { d.unlocked.add("power:"+i); consumeHand(p); msg(p,"upgrader-used",Map.of("power",String.valueOf(i))); saveStorage(); return; }
        msg(p,"upgrader-max");
    }
    private void openCrownChangerConfirm(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, localized(p, "Confirmer le changement", "Confirm crown change", "Confirmar cambio de corona"));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(11, menuButton(Material.LIME_CONCRETE, localized(p, "Confirmer", "Confirm", "Confirmar"), "changer-confirm", localized(p, "Une nouvelle couronne basique sera tirée au hasard.", "A new basic crown will be selected at random.", "Se elegirá una nueva corona básica al azar.")));
        inv.setItem(15, menuButton(Material.RED_CONCRETE, localized(p, "Annuler", "Cancel", "Cancelar"), "changer-cancel", localized(p, "Aucun objet ne sera consommé.", "No item will be consumed.", "No se consumirá ningún objeto.")));
        p.openInventory(inv);
    }

    private void useCrownChanger(Player p, ItemStack hand) {
        Profile d = profile(p.getUniqueId());
        if (d.crown.isBlank()) { msg(p, "no-crown"); return; }
        if (d.kingAbsorbed) { p.sendMessage(ChatColor.RED + "Tu dois terminer ton pacte avant de changer de couronne."); return; }
        List<String> choices = new ArrayList<>(normal);
        choices.remove(d.crown);
        if (choices.isEmpty()) { p.sendMessage(ChatColor.RED + "Aucune autre couronne basique disponible."); return; }
        String previous = d.crown;
        String next = choices.get(new Random().nextInt(choices.size()));
        consumeHand(p);
        d.unlocked.removeIf(key -> key.startsWith("power:")); // conserve les traces d’absorption de King
        giveCrown(p, next);
        p.sendTitle(ChatColor.LIGHT_PURPLE + "✦ " + next + " ✦", ChatColor.GRAY + previous + " → " + next, 10, 50, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "Crown Changer : " + ChatColor.WHITE + previous + ChatColor.GRAY + " → " + ChatColor.GOLD + next);
    }
    private void useMaterial(Player p, ItemStack hand) {
        Profile d=profile(p.getUniqueId()); if(d.crown.isBlank()){msg(p,"no-crown");return;}
        int max=Math.max(1,getConfig().getInt("couronne-durabilite.vies-max",10)); int add=Math.max(1,getConfig().getInt("materiaux.vies-par-materiau",2));
        if(d.crownBroken || d.crownShattered){msg(p,"material-cannot-revive");return;}
        if(d.crownMaterials>=max){msg(p,"material-full");return;}
        d.crownMaterials=Math.min(max,d.crownMaterials+add); d.emptySince=0L; consumeHand(p); updateCrownLore(p); applyPassives(p); msg(p,"material-used",Map.of("lives",String.valueOf(d.crownMaterials))); saveStorage();
    }
    private void useRepairer(Player p, ItemStack hand) {
        Profile d=profile(p.getUniqueId()); if(d.crown.isBlank()){msg(p,"no-crown");return;}
        int cost=Math.max(1,getConfig().getInt("materiaux.cout-reparation",3)); int count=countCustomItems(p,materialKey);
        if(count<cost){msg(p,"repairer-materials-needed",Map.of("amount",String.valueOf(cost)));return;}
        removeCustomItems(p,materialKey,cost); d.crownMaterials=Math.max(d.crownMaterials,Math.min(getConfig().getInt("couronne-durabilite.vies-max",10),getConfig().getInt("couronne-durabilite.vies-apres-reparation",10)));
        d.crownBroken=false; d.crownShattered=false; d.kingAbsorbed=false; d.pactApproved=false; d.emptySince=0L; consumeHand(p); ensureCrownItem(p); updateCrownLore(p); applyPassives(p); msg(p,"repairer-used"); saveStorage();
    }
    private int countCustomItems(Player p, NamespacedKey key) { int n=0; for(ItemStack it:p.getInventory().getContents())if(isCustomItem(it,key))n+=it.getAmount(); return n; }
    private void removeCustomItems(Player p, NamespacedKey key, int amount) { for(int i=0;i<p.getInventory().getSize()&&amount>0;i++){ItemStack it=p.getInventory().getItem(i);if(!isCustomItem(it,key))continue;int take=Math.min(amount,it.getAmount());amount-=take;if(take==it.getAmount())p.getInventory().setItem(i,null);else it.setAmount(it.getAmount()-take);} }
    private int countMaterial(Player p, Material material) {
        int count = 0;
        for (ItemStack item : p.getInventory().getStorageContents())
            if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }
    private boolean hasRitualOfferings(Player p, String crown) {
        for (var entry : ritualOfferings(crown).entrySet())
            if (countMaterial(p, entry.getKey()) < entry.getValue()) return false;
        return true;
    }
    private void consumeRitualOfferings(Player p, String crown) {
        for (var entry : ritualOfferings(crown).entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < p.getInventory().getStorageContents().length && remaining > 0; slot++) {
                ItemStack item = p.getInventory().getItem(slot);
                if (item == null || item.getType() != entry.getKey()) continue;
                int take = Math.min(remaining, item.getAmount());
                remaining -= take;
                if (take == item.getAmount()) p.getInventory().setItem(slot, null);
                else item.setAmount(item.getAmount() - take);
            }
        }
        p.updateInventory();
    }
    private Map<Material, Integer> ritualOfferings(String crown) {
        Map<Material, Integer> cost = new LinkedHashMap<>();
        String configName = crown.equals("Hériclés") ? "Hericles" : crown;
        List<String> configured = getConfig().getStringList("rituels.offrandes." + configName);
        for (String entry : configured) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;
            Material material = Material.matchMaterial(parts[0].trim());
            try {
                int amount = Integer.parseInt(parts[1].trim());
                if (material != null && amount > 0) cost.put(material, amount);
            } catch (NumberFormatException ignored) { }
        }
        if (!cost.isEmpty()) return cost;
        switch (crown) {
            case "King" -> { cost.put(Material.TOTEM_OF_UNDYING, 4); cost.put(Material.ECHO_SHARD, 16); cost.put(Material.GOLD_BLOCK, 8); cost.put(Material.DRAGON_HEAD, 1); }
            case "Tyrannus" -> { cost.put(Material.NETHERITE_BLOCK, 1); cost.put(Material.WITHER_SKELETON_SKULL, 8); cost.put(Material.BLAZE_ROD, 32); cost.put(Material.ANCIENT_DEBRIS, 16); }
            case "Hériclés" -> { cost.put(Material.HEAVY_CORE, 1); cost.put(Material.ENCHANTED_GOLDEN_APPLE, 4); cost.put(Material.HEART_OF_THE_SEA, 4); cost.put(Material.TOTEM_OF_UNDYING, 2); }
            default -> { }
        }
        return cost;
    }
    private String ritualOfferingText(String crown) {
        List<String> parts = new ArrayList<>();
        for (var entry : ritualOfferings(crown).entrySet())
            parts.add(entry.getValue() + "x " + entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return String.join(", ", parts);
    }
    private void updateCrownLore(Player p) { Profile d=profile(p.getUniqueId()); for(int i=0;i<p.getInventory().getSize();i++){ItemStack it=p.getInventory().getItem(i);if(d.crown.equals(itemCrown(it)))setCrownLore(it,d);} ItemStack helmet=p.getInventory().getHelmet();if(d.crown.equals(itemCrown(helmet)))setCrownLore(helmet,d); }
    private void setCrownLore(ItemStack it, Profile d) {
        ItemMeta m=it.getItemMeta(); if(m==null)return;
        List<String> lore=new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE+"✦ KINGDOMSMP ✦");
        lore.add(ChatColor.GRAY+(isLegendary(d.crown)?"Couronne légendaire":"Couronne basique"));
        lore.add(ChatColor.YELLOW+"Vies restantes : "+d.crownMaterials+"/"+getConfig().getInt("couronne-durabilite.vies-max",10));
        lore.add(d.crownShattered?ChatColor.DARK_RED+"✖ Couronne brisée":d.crownBroken?ChatColor.RED+"✖ Couronne cassée":d.crownMaterials<=Math.max(1,getConfig().getInt("couronne-durabilite.seuil-fracture",3))?ChatColor.GOLD+"⚠ Fracturée — pouvoirs actifs désactivés":ChatColor.GREEN+"✔ En état");
        lore.add(ChatColor.LIGHT_PURPLE+"Trim : "+trimPatternName(d.crown)+" • "+trimMaterialName(d.crown));
        lore.add(ChatColor.RED+"Malédiction de lien / Curse of Binding");
        m.setLore(lore); it.setItemMeta(m); applyCrownTrim(it,d.crown);
    }
    private String trimPatternName(String crown) { return switch(crown) {
        case "Nature"->"Raiser"; case "Wrath"->"Rib"; case "Heaven"->"Tide"; case "Gl1tched"->"Wayfinder"; case "Adaptation"->"Shaper"; case "Wealth"->"Bolt"; case "Fractionate"->"Coast"; case "Void"->"Dune"; case "Parade"->"Vex"; case "King"->"Silence"; case "Tyrannus"->"Ward"; case "Hériclés"->"Flow"; default->"—"; }; }
    private String trimMaterialName(String crown) { return switch(crown) {
        case "Nature"->"Émeraude"; case "Wrath"->"Redstone"; case "Heaven"->"Lapis-lazuli"; case "Gl1tched"->"Diamant"; case "Adaptation"->"Netherite"; case "Wealth"->"Cuivre"; case "Fractionate","Hériclés"->"Éclat d’améthyste"; case "Void"->"Lingot de fer"; case "Parade"->"Lingot d’or"; case "King"->"Brique de résine"; case "Tyrannus"->"Quartz du Nether"; default->"—"; }; }
    private void consumeCrownLife(Player p,int amount) { Profile d=profile(p.getUniqueId()); if(d.crown.isBlank()||d.crownShattered)return;d.crownMaterials=Math.max(0,d.crownMaterials-amount);if(d.crownMaterials==0){d.crownBroken=true;if(d.emptySince==0L)d.emptySince=System.currentTimeMillis();msg(p,"crown-broken");}updateCrownLore(p); }
    private void tickCrownDurability(Player p) { Profile d=profile(p.getUniqueId());if(d.crown.isBlank())return;long now=System.currentTimeMillis();if(d.crownMaterials<=0){if(d.emptySince==0L)d.emptySince=now;long delay=Math.max(60,getConfig().getLong("couronne-durabilite.delai-casse-secondes",86400))*1000L;if(!d.crownShattered&&now-d.emptySince>=delay){d.crownShattered=true;d.crownBroken=true;msg(p,"crown-shattered");}return;}long interval=Math.max(60,getConfig().getLong("couronne-durabilite.perte-vie-intervalle-secondes",21600))*1000L;String key="last-life:"+p.getUniqueId();long last=storage.getLong("internal."+key,now);if(now-last>=interval){consumeCrownLife(p,1);storage.set("internal."+key,now);saveStorage();}}

    @EventHandler public void onJoin(PlayerJoinEvent e) { Player p=e.getPlayer(); Profile d=profile(p.getUniqueId()); if (d.crown.isBlank() && getConfig().getBoolean("settings.attribution-automatique",true)) { String c=normal.get(new Random().nextInt(normal.size())); giveCrown(p,c); msg(p,"crown-assigned",Map.of("crown",c)); } getServer().getScheduler().runTaskLater(this,()->{ ensureCrownItem(p); applyBindingToExistingCrownItems(p); applyPassives(p); },20L); }
    private void ensureCrownItem(Player p) { Profile d=profile(p.getUniqueId()); if (d.crown.isBlank() || d.crownBroken || d.crownShattered || d.kingAbsorbed) return; boolean found=false; for (ItemStack i:p.getInventory().getContents()) if (d.crown.equals(itemCrown(i))) { found=true; break; } if (!found && !d.crown.equals(itemCrown(p.getInventory().getHelmet()))) p.getInventory().addItem(createCrown(d.crown)); }
    private void applyBindingToExistingCrownItems(Player p) {
        for (ItemStack item : p.getInventory().getContents()) applyBindingToCrown(item);
        applyBindingToCrown(p.getInventory().getHelmet());
    }
    private void applyBindingToCrown(ItemStack item) {
        if (!isCrownItem(item) || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasEnchant(Enchantment.BINDING_CURSE)) {
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (lore.stream().noneMatch(line -> ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains("curse of binding") || ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains("malédiction de lien")))
                lore.add(ChatColor.RED + "Curse of Binding / Malédiction de lien");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            restoreCrownOnRespawn(player);
            applyPassives(player);
        }, 1L);
    }

    // La couronne est un objet lié au profil : elle ne reste jamais dans les drops de mort.
    // Après le respawn, elle est replacée dans l'emplacement du casque, même si keepInventory est actif.
    private void restoreCrownOnRespawn(Player player) {
        if (!player.isOnline() || !player.isValid() || player.isDead()) return;
        Profile data = profile(player.getUniqueId());
        if (data.crown.isBlank() || data.kingAbsorbed) return;

        ItemStack crown = null;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (data.crown.equals(itemCrown(item))) {
                if (crown == null) crown = item.clone();
                player.getInventory().setItem(slot, null);
            }
        }
        ItemStack helmet = player.getInventory().getHelmet();
        if (data.crown.equals(itemCrown(helmet))) {
            crown = helmet.clone();
        } else if (helmet != null && helmet.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(helmet.clone());
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        if (crown == null) crown = createCrown(data.crown);
        applyBindingToCrown(crown);
        setCrownLore(crown, data);
        player.getInventory().setHelmet(crown);
        player.updateInventory();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { saveStorage(); }

    /** Limite les frappes plongeantes à la masse pour éviter les éliminations instantanées. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceSmashDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (attacker.equals(victim) || attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) return;
        if (!getConfig().getBoolean("combat.mace-smash-limit.enabled", true)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.MACE) return;
        double minHealth = Math.max(1.0, Math.min(20.0,
                getConfig().getDouble("combat.mace-smash-limit.minimum-health", 2.0)));
        // Une chute notable est requise pour ne pas limiter les coups normaux à la masse.
        if (attacker.getFallDistance() < Math.max(1.0,
                getConfig().getDouble("combat.mace-smash-limit.minimum-fall-distance", 1.5))) return;
        double allowedDamage = Math.max(0.0, victim.getHealth() - minHealth);
        double finalDamage = e.getFinalDamage();
        if (finalDamage > allowedDamage && finalDamage > 0.0) {
            // Mise à l'échelle pour respecter aussi les modificateurs de dégâts/armure.
            e.setDamage(e.getDamage() * (allowedDamage / finalDamage));
            attacker.sendActionBar(ChatColor.translateAlternateColorCodes('&', localized(attacker, "&eMasse limitée : la cible garde au moins {health}❤.", "&eMace damage limited: target keeps at least {health}❤.", "&eDaño de maza limitado: el objetivo conserva al menos {health}❤.")
                    .replace("{health}", String.format(java.util.Locale.US, "%.1f", minHealth))));
        }
    }

    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Profile d=profile(p.getUniqueId());
        if ("Heaven".equals(d.crown) && !d.crownBroken && !d.crownShattered && e.getCause()==EntityDamageEvent.DamageCause.FALL) { e.setCancelled(true); return; }
        if (getConfig().getBoolean("rituels.interruption-si-degats", true) && ritualInProgress.contains(p.getUniqueId()) && !e.isCancelled() && e.getFinalDamage() > 0) {
            ritualInterrupted.add(p.getUniqueId());
        }
        if (e.isCancelled() || e.getFinalDamage()<=0 || d.crownBroken || d.crownShattered || !"Adaptation".equals(d.crown)) return;
        if (inSpawn(p.getLocation())) return;
        long now=System.currentTimeMillis();
        if (now >= adaptationEffectEnds.getOrDefault(p.getUniqueId(), 0L)) adaptationHits.put(p.getUniqueId(), 0);
        int hits=adaptationHits.merge(p.getUniqueId(),1,Integer::sum); int every=Math.max(1,getConfig().getInt("couronnes.Adaptation.coups-recus-par-palier",10));
        if (hits % every == 0) {
            int level=Math.min(Math.max(0,getConfig().getInt("couronnes.Adaptation.niveau-max-resistance",4)-1),hits/every-1);
            int seconds=Math.max(1,getConfig().getInt("couronnes.Adaptation.duree-palier-secondes",30));
            if (p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,seconds*20,level,true,true,true))) managedEffects.computeIfAbsent(p.getUniqueId(), ignored -> new HashSet<>()).add(PotionEffectType.RESISTANCE);
            adaptationEffectEnds.put(p.getUniqueId(), now + seconds*1000L);
            p.sendActionBar(tr(p,"adaptation-upgrade").replace("{level}",String.valueOf(level+1)).replace("{seconds}",String.valueOf(seconds)));
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player victim=e.getEntity(), killer=victim.getKiller();
        e.getDrops().removeIf(this::isCrownItem);
        Profile victimProfile = profile(victim.getUniqueId());
        victimProfile.deaths++;
        if (!victimProfile.crown.isBlank() && getConfig().getBoolean("couronne-durabilite.perte-vie-a-la-mort", true)) consumeCrownLife(victim, 1);
        if (killer == null || killer.equals(victim)) { saveStorage(); return; }
        String pair=killer.getUniqueId()+":"+victim.getUniqueId(); long now=System.currentTimeMillis(), delay=Math.max(0,getConfig().getLong("settings.anti-farm-secondes",300))*1000L;
        long lastKill=storage.getLong("anti-farm."+pair, 0L);
        boolean rewarded = now-lastKill>=delay;
        if (rewarded) {
            killTimes.put(pair,now); storage.set("anti-farm."+pair, now);
            Profile kd=profile(killer.getUniqueId()); kd.kills++; if(kd.pactActive){kd.pactKills++;msg(killer,"pact-kill-progress",Map.of("kills",String.valueOf(kd.pactKills),"required",String.valueOf(getConfig().getInt("pacte.eliminations-requises",10))));} unlockNotice(killer,kd); killer.sendMessage(tr(killer,"kills-count").replace("{kills}",String.valueOf(kd.kills)));
            if (getConfig().getBoolean("materiaux.recompense-kill-activee", true)) {
                int amount = Math.max(1, getConfig().getInt("materiaux.par-kill", 1));
                Map<Integer, ItemStack> overflow = killer.getInventory().addItem(createMaterial(amount));
                overflow.values().forEach(item -> killer.getWorld().dropItemNaturally(killer.getLocation(), item));
                msg(killer, "material-kill", Map.of("amount", String.valueOf(amount)));
            }
        } else {
            msg(killer, "anti-farm-blocked");
        }
        Profile kd=profile(killer.getUniqueId()), vd=profile(victim.getUniqueId());
        if ("Hériclés".equals(vd.crown) && !vd.kingAbsorbed) {
            String killerOldCrown = kd.crown;
            String victimName = victim.getName();
            if (killerOldCrown == null) killerOldCrown = "";
            if (!killerOldCrown.isBlank()) {
                giveCrown(victim, killerOldCrown);
            } else {
                removeCrownItems(victim);
                vd.crown = ""; vd.crownBroken = false; vd.crownShattered = false; vd.crownMaterials = 0; vd.kingAbsorbed = false;
            }
            giveCrown(killer, "Hériclés");
            Bukkit.broadcastMessage(localized(killer, "§d✦ Hériclés a changé de porteur ! §e" + killer.getName() + " §7a vaincu " + victimName + ".", "§d✦ Hériclés has chosen a new bearer! §e" + killer.getName() + " §7defeated " + victimName + ".", "§d✦ ¡Hériclés eligió nuevo portador! §e" + killer.getName() + " §7derrotó a " + victimName + "."));
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, .7f);
            saveStorage();
        }
        if ("King".equals(kd.crown) && !vd.crown.isBlank() && !vd.crown.equals("King") && !kd.unlocked.contains("absorbed:"+vd.crown)) { String absorbed=vd.crown; victim.getWorld().spawnParticle(Particle.PORTAL,victim.getLocation().add(0,1,0),100,.5,.8,.5,.1); victim.getWorld().playSound(victim.getLocation(),Sound.ENTITY_WITHER_DEATH,1f,.8f); Bukkit.broadcastMessage(tr("fr","king-absorb-broadcast").replace("{victim}",victim.getName()).replace("{crown}",absorbed)); vd.crownShattered=true; vd.crownBroken=true; vd.kingAbsorbed=true; vd.pactActive=false; vd.pactApproved=false; vd.pactKills=0; vd.crownMaterials=0; removeCrownItems(victim); kd.unlocked.add("absorbed:"+absorbed); msg(killer,"king-absorbed",Map.of("crown",absorbed)); msg(victim,"pact-king-absorbed"); saveStorage(); }
        saveStorage();
    }
    private void unlockNotice(Player p, Profile d) { int[] levels=isLegendary(d.crown)?new int[]{3,5,8,12,15}:new int[]{3,5,8}; for(int i=0;i<levels.length;i++) if(d.kills==levels[i]) { p.sendMessage(tr(p,"power-unlocked").replace("{power}",String.valueOf(i+1))); if(isLegendary(d.crown)) Bukkit.broadcastMessage(tr("fr","legendary-progress").replace("{player}",p.getName()).replace("{crown}",d.crown).replace("{power}",String.valueOf(i+1))); } }
    @EventHandler public void onCrownEventMobKill(EntityDeathEvent e) {
        if (!crownEventActive || !"MONSTERS".equals(crownEventType) || !(e.getEntity() instanceof Monster) || e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        if (!eligibleForCrownEvent(p)) return;
        progressCrownEvent(p, 1, eventRequired(), "monstres", "mobs", "monstruos");
    }

    @EventHandler(ignoreCancelled = true) public void onCrownEventBlockBreak(BlockBreakEvent e) {
        if (!crownEventActive || !eligibleForCrownEvent(e.getPlayer())) return;
        Material type = e.getBlock().getType();
        if ("ORES".equals(crownEventType) && valuableOre(type)) {
            progressCrownEvent(e.getPlayer(), 1, eventRequired(), "minerais précieux", "valuable ores", "minerales valiosos");
        } else if ("LOGS".equals(crownEventType) && (type.name().endsWith("_LOG") || type.name().endsWith("_STEM") || type.name().endsWith("_HYPHAE"))) {
            progressCrownEvent(e.getPlayer(), 1, eventRequired(), "bûches", "logs", "troncos");
        } else if ("FARM".equals(crownEventType) && isMatureCrop(e.getBlock().getBlockData(), type)) {
            progressCrownEvent(e.getPlayer(), 1, eventRequired(), "cultures récoltées", "harvested crops", "cultivos cosechados");
        }
    }

    @EventHandler public void onCrownEventFishing(PlayerFishEvent e) {
        if (!crownEventActive || !"FISH".equals(crownEventType) || e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !eligibleForCrownEvent(e.getPlayer())) return;
        progressCrownEvent(e.getPlayer(), 1, eventRequired(), "prises de pêche", "fish caught", "peces pescados");
    }

    private boolean valuableOre(Material type) {
        return EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS, Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE).contains(type);
    }

    private boolean isMatureCrop(org.bukkit.block.data.BlockData data, Material type) {
        if (data instanceof org.bukkit.block.data.Ageable ageable) return ageable.getAge() >= ageable.getMaximumAge();
        return type == Material.MELON || type == Material.PUMPKIN || type == Material.SWEET_BERRY_BUSH;
    }

    private int eventRequired() {
        return switch (crownEventType) {
            case "ORES" -> Math.max(512, getConfig().getInt("evenements-couronnes.minerais-requis", 512));
            case "MONSTERS" -> Math.max(200, getConfig().getInt("evenements-couronnes.monstres-requis", 200));
            case "FISH" -> Math.max(128, getConfig().getInt("evenements-couronnes.poissons-requis", 128));
            case "FARM" -> Math.max(384, getConfig().getInt("evenements-couronnes.recoltes-requises", 384));
            case "LOGS" -> Math.max(768, getConfig().getInt("evenements-couronnes.buches-requises", 768));
            default -> 1;
        };
    }

    private String eventObjective(Player p) {
        return switch (crownEventType) {
            case "ORES" -> localized(p, "minerais", "ores", "minerales");
            case "MONSTERS" -> localized(p, "monstres hostiles", "hostile mobs", "monstruos hostiles");
            case "FISH" -> localized(p, "poissons pêchés", "fish caught", "peces pescados");
            case "FARM" -> localized(p, "cultures récoltées", "crops harvested", "cultivos cosechados");
            case "LOGS" -> localized(p, "bûches coupées", "logs chopped", "troncos talados");
            default -> localized(p, "objectifs", "objectives", "objetivos");
        };
    }

    private String eventTitle(Player p) {
        return switch (crownEventType) {
            case "ORES" -> localized(p, "Extraction extrême : mine 512 minerais", "Extreme mining: mine 512 ores", "Minería extrema: mina 512 minerales");
            case "MONSTERS" -> localized(p, "Grande chasse : élimine 200 monstres hostiles", "Grand hunt: kill 200 hostile mobs", "Gran cacería: elimina 200 monstruos hostiles");
            case "FISH" -> localized(p, "Maître pêcheur : attrape 128 poissons", "Master angler: catch 128 fish", "Maestro pescador: captura 128 peces");
            case "FARM" -> localized(p, "Récolte royale : récolte 384 cultures mûres", "Royal harvest: harvest 384 mature crops", "Cosecha real: cosecha 384 cultivos maduros");
            case "LOGS" -> localized(p, "Déforestation : coupe 768 bûches", "Deforestation: chop 768 logs", "Deforestación: tala 768 troncos");
            default -> localized(p, "Défi des couronnes", "Crown challenge", "Desafío de coronas");
        };
    }

    private boolean eligibleForCrownEvent(Player p) {
        Profile d = profile(p.getUniqueId());
        return d.crown != null && !d.crown.isBlank() && !d.crownBroken && !d.crownShattered && System.currentTimeMillis() <= crownEventEndsAt;
    }

    private void progressCrownEvent(Player p, int amount, int required, String fr, String en, String es) {
        UUID playerId = p.getUniqueId();
        if (eventCompleted.contains(playerId)) return;
        int progress = eventProgress.merge(playerId, amount, Integer::sum);
        p.sendActionBar(localized(p, "§dDéfi des couronnes §7: " + progress + "/" + required + " " + fr, "§dCrown challenge §7: " + progress + "/" + required + " " + en, "§dDesafío de coronas §7: " + progress + "/" + required + " " + es));
        if (progress < required) return;
        eventProgress.remove(playerId);
        eventCompleted.add(playerId);
        int reward = Math.max(10, getConfig().getInt("evenements-couronnes.recompense-materiaux", 10));
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(createMaterial(reward));
        overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.sendMessage(localized(p, "§aDéfi réussi ! Récompense : §e" + reward + " matériaux de couronne.", "§aChallenge complete! Reward: §e" + reward + " crown materials.", "§a¡Desafío completado! Recompensa: §e" + reward + " materiales de corona."));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        saveStorage();
    }

    private void startCrownEvents() {
        nextCrownEventAt = System.currentTimeMillis() + Math.max(60, getConfig().getLong("evenements-couronnes.intervalle-evenements-secondes", 10800)) * 1000L;
        new BukkitRunnable() {
            @Override public void run() {
                if (!getConfig().getBoolean("evenements-couronnes.actifs", true)) return;
                long now = System.currentTimeMillis();
                if (crownEventActive && now >= crownEventEndsAt) {
                    crownEventActive = false;
                    eventProgress.clear(); eventCompleted.clear();
                    nextCrownEventAt = now + Math.max(60, getConfig().getLong("evenements-couronnes.intervalle-evenements-secondes", 10800)) * 1000L;
                    Bukkit.broadcastMessage(ChatColor.GRAY + "[KingdomSMP] " + ChatColor.YELLOW + "L'événement des couronnes est terminé.");
                    return;
                }
                if (crownEventActive || now < nextCrownEventAt || Bukkit.getOnlinePlayers().isEmpty()) return;
                String[] types = {"MONSTERS", "ORES", "FISH", "FARM", "LOGS"};
                crownEventType = types[new Random().nextInt(types.length)];
                crownEventActive = true;
                crownEventEndsAt = now + Math.max(60, getConfig().getLong("evenements-couronnes.duree-secondes", 900)) * 1000L;
                eventProgress.clear(); eventCompleted.clear();
                int required = eventRequired();
                int reward = Math.max(10, getConfig().getInt("evenements-couronnes.recompense-materiaux", 10));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ ÉVÉNEMENT DES COURONNES ✦");
                    player.sendMessage(ChatColor.YELLOW + eventTitle(player));
                    player.sendMessage(ChatColor.GRAY + localized(player, "Objectif : " + required + " " + eventObjective(player), "Objective: " + required + " " + eventObjective(player), "Objetivo: " + required + " " + eventObjective(player)));
                    player.sendMessage(ChatColor.GRAY + localized(player, "Durée : 15 minutes. Récompense : " + reward + " matériaux de couronne.", "Duration: 15 minutes. Reward: " + reward + " crown materials.", "Duración: 15 minutos. Recompensa: " + reward + " materiales de corona."));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .8f, 1.2f);
                }
            }
        }.runTaskTimer(this, 20L, 20L * 10L);
    }

    private void openProfile(Player p) {
        Profile d = profile(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, localized(p, "Profil KingdomSMP", "KingdomSMP Profile", "Perfil KingdomSMP"));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        double kd = d.deaths == 0 ? d.kills : (double)d.kills / d.deaths;
        inv.setItem(10, menuButton(Material.NETHERITE_HELMET, localized(p, "Couronne : " + (d.crown.isBlank()?"Aucune":d.crown), "Crown: " + (d.crown.isBlank()?"None":d.crown), "Corona: " + (d.crown.isBlank()?"Ninguna":d.crown)), "profile-info", localized(p, "État : " + (d.crownShattered?"brisée":d.crownBroken?"cassée":d.crownMaterials+" vies") , "Status: " + (d.crownShattered?"shattered":d.crownBroken?"broken":d.crownMaterials+" lives"), "Estado: " + (d.crownShattered?"rota":d.crownBroken?"rota":d.crownMaterials+" vidas"))));
        inv.setItem(12, menuButton(Material.IRON_SWORD, localized(p, "Éliminations : " + d.kills, "Kills: " + d.kills, "Eliminaciones: " + d.kills), "profile-info", localized(p, "Éliminations enregistrées", "Recorded kills", "Eliminaciones registradas")));
        inv.setItem(14, menuButton(Material.SKELETON_SKULL, localized(p, "Morts : " + d.deaths, "Deaths: " + d.deaths, "Muertes: " + d.deaths), "profile-info", localized(p, "Ratio K/D : " + String.format(Locale.ROOT,"%.2f",kd), "K/D ratio: " + String.format(Locale.ROOT,"%.2f",kd), "Ratio B/M: " + String.format(Locale.ROOT,"%.2f",kd))));
        long unlockedPowers = d.unlocked.stream().filter(key -> key.startsWith("power:")).count();
        inv.setItem(16, menuButton(Material.ENCHANTED_BOOK, localized(p, "Améliorations débloquées : " + unlockedPowers, "Upgrades unlocked: " + unlockedPowers, "Mejoras desbloqueadas: " + unlockedPowers), "profile-info", localized(p, "Les améliorations manuelles de ta couronne actuelle", "Manual upgrades for your current crown", "Mejoras manuales de tu corona actual")));
        inv.setItem(22, menuButton(Material.ARROW, localized(p,"Retour","Back","Volver"), "profile-back", localized(p,"Retour au menu principal","Back to main menu","Volver al menú principal")));
        p.openInventory(inv);
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) { if(isCrownItem(e.getItemDrop().getItemStack())) { e.setCancelled(true); msg(e.getPlayer(),"crown-cannot-drop"); } }

    // Une couronne ne peut pas être transférée vers un coffre, un tonneau ou un autre inventaire externe.
    @EventHandler public void protectCrownInventoryMoves(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean currentCrown = isCrownItem(current);
        boolean cursorCrown = isCrownItem(cursor);
        boolean hotbarCrown = false;
        int hotbar = e.getHotbarButton();
        if (hotbar >= 0 && hotbar < player.getInventory().getSize()) {
            hotbarCrown = isCrownItem(player.getInventory().getItem(hotbar));
        }
        boolean externalInventory = e.getClickedInventory() != null && e.getClickedInventory() != player.getInventory();
        boolean shiftTransfer = e.isShiftClick() && currentCrown;
        boolean numberKeyTransfer = hotbarCrown && externalInventory;
        if (shiftTransfer || (externalInventory && (currentCrown || cursorCrown || numberKeyTransfer))) {
            e.setCancelled(true);
            if (currentCrown || cursorCrown || numberKeyTransfer) msg(player, "crown-cannot-drop");
        }
    }

    @EventHandler public void protectCrownInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player) || !isCrownItem(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            e.setCancelled(true);
            msg(player, "crown-cannot-drop");
        }
    }

    @EventHandler public void protectCrownFromHoppers(InventoryMoveItemEvent e) {
        if (isCrownItem(e.getItem())) e.setCancelled(true);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { if(isCrownItem(e.getMainHandItem())||isCrownItem(e.getOffHandItem())) e.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand()!=EquipmentSlot.HAND) return;
        Player p=e.getPlayer(); ItemStack hand=e.getItem(); if (hand==null || !hand.hasItemMeta()) { if(p.isSneaking() && getConfig().getBoolean("settings.activation.shift-clic-droit-menu", false) && !profile(p.getUniqueId()).crown.isBlank()){e.setCancelled(true);openPowers(p);} return; }
        if (isCustomItem(hand, upgraderKey)) { e.setCancelled(true); useUpgrader(p, hand); return; }
        if (isCustomItem(hand, repairerKey)) { e.setCancelled(true); useRepairer(p, hand); return; }
        if (isCustomItem(hand, materialKey)) { e.setCancelled(true); useMaterial(p, hand); return; }
        if (isCustomItem(hand, crownChangerKey)) { e.setCancelled(true); openCrownChangerConfirm(p); return; }
        if (p.isSneaking() && getConfig().getBoolean("settings.activation.shift-clic-droit-menu",false) && (isCrownItem(hand) || !profile(p.getUniqueId()).crown.isBlank())) { e.setCancelled(true); openPowers(p); return; }
        if (!p.isSneaking() && isCrownItem(hand) && getConfig().getBoolean("settings.activation.clic-droit-pouvoir",false)) {
            e.setCancelled(true); castPower(p,Math.max(1,Math.min(5,getConfig().getInt("settings.activation.pouvoir-clic-droit",1))));
        }
    }
    private void decorateMenuFrame(Inventory inv, Material material) {
        ItemStack pane = new ItemStack(material); ItemMeta meta = pane.getItemMeta();
        if (meta != null) { meta.setDisplayName(ChatColor.DARK_PURPLE + "✦"); meta.setLore(List.of(ChatColor.GRAY + "KingdomSMP")); pane.setItemMeta(meta); }
        int size=inv.getSize(), rows=size/9;
        for (int col=0; col<9; col++) { if (inv.getItem(col)==null) inv.setItem(col,pane.clone()); int bottom=(rows-1)*9+col; if(inv.getItem(bottom)==null) inv.setItem(bottom,pane.clone()); }
        for (int row=1; row<rows-1; row++) { int left=row*9, right=left+8; if(inv.getItem(left)==null)inv.setItem(left,pane.clone()); if(inv.getItem(right)==null)inv.setItem(right,pane.clone()); }
    }
    private ItemStack menuButton(Material material, String label, String action, String lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', label));
        if (lore != null && !lore.isBlank()) meta.setLore(List.of(ChatColor.GRAY + lore));
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta); return item;
    }
    private void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, tr(p, "gui-main-title"));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(10, menuButton(Material.ENCHANTED_BOOK, tr(p,"menu-powers"), "powers", tr(p,"menu-powers-lore")));
        inv.setItem(12, menuButton(Material.NETHER_STAR, tr(p,"menu-profile"), "profile", tr(p,"menu-profile-lore")));
        inv.setItem(14, menuButton(Material.WRITABLE_BOOK, tr(p,"menu-language"), "language", tr(p,"menu-language-lore")));
        inv.setItem(16, menuButton(Material.SHIELD, tr(p,"menu-trust"), "trust", tr(p,"menu-trust-lore")));
        inv.setItem(18, menuButton(Material.CRAFTING_TABLE, localized(p, "Recettes des objets", "Item recipes", "Recetas de objetos"), "recipes", localized(p, "Voir les crafts détaillés", "View detailed crafting recipes", "Ver recetas detalladas")));
        inv.setItem(20, menuButton(Material.NETHERITE_HELMET, localized(p, "Couronnes & descriptions", "Crowns & descriptions", "Coronas y descripciones"), "crowns", localized(p, "Passifs, description et trim de chaque couronne", "Passive, description and trim of every crown", "Pasivos, descripción y trim de cada corona")));
        if (p.hasPermission("kingdomsmp.admin")) inv.setItem(22, menuButton(Material.COMMAND_BLOCK, tr(p,"menu-admin"), "admin", tr(p,"menu-admin-lore")));
        p.openInventory(inv);
    }
    private void openLanguageMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, tr(p,"gui-language-title"));
        decorateMenuFrame(inv, Material.BLUE_STAINED_GLASS_PANE);
        inv.setItem(11, menuButton(Material.WHITE_BANNER,"Français", "lang:fr", "Français"));
        inv.setItem(13, menuButton(Material.BLUE_BANNER,"English", "lang:en", "English"));
        inv.setItem(15, menuButton(Material.RED_BANNER,"Español", "lang:es", "Español"));
        inv.setItem(22, menuButton(Material.ARROW,tr(p,"menu-back"),"back", "")); p.openInventory(inv);
    }
    private void openTrustMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, tr(p,"gui-trust-title")); int slot=0;
        for (Player target : Bukkit.getOnlinePlayers()) { if(target.equals(p))continue; ItemStack item=menuButton(profile(p.getUniqueId()).trusted.contains(target.getUniqueId())?Material.LIME_DYE:Material.GRAY_DYE, target.getName(), "trust:"+target.getUniqueId(), tr(p,"menu-trust-toggle")); inv.setItem(slot++,item); if(slot>=45)break; }
        inv.setItem(49,menuButton(Material.ARROW,tr(p,"menu-back"),"back","")); p.openInventory(inv);
    }
    private void openAdminMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, tr(p,"gui-admin-title")); int slot=0;
        for (String crown : allCrowns()) { inv.setItem(slot++,menuButton(isLegendary(crown)?Material.NETHER_STAR:Material.NETHERITE_HELMET, ChatColor.GOLD+crown, "choosecrown:"+crown, tr(p,"menu-crown-target"))); }
        inv.setItem(45,menuButton(Material.AMETHYST_SHARD,"Crown Material", "item:material", ""));
        inv.setItem(46,menuButton(Material.NETHER_STAR,"Crown Upgrader", "item:upgrader", ""));
        inv.setItem(47,menuButton(Material.ANVIL,"Crown Repairer", "item:repairer", ""));
        inv.setItem(48,menuButton(Material.WRITABLE_BOOK,tr(p,"menu-pacts"),"pacts",""));
        inv.setItem(50,menuButton(Material.PAPER,tr(p,"menu-admin-help"),"adminhelp",""));
        inv.setItem(53,menuButton(Material.ARROW,tr(p,"menu-back"),"back","")); p.openInventory(inv);
    }
    private void openPactAdminMenu(Player admin) {
        Inventory inv=Bukkit.createInventory(null,54,tr(admin,"gui-pact-title")); int slot=0;
        for (var entry:profiles.entrySet()) { Profile d=entry.getValue(); if(!d.kingAbsorbed)continue; Player target=Bukkit.getPlayer(entry.getKey()); if(target==null)continue;
            inv.setItem(slot++,menuButton(Material.BARRIER,ChatColor.RED+target.getName(),"pactstart:"+target.getUniqueId(),tr(admin,"menu-pact-start"))); if(slot>=45)break;
        }
        inv.setItem(49,menuButton(Material.ARROW,tr(admin,"menu-back"),"adminback","")); admin.openInventory(inv);
    }
    private void openAdminPlayers(Player admin, String crown) {
        pendingAdminCrowns.put(admin.getUniqueId(), crown);
        Inventory inv=Bukkit.createInventory(null,54,tr(admin,"gui-admin-players-title").replace("{crown}",crown)); int slot=0;
        for(Player target:Bukkit.getOnlinePlayers()) { inv.setItem(slot++,menuButton(Material.PLAYER_HEAD,ChatColor.YELLOW+target.getName(),"givecrown:"+target.getUniqueId(),tr(admin,"menu-assign-crown"))); if(slot>=45)break; }
        inv.setItem(49,menuButton(Material.ARROW,tr(admin,"menu-back"),"adminback","")); admin.openInventory(inv);
    }
    @EventHandler public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        String adminPlayersPrefix=tr(p,"gui-admin-players-title").split("\\{crown\\}",2)[0];
        boolean adminPlayersTitle=title.startsWith(adminPlayersPrefix);
        if (!adminPlayersTitle && !List.of(tr(p,"gui-title"),tr(p,"gui-main-title"),tr(p,"gui-language-title"),tr(p,"gui-trust-title"),tr(p,"gui-admin-title"),tr(p,"gui-pact-title"),localized(p, "Recettes KingdomSMP", "KingdomSMP Recipes", "Recetas KingdomSMP"), localized(p, "Profil KingdomSMP", "KingdomSMP Profile", "Perfil KingdomSMP"), localized(p, "Confirmer le changement", "Confirm crown change", "Confirmar cambio de corona"), crownMenuTitle(p), crownSectionTitle(p, "basic"), crownSectionTitle(p, "legendary")).contains(title)) return;
        e.setCancelled(true); ItemStack item=e.getCurrentItem(); if(item==null||!item.hasItemMeta())return;
        String action=item.getItemMeta().getPersistentDataContainer().get(menuActionKey,PersistentDataType.STRING);
        if(action==null) { if(title.equals(tr(p,"gui-title"))) { String raw=ChatColor.stripColor(item.getItemMeta().getDisplayName()); try { castPower(p,Integer.parseInt(raw.replaceAll("[^0-9]",""))); } catch(Exception ignored) {} } return; }
        if(action.equals("changer-confirm")){ItemStack hand=p.getInventory().getItemInMainHand();if(isCustomItem(hand,crownChangerKey))useCrownChanger(p,hand);else p.sendMessage(localized(p,"§cTu n'as plus de Crown Changer en main.","§cYou no longer have a Crown Changer in your hand.","§cYa no tienes un Crown Changer en la mano."));p.closeInventory();return;}
        if(action.equals("changer-cancel")){p.closeInventory();p.sendMessage(localized(p,"§7Changement annulé.","§7Change cancelled.","§7Cambio cancelado."));return;}
        if(action.equals("profile-back")){openMainMenu(p);return;} if(action.startsWith("recipe:")){openRecipeDetail(p,action.substring(7));return;} if(action.equals("powers")){openPowers(p);return;} if(action.equals("recipes")){openRecipesMenu(p);return;} if(action.equals("crowns")){openCrownsMenu(p);return;} if(action.equals("crown-section:basic")){openCrownSectionMenu(p, false);return;} if(action.equals("crown-section:legendary")){openCrownSectionMenu(p, true);return;} if(action.equals("crown-back")){openCrownsMenu(p);return;} if(action.equals("profile")){openProfile(p);return;}
        if(action.equals("language")){openLanguageMenu(p);return;} if(action.equals("trust")){openTrustMenu(p);return;} if(action.equals("admin")&&p.hasPermission("kingdomsmp.admin")){openAdminMenu(p);return;}
        if(action.equals("back")){openMainMenu(p);return;}
        if(action.equals("adminback")&&p.hasPermission("kingdomsmp.admin")){openAdminMenu(p);return;}
        if(action.equals("adminhelp")&&p.hasPermission("kingdomsmp.admin")){p.sendMessage(tr(p,"admin-help"));return;}
        if(action.equals("pacts")&&p.hasPermission("kingdomsmp.admin")){openPactAdminMenu(p);return;}
        if(action.startsWith("pactstart:")&&p.hasPermission("kingdomsmp.admin")){try{Player target=Bukkit.getPlayer(UUID.fromString(action.substring(10)));if(target==null){msg(p,"player-not-found");return;}Profile td=profile(target.getUniqueId());if(!td.kingAbsorbed){msg(p,"pact-not-needed");return;}td.pactActive=true;td.pactApproved=false;td.pactKills=0;saveStorage();msg(target,"pact-started",Map.of("kills",String.valueOf(getConfig().getInt("pacte.eliminations-requises",10)),"materials",String.valueOf(getConfig().getInt("pacte.materiaux-requis",5))));msg(p,"pact-admin-started",Map.of("player",target.getName()));openPactAdminMenu(p);}catch(IllegalArgumentException ignored){}return;}
        if(action.startsWith("choosecrown:")&&p.hasPermission("kingdomsmp.admin")){openAdminPlayers(p,action.substring(12));return;}
        if(action.startsWith("givecrown:")&&p.hasPermission("kingdomsmp.admin")){String crown=pendingAdminCrowns.get(p.getUniqueId());try{Player target=Bukkit.getPlayer(UUID.fromString(action.substring(10)));if(target==null){msg(p,"player-not-found");return;}if(crown==null){openAdminMenu(p);return;}giveCrown(target,crown);msg(p,"crown-given",Map.of("player",target.getName(),"crown",crown));msg(target,"crown-assigned",Map.of("crown",crown));pendingAdminCrowns.remove(p.getUniqueId());openAdminMenu(p);}catch(IllegalArgumentException ignored){}return;}
        if(action.startsWith("lang:")){profile(p.getUniqueId()).language=normalizeLanguage(action.substring(5));saveStorage();msg(p,"language-set");openMainMenu(p);return;}
        if(action.startsWith("trust:")){try{UUID id=UUID.fromString(action.substring(6));Profile d=profile(p.getUniqueId());if(!d.trusted.add(id))d.trusted.remove(id);saveStorage();openTrustMenu(p);}catch(IllegalArgumentException ignored){}return;}
        if(action.startsWith("crown:")&&p.hasPermission("kingdomsmp.admin")){String crown=action.substring(6);giveCrown(p,crown);msg(p,"crown-assigned",Map.of("crown",crown));openAdminMenu(p);return;}
        if(action.startsWith("item:")&&p.hasPermission("kingdomsmp.admin")){String type=action.substring(5);ItemStack give=switch(type){case "material"->createMaterial(1);case "upgrader"->createUpgrader();case "king"->createRitualToken("King");case "tyrannus"->createRitualToken("Tyrannus");case "hericles"->createRitualToken("Hériclés");default->createRepairer();};p.getInventory().addItem(give);msg(p,"item-given");return;}
        if(action.startsWith("power:")){try{castPower(p,Integer.parseInt(action.substring(6)));}catch(NumberFormatException ignored){}p.closeInventory();}
    }


    private boolean inSpawn(Location loc) {
        if (!getConfig().getBoolean("settings.spawn.protection-active",true)) return false;
        String world=getConfig().getString("settings.spawn.monde","world"); if(loc.getWorld()==null)return false;
        if(world!=null&&loc.getWorld().getName().equalsIgnoreCase(world)) {
            double x=getConfig().getDouble("settings.spawn.x",0), z=getConfig().getDouble("settings.spawn.z",0), r=getConfig().getDouble("settings.spawn.rayon",100);
            if(Math.abs(loc.getX()-x)<=r && Math.abs(loc.getZ()-z)<=r)return true;
        }
        if(Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && getConfig().getBoolean("settings.worldguard.bloquer-dans-regions-pvp-deny",true)) try { return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(loc), null, Flags.PVP)==com.sk89q.worldguard.protection.flags.StateFlag.State.DENY; } catch(Exception ex) { getLogger().fine("WorldGuard query unavailable: "+ex.getMessage()); }
        return false;
    }
    private void applyPassives(Player p) {
        if(!p.isOnline()||!p.isValid()||p.isDead())return; Profile d=profile(p.getUniqueId()); if(d.crown.isBlank())return; if(inSpawn(p.getLocation())){ clearManagedEffects(p); return; }
        String c=d.crown; if (d.crownBroken || d.crownShattered) { clearManagedEffects(p); return; }
        switch(c) {
            case "Heaven" -> addEffect(p,PotionEffectType.HASTE,2);
            case "Wrath" -> addEffect(p,PotionEffectType.STRENGTH,0);
            case "Fractionate" -> addEffect(p,PotionEffectType.SPEED,1);
            case "Wealth" -> {addEffect(p,PotionEffectType.HERO_OF_THE_VILLAGE,4);addEffect(p,PotionEffectType.LUCK,4);}
            case "Parade" -> addEffect(p,PotionEffectType.FIRE_RESISTANCE,0);
            case "Void" -> {addEffect(p,PotionEffectType.WEAVING,0);addEffect(p,PotionEffectType.HASTE,0);}
            case "Tyrannus" -> {addEffect(p,PotionEffectType.STRENGTH,0);addEffect(p,PotionEffectType.SPEED,1);addEffect(p,PotionEffectType.RESISTANCE,1);addEffect(p,PotionEffectType.FIRE_RESISTANCE,0);}
            case "Hériclés" -> {addEffect(p,PotionEffectType.STRENGTH,1);addEffect(p,PotionEffectType.SPEED,0);addEffect(p,PotionEffectType.RESISTANCE,1);addEffect(p,PotionEffectType.FIRE_RESISTANCE,0);}
            case "Nature" -> {String biome=p.getLocation().getBlock().getBiome().getKey().getKey().toUpperCase(Locale.ROOT);String effect=getConfig().getString("couronnes.Nature.effets-biomes."+biome,"");applyConfiguredEffect(p,effect);}
            case "Gl1tched" -> {long now=System.currentTimeMillis(), interval=getConfig().getLong("couronnes.Gl1tched.intervalle-secondes",600)*1000L; if(now-glitchTimes.getOrDefault(p.getUniqueId(),0L)>=interval){PotionEffectType[] es={PotionEffectType.SPEED,PotionEffectType.JUMP_BOOST,PotionEffectType.HASTE,PotionEffectType.REGENERATION,PotionEffectType.NIGHT_VISION};PotionEffectType chosen=es[new Random().nextInt(es.length)]; if(p.addPotionEffect(new PotionEffect(chosen,12000,0,true,true,true))) managedEffects.computeIfAbsent(p.getUniqueId(), ignored -> new HashSet<>()).add(chosen);glitchTimes.put(p.getUniqueId(),now);}}
            case "Adaptation" -> { PotionEffect active=p.getPotionEffect(PotionEffectType.RESISTANCE); if(active==null || active.getAmplifier()<0) addEffect(p,PotionEffectType.RESISTANCE,0); }
        }
    }
    private void clearManagedEffects(Player p) {
        Set<PotionEffectType> owned = managedEffects.remove(p.getUniqueId());
        if (owned == null) return;
        for (PotionEffectType type : owned) p.removePotionEffect(type);
    }
    private void addEffect(Player p,PotionEffectType type,int amp) {
        PotionEffect current = p.getPotionEffect(type);
        // Ne remplace pas une potion plus forte ou plus longue provenant d'une autre source.
        if (current != null && (current.getAmplifier() > amp || (current.getAmplifier() == amp && current.getDuration() > 80))) return;
        if (p.addPotionEffect(new PotionEffect(type,80,amp,true,false,true)))
            managedEffects.computeIfAbsent(p.getUniqueId(), ignored -> new HashSet<>()).add(type);
    }
    private void applyConfiguredEffect(Player p,String value) { if(value==null||!value.contains(":"))return;String[] a=value.split(":");PotionEffectType t=PotionEffectType.getByName(a[0]);if(t!=null)try{addEffect(p,t,Math.max(0,Integer.parseInt(a[1])-1));}catch(NumberFormatException ignored){} }

    private String localized(Player p, String fr, String en, String es) { return switch(profile(p.getUniqueId()).language) { case "en" -> en; case "es" -> es; default -> fr; }; }
    private List<String> wrapLore(String text, int width) { List<String> out=new ArrayList<>(); for(String part:text.split("\\n")) { StringBuilder line=new StringBuilder(); for(String word:part.split(" ")) { if(line.length()>0 && line.length()+word.length()+1>width) { out.add(ChatColor.GRAY+line.toString()); line.setLength(0); } if(line.length()>0)line.append(' '); line.append(word); } if(line.length()>0)out.add(ChatColor.GRAY+line.toString()); } return out; }
    private String crownDescription(Player p,String crown) { return switch(crown) {
        case "Nature" -> localized(p,"S’adapte au biome : ses effets passifs changent selon l’environnement.","Adapts to the biome: passive effects change with the environment.","Se adapta al bioma: sus efectos pasivos cambian con el entorno.");
        case "Wrath" -> localized(p,"Couronne de rage et de combat frontal.","A crown of rage and direct combat.","Corona de ira y combate directo.");
        case "Heaven" -> localized(p,"Pouvoir céleste : résistance et immunité aux dégâts de chute.","Celestial power: resistance and fall-damage immunity.","Poder celestial: resistencia e inmunidad al daño por caída.");
        case "Gl1tched" -> localized(p,"Couronne instable aux effets aléatoires et déplacements imprévisibles.","Unstable crown with random effects and unpredictable movement.","Corona inestable con efectos aleatorios y movimientos impredecibles.");
        case "Adaptation" -> localized(p,"Devient plus résistante au fil des coups reçus.","Grows more resistant as it takes hits.","Se vuelve más resistente al recibir golpes.");
        case "Wealth" -> localized(p,"Chance, richesse et avantages liés au commerce.","Luck, wealth and trading advantages.","Suerte, riqueza y ventajas comerciales.");
        case "Fractionate" -> localized(p,"Mobilité rapide et attaques de zone.","Fast mobility and area attacks.","Movilidad rápida y ataques de área.");
        case "Void" -> localized(p,"Maîtrise du vide : hâte et contrôle des ennemis proches.","Mastery of the void: haste and control over nearby enemies.","Dominio del vacío: prisa y control de enemigos cercanos.");
        case "Parade" -> localized(p,"Maîtrise du feu et pression sur les ennemis proches.","Fire mastery and pressure against nearby enemies.","Dominio del fuego y presión sobre enemigos cercanos.");
        case "King" -> localized(p,"Couronne légendaire capable d’absorber une couronne ennemie éligible.","Legendary crown able to absorb an eligible enemy crown.","Corona legendaria capaz de absorber una corona enemiga válida.");
        case "Tyrannus" -> localized(p,"Puissance brute, vitesse et résistance élevées.","Overwhelming strength, speed and high resistance.","Fuerza abrumadora, velocidad y gran resistencia.");
        case "Hériclés" -> localized(p,"Force héroïque, vitesse et résistance au feu.","Heroic strength, speed and fire resistance.","Fuerza heroica, velocidad y resistencia al fuego.");
        default -> localized(p,"Une couronne mystérieuse.","A mysterious crown.","Una corona misteriosa."); }; }
    private String passiveDescription(Player p,String crown) { return switch(crown) {
        case "Nature" -> localized(p,"Effet selon le biome.","Biome-dependent effect.","Efecto según el bioma.");
        case "Wrath" -> localized(p,"Force I.","Strength I.","Fuerza I.");
        case "Heaven" -> localized(p,"Résistance I et aucun dégât de chute.","Resistance I and no fall damage.","Resistencia I y sin daño por caída.");
        case "Gl1tched" -> localized(p,"Effet positif aléatoire périodique.","Periodic random positive effect.","Efecto positivo aleatorio periódico.");
        case "Adaptation" -> localized(p,"Résistance I ; amélioration après plusieurs coups reçus.","Resistance I; improves after taking several hits.","Resistencia I; mejora tras recibir varios golpes.");
        case "Wealth" -> localized(p,"Héros du village V et Chance V.","Hero of the Village V and Luck V.","Héroe de la aldea V y Suerte V.");
        case "Fractionate" -> localized(p,"Vitesse II.","Speed II.","Velocidad II.");
        case "Void" -> localized(p,"Tissage I et Célérité I.","Weaving I and Haste I.","Hilado I y Prisa I.");
        case "Parade" -> localized(p,"Résistance au feu I.","Fire Resistance I.","Resistencia al fuego I.");
        case "King" -> localized(p,"Absorption de couronne après une élimination éligible.","Crown absorption after an eligible kill.","Absorción de corona tras una eliminación válida.");
        case "Tyrannus" -> localized(p,"Force I, Vitesse II, Résistance II, Résistance au feu I.","Strength I, Speed II, Resistance II, Fire Resistance I.","Fuerza I, Velocidad II, Resistencia II, Resistencia al fuego I.");
        case "Hériclés" -> localized(p,"Force II, Vitesse I, Résistance II, Résistance au feu I.","Strength II, Speed I, Resistance II, Fire Resistance I.","Fuerza II, Velocidad I, Resistencia II, Resistencia al fuego I.");
        default -> localized(p,"Aucun passif défini.","No passive defined.","Sin pasivo definido."); }; }
    private String abilityDescription(Player p,String crown,int i) {
        String[][] fr={
            {"Soin naturel : Régénération II et Vitesse II pendant 4 s.","Racines repoussantes : repousse les ennemis proches.","Impulsion sauvage : inflige des dégâts aux ennemis proches."},
            {"Fureur : Force III pendant 5 s.","Coup de colère : frappe la cible proche.","Onde de rage : blesse les ennemis autour de toi."},
            {"Égide céleste : gagne des cœurs d’absorption.","Saut céleste : bond vertical et annulation de la chute.","Grâce divine : Résistance III et Régénération II temporaires."},
            {"Glitch : obtient un effet positif aléatoire.","Saut de faille : dash vers l’avant avec particules.","Désynchronisation : ralentit les ennemis proches."},
            {"Adaptation : Résistance III temporaire.","Carapace : gagne des cœurs d’absorption.","Évolution : régénération et résistance temporaires."},
            {"Bonne fortune : améliore temporairement Chance et Héros du village.","Taxe royale : ralentit la cible et accélère ton déplacement.","Prime : génère 3 lingots d’or et donne de l’absorption."},
            {"Surcadence : Vitesse IV temporaire.","Fracture spatiale : dash dans la direction regardée.","Frappe fractionnée : dégâts de zone."},
            {"Accélération du vide : Célérité III temporaire.","Ancre du vide : ralentit les ennemis proches.","Singularité : attire les ennemis proches vers toi."},
            {"Cœur ardent : Résistance au feu temporaire.","Marque incendiaire : enflamme les ennemis proches.","Vague de braises : dégâts de zone autour de toi."},
            {"Décret royal : blesse la cible et renforce ta Force.","Couronne de fer : Résistance III temporaire.","Jugement : frappe et repousse la cible proche.","Régénération royale : Régénération III temporaire.","Sentence : dégâts de zone autour du roi."},
            {"Souveraineté : Force III temporaire.","Marche du tyran : Vitesse IV temporaire.","Exécution : lourds dégâts sur la cible proche.","Présence écrasante : applique Faiblesse aux ennemis proches.","Peau de fer : Résistance III temporaire."},
            {"Force héroïque : Force IV temporaire.","Bond d’Hériclés : charge aérienne vers l’avant.","Onde héroïque : blesse les ennemis proches.","Peau de héros : cœurs d’absorption renforcés.","Choc mythique : explosion de dégâts autour de toi."}
        };
        String[][] en={
            {"Natural healing: Regeneration II and Speed II for 4 s.","Root burst: knocks back nearby enemies.","Wild pulse: damages nearby enemies."},
            {"Fury: Strength III for 5 s.","Anger strike: hits the nearest target.","Rage wave: damages nearby enemies."},
            {"Celestial aegis: gain absorption hearts.","Celestial leap: jump upward and reset fall distance.","Divine grace: temporary Resistance III and Regeneration II."},
            {"Glitch: gain a random positive effect.","Rift dash: dash forward with portal particles.","Desync: slows nearby enemies."},
            {"Adaptation: temporary Resistance III.","Carapace: gain absorption hearts.","Evolution: temporary regeneration and resistance."},
            {"Good fortune: temporary Luck and Hero of the Village.","Royal tax: slows the target and boosts your speed.","Bounty: creates 3 gold ingots and grants absorption."},
            {"Overclock: temporary Speed IV.","Spatial fracture: dash in the direction you face.","Fracture strike: area damage."},
            {"Void acceleration: temporary Haste III.","Void anchor: slows nearby enemies.","Singularity: pulls nearby enemies toward you."},
            {"Blazing heart: temporary Fire Resistance.","Incendiary mark: ignites nearby enemies.","Ember wave: area damage around you."},
            {"Royal decree: damages the target and boosts your Strength.","Iron crown: temporary Resistance III.","Judgment: strikes and knocks back the target.","Royal regeneration: temporary Regeneration III.","Sentence: area damage around the king."},
            {"Sovereignty: temporary Strength III.","Tyrant's march: temporary Speed IV.","Execution: heavy damage to the target.","Overwhelming presence: Weakness to nearby enemies.","Iron skin: temporary Resistance III."},
            {"Heroic strength: temporary Strength IV.","Heracles leap: forward aerial charge.","Heroic wave: damages nearby enemies.","Hero's skin: strong absorption hearts.","Mythic impact: explosive area damage."}
        };
        String[][] es={
            {"Sanación natural: Regeneración II y Velocidad II durante 4 s.","Raíces: repele a los enemigos cercanos.","Pulso salvaje: daña a los enemigos cercanos."},
            {"Furia: Fuerza III durante 5 s.","Golpe de ira: golpea al objetivo cercano.","Onda de ira: daña a los enemigos cercanos."},
            {"Égida celestial: obtienes absorción.","Salto celestial: salto vertical y anula la caída.","Gracia divina: Resistencia III y Regeneración II temporales."},
            {"Glitch: obtienes un efecto positivo aleatorio.","Salto de falla: impulso hacia delante.","Desincronización: ralentiza a los enemigos cercanos."},
            {"Adaptación: Resistencia III temporal.","Caparazón: obtienes absorción.","Evolución: regeneración y resistencia temporales."},
            {"Buena fortuna: Suerte y Héroe de la aldea temporales.","Impuesto real: ralentiza al objetivo y aumenta tu velocidad.","Recompensa: genera 3 lingotes de oro y absorción."},
            {"Sobrecarga: Velocidad IV temporal.","Fractura espacial: impulso hacia donde miras.","Golpe fracturado: daño de área."},
            {"Aceleración del vacío: Prisa III temporal.","Ancla del vacío: ralentiza a los enemigos cercanos.","Singularidad: atrae a los enemigos cercanos."},
            {"Corazón ardiente: Resistencia al fuego temporal.","Marca incendiaria: prende fuego a los enemigos cercanos.","Ola de brasas: daño de área."},
            {"Decreto real: daña al objetivo y mejora tu Fuerza.","Corona de hierro: Resistencia III temporal.","Juicio: golpea y repele al objetivo.","Regeneración real: Regeneración III temporal.","Sentencia: daño de área alrededor del rey."},
            {"Soberanía: Fuerza III temporal.","Marcha del tirano: Velocidad IV temporal.","Ejecución: gran daño al objetivo.","Presencia abrumadora: Debilidad a enemigos cercanos.","Piel de hierro: Resistencia III temporal."},
            {"Fuerza heroica: Fuerza IV temporal.","Salto de Hércules: carga aérea hacia delante.","Onda heroica: daña a los enemigos cercanos.","Piel de héroe: absorción potente.","Impacto mítico: daño explosivo de área."}
        };
        int idx=allCrowns().indexOf(crown); if(idx<0)return localized(p,"Capacidad especial.","Special ability.","Habilidad especial."); int slot=Math.max(0,Math.min(i-1,fr[idx].length-1));
        return switch(profile(p.getUniqueId()).language){case "en" -> en[idx][slot];case "es" -> es[idx][slot];default -> fr[idx][slot];};
    }
    private void openRecipesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, localized(p, "Recettes KingdomSMP", "KingdomSMP Recipes", "Recetas KingdomSMP"));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(10, menuButton(Material.AMETHYST_SHARD, "§dCrown Material", "recipe:material", localized(p,"Clique pour voir la recette","Click to view recipe","Pulsa para ver la receta")));
        inv.setItem(12, menuButton(Material.NETHER_STAR, "§dCrown Upgrader", "recipe:upgrader", localized(p,"Clique pour voir la recette","Click to view recipe","Pulsa para ver la receta")));
        inv.setItem(14, menuButton(Material.ANVIL, "§dCrown Repairer", "recipe:repairer", localized(p,"Clique pour voir la recette","Click to view recipe","Pulsa para ver la receta")));
        inv.setItem(16, menuButton(Material.ENDER_EYE, "§dCrown Changer", "recipe:changer", localized(p,"Change aléatoirement ta couronne basique","Randomly changes your basic crown","Cambia tu corona básica al azar")));
        inv.setItem(4, menuButton(Material.NETHER_STAR, "§dSceau royal", "recipe:king", "Catalyseur du rituel King"));
        inv.setItem(6, menuButton(Material.BLAZE_ROD, "§dNoyau du Tyran", "recipe:tyrannus", "Catalyseur du rituel Tyrannus"));
        inv.setItem(8, menuButton(Material.HEART_OF_THE_SEA, "§dCœur d’Hériclés", "recipe:hericles", "Catalyseur du rituel Hériclés"));
        inv.setItem(22, menuButton(Material.ARROW,tr(p,"menu-back"),"back",""));
        p.openInventory(inv);
    }
    private void openRecipeDetail(Player p, String type) {
        Inventory inv = Bukkit.createInventory(null, 36, localized(p, "Recettes KingdomSMP", "KingdomSMP Recipes", "Recetas KingdomSMP"));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        String[] shape; Map<Character, Material> map = new HashMap<>(); ItemStack output; String name;
        switch(type) {
            case "material" -> { shape = new String[]{"AGA","GEG","AGA"}; map.put('A',Material.AMETHYST_SHARD); map.put('G',Material.GOLD_INGOT); map.put('E',Material.ECHO_SHARD); output=createMaterial(1); name="Crown Material"; }
            case "upgrader" -> { shape = new String[]{"DND","AEA","DND"}; map.put('D',Material.DIAMOND); map.put('N',Material.NETHERITE_INGOT); map.put('A',Material.AMETHYST_SHARD); map.put('E',Material.EXPERIENCE_BOTTLE); output=createUpgrader(); name="Crown Upgrader"; }
            case "repairer" -> { shape = new String[]{"DAD","ANA","DAD"}; map.put('D',Material.DIAMOND); map.put('A',Material.AMETHYST_SHARD); map.put('N',Material.NETHERITE_INGOT); output=createRepairer(); name="Crown Repairer"; }
            case "changer" -> { shape = new String[]{"EAE","ANA","EAE"}; map.put('E',Material.ENDER_PEARL); map.put('A',Material.AMETHYST_SHARD); map.put('N',Material.NETHER_STAR); output=createCrownChanger(); name="Crown Changer"; }
            case "king" -> { shape = new String[]{"EEE","ENE","EEE"}; map.put('E',Material.ECHO_SHARD); map.put('N',Material.NETHER_STAR); output=createRitualToken("King"); name="Sceau royal"; }
            case "tyrannus" -> { shape = new String[]{"BDB","DND","BDB"}; map.put('B',Material.BLAZE_ROD); map.put('D',Material.DIAMOND); map.put('N',Material.NETHERITE_INGOT); output=createRitualToken("Tyrannus"); name="Noyau du Tyran"; }
            case "hericles" -> { shape = new String[]{"ASA","SHS","ASA"}; map.put('A',Material.AMETHYST_SHARD); map.put('S',Material.HEART_OF_THE_SEA); map.put('H',Material.HEAVY_CORE); output=createRitualToken("Hériclés"); name="Cœur d’Hériclés"; }
            default -> { shape = new String[]{"AGA","GEG","AGA"}; map.put('A',Material.AMETHYST_SHARD); map.put('G',Material.GOLD_INGOT); map.put('E',Material.ECHO_SHARD); output=createMaterial(1); name="Crown Material"; }
        }
        for (int y=0;y<Math.min(3, shape.length);y++) {
            String row = shape[y];
            for (int x=0;x<Math.min(3, row.length());x++) {
                char c = row.charAt(x);
                int slot = 10 + y * 9 + x;
                if (slot >= 0 && slot < inv.getSize() && map.containsKey(c)) inv.setItem(slot, new ItemStack(map.get(c)));
            }
        }
        inv.setItem(16, output);
        inv.setItem(31, menuButton(Material.ARROW, localized(p,"Retour aux recettes","Back to recipes","Volver a recetas"), "recipes", ""));
        p.openInventory(inv);
    }

    private String crownSectionTitle(Player p, String section) {
        return section.equals("legendary")
                ? localized(p, "Couronnes légendaires", "Legendary Crowns", "Coronas legendarias")
                : localized(p, "Couronnes basiques", "Basic Crowns", "Coronas básicas");
    }

    private void openCrownsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, crownMenuTitle(p));
        decorateMenuFrame(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(11, menuButton(Material.NETHERITE_HELMET,
                ChatColor.GREEN + localized(p, "COURONNES BASIQUES", "BASIC CROWNS", "CORONAS BÁSICAS"),
                "crown-section:basic", localized(p, "9 couronnes • Passifs et descriptions", "9 crowns • Passives and descriptions", "9 coronas • Pasivos y descripciones")));
        inv.setItem(15, menuButton(Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + localized(p, "COURONNES LÉGENDAIRES", "LEGENDARY CROWNS", "CORONAS LEGENDARIAS"),
                "crown-section:legendary", localized(p, "3 couronnes rares et puissantes", "3 rare and powerful crowns", "3 coronas raras y poderosas")));
        inv.setItem(22, menuButton(Material.ARROW, tr(p, "menu-back"), "back", ""));
        p.openInventory(inv);
    }

    private void openCrownSectionMenu(Player p, boolean legendarySection) {
        List<String> crowns = legendarySection ? legendary : normal;
        Inventory inv = Bukkit.createInventory(null, 54, crownSectionTitle(p, legendarySection ? "legendary" : "basic"));
        decorateMenuFrame(inv, legendarySection ? Material.MAGENTA_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE);
        int[] slots = legendarySection
                ? new int[]{20, 22, 24}
                : new int[]{10, 12, 14, 16, 19, 21, 23, 25, 31};
        for (int i = 0; i < crowns.size(); i++) {
            String crown = crowns.get(i);
            ItemStack item = createCrown(crown);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + (legendarySection
                    ? localized(p, "Couronne légendaire", "Legendary crown", "Corona legendaria")
                    : localized(p, "Couronne basique", "Basic crown", "Corona básica")));
            lore.addAll(wrapLore(crownDescription(p, crown), 38));
            lore.add(ChatColor.AQUA + localized(p, "Passif : ", "Passive: ", "Pasivo: ") + passiveDescription(p, crown));
            lore.add(ChatColor.LIGHT_PURPLE + trimDescription(p, crown));
            meta.setLore(lore);
            item.setItemMeta(meta);
            applyCrownTrim(item, crown);
            inv.setItem(slots[i], item);
        }
        inv.setItem(49, menuButton(Material.ARROW, localized(p, "Retour aux catégories", "Back to categories", "Volver a categorías"), "crown-back", ""));
        p.openInventory(inv);
    }

    private void openPowers(Player p) {
        Profile d=profile(p.getUniqueId()); if(d.crown.isBlank()){msg(p,"no-crown");return;}
        int n=isLegendary(d.crown)?5:3; Inventory inv=Bukkit.createInventory(null,27,tr(p,"gui-title")); decorateMenuFrame(inv, Material.BLACK_STAINED_GLASS_PANE); int[] thresholds=isLegendary(d.crown)?new int[]{3,5,8,12,15}:new int[]{3,5,8};
        for(int i=1;i<=n;i++){
            boolean unlocked=d.kills>=thresholds[i-1] || d.unlocked.contains("power:"+i);
            Material mat=unlocked?Material.ENCHANTED_BOOK:Material.BARRIER; ItemStack it=new ItemStack(mat);ItemMeta m=it.getItemMeta();
            m.setDisplayName((unlocked?ChatColor.GREEN:ChatColor.RED)+tr(p,"power-label").replace("{power}",String.valueOf(i)));
            List<String> lore=new ArrayList<>();
            lore.add(ChatColor.GRAY+tr(p,"required-kills").replace("{kills}",String.valueOf(thresholds[i-1])));
            lore.addAll(wrapLore(abilityDescription(p, d.crown, i), 42));
            lore.add(unlocked?ChatColor.GREEN+tr(p,"power-unlocked-label"):ChatColor.RED+tr(p,"power-locked-label"));
            lore.add(ChatColor.YELLOW+tr(p,"click-cast"));
            m.setLore(lore);m.getPersistentDataContainer().set(menuActionKey,PersistentDataType.STRING,"power:"+i);it.setItemMeta(m);inv.setItem(10+i,it);
        }
        ItemStack info=new ItemStack(Material.NETHER_STAR);ItemMeta im=info.getItemMeta();im.setDisplayName(ChatColor.GOLD+d.crown+" | "+d.kills);
        List<String> crownLore=new ArrayList<>(); crownLore.add(ChatColor.YELLOW+"Materials Left: "+d.crownMaterials+"/"+getConfig().getInt("couronne-durabilite.vies-max",10));
        crownLore.addAll(wrapLore(crownDescription(p,d.crown),42)); crownLore.add(ChatColor.AQUA+localized(p,"Passif : ","Passive: ","Pasivo: ")+passiveDescription(p,d.crown));
        crownLore.add(ChatColor.GRAY+localized(p,"Normale : 4–10 | Fracturée : 1–3 | Cassée : 0","Normal: 4–10 | Fractured: 1–3 | Broken: 0","Normal: 4–10 | Fracturada: 1–3 | Rota: 0")); im.setLore(crownLore);info.setItemMeta(im);inv.setItem(4,info);
        inv.setItem(26,menuButton(Material.ARROW,tr(p,"menu-back"),"back","")); p.openInventory(inv);
    }
    private void castPower(Player p,int index) {
        Profile d=profile(p.getUniqueId()); if(d.crown.isBlank()){msg(p,"no-crown");return;} if(d.crownBroken || d.crownShattered){msg(p,"crown-broken");return;} if(d.crownMaterials<=Math.max(1,getConfig().getInt("couronne-durabilite.seuil-fracture",3))){msg(p,"crown-fractured");return;} int[] thresholds=isLegendary(d.crown)?new int[]{3,5,8,12,15}:new int[]{3,5,8};if(index<1||index>thresholds.length)return;if(d.kills<thresholds[index-1] && !d.unlocked.contains("power:"+index)){msg(p,"power-locked");return;}if(inSpawn(p.getLocation())){msg(p,"power-spawn-blocked");return;}
        Long end=d.cooldowns.get(index);long now=System.currentTimeMillis();if(end!=null&&end>now){p.sendMessage(tr(p,"cooldown").replace("{seconds}",String.valueOf((end-now+999)/1000)));return;}
        Player target=nearestTarget(p,8);if(target!=null&&d.trusted.contains(target.getUniqueId())){msg(p,"trusted-blocked");return;}
        boolean legendaryCrown=isLegendary(d.crown);long cooldown=Math.max(3,getConfig().getLong("cooldowns."+d.crown+"."+index,getConfig().getLong("settings.cooldown-pouvoir-secondes",30)));
        switch(d.crown) {
            case "Nature" -> { if(index==1){addEffect(p,PotionEffectType.REGENERATION,1);addEffect(p,PotionEffectType.SPEED,1);p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),25,1,1,1,.05);} else if(index==2){for(Entity en:p.getNearbyEntities(6,4,6))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.15).setY(.35));p.getWorld().spawnParticle(Particle.COMPOSTER,p.getLocation(),35,2,1,2,.1);} else {p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),40,3,1,3,.1);for(Entity en:p.getNearbyEntities(5,3,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(5,p);} }
            case "Wrath" -> {if(index==1)addEffect(p,PotionEffectType.STRENGTH,2);else if(index==2&&target!=null)target.damage(8,p);else{for(Entity en:p.getNearbyEntities(5,3,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(6,p);p.getWorld().spawnParticle(Particle.FLAME,p.getLocation(),60,3,1,3,.05);}}
            case "Heaven" -> {if(index==1)addEffect(p,PotionEffectType.ABSORPTION,3);else if(index==2){p.setFallDistance(0);p.setVelocity(p.getVelocity().setY(1.1));}else{addEffect(p,PotionEffectType.RESISTANCE,2);addEffect(p,PotionEffectType.REGENERATION,1);}}
            case "Gl1tched" -> {if(index==1){PotionEffectType[] es={PotionEffectType.SPEED,PotionEffectType.JUMP_BOOST,PotionEffectType.INVISIBILITY,PotionEffectType.REGENERATION};addEffect(p,es[new Random().nextInt(es.length)],1);}else if(index==2){p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(5)));p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation(),50,.5,1,.5,.2);}else{for(Entity en:p.getNearbyEntities(5,4,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,80,2));}}
            case "Adaptation" -> {if(index==1)addEffect(p,PotionEffectType.RESISTANCE,2);else if(index==2)addEffect(p,PotionEffectType.ABSORPTION,2);else{addEffect(p,PotionEffectType.REGENERATION,2);addEffect(p,PotionEffectType.RESISTANCE,1);}}
            case "Wealth" -> {if(index==1){addEffect(p,PotionEffectType.LUCK,1);addEffect(p,PotionEffectType.HERO_OF_THE_VILLAGE,1);}else if(index==2){if(target!=null&&!trusted(p,target))target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,2));addEffect(p,PotionEffectType.SPEED,1);}else{p.getWorld().dropItemNaturally(p.getLocation(),new ItemStack(Material.GOLD_INGOT,3));addEffect(p,PotionEffectType.ABSORPTION,1);}}
            case "Fractionate" -> {if(index==1)addEffect(p,PotionEffectType.SPEED,3);else if(index==2){p.setVelocity(p.getLocation().getDirection().multiply(1.8).setY(.45));}else{for(Entity en:p.getNearbyEntities(4,3,4))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(5,p);p.getWorld().spawnParticle(Particle.SWEEP_ATTACK,p.getLocation(),12,2,1,2,.1);}}
            case "Void" -> {if(index==1)addEffect(p,PotionEffectType.HASTE,2);else if(index==2){for(Entity en:p.getNearbyEntities(6,4,6))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,2));}else{for(Entity en:p.getNearbyEntities(6,4,6))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.setVelocity(p.getLocation().toVector().subtract(le.getLocation().toVector()).normalize().multiply(.9));}}
            case "Parade" -> {if(index==1)addEffect(p,PotionEffectType.FIRE_RESISTANCE,0);else if(index==2){for(Entity en:p.getNearbyEntities(5,3,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.setFireTicks(100);}else{p.getWorld().spawnParticle(Particle.FLAME,p.getLocation(),100,3,1,3,.05);for(Entity en:p.getNearbyEntities(4,3,4))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(5,p);}}
            case "King" -> {if(index==1&&target!=null){target.damage(6,p);addEffect(p,PotionEffectType.STRENGTH,1);}else if(index==2)addEffect(p,PotionEffectType.RESISTANCE,2);else if(index==3&&target!=null){target.damage(10,p);target.setVelocity(p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.2));}else if(index==4)addEffect(p,PotionEffectType.REGENERATION,2);else{for(Entity en:p.getNearbyEntities(6,4,6))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(8,p);}}
            case "Tyrannus" -> {if(index==1)addEffect(p,PotionEffectType.STRENGTH,2);else if(index==2)addEffect(p,PotionEffectType.SPEED,3);else if(index==3&&target!=null)target.damage(10,p);else if(index==4){for(Entity en:p.getNearbyEntities(5,3,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,1));}else addEffect(p,PotionEffectType.RESISTANCE,2);}
            case "Hériclés" -> {if(index==1)addEffect(p,PotionEffectType.STRENGTH,3);else if(index==2){p.setVelocity(p.getLocation().getDirection().multiply(1.4).setY(.7));}else if(index==3){for(Entity en:p.getNearbyEntities(4,3,4))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(8,p);}else if(index==4)addEffect(p,PotionEffectType.ABSORPTION,4);else{p.getWorld().spawnParticle(Particle.EXPLOSION,p.getLocation(),1);for(Entity en:p.getNearbyEntities(5,4,5))if(en instanceof LivingEntity le&&le!=p&&!trusted(p,le))le.damage(12,p);}}
        }
        d.cooldowns.put(index,now+cooldown*1000L);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,1.3f);p.sendActionBar(tr(p,"power-used").replace("{power}",String.valueOf(index)).replace("{seconds}",String.valueOf(cooldown)));saveStorage();
    }
    private boolean trusted(Player owner, LivingEntity target) { return target instanceof Player p && profile(owner.getUniqueId()).trusted.contains(p.getUniqueId()); }
    private Player nearestTarget(Player p,double range) { Profile owner=profile(p.getUniqueId()); return p.getNearbyEntities(range,range,range).stream().filter(e->e instanceof Player).map(e->(Player)e).filter(t->!t.equals(p)&&t.getGameMode()!=GameMode.SPECTATOR&&!owner.trusted.contains(t.getUniqueId())).min(Comparator.comparingDouble(t->t.getLocation().distanceSquared(p.getLocation()))).orElse(null); }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0){if(sender instanceof Player p)openMainMenu(p);else msg(sender,"help");return true;} if(args[0].equalsIgnoreCase("help")){msg(sender,"help");return true;}
        if(args[0].equalsIgnoreCase("language")||args[0].equalsIgnoreCase("lang")){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}if(args.length==1){p.sendMessage(tr(p,"language-help"));return true;}String lang=normalizeLanguage(args[1]);if(!List.of("fr","en","es").contains(args[1].toLowerCase(Locale.ROOT))){msg(p,"language-invalid");return true;}profile(p.getUniqueId()).language=lang;saveStorage();msg(p,"language-set");return true;}
        if(args[0].equalsIgnoreCase("menu")||args[0].equalsIgnoreCase("gui")){if(sender instanceof Player p)openMainMenu(p);else msg(sender,"help");return true;}
        if(args[0].equalsIgnoreCase("me")||args[0].equalsIgnoreCase("profile")){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}openProfile(p);return true;}
        if(args[0].equalsIgnoreCase("powers")){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}openPowers(p);return true;}
        if(args[0].equalsIgnoreCase("cast")&&args.length>=2){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}if(!getConfig().getBoolean("settings.activation.commandes",true)){msg(p,"activation-disabled");return true;}try{castPower(p,Integer.parseInt(args[1]));}catch(NumberFormatException ex){msg(p,"usage-cast");}return true;}
        if(args[0].equalsIgnoreCase("trust")&&args.length>=2){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null||target.equals(p)){msg(p,"player-not-found");return true;}Profile d=profile(p.getUniqueId());if(!d.trusted.add(target.getUniqueId()))d.trusted.remove(target.getUniqueId());saveStorage();msg(p,"trust-updated");return true;}
        if(args[0].equalsIgnoreCase("item") && args.length>=2){
            if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}
            if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}
            ItemStack give;
            try { give=switch(args[1].toLowerCase(Locale.ROOT)){case "material" -> createMaterial(args.length>=3?Math.max(1,Math.min(64,Integer.parseInt(args[2]))):1);case "upgrader" -> createUpgrader();case "repairer" -> createRepairer();case "crownchanger", "changer" -> createCrownChanger();case "king" -> createRitualToken("King");case "tyrannus" -> createRitualToken("Tyrannus");case "hericles", "hericlés" -> createRitualToken("Hériclés");default -> null;}; }
            catch(NumberFormatException ex) { msg(sender,"item-help"); return true; }
            if(give==null){msg(sender,"item-help");return true;}p.getInventory().addItem(give);msg(sender,"item-given");return true;
        }
        if(args[0].equalsIgnoreCase("pact")) {
            if(args.length==1 || args[1].equalsIgnoreCase("status")) {
                if(!(sender instanceof Player p)){msg(sender,"pact-status-help");return true;}
                Profile d=profile(p.getUniqueId()); int mats=countCustomItems(p,materialKey);
                msg(p,"pact-status",Map.of("active",String.valueOf(d.pactActive),"kills",String.valueOf(d.pactKills),"required",String.valueOf(getConfig().getInt("pacte.eliminations-requises",10)),"materials",String.valueOf(mats),"materials-required",String.valueOf(getConfig().getInt("pacte.materiaux-requis",5)))); return true;
            }
            if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}
            if(args.length<3){msg(sender,"pact-help");return true;}
            String sub=args[1].toLowerCase(Locale.ROOT); Player target=Bukkit.getPlayerExact(args[2]); if(target==null){msg(sender,"player-not-found");return true;} Profile td=profile(target.getUniqueId());
            if(sub.equals("start")) { if(!td.kingAbsorbed){msg(sender,"pact-not-needed");return true;} td.pactActive=true;td.pactApproved=false;td.pactKills=0;saveStorage();msg(target,"pact-started",Map.of("kills",String.valueOf(getConfig().getInt("pacte.eliminations-requises",10)),"materials",String.valueOf(getConfig().getInt("pacte.materiaux-requis",5))));msg(sender,"pact-admin-started",Map.of("player",target.getName()));return true; }
            if(sub.equals("approve")) { int needKills=Math.max(0,getConfig().getInt("pacte.eliminations-requises",10)), needMats=Math.max(0,getConfig().getInt("pacte.materiaux-requis",5));int mats=countCustomItems(target,materialKey);if(!td.pactActive||td.pactKills<needKills||mats<needMats){msg(sender,"pact-not-complete",Map.of("kills",String.valueOf(td.pactKills),"required",String.valueOf(needKills),"materials",String.valueOf(mats),"materials-required",String.valueOf(needMats)));return true;}removeCustomItems(target,materialKey,needMats);td.pactActive=false;td.pactApproved=true;saveStorage();msg(target,"pact-approved");msg(sender,"pact-admin-approved",Map.of("player",target.getName()));return true; }
            if(sub.equals("cancel")){td.pactActive=false;td.pactApproved=false;td.pactKills=0;saveStorage();msg(target,"pact-cancelled");msg(sender,"pact-admin-cancelled",Map.of("player",target.getName()));return true;}
            msg(sender,"pact-help");return true;
        }
        if(args[0].equalsIgnoreCase("ritual")) {
            if (!sender.hasPermission("kingdomsmp.ritual")) { msg(sender, "no-permission"); return true; }
            Player target;
            String crownText;
            if (args.length == 2 && sender instanceof Player player) {
                target = player;
                crownText = args[1];
            } else if (args.length >= 3 && sender.hasPermission("kingdomsmp.admin")) {
                target = Bukkit.getPlayerExact(args[1]);
                crownText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (target == null) { msg(sender, "player-not-found"); return true; }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /kingdom ritual <couronne> (ou /kingdom ritual <joueur> <couronne> pour un admin)");
                return true;
            }
            String crown = findCrown(crownText);
            if (crown == null || !isLegendary(crown)) { msg(sender, "usage-ritual"); return true; }
            startRitual(sender, target, crown);
            return true;
        }
        if(args[0].equalsIgnoreCase("save")){if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}saveStorage();msg(sender,"saved");return true;}
        if(args[0].equalsIgnoreCase("reload")){if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}reloadConfig();msg(sender,"reloaded");return true;}
        if(args[0].equalsIgnoreCase("status")){if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}sender.sendMessage(ChatColor.GOLD+"KingdomSMP 0.4.8");for(String plugin:List.of("Vault","BetterTeams","WorldGuard")){boolean enabled=Bukkit.getPluginManager().isPluginEnabled(plugin);sender.sendMessage((enabled?ChatColor.GREEN:ChatColor.RED)+plugin+": "+(enabled?"détecté":"absent"));}sender.sendMessage(ChatColor.YELLOW+"Ce statut confirme la présence du plugin, pas toutes ses intégrations API.");return true;}
        if(args[0].equalsIgnoreCase("events")){if(!(sender instanceof Player p)){sender.sendMessage("Cette commande est réservée aux joueurs.");return true;}if(!crownEventActive){long seconds=Math.max(0,(nextCrownEventAt-System.currentTimeMillis())/1000L);p.sendMessage(localized(p,"§7Aucun événement actif. Prochain événement dans "+(seconds/3600)+"h "+((seconds%3600)/60)+"min.","§7No event active. Next event in "+(seconds/3600)+"h "+((seconds%3600)/60)+"min.","§7No hay evento activo. Próximo evento en "+(seconds/3600)+"h "+((seconds%3600)/60)+"min."));return true;}long seconds=Math.max(0,(crownEventEndsAt-System.currentTimeMillis())/1000L);int progress=eventProgress.getOrDefault(p.getUniqueId(),0);int required=eventRequired();p.sendMessage(ChatColor.LIGHT_PURPLE+"✦ "+localized(p,"Événement des couronnes","Crown event","Evento de coronas"));p.sendMessage(ChatColor.YELLOW+eventTitle(p));p.sendMessage(ChatColor.YELLOW+localized(p,"Progression : ","Progress: ","Progreso: ")+progress+"/"+required+" "+eventObjective(p));p.sendMessage(ChatColor.YELLOW+localized(p,"Temps restant : ","Time remaining: ","Tiempo restante: ")+seconds/60+" min");return true;}
        if(!sender.hasPermission("kingdomsmp.admin")){msg(sender,"no-permission");return true;}
        if(args[0].equalsIgnoreCase("crown")&&args.length>=3){Player target=Bukkit.getPlayerExact(args[1]);String crown=findCrown(String.join(" ",Arrays.copyOfRange(args,2,args.length)));if(target==null||crown==null){msg(sender,"usage-crown");return true;}giveCrown(target,crown);msg(sender,"crown-given",Map.of("player",target.getName(),"crown",crown));msg(target,"crown-assigned",Map.of("crown",crown));return true;}
        if(args[0].equalsIgnoreCase("remove")&&args.length>=2){Player t=Bukkit.getPlayerExact(args[1]);if(t==null){msg(sender,"player-not-found");return true;}Profile td=profile(t.getUniqueId());td.crown="";td.crownBroken=false;td.crownShattered=false;td.kingAbsorbed=false;td.pactActive=false;td.pactApproved=false;td.pactKills=0;removeCrownItems(t);clearManagedEffects(t);saveStorage();msg(sender,"crown-removed");return true;}
        msg(sender,"help");return true;
    }
    private void startRitual(CommandSender sender, Player target, String crown) {
        Profile data = profile(target.getUniqueId());
        if (crown.equals(data.crown)) { msg(sender, "ritual-already-has-crown"); return; }
        if (ritualInProgress.contains(target.getUniqueId())) { msg(sender, "ritual-already-running"); return; }
        if (data.kingAbsorbed && !data.pactApproved) { msg(sender, "ritual-pact-required"); return; }
        if (!hasRitualToken(target, crown)) {
            target.sendMessage(localized(target,
                    "§cCatalyseur requis pour " + crown + " : " + switch(crown) { case "King" -> "Sceau royal"; case "Tyrannus" -> "Noyau du Tyran"; default -> "Cœur d’Hériclés"; } + ".",
                    "§cRequired catalyst for " + crown + ": " + switch(crown) { case "King" -> "Royal Seal"; case "Tyrannus" -> "Tyrant Core"; default -> "Heracles Heart"; } + ".",
                    "§cNecesitas el catalizador para " + crown + "."));
            return;
        }
        if (!hasRitualOfferings(target, crown)) {
            target.sendMessage(localized(target,
                    "§cOffrandes insuffisantes ! Il te faut : §e" + ritualOfferingText(crown) + "§c.",
                    "§cInsufficient offerings! You need: §e" + ritualOfferingText(crown) + "§c.",
                    "§c¡Ofrendas insuficientes! Necesitas: §e" + ritualOfferingText(crown) + "§c."));
            return;
        }
        // Les offrandes sont sacrifiées au départ ; le catalyseur n'est consommé qu'à la réussite.
        consumeRitualOfferings(target, crown);
        ritualInProgress.add(target.getUniqueId());
        ritualInterrupted.remove(target.getUniqueId());
        String previousCrown = data.crown;
        World world = target.getWorld();
        Location origin = target.getLocation().clone();
        int duration = Math.max(60, getConfig().getInt("rituels.duree-secondes", 120));
        target.sendTitle(ChatColor.LIGHT_PURPLE + "✦ " + crown + " ✦", localized(target,
                "§cLe rituel commence… tiens 2 minutes sans être blessé !", "§cThe ritual begins… survive 2 minutes without taking damage!", "§cEl ritual comienza… ¡resiste 2 minutos sin recibir daño!"), 10, 80, 20);
        target.sendMessage(localized(target,
                "§6Les offrandes ont été sacrifiées : §e" + ritualOfferingText(crown) + "§6. Une blessure ou un déplacement de plus de 3 blocs brisera le rituel.",
                "§6Offerings have been sacrificed: §e" + ritualOfferingText(crown) + "§6. Damage or moving more than 3 blocks will break the ritual.",
                "§6Las ofrendas fueron sacrificadas: §e" + ritualOfferingText(crown) + "§6. Recibir daño o alejarte más de 3 bloques romperá el ritual."));
        world.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1f, .55f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                UUID id = target.getUniqueId();
                if (!target.isOnline() || !target.isValid() || target.isDead()) {
                    ritualInProgress.remove(id); ritualInterrupted.remove(id);
                    if (target.isOnline()) target.sendMessage(localized(target, "§cRituel échoué : mort ou déconnexion.", "§cRitual failed: death or disconnect.", "§cRitual fallido: muerte o desconexión."));
                    cancel(); return;
                }
                Profile current = profile(id);
                if (ritualInterrupted.remove(id)) {
                    ritualInProgress.remove(id);
                    target.sendMessage(localized(target, "§cRituel brisé par les dégâts ! Les offrandes sont perdues.", "§cRitual broken by damage! The offerings are lost.", "§c¡El daño rompió el ritual! Las ofrendas se pierden."));
                    world.playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, .8f, .7f); cancel(); return;
                }
                double maxDistance = Math.max(1.0, getConfig().getDouble("rituels.distance-maximale-blocs", 3.0));
                if (!Objects.equals(current.crown, previousCrown) || !target.getWorld().equals(world) || target.getLocation().distanceSquared(origin) > maxDistance * maxDistance) {
                    ritualInProgress.remove(id); ritualInterrupted.remove(id);
                    target.sendMessage(localized(target, "§cRituel échoué : couronne, monde ou position modifié. Offrandes perdues.", "§cRitual failed: crown, world, or position changed. Offerings lost.", "§cRitual fallido: cambió la corona, el mundo o la posición. Ofrendas perdidas."));
                    cancel(); return;
                }
                if (ticks % 20 == 0) {
                    target.sendActionBar(localized(target, "§dRituel de " + crown + " §7— §f" + Math.max(0, duration - ticks / 20) + " s", "§d" + crown + " ritual §7— §f" + Math.max(0, duration - ticks / 20) + " s", "§dRitual de " + crown + " §7— §f" + Math.max(0, duration - ticks / 20) + " s"));
                    world.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .4f, 1.4f);
                }
                target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 10, .35, .65, .35, .015);
                target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 12, .45, .7, .45, .025);
                if (++ticks >= duration * 20) {
                    ritualInProgress.remove(id); ritualInterrupted.remove(id);
                    if (!consumeRitualToken(target, crown)) { target.sendMessage(localized(target, "§cCatalyseur manquant : rituel annulé.", "§cCatalyst missing: ritual cancelled.", "§cFalta el catalizador: ritual cancelado.")); cancel(); return; }
                    giveCrown(target, crown);
                    target.getWorld().playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, .7f);
                    target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 80, .8, 1, .8, .1);
                    msg(target, "crown-assigned", Map.of("crown", crown));
                    msg(sender, "ritual-complete", Map.of("player", target.getName(), "crown", crown));
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private String findCrown(String raw){for(String c:allCrowns())if(c.equalsIgnoreCase(raw))return c;return null;}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return List.of("help","menu","me","powers","cast","language","lang","trust","crown","remove","ritual","item","pact","save","reload","status","events").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("item"))return List.of("material","upgrader","repairer","changer","king","tyrannus","hericles");
        if(args.length==2&&args[0].equalsIgnoreCase("pact"))return List.of("status","start","approve","cancel");
        if(args.length==3&&args[0].equalsIgnoreCase("pact")&&List.of("start","approve","cancel").contains(args[1].toLowerCase(Locale.ROOT)))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(args.length==2&&(args[0].equalsIgnoreCase("language")||args[0].equalsIgnoreCase("lang")))return List.of("fr","en","es");
        if(args.length==2&&args[0].equalsIgnoreCase("cast"))return List.of("1","2","3","4","5");
        if(args.length==2&&List.of("crown","remove","trust").contains(args[0].toLowerCase()))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("ritual"))return legendary;
        if(args.length==3&&List.of("crown","ritual").contains(args[0].toLowerCase()))return args[0].equalsIgnoreCase("ritual")?legendary:allCrowns();
        if(args.length==3&&args[0].equalsIgnoreCase("cast"))return List.of("1","2","3","4","5");return Collections.emptyList();}
}
