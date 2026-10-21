package org.ursc.trajectory.app.command;

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
 * Fit a single effective drag area A (ballistic coefficient B = Cd*A/m, Cd=2.2, m=1130) to tracking
 * states, using ONLY forecast weather (trend from the Aug-1 seed instant) -- no observed future data.
 * Because the along-track error is linear in A, each fit solves A* from two propagations.
 * args: swFile eopFile  seedUtc sx sy sz svx svy svz   d1Days t1x t1y t1z   d8Days t8x t8y t8z
 */
public final class FitB {

    static final double M = 1130.0, CD = 2.2;

    static final class TrendForecast implements SpaceWeatherProvider {
        final AbsoluteDate anchor; final double f107, f107a, ap, tau = 4.0;
        TrendForecast(AbsoluteDate a, double f, double fa, double p){anchor=a;f107=f;f107a=fa;ap=p;}
        public double getDailyF107(AbsoluteDate d){double dt=d.durationFrom(anchor)/86400.0;return dt<=0?f107:f107a+(f107-f107a)*Math.exp(-dt/tau);}
        public double getAverageF107(AbsoluteDate d){return f107a;}
        public double getDailyAp(AbsoluteDate d){return ap;}
    }

    static Frame gcrf, itrf;
    static SpacecraftState seed;
    static SpaceWeatherProvider fc;

    public static void main(final String[] a) throws Exception {
        final AbsoluteDate epoch = PredictCommand.parseUtc(a[2]);
        final List<AbsoluteDate> dl = new ArrayList<>(); dl.add(epoch);
        final List<Vector3D> pl = new ArrayList<>(); pl.add(new Vector3D(d(a,3),d(a,4),d(a,5)));
        final List<Vector3D> vl = new ArrayList<>(); vl.add(new Vector3D(d(a,6),d(a,7),d(a,8)));
        OrbitFiles.configureFrames(PoeOrbReader.of(dl,pl,vl,0.0), OrbitFiles.loadEop(a[1], PoeOrbReader.of(dl,pl,vl,0.0)));
        gcrf = FramesFactory.getGCRF(); itrf = FramesFactory.getITRF();
        final Transform i2g = FramesFactory.getTransform(itrf, gcrf, epoch);
        final Vector3D rG = i2g.transformPosition(new Vector3D(d(a,3),d(a,4),d(a,5)));
        final Vector3D vG = i2g.transformVelocity(new Vector3D(d(a,6),d(a,7),d(a,8)), rG);
        seed = new SpacecraftState(new CartesianOrbit(new PVCoordinates(rG,vG), gcrf, epoch, Constants.EARTH_MU), M);

        final OrbitFiles.Setup tmp = new OrbitFiles.Setup();
        final SpaceWeatherProvider obs = OrbitFiles.loadSpaceWeather(a[0], tmp);
        fc = new TrendForecast(epoch, obs.getDailyF107(epoch), obs.getAverageF107(epoch), obs.getDailyAp(epoch));

        final double d1 = d(a,9);  final Vector3D t1 = new Vector3D(d(a,10),d(a,11),d(a,12));
        final double d8 = d(a,13); final Vector3D t8 = new Vector3D(d(a,14),d(a,15),d(a,16));
        final AbsoluteDate e1 = epoch.shiftedBy(d1*86400.0), e8 = epoch.shiftedBy(d8*86400.0);

        System.out.println("Forecast weather only (no observed future). Cd=2.2, m=1130 kg fixed; fit area A.\n");
        System.out.printf(Locale.US, "Baseline A=8 m^2 (B=%.5f): %.0f-day err %,.1f m   %.0f-day err %,.1f m%n%n",
                CD*8.0/M, d1, totalErr(8.0,e1,t1), d8, totalErr(8.0,e8,t8));

        // Fit A to the full week (target = Aug-8 point, the strongest drag signal)
        final double aWeek = fitArea(e8, t8);
        System.out.println(">>> Orbit determination over the Aug1->8 arc (fit A to the Aug-8 state):");
        System.out.printf(Locale.US, "    A* = %.2f m^2   B* = Cd*A*/m = %.5f m^2/kg%n", aWeek, CD*aWeek/M);
        System.out.printf(Locale.US, "    residual: %.0f-day %,.1f m (fitted)   %.0f-day %,.1f m (cross-check)%n%n",
                d8, totalErr(aWeek,e8,t8), d1, totalErr(aWeek,e1,t1));

        // Honest forward: fit A on the first day only (Aug1->2), predict Aug-8 (held-out)
        final double aDay1 = fitArea(e1, t1);
        System.out.println(">>> Forward test (fit A on day-1 arc Aug1->2, predict Aug-8 held-out):");
        System.out.printf(Locale.US, "    A* = %.2f m^2   B* = %.5f%n", aDay1, CD*aDay1/M);
        System.out.printf(Locale.US, "    %.0f-day %,.1f m (fitted)   %.0f-day %,.1f m (HELD-OUT forward)%n",
                d1, totalErr(aDay1,e1,t1), d8, totalErr(aDay1,e8,t8));
    }

    /** Solve A* that nulls the signed along-track error at the target, from two propagations (linear). */
    static double fitArea(final AbsoluteDate target, final Vector3D truthItrf) {
        final double e2 = signedAlong(2.0, target, truthItrf);
        final double e8 = signedAlong(8.0, target, truthItrf);
        double aStar = 2.0 - 6.0 * e2 / (e8 - e2);
        return Math.max(0.1, aStar);
    }

    static double signedAlong(final double area, final AbsoluteDate target, final Vector3D truthItrf) {
        final SpacecraftState f = prop(area, target);
        final Vector3D truthG = FramesFactory.getTransform(itrf, gcrf, f.getDate()).transformPosition(truthItrf);
        final Vector3D e = f.getPosition().subtract(truthG);
        final Vector3D rr = f.getPosition().normalize();
        final Vector3D cc = f.getPosition().crossProduct(f.getVelocity()).normalize();
        return e.dotProduct(cc.crossProduct(rr));
    }

    static double totalErr(final double area, final AbsoluteDate target, final Vector3D truthItrf) {
        final SpacecraftState f = prop(area, target);
        return FramesFactory.getTransform(gcrf, itrf, f.getDate()).transformPosition(f.getPosition())
                .subtract(truthItrf).getNorm();
    }

    static SpacecraftState prop(final double area, final AbsoluteDate end) {
        final OrbitFiles.Setup s = new OrbitFiles.Setup();
        s.mass = M; s.dragArea = area; s.srpArea = area; s.gravityDegree = 70; s.spaceWeather = fc;
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.gravityDegree = 70; fs.gravityOrder = 70;
        fs.sun = true; fs.moon = true; fs.atmosphere = "nrlmsise00";
        fs.srp = true; fs.solidTides = true; fs.oceanTides = true; fs.relativity = true; fs.albedo = true;
        return ((NumericalPropagator) OrbitFiles.config(fs, seed, s, CD, s.cr).build()).propagate(end);
    }

    static double d(String[] a, int i){ return Double.parseDouble(a[i]); }
    private FitB(){}
}
