package org.ursc.trajectory.gnss.products;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Interprets IGS product file names, both the long form
 * ({@code IGS0OPSFIN_20242900000_01D_15M_ORB.SP3.gz}) and the legacy short form
 * ({@code igs23345.sp3.Z}, {@code igr23345.clk}, {@code igu23345_06.sp3.Z}).
 *
 * <p>The name gives product class, analysis centre, data start and span. It does
 * <b>not</b> prove when the product was published; {@link #nominalIssueTime()} is a
 * documented nominal-latency assumption and is always reported as unverified.</p>
 */
public final class IgsProductName {

    /** Nominal publication latencies used for cutoff eligibility (assumptions, not metadata). */
    public static final double ULTRA_LATENCY_AFTER_OBSERVED_END_S = 3 * 3600.0;
    public static final double RAPID_LATENCY_AFTER_END_S = 17 * 3600.0;
    public static final double FINAL_LATENCY_AFTER_END_S = 18 * 86400.0;

    public enum Content { ORBIT, CLOCK, UNKNOWN }

    private static final Pattern LONG = Pattern.compile(
            "^([A-Z0-9]{3})([0-9])([A-Z0-9]{3})(FIN|RAP|ULT|RTS|PRD)_(\\d{4})(\\d{3})(\\d{2})(\\d{2})_(\\d{2})([MHDWLU])_"
            + "(\\d{2}[SMHDU])_([A-Z]{3})\\.([A-Z0-9]{3})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile(
            "^([a-z]{3})(\\d{4})(\\d)(?:_(\\d{2}))?\\.(sp3|eph|clk|clk_30s|clk_05s)",
            Pattern.CASE_INSENSITIVE);
    private static final AbsoluteDate GPS_EPOCH =
            new AbsoluteDate(1980, 1, 6, 0, 0, 0.0, TimeScalesFactory.getGPS());

    private final ProductClass productClass;
    private final String provider;
    private final AbsoluteDate start;
    private final double spanSeconds;
    private final Content content;
    private final boolean recognised;

    private IgsProductName(final ProductClass cls, final String provider, final AbsoluteDate start,
                           final double span, final Content content, final boolean recognised) {
        this.productClass = cls;
        this.provider = provider;
        this.start = start;
        this.spanSeconds = span;
        this.content = content;
        this.recognised = recognised;
    }

    /** Parse a (possibly compressed) file name; {@link #recognised()} is false if unknown. */
    public static IgsProductName parse(final String fileName) {
        String n = fileName;
        final int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        Matcher m = LONG.matcher(n);
        if (m.find()) {
            final String type = m.group(4).toUpperCase(Locale.US);
            final ProductClass cls = "FIN".equals(type) ? ProductClass.FINAL
                    : "RAP".equals(type) ? ProductClass.RAPID
                    : "ULT".equals(type) ? ProductClass.ULTRA_RAPID : null;
            final int year = Integer.parseInt(m.group(5));
            final int doy = Integer.parseInt(m.group(6));
            final AbsoluteDate start = new AbsoluteDate(year, 1, 1, Integer.parseInt(m.group(7)),
                    Integer.parseInt(m.group(8)), 0.0, TimeScalesFactory.getGPS()).shiftedBy((doy - 1) * 86400.0);
            final double span = Integer.parseInt(m.group(9)) * unit(m.group(10).toUpperCase(Locale.US).charAt(0));
            final String fmt = m.group(12).toUpperCase(Locale.US);
            final Content content = "ORB".equals(fmt) ? Content.ORBIT : "CLK".equals(fmt) ? Content.CLOCK : Content.UNKNOWN;
            return new IgsProductName(cls, m.group(1).toUpperCase(Locale.US), start, span, content, cls != null);
        }
        m = SHORT.matcher(n);
        if (m.find()) {
            final String ac = m.group(1).toLowerCase(Locale.US);
            final int week = Integer.parseInt(m.group(2));
            final int dow = Integer.parseInt(m.group(3));
            final String ext = m.group(5).toLowerCase(Locale.US);
            final Content content = ext.startsWith("clk") ? Content.CLOCK : Content.ORBIT;
            AbsoluteDate dayStart = GPS_EPOCH.shiftedBy((week * 7.0 + dow) * 86400.0);
            if ("igu".equals(ac)) {
                final int hh = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
                // legacy igu_HH: observed HH-24h .. HH, predicted HH .. HH+24h
                return new IgsProductName(ProductClass.ULTRA_RAPID, "IGS",
                        dayStart.shiftedBy(hh * 3600.0 - 86400.0), 2 * 86400.0, content, true);
            }
            final ProductClass cls = "igr".equals(ac) ? ProductClass.RAPID : ProductClass.FINAL;
            final String provider = "igr".equals(ac) || "igs".equals(ac) ? "IGS" : ac.toUpperCase(Locale.US);
            return new IgsProductName(cls, provider, dayStart, 86400.0, content, true);
        }
        return new IgsProductName(null, "", null, 0.0, Content.UNKNOWN, false);
    }

    private static double unit(final char c) {
        switch (c) {
            case 'M':
                return 60.0;
            case 'H':
                return 3600.0;
            case 'W':
                return 7 * 86400.0;
            case 'D':
            default:
                return 86400.0;
        }
    }

    public boolean recognised() {
        return recognised;
    }

    public ProductClass productClass() {
        return productClass;
    }

    public String provider() {
        return provider;
    }

    public AbsoluteDate start() {
        return start;
    }

    public double spanSeconds() {
        return spanSeconds;
    }

    public Content content() {
        return content;
    }

    /** End of the observed (non-predicted) segment; equals the end of span except for ultra-rapid. */
    public AbsoluteDate observedEnd() {
        if (start == null) {
            return null;
        }
        return productClass == ProductClass.ULTRA_RAPID ? start.shiftedBy(86400.0) : start.shiftedBy(spanSeconds);
    }

    /** Nominal publication time under the documented latency assumptions (unverified). */
    public AbsoluteDate nominalIssueTime() {
        if (start == null || productClass == null) {
            return null;
        }
        switch (productClass) {
            case ULTRA_RAPID:
                return observedEnd().shiftedBy(ULTRA_LATENCY_AFTER_OBSERVED_END_S);
            case RAPID:
                return start.shiftedBy(spanSeconds + RAPID_LATENCY_AFTER_END_S);
            case FINAL:
            default:
                return start.shiftedBy(spanSeconds + FINAL_LATENCY_AFTER_END_S);
        }
    }
}
