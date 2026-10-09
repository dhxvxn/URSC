package org.ursc.trajectory.app;

import java.util.Locale;

import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.StepHandler;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * A step handler that draws a progress bar with ETA on <em>stderr</em>, leaving
 * stdout clean for piping. Progress is the fraction of the propagation span
 * covered so far.
 */
public final class ProgressStepHandler implements StepHandler {

    private AbsoluteDate start;
    private double span;
    private long wallStartNanos;
    private double lastFraction = -1;
    private final boolean enabled;

    public ProgressStepHandler(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void init(final SpacecraftState initialState, final AbsoluteDate target) {
        this.start = initialState.getDate();
        this.span = target.durationFrom(start);
        this.wallStartNanos = System.nanoTime();
    }

    @Override
    public void handleStep(final SpacecraftState state) {
        if (!enabled || span == 0.0) {
            return;
        }
        final double fraction = Math.max(0.0, Math.min(1.0, state.getDate().durationFrom(start) / span));
        if (fraction - lastFraction < 0.01 && fraction < 1.0) {
            return; // throttle to ~1% updates
        }
        lastFraction = fraction;
        draw(fraction);
    }

    @Override
    public void finish(final SpacecraftState finalState) {
        if (!enabled || span == 0.0) {
            return;
        }
        draw(1.0);
        System.err.println();
    }

    private void draw(final double fraction) {
        final int barWidth = 30;
        final int filled = (int) Math.round(fraction * barWidth);
        final double elapsed = (System.nanoTime() - wallStartNanos) / 1.0e9;
        final double eta = fraction > 0 ? elapsed * (1 - fraction) / fraction : 0.0;
        final StringBuilder bar = new StringBuilder("\r[");
        for (int i = 0; i < barWidth; i++) {
            bar.append(i < filled ? '#' : ' ');
        }
        bar.append(String.format(Locale.US, "] %5.1f%%  elapsed %.1fs  eta %.1fs   ",
                fraction * 100, elapsed, eta));
        System.err.print(bar);
        System.err.flush();
    }
}
