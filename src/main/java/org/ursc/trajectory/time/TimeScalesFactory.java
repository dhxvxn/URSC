package org.ursc.trajectory.time;

/**
 * Factory giving access to the supported time scales.
 *
 * <p>Supported: TAI, TT, UTC (with a full leap-second table), GPS and UT1.
 * UT1 is approximated as UTC (i.e. dUT1 = 0) which is accurate to under one
 * second &mdash; adequate for LEO trajectory prediction; a UT1 provider reading
 * IERS EOP files is a natural future extension.</p>
 */
public final class TimeScalesFactory {

    private static final TimeScale TAI = new TimeScale() {
        public double offsetFromTAI(final AbsoluteDate date) {
            return 0.0;
        }
        public String getName() {
            return "TAI";
        }
    };

    /** Terrestrial Time: TT = TAI + 32.184 s. */
    private static final TimeScale TT = new TimeScale() {
        public double offsetFromTAI(final AbsoluteDate date) {
            return 32.184;
        }
        public String getName() {
            return "TT";
        }
    };

    /** GPS time: GPS = TAI - 19 s. */
    private static final TimeScale GPS = new TimeScale() {
        public double offsetFromTAI(final AbsoluteDate date) {
            return -19.0;
        }
        public String getName() {
            return "GPS";
        }
    };

    private static final UTCScale UTC = new UTCScale();

    /** UT1 approximated by UTC (dUT1 = 0). */
    private static final TimeScale UT1 = new TimeScale() {
        public double offsetFromTAI(final AbsoluteDate date) {
            return UTC.offsetFromTAI(date);
        }
        public String getName() {
            return "UT1";
        }
    };

    private TimeScalesFactory() {
    }

    public static TimeScale getTAI() {
        return TAI;
    }

    public static TimeScale getTT() {
        return TT;
    }

    public static TimeScale getGPS() {
        return GPS;
    }

    public static UTCScale getUTC() {
        return UTC;
    }

    public static TimeScale getUT1() {
        return UT1;
    }
}
