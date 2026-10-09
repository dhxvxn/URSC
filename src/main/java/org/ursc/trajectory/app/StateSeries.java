package org.ursc.trajectory.app;

import java.util.List;

import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;

/** Extracts plottable scalar series from a list of propagated states. */
public final class StateSeries {

    private static final OneAxisEllipsoid EARTH = new OneAxisEllipsoid(
            Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);

    private StateSeries() {
    }

    public static double[] timeHours(final List<SpacecraftState> states) {
        if (states.isEmpty()) {
            return new double[0];
        }
        final var start = states.get(0).getDate();
        final double[] t = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            t[i] = states.get(i).getDate().durationFrom(start) / 3600.0;
        }
        return t;
    }

    public static double[] altitudeKm(final List<SpacecraftState> states) {
        final double[] y = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            final SpacecraftState s = states.get(i);
            final var rBody = FramesFactory
                    .getTransform(s.getFrame(), FramesFactory.getITRF(), s.getDate())
                    .transformPosition(s.getPosition());
            y[i] = EARTH.getAltitude(rBody) / 1000.0;
        }
        return y;
    }

    public static double[] semiMajorAxisKm(final List<SpacecraftState> states) {
        final double[] y = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            y[i] = states.get(i).getOrbit().getA() / 1000.0;
        }
        return y;
    }

    public static double[] eccentricity(final List<SpacecraftState> states) {
        final double[] y = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            y[i] = states.get(i).getOrbit().getE();
        }
        return y;
    }

    /** Pick a named series: alt | sma | ecc. */
    public static double[] series(final String name, final List<SpacecraftState> states) {
        switch (name.toLowerCase()) {
            case "sma":
                return semiMajorAxisKm(states);
            case "ecc":
                return eccentricity(states);
            case "alt":
            default:
                return altitudeKm(states);
        }
    }

    public static String seriesLabel(final String name) {
        switch (name.toLowerCase()) {
            case "sma":
                return "semi-major axis (km)";
            case "ecc":
                return "eccentricity";
            case "alt":
            default:
                return "altitude (km)";
        }
    }

    public static KeplerianOrbit keplerian(final SpacecraftState s) {
        return new KeplerianOrbit(s.getOrbit());
    }
}
