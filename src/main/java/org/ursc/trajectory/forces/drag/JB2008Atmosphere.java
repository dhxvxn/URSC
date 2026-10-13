package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.GeodeticPoint;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.forces.drag.jb2008.Jb2008;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Jacchia-Bowman 2008 atmosphere adapter plugging the {@link Jb2008} engine into the
 * {@link Atmosphere} interface, exactly like {@link NRLMSISE00Atmosphere}. The solar/geomagnetic
 * drivers (F10.7, S10, M10, Y10, DTC) come from a {@link Jb2008Inputs} provider.
 *
 * <p>JB2008 is markedly better than NRLMSISE-00 during geomagnetic storms, because the DTC
 * temperature correction and the EUV/FUV/X-ray solar indices capture storm-time density that
 * F10.7/Ap alone miss.</p>
 */
public final class JB2008Atmosphere implements Atmosphere {

    private final OneAxisEllipsoid earth;
    private final Jb2008Inputs inputs;
    private final Jb2008 engine = new Jb2008();

    public JB2008Atmosphere(final OneAxisEllipsoid earth, final Jb2008Inputs inputs) {
        this.earth = earth;
        this.inputs = inputs;
    }

    @Override
    public double getDensity(final AbsoluteDate date, final Vector3D positionBodyFixed) {
        // satellite geodetic position (body-fixed)
        final GeodeticPoint sat = earth.transform(positionBodyFixed);

        // Sun position in the Earth-fixed frame, then its geodetic longitude/latitude
        final Vector3D sunGcrf = CelestialBodyFactory.getSun().getPosition(date, FramesFactory.getGCRF());
        final Vector3D sunEcef = FramesFactory
                .getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), date)
                .transformPosition(sunGcrf);
        final GeodeticPoint sun = earth.transform(sunEcef);

        final double dateMJD = modifiedJulianDate(date);

        return engine.density(dateMJD,
                sun.getLongitude(), sun.getLatitude(),
                sat.getLongitude(), sat.getLatitude(), sat.getAltitude(),
                inputs.getF10(date), inputs.getF10B(date),
                inputs.getS10(date), inputs.getS10B(date),
                inputs.getXM10(date), inputs.getXM10B(date),
                inputs.getY10(date), inputs.getY10B(date),
                inputs.getDSTDTC(date));
    }

    /** Modified Julian date (with day fraction) from the UTC calendar reading. */
    private static double modifiedJulianDate(final AbsoluteDate date) {
        final double[] c = date.getComponents(TimeScalesFactory.getUTC());
        final int y = (int) c[0];
        final int m = (int) c[1];
        final int d = (int) c[2];
        final int a = (14 - m) / 12;
        final int y2 = y + 4800 - a;
        final int m2 = m + 12 * a - 3;
        final int jdn = d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045;
        final int mjd0 = jdn - 2400001;
        final double dayFraction = (c[3] * 3600.0 + c[4] * 60.0 + c[5]) / 86400.0;
        return mjd0 + dayFraction;
    }

    @Override
    public String getName() {
        return "JB2008Atmosphere";
    }
}
