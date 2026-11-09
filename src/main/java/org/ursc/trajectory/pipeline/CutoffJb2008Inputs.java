package org.ursc.trajectory.pipeline;

import org.ursc.trajectory.forces.drag.Jb2008Inputs;
import org.ursc.trajectory.time.AbsoluteDate;

/** JB2008 solar/geomagnetic drivers held at their cutoff values after the cutoff (cutoff-safe). */
final class CutoffJb2008Inputs implements Jb2008Inputs {

    private final Jb2008Inputs base;
    private final AbsoluteDate cutoff;

    CutoffJb2008Inputs(final Jb2008Inputs base, final AbsoluteDate cutoff) {
        this.base = base;
        this.cutoff = cutoff;
    }

    private AbsoluteDate c(final AbsoluteDate d) {
        return d.isAfter(cutoff) ? cutoff : d;
    }

    public double getF10(final AbsoluteDate d) {
        return base.getF10(c(d));
    }

    public double getF10B(final AbsoluteDate d) {
        return base.getF10B(c(d));
    }

    public double getS10(final AbsoluteDate d) {
        return base.getS10(c(d));
    }

    public double getS10B(final AbsoluteDate d) {
        return base.getS10B(c(d));
    }

    public double getXM10(final AbsoluteDate d) {
        return base.getXM10(c(d));
    }

    public double getXM10B(final AbsoluteDate d) {
        return base.getXM10B(c(d));
    }

    public double getY10(final AbsoluteDate d) {
        return base.getY10(c(d));
    }

    public double getY10B(final AbsoluteDate d) {
        return base.getY10B(c(d));
    }

    public double getDSTDTC(final AbsoluteDate d) {
        return base.getDSTDTC(c(d));
    }
}
