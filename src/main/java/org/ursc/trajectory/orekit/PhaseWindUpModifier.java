package org.ursc.trajectory.orekit;

import java.util.Collections;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.EstimationModifier;
import org.orekit.estimation.measurements.gnss.OneWayGNSSPhase;
import org.orekit.gnss.PredefinedGnssSignal;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.orekit.utils.ParameterDriver;

/**
 * Carrier-phase wind-up (Wu et al., 1993) for a GPS-to-LEO one-way phase, as an Orekit
 * measurement modifier. The right-hand circularly polarised carrier advances by the rotation of
 * the receiving antenna relative to the transmitting one about the line of sight; for the
 * ionosphere-free combination this is {@code lambda_NL * phi / 2 pi} with the narrow-lane
 * wavelength {@code c / (f1 + f2)} (10.7 cm). The transmitter axes come from the GPS
 * satellite's yaw-steering attitude, the receiver axes from the propagated LEO attitude
 * (antenna boresight = body -Z, zenith). One instance serves one phase arc, so the angle is
 * kept continuous along the arc.
 */
public final class PhaseWindUpModifier implements EstimationModifier<OneWayGNSSPhase> {

    private static final double LAMBDA_NL = Constants.SPEED_OF_LIGHT
            / (PredefinedGnssSignal.G01.getFrequency() + PredefinedGnssSignal.G02.getFrequency());

    private final AttitudeProvider transmitterAttitude;
    private double last = Double.NaN;
    private org.orekit.time.AbsoluteDate lastDate;

    public PhaseWindUpModifier(final AttitudeProvider transmitterAttitude) {
        this.transmitterAttitude = transmitterAttitude;
    }

    @Override
    public String getEffectName() {
        return "phase wind-up";
    }

    @Override
    public List<ParameterDriver> getParametersDrivers() {
        return Collections.emptyList();
    }

    @Override
    public void modifyWithoutDerivatives(final EstimatedMeasurementBase<OneWayGNSSPhase> estimated) {
        final SpacecraftState receiver = estimated.getStates()[0];
        final TimeStampedPVCoordinates[] p = estimated.getParticipants();
        final TimeStampedPVCoordinates emitter = p[0];
        final TimeStampedPVCoordinates rcv = p[p.length - 1];
        // receiver antenna axes in the inertial frame: x = body X, y = -body Y (boresight -Z)
        final Rotation rr = receiver.shiftedBy(rcv.getDate().durationFrom(receiver.getDate())).getAttitude().getRotation();
        final Vector3D xr = rr.applyInverseTo(Vector3D.PLUS_I);
        final Vector3D yr = rr.applyInverseTo(Vector3D.MINUS_J);
        // transmitter axes from the GPS attitude model at emission
        final Rotation rt = transmitterAttitude.getAttitude(
                (date, frame) -> emitter.shiftedBy(date.durationFrom(emitter.getDate())),
                emitter.getDate(), receiver.getFrame()).getRotation();
        final Vector3D xt = rt.applyInverseTo(Vector3D.PLUS_I);
        final Vector3D yt = rt.applyInverseTo(Vector3D.PLUS_J);
        final Vector3D k = rcv.getPosition().subtract(emitter.getPosition()).normalize();
        final Vector3D dr = xr.subtract(k.scalarMultiply(k.dotProduct(xr))).add(k.crossProduct(yr));
        final Vector3D dt = xt.subtract(k.scalarMultiply(k.dotProduct(xt))).subtract(k.crossProduct(yt));
        final double cos = Math.max(-1.0, Math.min(1.0, dt.dotProduct(dr) / (dt.getNorm() * dr.getNorm())));
        double phi = Math.copySign(Math.acos(cos), k.dotProduct(dt.crossProduct(dr)));
        // continuity along the arc (measurements are evaluated in time order in each iteration)
        if (!Double.isNaN(last) && lastDate != null && !estimated.getDate().isBefore(lastDate)) {
            phi += 2.0 * Math.PI * Math.rint((last - phi) / (2.0 * Math.PI));
        }
        last = phi;
        lastDate = estimated.getDate();
        final double[] v = estimated.getEstimatedValue().clone();
        // the phase is in metres (wavelength 1 m in the measurement)
        v[0] += LAMBDA_NL * phi / (2.0 * Math.PI);
        estimated.modifyEstimatedValue(this, v);
    }
}
