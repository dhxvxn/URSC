package org.ursc.trajectory.frames;

import org.ursc.trajectory.time.AbsoluteDate;

/** Constant Earth Orientation Parameters; the default is all zeros. */
public final class ConstantEop implements EopProvider {

    /** All-zero EOP: UT1 = UTC, no polar motion. */
    public static final ConstantEop ZERO = new ConstantEop(0.0, 0.0, 0.0);

    private final double ut1MinusUtc;
    private final double xp;
    private final double yp;

    public ConstantEop(final double ut1MinusUtc, final double xp, final double yp) {
        this.ut1MinusUtc = ut1MinusUtc;
        this.xp = xp;
        this.yp = yp;
    }

    @Override
    public double getUT1MinusUTC(final AbsoluteDate date) {
        return ut1MinusUtc;
    }

    @Override
    public double getXp(final AbsoluteDate date) {
        return xp;
    }

    @Override
    public double getYp(final AbsoluteDate date) {
        return yp;
    }
}
