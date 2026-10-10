package org.ursc.trajectory.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Runs several configured propagators over the same span and quantifies how far
 * they diverge from a reference (the first variant): final Keplerian elements and
 * the maximum inertial position difference at matching sample times.
 */
public final class ModelComparison {

    /**
     * A named variant. The propagator is supplied lazily and built immediately
     * before it is run, so variants that change global state (e.g. the frame
     * model or EOP, set in {@code ScenarioLoader.build()}) do not interfere with
     * one another.
     */
    public static final class Variant {
        public final String name;
        public final Supplier<SampledPropagator> provider;

        public Variant(final String name, final Supplier<SampledPropagator> provider) {
            this.name = name;
            this.provider = provider;
        }
    }

    /** One row of comparison output. */
    public static final class Row {
        public final String name;
        public final KeplerianOrbit finalOrbit;
        public final double maxPositionDiffM; // vs reference variant (0 for the reference)

        Row(final String name, final KeplerianOrbit finalOrbit, final double maxPositionDiffM) {
            this.name = name;
            this.finalOrbit = finalOrbit;
            this.maxPositionDiffM = maxPositionDiffM;
        }
    }

    private ModelComparison() {
    }

    /**
     * @param variants        propagators to run (index 0 is the reference)
     * @param durationSeconds span to propagate from each propagator's initial epoch
     * @param sampleStep      common output grid used for position-divergence comparison
     */
    public static List<Row> compare(final List<Variant> variants, final double durationSeconds,
                                    final double sampleStep) {
        final List<List<SpacecraftState>> histories = new ArrayList<>();
        final List<KeplerianOrbit> finals = new ArrayList<>();

        for (final Variant v : variants) {
            // build immediately before running so each variant's global config
            // (frame model, EOP, ...) is the one in effect during its propagation
            final SampledPropagator propagator = v.provider.get();
            final EphemerisCollector collector = new EphemerisCollector();
            propagator.setStepHandler(sampleStep, collector);
            final AbsoluteDate start = propagator.getInitialState().getDate();
            final SpacecraftState fin = propagator.propagate(start.shiftedBy(durationSeconds));
            histories.add(collector.getStates());
            finals.add(new KeplerianOrbit(fin.getOrbit()));
        }

        final List<Row> rows = new ArrayList<>();
        final List<SpacecraftState> ref = histories.get(0);
        for (int vi = 0; vi < variants.size(); vi++) {
            double maxDiff = 0.0;
            if (vi > 0) {
                final List<SpacecraftState> h = histories.get(vi);
                final int n = Math.min(ref.size(), h.size());
                for (int i = 0; i < n; i++) {
                    final Vector3D dr = h.get(i).getPosition().subtract(ref.get(i).getPosition());
                    maxDiff = Math.max(maxDiff, dr.getNorm());
                }
            }
            rows.add(new Row(variants.get(vi).name, finals.get(vi), maxDiff));
        }
        return rows;
    }
}
