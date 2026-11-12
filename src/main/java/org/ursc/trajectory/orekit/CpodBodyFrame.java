package org.ursc.trajectory.orekit;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.FieldPVCoordinatesProvider;
import org.orekit.utils.PVCoordinatesProvider;

/**
 * An Orekit attitude law expressed in the CPOD spacecraft body convention of the Sentinel-3 POD
 * documents (X anti-velocity, Z nadir): the underlying Orekit ground-pointing law (X towards the
 * velocity, Z nadir) turned 180 deg about Z. Spin and acceleration are turned with it.
 */
public final class CpodBodyFrame implements AttitudeProvider {

    private static final Rotation FLIP = new Rotation(Vector3D.PLUS_K, Math.PI, RotationConvention.VECTOR_OPERATOR);

    private final AttitudeProvider law;

    public CpodBodyFrame(final AttitudeProvider law) {
        this.law = law;
    }

    @Override
    public Attitude getAttitude(final PVCoordinatesProvider pv, final AbsoluteDate date, final Frame frame) {
        final Attitude a = law.getAttitude(pv, date, frame);
        return new Attitude(date, frame, FLIP.applyTo(a.getRotation()), FLIP.applyTo(a.getSpin()),
                FLIP.applyTo(a.getRotationAcceleration()));
    }

    @Override
    public <T extends CalculusFieldElement<T>> FieldAttitude<T> getAttitude(final FieldPVCoordinatesProvider<T> pv,
                                                                            final FieldAbsoluteDate<T> date, final Frame frame) {
        final FieldAttitude<T> a = law.getAttitude(pv, date, frame);
        final FieldRotation<T> flip = new FieldRotation<>(date.getField(), FLIP);
        return new FieldAttitude<>(date, frame, flip.applyTo(a.getRotation()), flip.applyTo(a.getSpin()),
                flip.applyTo(a.getRotationAcceleration()));
    }
}
