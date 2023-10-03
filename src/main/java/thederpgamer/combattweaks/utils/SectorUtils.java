package thederpgamer.combattweaks.utils;

import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.server.data.ServerConfig;

/**
 * <Description>
 *
 * @author TheDerpGamer
 * @since 08/28/2021
 */
public class SectorUtils {

    public static void transformToSector(Transform transform, Vector3i currentSector, Vector3i targetSector) {
        int sectorSize = (int) ServerConfig.SECTOR_SIZE.getCurrentState();
        Vector3i diff = new Vector3i(currentSector);
        diff.sub(targetSector);
        transform.origin.x -= sectorSize * diff.x;
        transform.origin.y -= sectorSize * diff.y;
        transform.origin.z -= sectorSize * diff.z;
    }
}
