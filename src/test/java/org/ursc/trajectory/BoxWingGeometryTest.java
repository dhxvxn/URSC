package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.forces.BoxWingGeometry;
import org.ursc.trajectory.math.Vector3D;

/**
 * Validates the box-wing projected-area summation against the Sentinel-3A CPOD macro-model
 * deployed projected-area table (GMV-CPOD-TN-0027): bus-only X/Y/Z = 2.84/5.41/6.10 m², and
 * deployed X/Y/Z = 3.244/6.058/16.576 m² (bus + 10.50 m² solar array).
 */
class BoxWingGeometryTest {

    private static final double AX = 2.84;
    private static final double AY = 5.41;
    private static final double AZ = 6.10;
    private static final double WING = 10.50;

    // identity body frame: Xsat/Ysat/Zsat aligned with the inertial axes
    private static final BodyFrame BODY = new BodyFrame(
            Vector3D.PLUS_I, Vector3D.PLUS_J, Vector3D.PLUS_K);

    @Test
    void busOnlyProjectedAreasMatchCpodBusColumn() {
        // no array (arrayNormal = null): the projected area along each body axis is that face area
        assertEquals(AX, BoxWingGeometry.projectedArea(Vector3D.PLUS_I, BODY, null, AX, AY, AZ, WING), 1e-12);
        assertEquals(AY, BoxWingGeometry.projectedArea(Vector3D.PLUS_J, BODY, null, AX, AY, AZ, WING), 1e-12);
        assertEquals(AZ, BoxWingGeometry.projectedArea(Vector3D.PLUS_K, BODY, null, AX, AY, AZ, WING), 1e-12);
    }

    @Test
    void deployedNadirViewMatchesCpodTable() {
        // The array lies ~flat (normal ≈ +Z) in the deployed reference; viewed along nadir (Z) it
        // presents its full planform on top of the bus Z face: 6.10 + 10.50 = 16.60 ≈ 16.576.
        final double deployedZ = BoxWingGeometry.projectedArea(
                Vector3D.PLUS_K, BODY, Vector3D.PLUS_K, AX, AY, AZ, WING);
        assertEquals(16.576, deployedZ, 0.05, "deployed Z view: " + deployedZ);
    }

    @Test
    void deployedTableReproducedByReferenceArrayNormal() {
        // The CPOD deployed-minus-bus differences (0.404, 0.648, 10.476 m²) are the projection of a
        // single flat 10.50 m² array plate; dividing by the area gives the plate's direction cosines.
        // (a) those cosines form a unit vector to within the table's 3-decimal rounding (the array is
        // one flat plate — table self-consistency); (b) fed back as the array normal they reproduce
        // the full deployed X/Y/Z table exactly.
        final Vector3D n = new Vector3D((3.244 - AX) / WING, (6.058 - AY) / WING, (16.576 - AZ) / WING);
        assertEquals(1.0, n.getNorm(), 1e-3, "array direction cosines not unit: " + n.getNorm());
        assertEquals(3.244, BoxWingGeometry.projectedArea(Vector3D.PLUS_I, BODY, n, AX, AY, AZ, WING), 1e-9);
        assertEquals(6.058, BoxWingGeometry.projectedArea(Vector3D.PLUS_J, BODY, n, AX, AY, AZ, WING), 1e-9);
        assertEquals(16.576, BoxWingGeometry.projectedArea(Vector3D.PLUS_K, BODY, n, AX, AY, AZ, WING), 1e-9);
    }

    @Test
    void sunTrackingArrayPresentsCantReducedAreaToSun() {
        // Sun in the array rotation plane (perpendicular to Ysat): the canted array presents
        // cos(cant)·wing to the Sun, and never more than its full area.
        final double cant = Math.toRadians(24.0);
        final Vector3D sun = Vector3D.PLUS_K;                 // perpendicular to body Y
        final Vector3D n = BoxWingGeometry.arrayNormal(BODY, sun, cant);
        final double arrayToSun = WING * Math.abs(sun.dotProduct(n));
        assertEquals(WING * Math.cos(cant), arrayToSun, 1e-9, "array-to-Sun area: " + arrayToSun);
        assertTrue(arrayToSun <= WING + 1e-9);
    }
}
