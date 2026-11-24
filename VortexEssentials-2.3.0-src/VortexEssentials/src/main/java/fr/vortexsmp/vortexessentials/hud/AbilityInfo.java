package fr.vortexsmp.vortexessentials.hud;

import fr.vortexsmp.vortexessentials.item.VortexItem;

import java.util.List;

/** Capacités (clé de cooldown + libellé) de chaque arme, pour l'affichage HUD. */
public final class AbilityInfo {
    public record Ability(String key, String label) {}

    private AbilityInfo() {}

    public static List<Ability> of(VortexItem v) {
        if (v == null) return List.of();
        return switch (v) {
            case SEALORD -> List.of(new Ability("sealord_slam", "Tidal Slam"), new Ability("sealord_maelstrom", "Maelstrom"));
            case LIGHTSPEED -> List.of(new Ability("lightspeed_dash", "Dash"), new Ability("lightspeed_slash", "Sonic Slash"));
            case MINTUS -> List.of(new Ability("mintus_cheer", "Rallying Cheer"), new Ability("mintus_wrath", "Village's Wrath"));
            case REAPER_SCYTHE -> List.of(new Ability("reaper", "Drain"), new Ability("reaper_harvest", "Moisson d'âmes"));
            case EXCALIBUR -> List.of(new Ability("excalibur", "Bouclier"));
            case EVOKER_WAND -> List.of(new Ability("evoker_fangs", "Crocs"), new Ability("evoker_reflect", "Barrière"));
            case CHRONO_SWORD -> List.of(new Ability("chrono", "Chrono"));
            case SCEPTRE_OF_ARACHNE -> List.of(new Ability("arachne_empower", "Empower"), new Ability("arachne_shot", "Tir"));
            case MAGMA_CLUB -> List.of(new Ability("magma_eruption", "Éruption"), new Ability("magma_wave", "Vague de magma"));
            case BLAZING_CROSSBOW -> List.of(new Ability("crossbow_barrage", "Barrage"));
            case SKYFALL -> List.of(new Ability("skyfall_rain", "Arrow Rain"));
            case VOIDRENDER -> List.of(new Ability("voidrender_rail", "Rail Shot"), new Ability("voidrender_blink", "Blink Bolt"));
            case STORMPIERCER -> List.of(new Ability("storm_javelin", "Thunder Javelin"), new Ability("storm_dash", "Skewer Dash"));
            case UMBRAL_SCYTHE -> List.of(new Ability("umbral_leap", "Eclipse Leap"), new Ability("umbral_chains", "Reaper Chains"), new Ability("umbral_shatter", "Shatterer"));
            default -> List.of();
        };
    }
}
