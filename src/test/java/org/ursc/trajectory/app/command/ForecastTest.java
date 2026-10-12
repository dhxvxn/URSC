package org.ursc.trajectory.app.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Smoke + physics check for the forecast command on a synthetic single-state seed. */
class ForecastTest {

    @Test
    void forecastFromSeedWritesEphemerisAndDecays() throws Exception {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(0.0, 0.0, 0.0));

        final AbsoluteDate t0 = new AbsoluteDate(2024, 10, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // low orbit so drag decay is clear over the short forecast
        final KeplerianOrbit k0 = new KeplerianOrbit(Constants.EARTH_EQUATORIAL_RADIUS + 300e3,
                0.001, Math.toRadians(51.6), Math.toRadians(30), Math.toRadians(40), 0.0,
                PositionAngle.TRUE, FramesFactory.getGCRF(), t0, Constants.EARTH_MU);
        final PVCoordinates pv = k0.getPVCoordinates();
        final Transform toItrf =
                FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), t0);
        final Vector3D rI = toItrf.transformPosition(pv.getPosition());
        final Vector3D vI = toItrf.transformVelocity(pv.getVelocity(), rI);

        final Path seed = Files.createTempFile("seed", ".EOF");
        final Path csv = Files.createTempFile("forecast", ".csv");
        seed.toFile().deleteOnExit();
        csv.toFile().deleteOnExit();
        Files.write(seed, oneOsvEof(rI, vI).getBytes(StandardCharsets.UTF_8));

        final int rc = new ForecastCommand().run(new Args(new String[] {
                seed.toString(), "--days", "0.5", "--step", "120", "--csv", csv.toString(),
                "--plot", "none"}));
        assertEquals(0, rc, "forecast should succeed");

        final List<String> lines = Files.readAllLines(csv);
        assertTrue(lines.size() > 10, "ephemeris CSV should have many rows, got " + lines.size());
        // altitude is the last column; a 300 km orbit must lose altitude over half a day
        final double firstAlt = lastField(lines.get(1));
        final double lastAlt = lastField(lines.get(lines.size() - 1));
        assertTrue(lastAlt < firstAlt,
                "altitude should decay under drag: first=" + firstAlt + " last=" + lastAlt);
    }

    private static double lastField(final String csvRow) {
        final String[] parts = csvRow.split(",");
        return Double.parseDouble(parts[parts.length - 1].trim());
    }

    private static String oneOsvEof(final Vector3D r, final Vector3D v) {
        return "<?xml version=\"1.0\"?>\n<Data_Block>\n<List_of_OSVs>\n<OSV>\n"
                + "<UTC>UTC=2024-10-01T00:00:00.000000</UTC>\n"
                + String.format(Locale.US, "<X unit=\"m\">%.6f</X>\n<Y unit=\"m\">%.6f</Y>\n<Z unit=\"m\">%.6f</Z>\n",
                        r.getX(), r.getY(), r.getZ())
                + String.format(Locale.US, "<VX unit=\"m/s\">%.6f</VX>\n<VY unit=\"m/s\">%.6f</VY>\n<VZ unit=\"m/s\">%.6f</VZ>\n",
                        v.getX(), v.getY(), v.getZ())
                + "</OSV>\n</List_of_OSVs>\n</Data_Block>\n";
    }
}
