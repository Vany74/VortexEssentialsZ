package fr.vortexsmp.vortexessentials;

import fr.vortexsmp.vortexessentials.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Petits utilitaires de messages (codes § legacy -> Adventure). */
public final class Msg {
    public static final String PREFIX = "§8[§bVortex§8] ";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Msg() {}

    /** Texte traduit (langue du serveur). */
    public static Component c(String legacy) {
        return LEGACY.deserialize(Lang.tr(legacy));
    }

    /** Texte non traduit (contenu écrit par les admins). */
    public static Component raw(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /** Composant sans italique (pour noms et lore d'items). */
    public static Component plain(String legacy) {
        return LEGACY.deserialize(Lang.tr(legacy)).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String legacy) {
        to.sendMessage(c(legacy));
    }

    public static void prefixed(CommandSender to, String legacy) {
        to.sendMessage(c(PREFIX + legacy));
    }
}
