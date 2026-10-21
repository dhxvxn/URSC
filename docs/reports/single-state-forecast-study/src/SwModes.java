package org.ursc.trajectory.app.command;

import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real-error weather-mode comparison: propagate the Aug-1 seed to the exact Aug-8 epoch under three
 * space-weather assumptions and compare to the supplied Aug-8 truth state (ITRF). Reports the true
 * 7-day position error for each mode.
 *   reconstruction : real observed F10.7/Ap across the span (the best-weather reference)
 *   constant       : the seed-instant F10.7/F10.7a/Ap held flat
 *   forecast-trend : F10.7 relaxes seed->81-day-average (tau=4 d); Ap persisted at the seed value
 * args: swFile eopFile  utc  x y z vx vy vz   horizonDays  tx ty tz
 */
public final class SwModes {

    static final class TrendForecast implements SpaceWeatherProvider {
        private final AbsoluteDate anchor;
        private final double f107, f107a, ap, tauDays = 4.0;
        TrendForecast(final AbsoluteDate anchor, final double f107, final double f107a, final double ap) {
            this.anchor = anchor; this.f107 = f107; this.f107a = f107a; this.ap = ap;
        }
        public double getDailyF107(final AbsoluteDate d) {
            final double dt = d.durationFrom(anchor) / 86400.0;
            return dt <= 0 ? f107 : f107a + (f107 - f107a) * Math.exp(-dt / tauDays);
        }
        public double getAverageF107(final AbsoluteDate d) {
            return f107a;
        }
        public double getDailyAp(final AbsoluteDate d) {
            return ap;
        }
    }

    public static void main(final String[] a) throws Exception {
        final String swFile = a[0], eopFile = a[1], utc = a[2];
        final double[] r = {Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5])};
        final double[] v = {Double.parseDouble(a[6]), Double.parseDouble(a[7]), Double.parseDouble(a[8])};
        final double horizonDays = Double.parseDouble(a[9]);
        final Vector3D truthItrf = new Vector3D(
                Double.parseDouble(a[10]), Double.parseDouble(a[11]), Double.parseDouble(a[12]));

        final AbsoluteDate epoch = PredictCommand.parseUtc(utc);
        final List<AbsoluteDate> d1 = new ArrayList<>(); d1.add(epoch);
        final List<Vector3D> p1 = new ArrayList<>(); p1.add(new Vector3D(r[0], r[1], r[2]));
        final List<Vector3D> v1 = new ArrayList<>(); v1.add(new Vector3D(v[0], v[1], v[2]));
        OrbitFiles.configureFrames(PoeOrbReader.of(d1, p1, v1, 0.0),
                OrbitFiles.loadEop(eopFile, PoeOrbReader.of(d1, p1, v1, 0.0)));

        final Frame gcrf = FramesFactory.getGCRF();
        final Frame itrf = FramesFactory.getITRF();
        final Transform i2g = FramesFactory.getTransform(itrf, gcrf, epoch);
        final Vector3D rG = i2g.transformPosition(new Vector3D(r[0], r[1], r[2]));
        final Vector3D vG = i2g.transformVelocity(new Vector3D(v[0], v[1], v[2]), rG);
        final SpacecraftState seed = new SpacecraftState(
                new CartesianOrbit(new PVCoordinates(rG, vG), gcrf, epoch, Constants.EARTH_MU), 1130.0);

        final OrbitFiles.Setup tmp = new OrbitFiles.Setup();
        final SpaceWeatherProvider observed = OrbitFiles.loadSpaceWeather(swFile, tmp);
        final double f107 = observed.getDailyF107(epoch);
        final double f107a = observed.getAverageF107(epoch);
        final double ap = observed.getDailyAp(epoch);
        System.out.printf(Locale.US, "Seed %s (ITRF)  alt=%.1f km   horizon=%.0f days%n", epoch,
                (rG.getNorm() - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0, horizonDays);
        System.out.printf(Locale.US, "Seed-instant weather: F10.7=%.1f  F10.7a=%.1f  Ap=%.1f%n", f107, f107a, ap);

        final AbsoluteDate end = epoch.shiftedBy(horizonDays * 86400.0);
        final double eRec = err(seed, observed, end, itrf, gcrf, truthItrf);
        final double eCon = err(seed, new ConstantSpaceWeather(f107, f107a, ap), end, itrf, gcrf, truthItrf);
        final double eFc = err(seed, new TrendForecast(epoch, f107, f107a, ap), end, itrf, gcrf, truthItrf);

        System.out.printf(Locale.US, "%nTrue %.0f-day position error vs the supplied truth (ITRF):%n", horizonDays);
        System.out.printf(Locale.US, "  reconstruction (observed weather) : %,.1f m%n", eRec);
        System.out.printf(Locale.US, "  constant (seed weather frozen)    : %,.1f m%n", eCon);
        System.out.printf(Locale.US, "  forecast-trend (F10.7->avg)       : %,.1f m%n", eFc);
    }

    private static double err(final SpacecraftState seed, final SpaceWeatherProvider sw,
                              final AbsoluteDate end, final Frame itrf, final Frame gcrf,
                              final Vector3D truthItrf) {
        final OrbitFiles.Setup s = new OrbitFiles.Setup();
        s.mass = 1130.0; s.dragArea = 8.0; s.srpArea = 8.0; s.gravityDegree = 70;
        s.spaceWeather = sw;
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.gravityDegree = 70; fs.gravityOrder = 70;
        fs.sun = true; fs.moon = true; fs.atmosphere = "nrlmsise00";
        fs.srp = true; fs.solidTides = true; fs.oceanTides = true; fs.relativity = true; fs.albedo = true;
        final NumericalPropagator prop =
                (NumericalPropagator) OrbitFiles.config(fs, seed, s, 2.2, s.cr).build();
        final SpacecraftState fin = prop.propagate(end);
        final Transform g2i = FramesFactory.getTransform(gcrf, itrf, fin.getDate());
        return g2i.transformPosition(fin.getPosition()).subtract(truthItrf).getNorm();
    }

    private SwModes() { }
}
