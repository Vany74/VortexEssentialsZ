package fr.vortexsmp.vortexessentials.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Joueurs dont le plugin casse déjà une zone : évite de retraiter nos propres BlockBreakEvent. */
public final class BreakGuard {
    public static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    private BreakGuard() {}
}
