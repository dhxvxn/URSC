package org.ursc.trajectory.orekit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.data.DataSource;
import org.orekit.gnss.PredefinedGnssSignal;
import org.orekit.gnss.SatInSystem;
import org.orekit.gnss.SatelliteSystem;
import org.orekit.gnss.antenna.AntexLoader;
import org.orekit.gnss.antenna.FrequencyPattern;
import org.orekit.gnss.antenna.SatelliteAntenna;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeSpanMap;

/**
 * GPS satellite antennas from an IGS ANTEX file, through Orekit's {@link AntexLoader}: the
 * ionosphere-free combination of the L1/L2 phase-centre offsets and variations, and the
 * satellite's nominal yaw-steering attitude (Orekit's GNSS attitude model of its block,
 * {@link org.orekit.gnss.antenna.SatelliteType#buildAttitudeProvider}). IGS precise orbits refer
 * to the satellite centre of mass and IGS clocks to the ionosphere-free antenna phase centre
 * of this ANTEX, so the offset belongs in the measurement model with precise products only;
 * broadcast orbits already refer to the antenna.
 */
public final class GpsAntennas {

    private static final double F1 = PredefinedGnssSignal.G01.getFrequency();
    private static final double F2 = PredefinedGnssSignal.G02.getFrequency();
    private static final double A1 = F1 * F1 / (F1 * F1 - F2 * F2);
    private static final double A2 = -F2 * F2 / (F1 * F1 - F2 * F2);

    private final AntexLoader antex;
    private final OrekitSession session;
    private boolean phaseCentres = true;
    private final Map<String, FrequencyPattern> patterns = new HashMap<>();
    private final Map<String, AttitudeProvider> attitudes = new HashMap<>();

    private GpsAntennas(final AntexLoader antex, final OrekitSession session) {
        this.antex = antex;
        this.session = session;
    }

    /** Load {@code file}; null if it is missing. */
    public static GpsAntennas load(final Path file, final OrekitSession session) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        return new GpsAntennas(new AntexLoader(new DataSource(file.toString()), session.gps), session);
    }

    private SatelliteAntenna antenna(final int prn, final AbsoluteDate t) {
        try {
            final TimeSpanMap<SatelliteAntenna> m = antex.findSatelliteAntenna(new SatInSystem(SatelliteSystem.GPS, prn));
            return m == null ? null : m.get(t);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String key(final SatelliteAntenna a) {
        return a.getSatInSystem() + "/" + a.getValidFrom();
    }

    /** The same antennas used for the attitude only (no phase-centre corrections). */
    public GpsAntennas withoutPhaseCentres() {
        final GpsAntennas g = new GpsAntennas(antex, session);
        g.phaseCentres = false;
        return g;
    }

    /** Ionosphere-free phase-centre pattern of the satellite, or null if unknown or disabled. */
    public synchronized FrequencyPattern ionosphereFreePattern(final int prn, final AbsoluteDate t) {
        final SatelliteAntenna a = phaseCentres ? antenna(prn, t) : null;
        if (a == null) {
            return null;
        }
        return patterns.computeIfAbsent(key(a), k -> {
            final FrequencyPattern p1 = a.getPattern(PredefinedGnssSignal.G01);
            final FrequencyPattern p2 = a.getPattern(PredefinedGnssSignal.G02);
            final Vector3D ecc = new Vector3D(A1, p1.getEccentricities(), A2, p2.getEccentricities());
            return new FrequencyPattern(ecc, (polar, azimuth) ->
                    A1 * p1.getPhaseCenterVariationFunction().value(polar, azimuth)
                            + A2 * p2.getPhaseCenterVariationFunction().value(polar, azimuth));
        });
    }

    /** Nominal attitude (yaw steering, eclipse manoeuvres) of the satellite, or null if unknown. */
    public synchronized AttitudeProvider attitude(final int prn, final AbsoluteDate t) {
        final SatelliteAntenna a = antenna(prn, t);
        if (a == null) {
            return null;
        }
        return attitudes.computeIfAbsent(key(a), k -> a.getSatelliteType().buildAttitudeProvider(
                a.getValidFrom(), a.getValidUntil(), session.sun, session.gcrf, prn));
    }
}
