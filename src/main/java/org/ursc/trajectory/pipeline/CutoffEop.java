package org.ursc.trajectory.pipeline;

import org.ursc.trajectory.frames.EopProvider;
import org.ursc.trajectory.time.AbsoluteDate;

/** Earth-orientation values after the cutoff are held at the cutoff value (cutoff-safe). */
final class CutoffEop implements EopProvider {

    private final EopProvider base;
    private final AbsoluteDate cutoff;

    CutoffEop(final EopProvider base, final AbsoluteDate cutoff) {
        this.base = base;
        this.cutoff = cutoff;
    }

    private AbsoluteDate clamp(final AbsoluteDate d) {
        return d.isAfter(cutoff) ? cutoff : d;
    }

    @Override
    public double getUT1MinusUTC(final AbsoluteDate date) {
        return base.getUT1MinusUTC(clamp(date));
    }

    @Override
    public double getXp(final AbsoluteDate date) {
        return base.getXp(clamp(date));
    }

    @Override
    public double getYp(final AbsoluteDate date) {
        return base.getYp(clamp(date));
    }
}
