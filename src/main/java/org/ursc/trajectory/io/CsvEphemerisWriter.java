package org.ursc.trajectory.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.StepHandler;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * A {@link StepHandler} that writes each sampled state to a CSV file: epoch,
 * inertial position/velocity, osculating Keplerian elements and geodetic
 * sub-satellite point with altitude. Opens one file for the whole run.
 */
public final class CsvEphemerisWriter implements StepHandler {

    private final Path path;
    private final OneAxisEllipsoid earth;
    private Writer writer;

    public CsvEphemerisWriter(final Path path) {
        this.path = path;
        this.earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
    }

    @Override
    public void init(final SpacecraftState initialState, final AbsoluteDate target) {
        try {
            writer = new BufferedWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
            writer.write("epoch_utc,x_m,y_m,z_m,vx_ms,vy_ms,vz_ms,"
                    + "a_m,e,i_deg,raan_deg,argPerigee_deg,trueAnomaly_deg,"
                    + "lat_deg,lon_deg,alt_m\n");
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot open ephemeris file " + path, e);
        }
    }

    @Override
    public void handleStep(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D v = state.getVelocity();
        final KeplerianOrbit k = new KeplerianOrbit(state.getOrbit());

        final Vector3D rBody = FramesFactory
                .getTransform(state.getFrame(), FramesFactory.getITRF(), state.getDate())
                .transformPosition(r);
        final var geo = earth.transform(rBody);

        try {
            writer.write(String.format(Locale.US,
                    "%s,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f,"
                            + "%.3f,%.8f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f%n",
                    state.getDate().toString(TimeScalesFactory.getUTC()),
                    r.getX(), r.getY(), r.getZ(), v.getX(), v.getY(), v.getZ(),
                    k.getA(), k.getE(), Math.toDegrees(k.getI()),
                    Math.toDegrees(k.getRightAscensionOfAscendingNode()),
                    Math.toDegrees(k.getPerigeeArgument()),
                    Math.toDegrees(k.getTrueAnomaly()),
                    Math.toDegrees(geo.getLatitude()), Math.toDegrees(geo.getLongitude()),
                    geo.getAltitude()));
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot write ephemeris row", e);
        }
    }

    @Override
    public void finish(final SpacecraftState finalState) {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot close ephemeris file " + path, e);
        }
    }
}
