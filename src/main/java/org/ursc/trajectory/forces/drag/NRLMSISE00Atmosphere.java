package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.bodies.GeodeticPoint;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.forces.drag.msis.Nrlmsise00;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * NRLMSISE-00 atmosphere adapter plugging the verified MSIS engine into the
 * {@link Atmosphere} interface so it can be dropped into {@code DragForce} exactly
 * like the exponential and Harris-Priester models.
 *
 * <p>The solar/geomagnetic drivers come from a {@link SpaceWeatherProvider}, which
 * makes the activity level a selectable input (constant, or a future file/live
 * feed). The effective total mass density for drag ({@code gtd7d}, which includes
 * anomalous oxygen above ~500 km) is returned in kg/m^3.</p>
 */
public final class NRLMSISE00Atmosphere implements Atmosphere {

    private final OneAxisEllipsoid earth;
    private final SpaceWeatherProvider weather;

    public NRLMSISE00Atmosphere(final OneAxisEllipsoid earth, final SpaceWeatherProvider weather) {
        this.earth = earth;
        this.weather = weather;
    }

    @Override
    public double getDensity(final AbsoluteDate date, final Vector3D positionBodyFixed) {
        final GeodeticPoint geo = earth.transform(positionBodyFixed);
        final double altKm = geo.getAltitude() / 1000.0;
        final double latDeg = Math.toDegrees(geo.getLatitude());
        final double lonDeg = Math.toDegrees(geo.getLongitude());

        // day of year and UT seconds-of-day from the UTC calendar reading
        final double[] c = date.getComponents(TimeScalesFactory.getUTC());
        final int year = (int) c[0];
        final double secOfDay = c[3] * 3600.0 + c[4] * 60.0 + c[5];
        final AbsoluteDate yearStart =
                new AbsoluteDate(year, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final int doy = (int) Math.floor(date.durationFrom(yearStart) / 86400.0) + 1;

        final Nrlmsise00.Input in = new Nrlmsise00.Input();
        in.year = year;
        in.doy = doy;
        in.sec = secOfDay;
        in.alt = altKm;
        in.gLat = latDeg;
        in.gLong = lonDeg;
        // recommended consistent local solar time (hours)
        in.lst = secOfDay / 3600.0 + lonDeg / 15.0;
        in.f107 = weather.getDailyF107(date);
        in.f107A = weather.getAverageF107(date);
        in.ap = weather.getDailyAp(date);

        final Nrlmsise00.Flags flags = Nrlmsise00.Flags.standard(); // CGS output (switch 0 = 0)
        final Nrlmsise00 model = new Nrlmsise00();
        final Nrlmsise00.Output out = new Nrlmsise00.Output();
        model.gtd7d(in, flags, out);

        // d[5] is in g/cm^3 (switch 0 off); convert to kg/m^3
        return out.d[5] * 1000.0;
    }

    @Override
    public String getName() {
        return "NRLMSISE00Atmosphere";
    }
}
