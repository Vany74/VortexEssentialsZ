package fr.vortexsmp.vortexessentials;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/** Fichier YAML de données dans le dossier du plugin (limits.yml, mythic-data.yml). */
public final class YamlStore {
    private final Plugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public YamlStore(Plugin plugin, String name) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), name);
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration get() { return cfg; }

    public void reload() { cfg = YamlConfiguration.loadConfiguration(file); }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'écrire " + file.getName() + " : " + e.getMessage());
        }
    }
}
