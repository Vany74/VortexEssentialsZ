package fr.vortexsmp.vortexessentials.tools;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Zone de minage carrée n×n dans le plan de la face minée (3×3 = exactement 9 blocs, comme un marteau). */
public final class AreaPlane {
    private AreaPlane() {}

    /** Axe de la face minée : 0 = x, 1 = y, 2 = z. */
    public static int axis(Player p, Block mined) {
        RayTraceResult rt = p.rayTraceBlocks(7.0);
        if (rt != null && rt.getHitBlock() != null && rt.getHitBlock().equals(mined) && rt.getHitBlockFace() != null) {
            BlockFace f = rt.getHitBlockFace();
            if (f.getModX() != 0) return 0;
            if (f.getModY() != 0) return 1;
            return 2;
        }
        Vector d = p.getLocation().getDirection();
        double ax = Math.abs(d.getX()), ay = Math.abs(d.getY()), az = Math.abs(d.getZ());
        return ax >= ay && ax >= az ? 0 : (ay >= az ? 1 : 2);
    }

    /** {minX, maxX, minY, maxY, minZ, maxZ} : l'axe de la face reste à 0, les deux autres couvrent n blocs. */
    public static int[] offsets(int axis, int n, Vector dir) {
        int[] rx = {0, 0}, ry = {0, 0}, rz = {0, 0};
        if (axis != 0) rx = range(n, dir.getX());
        if (axis != 1) ry = range(n, dir.getY());
        if (axis != 2) rz = range(n, dir.getZ());
        return new int[]{rx[0], rx[1], ry[0], ry[1], rz[0], rz[1]};
    }

    /** Impair : centré. Pair : étendu dans le sens du regard. */
    private static int[] range(int n, double dir) {
        if (n % 2 == 1) return new int[]{-(n - 1) / 2, (n - 1) / 2};
        return dir >= 0 ? new int[]{0, n - 1} : new int[]{-(n - 1), 0};
    }
}
