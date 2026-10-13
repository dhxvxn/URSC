import java.util.Locale;

import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.atmosphere.JB2008;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

/**
 * Emits Orekit JB2008 reference densities for a set of fixed input tuples, as Java-ready rows to
 * paste into the URSC Jb2008Test cross-validation. Uses the OREKIT-independent 15-argument
 * getDensity entry (angles passed through, indices via an internal local provider).
 *
 * Usage: OrekitJb2008Ref <orekit-data-dir>
 */
public final class OrekitJb2008Ref {

    // tuples: dateMJD, sunRA, sunDecli, satLon, satLat, satAltM, f10, f10b, s10, s10b, xm10, xm10b, y10, y10b, dstdtc
    static final double[][] CASES = {
        {54000.5, 0.0, 0.0, 0.0, 0.0, 400000.0, 150, 150, 150, 150, 150, 150, 150, 150, 0.0},
        {54000.3, 1.2, -0.35, 2.0, 0.6, 400000.0, 120, 115, 110, 112, 118, 116, 100, 105, 0.0},
        {60590.5, 3.0, -0.1, -1.5, -0.7, 800000.0, 276.5, 209.0, 187.2, 180.9, 216.6, 210.8, 197.4, 191.8, 85.0},
        {60590.9, 0.5, 0.2, 0.3, 0.9, 800000.0, 170.9, 203.2, 162.9, 178.2, 183.0, 206.7, 180.0, 190.7, 200.0},
        {55200.25, -2.0, 0.4, 1.0, -0.3, 550000.0, 90, 92, 88, 90, 95, 93, 80, 82, 30.0},
        {58849.75, 2.5, -0.2, -2.8, 0.2, 1000000.0, 70, 72, 68, 70, 72, 71, 60, 62, 10.0},
    };

    public static void main(String[] args) {
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(new java.io.File(args[0])));
        final TimeScale utc = TimeScalesFactory.getUTC();
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING, FramesFactory.getITRF(IERSConventions.IERS_2010, false));
        final CelestialBody sun = CelestialBodyFactory.getSun();
        final JB2008 jb = new JB2008(null, sun, earth, utc);

        for (double[] c : CASES) {
            final double rho = jb.getDensity(c[0], c[1], c[2], c[3], c[4], c[5],
                    c[6], c[7], c[8], c[9], c[10], c[11], c[12], c[13], c[14]);
            System.out.printf(Locale.ROOT,
                    "{%.4f, %.4f, %.4f, %.4f, %.4f, %.1f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.6e},%n",
                    c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], c[12], c[13], c[14], rho);
        }
    }

    private OrekitJb2008Ref() { }
}
