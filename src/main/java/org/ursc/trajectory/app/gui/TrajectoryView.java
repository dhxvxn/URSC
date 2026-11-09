package org.ursc.trajectory.app.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.GeodeticPoint;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.pipeline.RunResult;
import org.ursc.trajectory.pipeline.TimeUtil;

/**
 * Visualization payload for a run: Earth-fixed and inertial positions (km), geodetic
 * sub-satellite track, Sun direction (for day/night shading), the Earth rotation angle (for
 * the inertial view) and, when available, 1-sigma RTN position uncertainty (m).
 * Must be built while the run's frame/EOP environment is still configured.
 */
final class TrajectoryView {

    private TrajectoryView() {
    }

    private static double r3(final double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    static Map<String, Object> build(final RunResult res) {
        return build(res.states);
    }

    static Map<String, Object> build(final List<RunResult.State> states) {
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        final List<String> epochs = new ArrayList<>();
        final List<Double> t = new ArrayList<>();
        final List<Double> itrf = new ArrayList<>();
        final List<Double> gcrf = new ArrayList<>();
        final List<Double> lat = new ArrayList<>();
        final List<Double> lon = new ArrayList<>();
        final List<Double> alt = new ArrayList<>();
        final List<Double> sun = new ArrayList<>();
        final List<Double> era = new ArrayList<>();
        final List<Object> sigma = new ArrayList<>();
        boolean anyCov = false;
        final RunResult.State first = states.get(0);
        for (final RunResult.State s : states) {
            final Transform g2i = FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), s.date);
            final Vector3D ri = g2i.transformPosition(s.position);
            epochs.add(TimeUtil.formatUtc(s.date));
            t.add(r3(s.date.durationFrom(first.date)));
            itrf.add(r3(ri.getX() / 1000));
            itrf.add(r3(ri.getY() / 1000));
            itrf.add(r3(ri.getZ() / 1000));
            gcrf.add(r3(s.position.getX() / 1000));
            gcrf.add(r3(s.position.getY() / 1000));
            gcrf.add(r3(s.position.getZ() / 1000));
            final GeodeticPoint gp = earth.transform(ri);
            lat.add(Math.round(Math.toDegrees(gp.getLatitude()) * 1e4) / 1e4);
            lon.add(Math.round(Math.toDegrees(gp.getLongitude()) * 1e4) / 1e4);
            alt.add(r3(gp.getAltitude() / 1000));
            // the Sun ephemeris is provided in GCRF; rotate the direction into ITRF
            final Vector3D su = g2i.transformVector(
                    CelestialBodyFactory.getSun().getPosition(s.date, FramesFactory.getGCRF())).normalize();
            sun.add(Math.round(su.getX() * 1e5) / 1e5);
            sun.add(Math.round(su.getY() * 1e5) / 1e5);
            sun.add(Math.round(su.getZ() * 1e5) / 1e5);
            final Vector3D xAxis = g2i.getInverse().transformVector(Vector3D.PLUS_I);
            era.add(Math.round(Math.atan2(xAxis.getY(), xAxis.getX()) * 1e7) / 1e7);
            if (s.covariance != null) {
                anyCov = true;
                sigma.add(rtnSigma(s));
            } else {
                sigma.add(null);
            }
        }
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("epochs", epochs);
        m.put("t", t);
        m.put("itrf", itrf);
        m.put("gcrf", gcrf);
        m.put("lat", lat);
        m.put("lon", lon);
        m.put("alt", alt);
        m.put("sunItrf", sun);
        m.put("era", era);
        m.put("sigmaRtn", anyCov ? sigma : null);
        return m;
    }

    /** 1-sigma radial / transverse / normal position uncertainty (m). */
    static List<Double> rtnSigma(final RunResult.State s) {
        final Vector3D r = s.position.normalize();
        final Vector3D n = s.position.crossProduct(s.velocity).normalize();
        final Vector3D tr = n.crossProduct(r);
        final Vector3D[] axes = {r, tr, n};
        final List<Double> out = new ArrayList<>();
        for (final Vector3D a : axes) {
            final double[] u = {a.getX(), a.getY(), a.getZ()};
            double v = 0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    v += u[i] * s.covariance[i][j] * u[j];
                }
            }
            out.add(Math.round(Math.sqrt(Math.max(0, v)) * 1000.0) / 1000.0);
        }
        return out;
    }
}
