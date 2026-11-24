package fr.vortexsmp.vortexessentials.discord;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import fr.vortexsmp.vortexessentials.YamlStore;
import fr.vortexsmp.vortexessentials.lang.Lang;
import fr.vortexsmp.vortexessentials.mythic.MythicRegistry;
import fr.vortexsmp.vortexessentials.item.Category;
import fr.vortexsmp.vortexessentials.item.VortexItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notifications Discord par webhook (arrivées, départs, morts, succès, chat, démarrage/arrêt, armes mythiques,
 * événements de boost) et message de statut mis à jour périodiquement.
 * Les envois passent par une file à un seul fil (respecte les limites de Discord) ; rien ne bloque le serveur.
 * L'URL du webhook est un secret : elle n'est jamais écrite dans les logs ni envoyée ailleurs que chez Discord.
 */
public final class DiscordWebhook {
    private static final Pattern URL_OK = Pattern.compile("^https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern RETRY = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private final VortexEssentials plugin;
    private final YamlStore state;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VortexEssentials-Discord");
        t.setDaemon(true);
        return t;
    });
    private final long startedAt = System.currentTimeMillis();

    private volatile String url = "";
    private volatile boolean enabled;
    private BukkitTask statusTask;

    public DiscordWebhook(VortexEssentials plugin) {
        this.plugin = plugin;
        this.state = new YamlStore(plugin, "discord-state.yml");
        reload();
    }

    // ═════════════ Configuration ═════════════

    public void reload() {
        String u = plugin.getConfig().getString("discord.webhook-url", "").trim();
        boolean want = plugin.getConfig().getBoolean("discord.enabled", false);
        if (want && !URL_OK.matcher(u).matches()) {
            plugin.getLogger().warning("discord.webhook-url invalide (format https://discord.com/api/webhooks/<id>/<token>) : Discord désactivé.");
            want = false;
        }
        url = u;
        enabled = want;
        if (statusTask != null) statusTask.cancel();
        statusTask = null;
        if (enabled && plugin.getConfig().getBoolean("discord.status.enabled", false)) {
            long period = Math.max(30, plugin.getConfig().getInt("discord.status.interval-seconds", 60)) * 20L;
            statusTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendStatus, 100L, period);
        }
    }

    public boolean active() { return enabled; }

    public boolean event(String key) { return enabled && plugin.getConfig().getBoolean("discord.events." + key, false); }

    public void shutdown() {
        if (statusTask != null) statusTask.cancel();
        worker.shutdown();
    }

    // ═════════════ JSON ═════════════

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private String payload(String content, String embed) {
        StringBuilder b = new StringBuilder("{\"username\":\"")
                .append(esc(plugin.getConfig().getString("discord.username", "VortexEssentials")))
                .append("\",\"allowed_mentions\":{\"parse\":[]}");
        if (content != null) b.append(",\"content\":\"").append(esc(content)).append('"');
        if (embed != null) b.append(",\"embeds\":[").append(embed).append(']');
        return b.append('}').toString();
    }

    private String embed(String title, String description, int color, String thumb, String fields) {
        StringBuilder b = new StringBuilder("{\"title\":\"").append(esc(Lang.tr(title))).append('"');
        if (description != null && !description.isBlank()) b.append(",\"description\":\"").append(esc(Lang.tr(description))).append('"');
        b.append(",\"color\":").append(color)
                .append(",\"timestamp\":\"").append(Instant.now()).append('"');
        if (thumb != null) b.append(",\"thumbnail\":{\"url\":\"").append(esc(thumb)).append("\"}");
        if (fields != null) b.append(",\"fields\":[").append(fields).append(']');
        return b.append('}').toString();
    }

    private String avatar(Player p) {
        String tpl = plugin.getConfig().getString("discord.avatar-url-template", "");
        if (tpl == null || tpl.isBlank()) return null;
        return tpl.replace("{uuid}", p.getUniqueId().toString()).replace("{name}", p.getName());
    }

    // ═════════════ API publique (appelable depuis le fil principal) ═════════════

    public void announce(String title, String description, int color) {
        if (!enabled) return;
        enqueue(payload(null, embed(title, description, color, null, null)));
    }

    public void player(String title, String description, int color, Player p) {
        if (!enabled) return;
        enqueue(payload(null, embed(title, description, color, avatar(p), null)));
    }

    public void chat(String name, String message) {
        if (!enabled) return;
        String clean = message.replace("@", "@\u200b");
        if (clean.length() > 1800) clean = clean.substring(0, 1800) + "…";
        enqueue(payload("**" + name.replace("_", "\\_") + "** : " + clean, null));
    }

    /** Envoi bloquant (arrêt du serveur) : ne dépasse pas ~4 s. */
    public void announceBlocking(String title, String description, int color) {
        if (!enabled) return;
        try {
            post(payload(null, embed(title, description, color, null, null)), 4000);
        } catch (Exception ignored) { }
    }

    public String test() {
        if (!enabled) return "Discord est désactivé ou le webhook est invalide (voir la console).";
        announce("Test VortexEssentials", "Le webhook fonctionne.", 0x5865F2);
        return null;
    }

    // ═════════════ File d'envoi ═════════════

    private void enqueue(String json) {
        worker.submit(() -> {
            try {
                post(json, 8000);
                Thread.sleep(600); // marge sous la limite de débit de Discord
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                plugin.getLogger().warning("Discord : envoi impossible (" + e.getClass().getSimpleName() + ").");
            }
        });
    }

    private HttpResponse<String> send(HttpRequest.Builder rb, String method, String json, int timeoutMs) throws Exception {
        HttpRequest rq = rb.timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=utf-8")
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return http.send(rq, HttpResponse.BodyHandlers.ofString());
    }

    private void post(String json, int timeoutMs) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(url)), "POST", json, timeoutMs);
            int c = r.statusCode();
            if (c >= 200 && c < 300) return;
            if (c == 429) {
                Matcher m = RETRY.matcher(r.body());
                long wait = m.find() ? (long) (Double.parseDouble(m.group(1)) * 1000) : 2000;
                Thread.sleep(Math.min(wait, 10_000));
                continue;
            }
            plugin.getLogger().warning("Discord : réponse HTTP " + c + " (vérifiez le webhook).");
            return;
        }
    }

    // ═════════════ Message de statut (mis à jour) ═════════════

    private static String md(String s) { return s.replace("_", "\\_").replace("*", "\\*"); }

    private void sendStatus() {
        if (!enabled) return;
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(md(p.getName()));
        int online = names.size();
        String list = names.isEmpty() ? "—" : String.join(", ", names.subList(0, Math.min(25, names.size())))
                + (names.size() > 25 ? " … (+" + (names.size() - 25) + ")" : "");

        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long up = (System.currentTimeMillis() - startedAt) / 60_000L;

        StringBuilder mythic = new StringBuilder();
        MythicRegistry reg = plugin.mythic();
        for (VortexItem v : VortexItem.mythics()) {
            String by = reg.forgedBy(v);
            mythic.append(v.displayName()).append(" : ").append(reg.isForged(v) ? "forgée" + (by != null ? " par " + md(by) : "") : "disponible").append('\n');
        }
        StringBuilder ev = new StringBuilder();
        for (var e : plugin.boosts().events()) {
            ev.append(e.stat()).append(" x").append(e.multiplier()).append(" (")
                    .append(Math.max(0, (e.until() - System.currentTimeMillis()) / 60_000L)).append(" min)\n");
        }

        String fields = field("Joueurs", online + "/" + Bukkit.getMaxPlayers(), true)
                + "," + field("TPS", String.format("%.1f", tps), true)
                + "," + field("Mémoire", usedMb + " Mo", true)
                + "," + field("En ligne", list, false)
                + "," + field("Armes mythiques", mythic.toString().trim(), false)
                + (ev.length() > 0 ? "," + field("Événements de boost", ev.toString().trim(), false) : "")
                + "," + field("Uptime", up / 60 + " h " + up % 60 + " min", true);
        String json = payload(null, embed("Statut du serveur", online > 0 ? "Le serveur est en ligne." : "Le serveur est en ligne (personne pour le moment).",
                tps >= 18 ? 0x57F287 : tps >= 12 ? 0xFEE75C : 0xED4245, null, fields));

        worker.submit(() -> {
            try {
                String id = state.get().getString("status-message-id", "");
                if (!id.isEmpty()) {
                    HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(url + "/messages/" + id)), "PATCH", json, 8000);
                    if (r.statusCode() == 404) id = "";
                    else return;
                }
                HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(url + "?wait=true")), "POST", json, 8000);
                Matcher m = ID.matcher(r.body());
                if (m.find()) {
                    state.get().set("status-message-id", m.group(1));
                    state.save();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Discord : mise à jour du statut impossible (" + e.getClass().getSimpleName() + ").");
            }
        });
    }

    private String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + esc(Lang.tr(name)) + "\",\"value\":\"" + esc(value.isBlank() ? "—" : Lang.tr(value))
                + "\",\"inline\":" + inline + "}";
    }
}
