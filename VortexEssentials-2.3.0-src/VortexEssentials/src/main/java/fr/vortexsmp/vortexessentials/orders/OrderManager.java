package fr.vortexsmp.vortexessentials.orders;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commandes (« orders » façon DonutSMP) : un joueur demande N exemplaires d'un item à un prix unitaire, l'argent est mis
 * de côté ; les autres joueurs livrent et sont payés tout de suite. Le serveur peut aussi poster des commandes (illimitées
 * en argent, quantité limitée et reconstituée). Données dans orders.yml.
 */
public final class OrderManager {
    public static final class Order {
        public int id;
        public UUID owner;
        public String ownerName = "Serveur";
        public Material mat;
        public int total, remaining, collected;
        public double price, escrow;
        public boolean server;
        public long refillMs, lastRefill;
        public String data;                 // item complet sérialisé (enchantements, nom...) ; null = item simple
        private transient ItemStack tpl;

        /** Exemplaire de l'item demandé (quantité 1). */
        public ItemStack template() {
            if (tpl == null) {
                if (data != null) {
                    try { tpl = ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(data)); } catch (Exception e) { tpl = new ItemStack(mat); }
                } else {
                    tpl = new ItemStack(mat);
                }
            }
            return tpl.clone();
        }

        public boolean matches(ItemStack s) {
            if (s == null || s.getType() != mat) return false;
            ItemStack one = s.clone();
            one.setAmount(1);
            return one.isSimilar(template());
        }
    }

    private static String encode(ItemStack hand) {
        if (!hand.hasItemMeta()) return null;
        ItemStack one = hand.clone();
        one.setAmount(1);
        return java.util.Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    private final VortexEssentials plugin;
    private final YamlStore store;
    private final List<Order> orders = new ArrayList<>();
    private int nextId = 1;

    public OrderManager(VortexEssentials plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "orders.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refill, 1200L, 1200L);
    }

    private void load() {
        orders.clear();
        nextId = store.get().getInt("next-id", 1);
        ConfigurationSection cs = store.get().getConfigurationSection("orders");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(k);
            Material m = s == null ? null : Material.matchMaterial(s.getString("item", ""));
            if (m == null) continue;
            Order o = new Order();
            o.id = Integer.parseInt(k);
            o.mat = m;
            o.server = s.getBoolean("server");
            if (s.contains("owner")) o.owner = UUID.fromString(s.getString("owner"));
            o.ownerName = s.getString("owner-name", "Serveur");
            o.total = s.getInt("total");
            o.remaining = s.getInt("remaining");
            o.collected = s.getInt("collected");
            o.price = s.getDouble("price");
            o.escrow = s.getDouble("escrow");
            o.data = s.getString("data");
            o.refillMs = s.getLong("refill-ms");
            o.lastRefill = s.getLong("last-refill");
            orders.add(o);
        }
    }

    public void save() {
        store.get().set("orders", null);
        store.get().set("next-id", nextId);
        for (Order o : orders) {
            String p = "orders." + o.id;
            store.get().set(p + ".item", o.mat.name());
            store.get().set(p + ".server", o.server);
            if (o.owner != null) store.get().set(p + ".owner", o.owner.toString());
            store.get().set(p + ".owner-name", o.ownerName);
            store.get().set(p + ".total", o.total);
            store.get().set(p + ".remaining", o.remaining);
            store.get().set(p + ".collected", o.collected);
            store.get().set(p + ".price", o.price);
            store.get().set(p + ".escrow", o.escrow);
            if (o.data != null) store.get().set(p + ".data", o.data);
            store.get().set(p + ".refill-ms", o.refillMs);
            store.get().set(p + ".last-refill", o.lastRefill);
        }
        store.save();
    }

    public List<Order> open() {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.remaining > 0) out.add(o);
        out.sort(Comparator.comparingDouble((Order o) -> o.price * o.remaining).reversed());
        return out;
    }

    public List<Order> mine(UUID id) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (!o.server && id.equals(o.owner)) out.add(o);
        return out;
    }

    public List<Order> servers() {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.server) out.add(o);
        return out;
    }

    public Order get(int id) {
        for (Order o : orders) if (o.id == id) return o;
        return null;
    }

    public int pendingFor(UUID id) {
        int n = 0;
        for (Order o : orders) if (!o.server && id.equals(o.owner)) n += o.collected;
        return n;
    }

    private static boolean plain(ItemStack s) { return s != null && !s.getType().isAir(); }

    public String create(Player p, ItemStack hand, int qty, double price) {
        var c = plugin.getConfig();
        if (!plugin.money().available()) return "Aucune économie détectée.";
        if (!plain(hand)) return "Tenez en main l'item demandé.";
        if (qty < 1 || qty > c.getInt("orders.max-quantity", 2304)) return "Quantité invalide (1 à " + c.getInt("orders.max-quantity", 2304) + ").";
        if (price < c.getDouble("orders.min-price", 0.01) || price > c.getDouble("orders.max-price", 10_000_000)) return "Prix unitaire invalide.";
        if (mine(p.getUniqueId()).size() >= c.getInt("orders.max-per-player", 10)) return "Vous avez déjà trop de commandes ouvertes.";
        double escrow = Math.round(qty * price * 100.0) / 100.0;
        if (!plugin.money().withdraw(p, escrow)) return "Fonds insuffisants : il vous faut " + plugin.money().format(escrow) + ".";
        Order o = new Order();
        o.id = nextId++;
        o.owner = p.getUniqueId();
        o.ownerName = p.getName();
        o.mat = hand.getType();
        o.data = encode(hand);
        o.total = o.remaining = qty;
        o.price = price;
        o.escrow = escrow;
        orders.add(o);
        save();
        return null;
    }

    public String createServer(ItemStack hand, int qty, double price, long refillMinutes) {
        if (!plain(hand)) return "Tenez en main l'item demandé.";
        Order o = new Order();
        o.id = nextId++;
        o.server = true;
        o.mat = hand.getType();
        o.data = encode(hand);
        o.total = o.remaining = Math.max(1, qty);
        o.price = price;
        o.refillMs = Math.max(0, refillMinutes) * 60_000L;
        o.lastRefill = System.currentTimeMillis();
        orders.add(o);
        save();
        return null;
    }

    public int deliver(Player p, Order o, int max) {
        int have = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (o.matches(s)) have += s.getAmount();
        int n = Math.min(Math.min(have, o.remaining), max);
        if (n <= 0) return 0;
        double pay = Math.round(n * o.price * 100.0) / 100.0;
        if (!o.server) {
            if (o.escrow < pay) {
                pay = o.escrow;
                n = (int) Math.floor(pay / o.price + 1e-9);
                if (n <= 0) return 0;
                pay = Math.round(n * o.price * 100.0) / 100.0;
            }
        }
        if (!plugin.money().deposit(p, pay)) return 0;
        int left = n;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack s = inv[i];
            if (!o.matches(s)) continue;
            int take = Math.min(left, s.getAmount());
            if (take >= s.getAmount()) inv[i] = null; else s.setAmount(s.getAmount() - take);
            left -= take;
        }
        p.getInventory().setStorageContents(inv);
        o.remaining -= n;
        if (o.server) {
            if (o.lastRefill == 0) o.lastRefill = System.currentTimeMillis();
        } else {
            o.escrow = Math.max(0, o.escrow - pay);
            o.collected += n;
        }
        save();
        return n;
    }

    public int collect(Player p, Order o) {
        if (o.collected <= 0) return 0;
        int given = 0;
        while (o.collected > 0) {
            ItemStack give = o.template();
            int amt = Math.min(o.collected, Math.max(1, give.getMaxStackSize()));
            give.setAmount(amt);
            Map<Integer, ItemStack> left = p.getInventory().addItem(give);
            int notGiven = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            o.collected -= amt - notGiven;
            given += amt - notGiven;
            if (notGiven > 0) break;
        }
        if (o.remaining <= 0 && o.collected <= 0) orders.remove(o);
        save();
        return given;
    }

    public String cancel(Player p, Order o, boolean admin) {
        if (!admin && (p == null || !p.getUniqueId().equals(o.owner))) return "Cette commande n'est pas à vous.";
        if (!o.server) {
            if (o.escrow > 0) plugin.money().deposit(Bukkit.getOfflinePlayer(o.owner), o.escrow);
            Player owner = Bukkit.getPlayer(o.owner);
            if (owner != null) {
                o.escrow = 0;
                collect(owner, o);
                if (o.collected > 0) {
                    o.remaining = 0;
                    save();
                    return null;
                }
            }
        }
        orders.remove(o);
        save();
        return null;
    }

    private void refill() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Order o : orders) {
            if (o.server && o.refillMs > 0 && o.remaining < o.total && now - o.lastRefill >= o.refillMs) {
                o.remaining = o.total;
                o.lastRefill = now;
                changed = true;
            }
        }
        if (changed) save();
    }
}
