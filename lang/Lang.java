package fr.vortexsmp.vortexessentials.lang;

import fr.vortexsmp.vortexessentials.VortexEssentials;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduction des textes. Les textes du plugin sont écrits en français ; une langue est un fichier
 * plugins/VortexEssentials/lang/<code>.txt dont chaque ligne est « texte français => traduction ».
 * Les textes sont traduits morceau par morceau (entre deux codes couleur §), les termes les plus longs d'abord.
 * Un morceau sans traduction reste en français. Ajouter une langue = copier en.txt et le traduire.
 */
public final class Lang {
    private static final Pattern CODES = Pattern.compile("(§[0-9a-fk-orx])+", Pattern.CASE_INSENSITIVE);
    private static final List<String> BUNDLED = List.of("en", "es");

    private static volatile List<Pattern> patterns = List.of();
    private static volatile List<String> replacements = List.of();
    private static volatile String current = "fr";

    private Lang() {}

    public static String current() { return current; }

    public static void load(VortexEssentials plugin) {
        String code = plugin.getConfig().getString("language", "fr").toLowerCase(Locale.ROOT).trim();
        File dir = new File(plugin.getDataFolder(), "lang");
        dir.mkdirs();
        for (String b : BUNDLED) {
            if (!new File(dir, b + ".txt").exists()) plugin.saveResource("lang/" + b + ".txt", false);
        }
        if (code.isEmpty() || code.equals("fr")) {
            patterns = List.of();
            replacements = List.of();
            current = "fr";
            return;
        }
        File f = new File(dir, code + ".txt");
        if (!f.exists()) {
            plugin.getLogger().warning("Langue '" + code + "' introuvable (lang/" + code + ".txt) : français utilisé.");
            patterns = List.of();
            replacements = List.of();
            current = "fr";
            return;
        }
        Map<String, String> map = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int i = line.indexOf(" => ");
                if (i <= 0) continue;
                String k = line.substring(0, i).trim();
                String v = line.substring(i + 4).trim();
                if (!k.isEmpty()) map.put(k, v);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Lecture de " + f.getName() + " impossible : " + e.getMessage());
            return;
        }
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        List<Pattern> ps = new ArrayList<>();
        List<String> rs = new ArrayList<>();
        for (String k : keys) {
            // limites de mot : évite de modifier l'intérieur d'un autre mot
            ps.add(Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}])"));
            rs.add(Matcher.quoteReplacement(map.get(k)));
        }
        patterns = ps;
        replacements = rs;
        current = code;
        plugin.getLogger().info("Langue : " + code + " (" + map.size() + " termes).");
    }

    /** Langues disponibles (fichiers présents dans lang/ + fr). */
    public static List<String> available(VortexEssentials plugin) {
        TreeSet<String> out = new TreeSet<>(BUNDLED);
        File[] files = new File(plugin.getDataFolder(), "lang").listFiles((d, n) -> n.endsWith(".txt"));
        if (files != null) for (File f : files) out.add(f.getName().replace(".txt", ""));
        out.add("fr");
        return new ArrayList<>(out);
    }

    /** Traduit un texte (codes couleur conservés). */
    public static String tr(String s) {
        List<Pattern> ps = patterns;
        if (s == null || ps.isEmpty() || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        Matcher m = CODES.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(translate(s.substring(last, m.start()), ps));
            out.append(m.group());
            last = m.end();
        }
        out.append(translate(s.substring(last), ps));
        return out.toString();
    }

    private static String translate(String text, List<Pattern> ps) {
        if (text.isBlank()) return text;
        String out = text;
        List<String> rs = replacements;
        for (int i = 0; i < ps.size(); i++) out = ps.get(i).matcher(out).replaceAll(rs.get(i));
        return out;
    }
}
