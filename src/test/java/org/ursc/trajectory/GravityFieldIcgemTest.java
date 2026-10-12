package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;

/** Loading an ICGEM .gfc gravity model (orekit-data format) via the header-aware loader. */
class GravityFieldIcgemTest {

    private static final String GFC =
            "product_type                gravity_field\n"
            + "modelname                   TESTMODEL\n"
            + "earth_gravity_constant      3.9860044150E+14\n"
            + "radius                      6.3781363000E+06\n"
            + "max_degree                  3\n"
            + "errors                      no\n"
            + "norm                        fully_normalized\n"
            + "key     L    M         C                   S\n"
            + "end_of_head\n"
            + "gfc    0    0  1.00000000000E+00  0.00000000000E+00\n"
            + "gfc    2    0 -4.84165143790E-04  0.00000000000E+00\n"
            + "gfc    2    1 -2.06615509074E-10  1.38441389138E-09\n"
            + "gfc    2    2  2.43938357328E-06 -1.40027370399E-06\n"
            + "gfc    3    0  9.57161207093E-07  0.00000000000E+00\n";

    @Test
    void readsHeaderAndDenormalizesCoefficients() throws Exception {
        final Path f = Files.createTempFile("testmodel", ".gfc");
        Files.write(f, GFC.getBytes(StandardCharsets.UTF_8));
        try {
            final GravityField field = GravityFieldFactory.loadIcgem(f, 2, 2);
            // header GM and radius
            assertEquals(3.986004415e14, field.getMu(), 1.0);
            assertEquals(6378136.3, field.getReferenceRadius(), 1e-3);
            // truncated to the requested degree/order
            assertEquals(2, field.getMaxDegree());
            assertEquals(2, field.getMaxOrder());
            // C(0,0) is the point-mass term
            assertEquals(1.0, field.getC(0, 0), 1e-15);
            // C(2,0) de-normalised with the factory's own factor
            final double expectedC20 = -4.84165143790E-04 * GravityFieldFactory.denormalizationFactor(2, 0);
            assertEquals(expectedC20, field.getC(2, 0), 1e-15);
            // degree-3 coefficient dropped by the degree-2 truncation (array only sized to 2)
            assertEquals(2, field.getMaxDegree());
        } finally {
            Files.deleteIfExists(f);
        }
    }
}
