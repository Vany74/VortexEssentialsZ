package fr.vortexsmp.vortexessentials.pack;

import fr.vortexsmp.vortexessentials.Msg;
import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Envoi du resource pack aux joueurs, suivi de leur statut (les échecs sont expliqués en console et aux admins)
 * et outils de diagnostic : /vortex pack status | send | hash | check.
 * Utilisez soit ce module (resource-pack.* dans config.yml), soit server.properties, pas les deux.
 */
public final class PackManager implements Listener {
    private final VortexEssentials plugin;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> statuses = new HashMap<>();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();

    public PackManager(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    private String url() { return plugin.getConfig().getString("resource-pack.url", "").trim(); }
    private String sha1() { return plugin.getConfig().getString("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT); }
    private boolean configured() { return plugin.getConfig().getBoolean("resource-pack.enabled", false) && !url().isEmpty(); }

    private byte[] hashBytes() {
        String h = sha1();
        if (h.length() != 40) return null;
        try { return HexFormat.of().parseHex(h); } catch (IllegalArgumentException e) { return null; }
    }

    // ═════════════ Envoi ═════════════

    @EventHandler
    public void join(PlayerJoinEvent e) {
        if (!configured()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) sendTo(e.getPlayer()); }, 20L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { statuses.remove(e.getPlayer().getUniqueId()); }

    public void sendTo(Player p) {
        try {
            String prompt = plugin.getConfig().getString("resource-pack.prompt", "&bVortexEssentials &7— textures des items");
            p.setResourcePack(url(), hashBytes(), Msg.c(prompt.replace('&', '§')), plugin.getConfig().getBoolean("resource-pack.force", false));
        } catch (Exception ex) {
            plugin.getLogger().warning("Resource pack : envoi impossible (" + ex.getMessage() + ").");
        }
    }

    // ═════════════ Suivi des statuts ═════════════

    @EventHandler
    public void status(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        statuses.put(p.getUniqueId(), e.getStatus());
        String why = switch (e.getStatus()) {
            case FAILED_DOWNLOAD -> "téléchargement impossible : le lien n'est pas direct, le SHA-1 ne correspond pas au fichier, "
                    + "le fichier est trop gros ou l'hébergeur bloque Minecraft. Utilisez /vortex pack hash.";
            case INVALID_URL -> "lien invalide (il doit commencer par http:// ou https://).";
            case FAILED_RELOAD -> "pack téléchargé mais invalide ou corrompu (pack.mcmeta, JSON ou structure du zip). Utilisez /vortex pack hash.";
            case DECLINED -> "le joueur a refusé le pack.";
            default -> null;
        };
        if (why == null) return;
        plugin.getLogger().warning("Resource pack — " + p.getName() + " : " + e.getStatus() + " — " + why);
        p.sendMessage(Msg.c("§cLe resource pack n'a pas pu être chargé §7(" + e.getStatus().name().toLowerCase(Locale.ROOT) + ")§c. Prévenez un admin."));
        for (Player a : Bukkit.getOnlinePlayers()) {
            if (!a.equals(p) && a.hasPermission("vortexessentials.admin")) {
                a.sendMessage(Msg.c(Msg.PREFIX + "§cPack refusé/échoué pour §f" + p.getName() + " §7: " + e.getStatus().name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    // ═════════════ /vortex pack ═════════════

    public void command(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> status(s);
            case "send" -> {
                if (!configured()) { Msg.send(s, "§cActivez resource-pack.enabled et renseignez resource-pack.url dans config.yml."); return; }
                if (a.length > 2) {
                    Player t = Bukkit.getPlayerExact(a[2]);
                    if (t == null) { Msg.send(s, "§cJoueur introuvable."); return; }
                    sendTo(t);
                    Msg.prefixed(s, "§7Pack envoyé à §f" + t.getName());
                } else {
                    Bukkit.getOnlinePlayers().forEach(this::sendTo);
                    Msg.prefixed(s, "§7Pack envoyé à tous les joueurs.");
                }
            }
            case "hash" -> hash(s);
            case "check" -> check(s);
            default -> Msg.send(s, "§e/vortex pack §7[status | send [joueur] | hash | check]");
        }
    }

    private void status(CommandSender s) {
        boolean models = plugin.getConfig().getBoolean("use-custom-models", false);
        Msg.send(s, "§b§lResource pack");
        Msg.send(s, "§7Modèles sur les items (use-custom-models) : " + (models ? "§aactivé" : "§cdésactivé — les items restent vanilla"));
        Msg.send(s, "§7Envoi par le plugin : " + (configured() ? "§aactivé" : "§cdésactivé") + " §8| §7lien : " + (url().isEmpty() ? "§cvide" : "§afourni")
                + " §8| §7SHA-1 : " + (hashBytes() != null ? "§avalide" : "§c" + (sha1().isEmpty() ? "vide" : "invalide (40 caractères hexa)")));
        if (statuses.isEmpty()) { Msg.send(s, "§7Aucun statut joueur reçu."); return; }
        for (Map.Entry<UUID, PlayerResourcePackStatusEvent.Status> en : statuses.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) Msg.send(s, "§8- §f" + p.getName() + " §7: " + en.getValue().name().toLowerCase(Locale.ROOT));
        }
    }

    /** Télécharge le pack, calcule son SHA-1 et vérifie la structure du zip. */
    private void hash(CommandSender s) {
        if (url().isEmpty()) { Msg.send(s, "§cresource-pack.url est vide dans config.yml."); return; }
        Msg.prefixed(s, "§7Téléchargement du pack pour analyse...");
        String link = url();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> out = new java.util.ArrayList<>();
            try {
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = r.body();
                out.add("§7HTTP : §f" + r.statusCode() + " §7| taille : §f" + body.length + " octets");
                if (r.statusCode() != 200) out.add("§cLe lien ne renvoie pas le fichier (HTTP " + r.statusCode() + ").");
                if (body.length > 100 * 1024 * 1024) out.add("§cPack très gros (> 100 Mo) : Minecraft peut refuser de le télécharger.");

                boolean zip = body.length > 4 && body[0] == 'P' && body[1] == 'K';
                if (!zip) {
                    out.add("§cCe n'est pas un fichier zip : c'est probablement une page web (lien non direct).");
                } else {
                    String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(body));
                    out.add("§7SHA-1 réel : §f" + h);
                    out.add(sha1().isEmpty() ? "§eresource-pack.sha1 est vide : copiez la valeur ci-dessus."
                            : sha1().equals(h) ? "§aLe SHA-1 de la config correspond." : "§cLE SHA-1 DE LA CONFIG NE CORRESPOND PAS : remplacez-le par la valeur ci-dessus.");
                    boolean mcmeta = false, items = false;
                    String first = null;
                    try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(body))) {
                        ZipEntry en;
                        while ((en = z.getNextEntry()) != null) {
                            String n = en.getName();
                            if (first == null) first = n;
                            if (n.equals("pack.mcmeta")) mcmeta = true;
                            if (n.startsWith("assets/vortex/items/")) items = true;
                        }
                    }
                    out.add(mcmeta ? "§apack.mcmeta à la racine : OK." : "§cpack.mcmeta absent de la racine du zip (premier fichier : " + first
                            + "). Zippez le CONTENU du dossier, pas le dossier lui-même.");
                    out.add(items ? "§aModèles vortex trouvés (assets/vortex/items)." : "§cAucun modèle assets/vortex/items : ce n'est pas le pack VortexEssentials.");
                }
            } catch (Exception e) {
                out.add("§cImpossible de télécharger le pack : " + e.getClass().getSimpleName() + " (" + e.getMessage() + ")");
            }
            Bukkit.getScheduler().runTask(plugin, () -> out.forEach(l -> Msg.send(s, l)));
        });
    }

    /** Montre le modèle inscrit dans l'item tenu. */
    private void check(CommandSender s) {
        if (!(s instanceof Player p)) { Msg.send(s, "§cCommande réservée aux joueurs."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !hand.hasItemMeta()) { Msg.send(s, "§cTenez un item Vortex en main."); return; }
        NamespacedKey model = hand.getItemMeta().hasItemModel() ? hand.getItemMeta().getItemModel() : null;
        Msg.send(s, "§7Modèle de l'item : " + (model == null ? "§cnon défini §7(activez use-custom-models puis redonnez l'item)" : "§a" + model));
        if (model != null && !plugin.getConfig().getBoolean("use-custom-models", false)) {
            Msg.send(s, "§7use-custom-models est désactivé : les nouveaux items n'auront plus de modèle.");
        }
    }

    public List<String> complete(String[] a) {
        if (a.length == 2) return List.of("status", "send", "hash", "check").stream().filter(x -> x.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 3 && a[1].equalsIgnoreCase("send")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
