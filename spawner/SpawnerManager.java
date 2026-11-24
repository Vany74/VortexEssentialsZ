package fr.vortexsmp.vortexessentials.spawner;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.shop.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawners empilables avec mobs réels, auto-farm, stockage des drops et de l'expérience, boutique (Vault), protections
 * contre explosions et pistons ; améliorations (vitesse, portée, quantité), joueurs autorisés, limites par joueur et par
 * chunk, hologrammes, vente automatique des drops et journal d'audit. Réglages : spawners.* dans config.yml.
 */
public final class SpawnerManager implements Listener {
    private final VortexEssentials plugin;
    private final YamlStore store;
    private final Map<String, SpawnerData> spawners = new HashMap<>();
    private final NamespacedKey typeKey, mobKey, factorKey;
    private boolean dirty;

    public SpawnerManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "spawners.yml");
        this.typeKey = new NamespacedKey(plugin, "uspawner_type");
        this.mobKey = new NamespacedKey(plugin, "uspawner_mob");
        this.factorKey = new NamespacedKey(plugin, "uspawner_factor");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 600L, 600L);
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("spawners.enabled", true); }
    private int ci(String p, int def) { return plugin.getConfig().getInt("spawners." + p, def); }
    private boolean cb(String p, boolean def) { return plugin.getConfig().getBoolean("spawners." + p, def); }
    public List<Integer> upgradePrices(String kind) { return plugin.getConfig().getIntegerList("spawners.upgrades." + kind); }
    public int maxStack() { return ci("max-stack", 128); }

    static String key(Block b) { return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ(); }

    public Map<String, SpawnerData> all() { return spawners; }
    public SpawnerData at(Block b) { return spawners.get(key(b)); }
    public void markDirty() { dirty = true; }

    private void load() {
        spawners.clear();
        ConfigurationSection cs = store.get().getConfigurationSection("spawners");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(k);
            if (s == null) continue;
            try {
                SpawnerData d = new SpawnerData();
                d.key = s.getString("key");
                d.world = s.getString("world");
                d.x = s.getInt("x"); d.y = s.getInt("y"); d.z = s.getInt("z");
                d.type = EntityType.valueOf(s.getString("type", "ZOMBIE"));
                d.stack = Math.max(1, s.getInt("stack", 1));
                if (s.contains("owner")) d.owner = UUID.fromString(s.getString("owner"));
                d.ownerName = s.getString("owner-name", "?");
                for (String t : s.getStringList("trusted")) d.trusted.add(UUID.fromString(t));
                d.speed = s.getInt("speed"); d.range = s.getInt("range"); d.qty = s.getInt("qty");
                d.autoSell = s.getBoolean("auto-sell");
                d.exp = s.getLong("exp");
                ConfigurationSection dr = s.getConfigurationSection("drops");
                if (dr != null) for (String m : dr.getKeys(false)) {
                    Material mat = Material.matchMaterial(m);
                    if (mat != null) d.drops.put(mat, dr.getLong(m));
                }
                if (d.key != null) spawners.put(d.key, d);
            } catch (Exception ignored) { }
        }
    }

    public void save() {
        dirty = false;
        store.get().set("spawners", null);
        int i = 0;
        for (SpawnerData d : spawners.values()) {
            String p = "spawners.s" + (i++);
            store.get().set(p + ".key", d.key);
            store.get().set(p + ".world", d.world);
            store.get().set(p + ".x", d.x); store.get().set(p + ".y", d.y); store.get().set(p + ".z", d.z);
            store.get().set(p + ".type", d.type.name());
            store.get().set(p + ".stack", d.stack);
            if (d.owner != null) store.get().set(p + ".owner", d.owner.toString());
            store.get().set(p + ".owner-name", d.ownerName);
            List<String> tr = new ArrayList<>();
            for (UUID u : d.trusted) tr.add(u.toString());
            store.get().set(p + ".trusted", tr);
            store.get().set(p + ".speed", d.speed); store.get().set(p + ".range", d.range); store.get().set(p + ".qty", d.qty);
            store.get().set(p + ".auto-sell", d.autoSell);
            store.get().set(p + ".exp", d.exp);
            for (Map.Entry<Material, Long> e : d.drops.entrySet()) if (e.getValue() > 0) store.get().set(p + ".drops." + e.getKey().name(), e.getValue());
        }
        store.save();
    }

    public void shutdown() {
        save();
        for (SpawnerData d : spawners.values()) removeHolo(d);
    }

    public void audit(String line) {
        if (!cb("audit-log", true)) return;
        String text = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + line + System.lineSeparator();
        File dir = new File(plugin.getDataFolder(), "spawners");
        dir.mkdirs();
        File f = new File(dir, "audit-" + LocalDate.now() + ".log");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { Files.writeString(f.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) { }
        });
    }

    public List<String> tail(int n) {
        File f = new File(new File(plugin.getDataFolder(), "spawners"), "audit-" + LocalDate.now() + ".log");
        List<String> out = new ArrayList<>();
        try {
            if (f.exists()) {
                List<String> all = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                out.addAll(all.subList(Math.max(0, all.size() - n), all.size()));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static String pretty(EntityType t) {
        String s = t.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public ItemStack item(EntityType type, int amount) {
        ItemStack it = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(type);
            bsm.setBlockState(cs);
        }
        meta.displayName(Msg.c("§e" + pretty(type) + " Spawner"));
        meta.lore(List.of(Msg.c("§7Posez-le, puis posez-en d'autres du même type"), Msg.c("§7dessus (clic droit) pour les empiler.")));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        it.setItemMeta(meta);
        return it;
    }

    public EntityType typeOf(ItemStack s) {
        if (s == null || s.getType() != Material.SPAWNER || !s.hasItemMeta()) return null;
        String v = s.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (v == null) return null;
        try { return EntityType.valueOf(v); } catch (IllegalArgumentException e) { return null; }
    }

    private void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
    }

    private long countOwned(UUID id) {
        long n = 0;
        for (SpawnerData d : spawners.values()) if (id.equals(d.owner)) n++;
        return n;
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        EntityType t = typeOf(e.getItemInHand());
        if (t == null || !enabled()) return;
        Player p = e.getPlayer();
        if (cb("require-use-permission", true) && !p.hasPermission("ultimatespawners.use")) {
            e.setCancelled(true);
            Msg.prefixed(p, "§cVous n'avez pas la permission d'utiliser les spawners.");
            return;
        }
        int perPlayer = ci("limits.max-blocks-per-player", 50), perChunk = ci("limits.max-blocks-per-chunk", 8);
        Block b = e.getBlockPlaced();
        if (!p.hasPermission("ultimatespawners.admin")) {
            if (perPlayer > 0 && countOwned(p.getUniqueId()) >= perPlayer) {
                e.setCancelled(true);
                Msg.prefixed(p, "§cLimite atteinte : §f" + perPlayer + " §cspawners posés par joueur (empilez-les !).");
                return;
            }
            if (perChunk > 0) {
                long inChunk = spawners.values().stream().filter(d -> d.world.equals(b.getWorld().getName()) && (d.x >> 4) == (b.getX() >> 4) && (d.z >> 4) == (b.getZ() >> 4)).count();
                if (inChunk >= perChunk) {
                    e.setCancelled(true);
                    Msg.prefixed(p, "§cLimite atteinte : §f" + perChunk + " §cspawners par chunk.");
                    return;
                }
            }
        }
        SpawnerData d = new SpawnerData();
        d.key = key(b);
        d.world = b.getWorld().getName();
        d.x = b.getX(); d.y = b.getY(); d.z = b.getZ();
        d.type = t;
        d.owner = p.getUniqueId();
        d.ownerName = p.getName();
        d.nextSpawn = System.currentTimeMillis() + 3000;
        spawners.put(d.key, d);
        Bukkit.getScheduler().runTask(plugin, () -> {
            BlockState st = b.getState();
            if (st instanceof CreatureSpawner cs) {
                cs.setSpawnedType(t);
                cs.setRequiredPlayerRange(0);
                cs.update();
            }
        });
        dirty = true;
        audit(p.getName() + " pose " + pretty(t) + " en " + d.key);
        Msg.prefixed(p, "§aSpawner " + pretty(t) + " posé. §7Clic droit : menu — posez-en d'autres du même type dessus pour empiler.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData d = at(b);
        if (d == null) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        EntityType ht = typeOf(hand);
        if (ht == null && p.isSneaking() && !hand.getType().isAir()) return;
        e.setCancelled(true);
        if (!d.allowed(p.getUniqueId()) && !p.hasPermission("ultimatespawners.admin")) {
            Msg.prefixed(p, "§cCe spawner appartient à §f" + d.ownerName + "§c.");
            return;
        }
        if (ht != null) {
            if (ht != d.type) { Msg.prefixed(p, "§cCe spawner est de type §f" + pretty(d.type) + "§c : impossible de fusionner."); return; }
            int room = maxStack() - d.stack;
            if (room <= 0) { Msg.prefixed(p, "§cPile pleine (§f" + maxStack() + "§c)."); return; }
            int add = Math.min(room, p.isSneaking() ? hand.getAmount() : 1);
            hand.setAmount(hand.getAmount() - add);
            d.stack += add;
            dirty = true;
            refreshHolo(d);
            audit(p.getName() + " fusionne +" + add + " " + pretty(d.type) + " en " + d.key + " (pile " + d.stack + ")");
            Msg.prefixed(p, "§aPile : §f" + d.stack + "§7/§f" + maxStack());
            return;
        }
        plugin.spawnerMenus().open(p, d);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void breakBlock(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData d = at(b);
        if (d == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("ultimatespawners.break") || (!d.allowed(p.getUniqueId()) && !p.hasPermission("ultimatespawners.admin"))) {
            Msg.prefixed(p, "§cVous ne pouvez pas récupérer ce spawner (propriétaire : §f" + d.ownerName + "§c).");
            return;
        }
        if (cb("silk-touch-required", true) && !p.hasPermission("ultimatespawners.admin")
                && !p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            Msg.prefixed(p, "§cIl faut un outil avec Toucher de soie pour récupérer un spawner.");
            return;
        }
        int take = p.isSneaking() ? d.stack : 1;
        give(p, item(d.type, take));
        d.stack -= take;
        audit(p.getName() + " récupère " + take + " " + pretty(d.type) + " en " + d.key + " (reste " + d.stack + ")");
        if (d.stack <= 0) {
            for (Map.Entry<Material, Long> en : d.drops.entrySet()) giveItems(p, en.getKey(), en.getValue());
            if (d.exp > 0) p.giveExp((int) Math.min(Integer.MAX_VALUE, d.exp));
            removeHolo(d);
            spawners.remove(d.key);
            b.setType(Material.AIR);
        } else {
            refreshHolo(d);
        }
        dirty = true;
    }

    public void giveItems(Player p, Material m, long amount) {
        long left = amount;
        while (left > 0) {
            int amt = (int) Math.min(left, m.getMaxStackSize());
            give(p, new ItemStack(m, amt));
            left -= amt;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        if (cb("protect-from-explosions", true)) e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent e) {
        if (cb("protect-from-explosions", true)) e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonExtend(BlockPistonExtendEvent e) {
        if (cb("protect-from-pistons", true) && e.getBlocks().stream().anyMatch(b -> b.getType() == Material.SPAWNER && at(b) != null)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent e) {
        if (cb("protect-from-pistons", true) && e.getBlocks().stream().anyMatch(b -> b.getType() == Material.SPAWNER && at(b) != null)) e.setCancelled(true);
    }

    private void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        for (SpawnerData d : new ArrayList<>(spawners.values())) {
            World w = Bukkit.getWorld(d.world);
            if (w == null || !w.isChunkLoaded(d.x >> 4, d.z >> 4)) continue;
            if (w.getBlockAt(d.x, d.y, d.z).getType() != Material.SPAWNER) { removeHolo(d); spawners.remove(d.key); dirty = true; continue; }
            if (cb("hologram", true) && now - d.lastHolo > 15_000) refreshHolo(d);
            if (now < d.nextSpawn) continue;
            double r = ci("base-range", 16) + 4.0 * d.range;
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(new Location(w, d.x + 0.5, d.y, d.z + 0.5)) <= r * r) { near = true; break; }
            }
            if (!near) { d.nextSpawn = now + 2000; continue; }
            cycle(d, w, now);
        }
    }

    private void cycle(SpawnerData d, World w, long now) {
        int min = ci("min-spawn-delay", 200), max = Math.max(min + 1, ci("max-spawn-delay", 800));
        double delayTicks = ThreadLocalRandom.current().nextInt(min, max + 1) / (1.0 + 0.3 * d.speed);
        d.nextSpawn = now + (long) (delayTicks * 50);

        int perCycle = d.stack * (1 + d.qty);
        int spawn = Math.max(1, Math.min(perCycle, ci("max-spawn-per-cycle", 12)));
        double factor = (double) perCycle / spawn;
        boolean farm = cb("auto-farm.enabled", true);
        int kill = ci("auto-farm-kill-delay-ticks", 40);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < spawn; i++) {
            Location l = new Location(w, d.x + 0.5 + rnd.nextInt(-2, 3), d.y + 1, d.z + 0.5 + rnd.nextInt(-2, 3));
            if (!l.getBlock().isPassable()) l = new Location(w, d.x + 0.5, d.y + 1, d.z + 0.5);
            Entity ent;
            try { ent = w.spawnEntity(l, d.type); } catch (Exception ex) { return; }
            if (ent instanceof LivingEntity le) {
                le.setAI(!farm);
                le.setSilent(true);
                le.setRemoveWhenFarAway(true);
            }
            ent.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, d.key);
            ent.getPersistentDataContainer().set(factorKey, PersistentDataType.DOUBLE, factor);
            if (farm) {
                final Entity target = ent;
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (target.isValid() && target instanceof LivingEntity x) x.setHealth(0); }, Math.max(1, kill));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent e) {
        String k = e.getEntity().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (k == null) return;
        SpawnerData d = spawners.get(k);
        if (d == null) return;
        Double f = e.getEntity().getPersistentDataContainer().get(factorKey, PersistentDataType.DOUBLE);
        double factor = f == null ? 1.0 : f;
        if (cb("auto-farm.store-drops", true)) {
            for (ItemStack s : e.getDrops()) d.drops.merge(s.getType(), Math.max(1, Math.round(s.getAmount() * factor)), Long::sum);
            e.getDrops().clear();
        }
        if (cb("auto-farm.store-experience", true)) {
            d.exp += Math.round(e.getDroppedExp() * factor);
            e.setDroppedExp(0);
        }
        dirty = true;
        if (d.autoSell) sellAll(d, null);
    }

    public double sellAll(SpawnerData d, Player seller) {
        if (!plugin.money().available()) return 0;
        OfflinePlayer owner = seller != null ? seller : (d.owner == null ? null : Bukkit.getOfflinePlayer(d.owner));
        if (owner == null) return 0;
        double total = 0;
        List<Material> sold = new ArrayList<>();
        for (Map.Entry<Material, Long> en : d.drops.entrySet()) {
            ShopManager.Entry price = plugin.shop().sellIndex().get(en.getKey().name());
            if (price == null || price.sell < 0 || en.getValue() <= 0) continue;
            double unit = seller != null ? plugin.shop().sellUnit(seller, price) : price.sell;
            if (unit <= 0) continue;
            total += unit * en.getValue();
            sold.add(en.getKey());
        }
        total = Math.round(total * 100.0) / 100.0;
        if (total <= 0 || !plugin.money().deposit(owner, total)) return 0;
        for (Material m : sold) d.drops.remove(m);
        dirty = true;
        audit((seller != null ? seller.getName() : "auto") + " vend les drops de " + d.key + " pour " + total);
        return total;
    }

    private Component holoText(SpawnerData d) {
        return Msg.c("§e" + pretty(d.type) + " §7×§f" + d.stack + "\n§7Stocké : §f" + d.storedItems() + " §7items");
    }

    public void refreshHolo(SpawnerData d) {
        if (!cb("hologram", true)) { removeHolo(d); return; }
        World w = Bukkit.getWorld(d.world);
        if (w == null || !w.isChunkLoaded(d.x >> 4, d.z >> 4)) return;
        d.lastHolo = System.currentTimeMillis();
        Entity en = d.holo == null ? null : Bukkit.getEntity(d.holo);
        if (en instanceof TextDisplay td && td.isValid()) {
            td.text(holoText(d));
            return;
        }
        Location l = new Location(w, d.x + 0.5, d.y + 1.7, d.z + 0.5);
        TextDisplay td = w.spawn(l, TextDisplay.class, t -> {
            t.text(holoText(d));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
        });
        d.holo = td.getUniqueId();
    }

    public void removeHolo(SpawnerData d) {
        if (d.holo == null) return;
        Entity en = Bukkit.getEntity(d.holo);
        if (en != null) en.remove();
        d.holo = null;
    }
}
