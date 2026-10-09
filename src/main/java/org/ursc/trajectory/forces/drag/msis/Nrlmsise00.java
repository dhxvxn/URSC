package org.ursc.trajectory.forces.drag.msis;

/**
 * NRLMSISE-00 empirical neutral-atmosphere model (Picone, Hedin &amp; Drob, 2002).
 *
 * <p>Faithful Java port of the public-domain reference C implementation
 * (D. Brodowski, release 20041227). The numerical algorithm and coefficients are
 * reproduced exactly; only language mechanics differ (C pointer out-parameters
 * become small array holders, and the C file-scope working variables become
 * instance fields so a single {@code Nrlmsise00} instance is a self-contained,
 * reusable calculator — not thread-safe; use one per thread).</p>
 *
 * <p>Entry points mirror the reference: {@link #gtd7} (mass density excludes
 * anomalous oxygen) and {@link #gtd7d} (effective total mass density for drag,
 * including anomalous oxygen — the one to use for satellite drag).</p>
 */
public final class Nrlmsise00 {

    /** Inputs for one evaluation (see the reference documentation for details). */
    public static final class Input {
        public int year;        // currently ignored by the model
        public int doy;         // day of year
        public double sec;      // seconds in day (UT)
        public double alt;      // altitude (km)
        public double gLat;     // geodetic latitude (deg)
        public double gLong;    // geodetic longitude (deg)
        public double lst;      // local apparent solar time (hours)
        public double f107A;    // 81-day average F10.7 (centred on doy)
        public double f107;     // daily F10.7 for previous day
        public double ap;       // daily magnetic index
        public double[] apArray; // optional 7-element ap history (for switch 9 == -1)
    }

    /** Switch configuration. switches[0..23]; standard: 0 for index 0, 1 for 1..23. */
    public static final class Flags {
        public final int[] switches = new int[24];
        public final double[] sw = new double[24];
        public final double[] swc = new double[24];

        /** Build with the standard switch setting (index 0 off, 1..23 on). */
        public static Flags standard() {
            final Flags f = new Flags();
            for (int i = 1; i < 24; i++) {
                f.switches[i] = 1;
            }
            return f;
        }
    }

    /** Model outputs: d[0..8] number/mass densities, t[0..1] temperatures. */
    public static final class Output {
        public final double[] d = new double[9];
        public final double[] t = new double[2];
    }

    // ---- shared working variables (file-scope statics in the C reference) ----
    private double gsurf;
    private double re;
    private double dd;
    private double dm04, dm16, dm28, dm32, dm40, dm01, dm14;
    private final double[] mesoTn1 = new double[5];
    private final double[] mesoTn2 = new double[4];
    private final double[] mesoTn3 = new double[5];
    private final double[] mesoTgn1 = new double[2];
    private final double[] mesoTgn2 = new double[2];
    private final double[] mesoTgn3 = new double[2];
    private double dfa;
    private final double[][] plg = new double[4][9];
    private double ctloc, stloc, c2tloc, s2tloc, s3tloc, c3tloc;
    private double apdf;
    private final double[] apt = new double[4];

    public void tselec(final Flags flags) {
        for (int i = 0; i < 24; i++) {
            if (i != 9) {
                flags.sw[i] = (flags.switches[i] == 1) ? 1 : 0;
                flags.swc[i] = (flags.switches[i] > 0) ? 1 : 0;
            } else {
                flags.sw[i] = flags.switches[i];
                flags.swc[i] = flags.switches[i];
            }
        }
    }

    private void glatf(final double lat) {
        final double dgtr = 1.74533E-2;
        final double c2 = Math.cos(2.0 * dgtr * lat);
        gsurf = 980.616 * (1.0 - 0.0026373 * c2);
        re = 2.0 * gsurf / (3.085462E-6 + 2.27E-9 * c2) * 1.0E-5;
    }

    private static double ccor(final double alt, final double r, final double h1, final double zh) {
        double e = (alt - zh) / h1;
        if (e > 70) {
            return Math.exp(0);
        }
        if (e < -70) {
            return Math.exp(r);
        }
        final double ex = Math.exp(e);
        e = r / (1.0 + ex);
        return Math.exp(e);
    }

    private static double ccor2(final double alt, final double r, final double h1,
                                final double zh, final double h2) {
        final double e1 = (alt - zh) / h1;
        final double e2 = (alt - zh) / h2;
        if ((e1 > 70) || (e2 > 70)) {
            return Math.exp(0);
        }
        if ((e1 < -70) && (e2 < -70)) {
            return Math.exp(r);
        }
        final double ex1 = Math.exp(e1);
        final double ex2 = Math.exp(e2);
        final double ccor2v = r / (1.0 + 0.5 * (ex1 + ex2));
        return Math.exp(ccor2v);
    }

    private double scalh(final double alt, final double xm, final double temp) {
        final double rgas = 831.4;
        double g = gsurf / Math.pow(1.0 + alt / re, 2.0);
        g = rgas * temp / (g * xm);
        return g;
    }

    private static double dnet(double dd, final double dm, final double zhm,
                               final double xmm, final double xm) {
        double a = zhm / (xmm - xm);
        if (!((dm > 0) && (dd > 0))) {
            if ((dd == 0) && (dm == 0)) {
                dd = 1;
            }
            if (dm == 0) {
                return dd;
            }
            if (dd == 0) {
                return dm;
            }
        }
        final double ylog = a * Math.log(dm / dd);
        if (ylog < -10) {
            return dd;
        }
        if (ylog > 10) {
            return dm;
        }
        a = dd * Math.pow(1.0 + Math.exp(ylog), 1.0 / a);
        return a;
    }

    private double zeta(final double zz, final double zl) {
        return (zz - zl) * (re + zl) / (re + zz);
    }

    private static void splini(final double[] xa, final double[] ya, final double[] y2a,
                               final int n, final double x, final double[] yOut) {
        double yi = 0;
        int klo = 0;
        int khi = 1;
        while ((x > xa[klo]) && (khi < n)) {
            double xx = x;
            if (khi < (n - 1)) {
                xx = (x < xa[khi]) ? x : xa[khi];
            }
            final double h = xa[khi] - xa[klo];
            final double a = (xa[khi] - xx) / h;
            final double b = (xx - xa[klo]) / h;
            final double a2 = a * a;
            final double b2 = b * b;
            yi += ((1.0 - a2) * ya[klo] / 2.0 + b2 * ya[khi] / 2.0
                    + ((-(1.0 + a2 * a2) / 4.0 + a2 / 2.0) * y2a[klo]
                    + (b2 * b2 / 4.0 - b2 / 2.0) * y2a[khi]) * h * h / 6.0) * h;
            klo++;
            khi++;
        }
        yOut[0] = yi;
    }

    private static void splint(final double[] xa, final double[] ya, final double[] y2a,
                               final int n, final double x, final double[] yOut) {
        int klo = 0;
        int khi = n - 1;
        while ((khi - klo) > 1) {
            final int k = (khi + klo) / 2;
            if (xa[k] > x) {
                khi = k;
            } else {
                klo = k;
            }
        }
        final double h = xa[khi] - xa[klo];
        final double a = (xa[khi] - x) / h;
        final double b = (x - xa[klo]) / h;
        yOut[0] = a * ya[klo] + b * ya[khi]
                + ((a * a * a - a) * y2a[klo] + (b * b * b - b) * y2a[khi]) * h * h / 6.0;
    }

    private static void spline(final double[] x, final double[] y, final int n,
                               final double yp1, final double ypn, final double[] y2) {
        final double[] u = new double[n];
        if (yp1 > 0.99E30) {
            y2[0] = 0;
            u[0] = 0;
        } else {
            y2[0] = -0.5;
            u[0] = (3.0 / (x[1] - x[0])) * ((y[1] - y[0]) / (x[1] - x[0]) - yp1);
        }
        for (int i = 1; i < (n - 1); i++) {
            final double sig = (x[i] - x[i - 1]) / (x[i + 1] - x[i - 1]);
            final double p = sig * y2[i - 1] + 2.0;
            y2[i] = (sig - 1.0) / p;
            u[i] = (6.0 * ((y[i + 1] - y[i]) / (x[i + 1] - x[i]) - (y[i] - y[i - 1])
                    / (x[i] - x[i - 1])) / (x[i + 1] - x[i - 1]) - sig * u[i - 1]) / p;
        }
        final double qn;
        final double un;
        if (ypn > 0.99E30) {
            qn = 0;
            un = 0;
        } else {
            qn = 0.5;
            un = (3.0 / (x[n - 1] - x[n - 2])) * (ypn - (y[n - 1] - y[n - 2]) / (x[n - 1] - x[n - 2]));
        }
        y2[n - 1] = (un - qn * u[n - 2]) / (qn * y2[n - 2] + 1.0);
        for (int k = n - 2; k >= 0; k--) {
            y2[k] = y2[k] * y2[k + 1] + u[k];
        }
    }

    private double densm(final double alt, double densmTmp, final double xm, final double[] tz,
                         final int mn3, final double[] zn3, final double[] tn3, final double[] tgn3,
                         final int mn2, final double[] zn2, final double[] tn2, final double[] tgn2) {
        final double[] xs = new double[10];
        final double[] ys = new double[10];
        final double[] y2out = new double[10];
        final double rgas = 831.4;
        final double[] yHolder = new double[1];
        final double[] yiHolder = new double[1];

        if (alt > zn2[0]) {
            return (xm == 0.0) ? tz[0] : densmTmp;
        }

        // STRATOSPHERE / MESOSPHERE TEMPERATURE
        double z = (alt > zn2[mn2 - 1]) ? alt : zn2[mn2 - 1];
        int mn = mn2;
        double z1 = zn2[0];
        double z2 = zn2[mn - 1];
        double t1 = tn2[0];
        double t2 = tn2[mn - 1];
        double zg = zeta(z, z1);
        double zgdif = zeta(z2, z1);

        for (int k = 0; k < mn; k++) {
            xs[k] = zeta(zn2[k], z1) / zgdif;
            ys[k] = 1.0 / tn2[k];
        }
        double yd1 = -tgn2[0] / (t1 * t1) * zgdif;
        double yd2 = -tgn2[1] / (t2 * t2) * zgdif * Math.pow((re + z2) / (re + z1), 2.0);

        spline(xs, ys, mn, yd1, yd2, y2out);
        double x = zg / zgdif;
        splint(xs, ys, y2out, mn, x, yHolder);
        tz[0] = 1.0 / yHolder[0];
        if (xm != 0.0) {
            final double glb = gsurf / Math.pow(1.0 + z1 / re, 2.0);
            final double gamm = xm * glb * zgdif / rgas;
            splini(xs, ys, y2out, mn, x, yiHolder);
            double expl = gamm * yiHolder[0];
            if (expl > 50.0) {
                expl = 50.0;
            }
            densmTmp = densmTmp * (t1 / tz[0]) * Math.exp(-expl);
        }

        if (alt > zn3[0]) {
            return (xm == 0.0) ? tz[0] : densmTmp;
        }

        // TROPOSPHERE / STRATOSPHERE TEMPERATURE
        z = alt;
        mn = mn3;
        z1 = zn3[0];
        z2 = zn3[mn - 1];
        t1 = tn3[0];
        t2 = tn3[mn - 1];
        zg = zeta(z, z1);
        zgdif = zeta(z2, z1);

        for (int k = 0; k < mn; k++) {
            xs[k] = zeta(zn3[k], z1) / zgdif;
            ys[k] = 1.0 / tn3[k];
        }
        yd1 = -tgn3[0] / (t1 * t1) * zgdif;
        yd2 = -tgn3[1] / (t2 * t2) * zgdif * Math.pow((re + z2) / (re + z1), 2.0);

        spline(xs, ys, mn, yd1, yd2, y2out);
        x = zg / zgdif;
        splint(xs, ys, y2out, mn, x, yHolder);
        tz[0] = 1.0 / yHolder[0];
        if (xm != 0.0) {
            final double glb = gsurf / Math.pow(1.0 + z1 / re, 2.0);
            final double gamm = xm * glb * zgdif / rgas;
            splini(xs, ys, y2out, mn, x, yiHolder);
            double expl = gamm * yiHolder[0];
            if (expl > 50.0) {
                expl = 50.0;
            }
            densmTmp = densmTmp * (t1 / tz[0]) * Math.exp(-expl);
        }
        return (xm == 0.0) ? tz[0] : densmTmp;
    }

    private double densu(final double alt, final double dlb, final double tinf, final double tlb,
                         final double xm, final double alpha, final double[] tz, final double zlb,
                         final double s2, final int mn1, final double[] zn1, final double[] tn1,
                         final double[] tgn1) {
        final double rgas = 831.4;
        double densuTemp = 1.0;
        final double[] xs = new double[5];
        final double[] ys = new double[5];
        final double[] y2out = new double[5];
        final double[] yHolder = new double[1];
        final double[] yiHolder = new double[1];

        final double za = zn1[0];
        double z = (alt > za) ? alt : za;
        final double zg2 = zeta(z, zlb);

        final double tt = tinf - (tinf - tlb) * Math.exp(-s2 * zg2);
        final double ta = tt;
        tz[0] = tt;
        densuTemp = tz[0];

        double z1 = 0;
        double zgdif = 0;
        double t1 = 0;
        int mn = 0;
        double x = 0;
        if (alt < za) {
            final double dta = (tinf - ta) * s2 * Math.pow((re + zlb) / (re + za), 2.0);
            tgn1[0] = dta;
            tn1[0] = ta;
            z = (alt > zn1[mn1 - 1]) ? alt : zn1[mn1 - 1];
            mn = mn1;
            z1 = zn1[0];
            final double z2 = zn1[mn - 1];
            t1 = tn1[0];
            final double t2 = tn1[mn - 1];
            final double zg = zeta(z, z1);
            zgdif = zeta(z2, z1);
            for (int k = 0; k < mn; k++) {
                xs[k] = zeta(zn1[k], z1) / zgdif;
                ys[k] = 1.0 / tn1[k];
            }
            final double yd1 = -tgn1[0] / (t1 * t1) * zgdif;
            final double yd2 = -tgn1[1] / (t2 * t2) * zgdif * Math.pow((re + z2) / (re + z1), 2.0);
            spline(xs, ys, mn, yd1, yd2, y2out);
            x = zg / zgdif;
            splint(xs, ys, y2out, mn, x, yHolder);
            tz[0] = 1.0 / yHolder[0];
            densuTemp = tz[0];
        }
        if (xm == 0) {
            return densuTemp;
        }

        double glb = gsurf / Math.pow(1.0 + zlb / re, 2.0);
        final double gamma = xm * glb / (s2 * rgas * tinf);
        double expl = Math.exp(-s2 * gamma * zg2);
        if (expl > 50.0) {
            expl = 50.0;
        }
        if (tt <= 0) {
            expl = 50.0;
        }

        final double densa = dlb * Math.pow(tlb / tt, 1.0 + alpha + gamma) * expl;
        densuTemp = densa;
        if (alt >= za) {
            return densuTemp;
        }

        glb = gsurf / Math.pow(1.0 + z1 / re, 2.0);
        final double gamm = xm * glb * zgdif / rgas;
        splini(xs, ys, y2out, mn, x, yiHolder);
        expl = gamm * yiHolder[0];
        if (expl > 50.0) {
            expl = 50.0;
        }
        if (tz[0] <= 0) {
            expl = 50.0;
        }
        densuTemp = densuTemp * Math.pow(t1 / tz[0], 1.0 + alpha) * Math.exp(-expl);
        return densuTemp;
    }

    // 3hr magnetic activity functions (Eq. A24)
    private static double g0(final double a, final double[] p) {
        return a - 4.0 + (p[25] - 1.0) * (a - 4.0
                + (Math.exp(-Math.sqrt(p[24] * p[24]) * (a - 4.0)) - 1.0) / Math.sqrt(p[24] * p[24]));
    }

    private static double sumex(final double ex) {
        return 1.0 + (1.0 - Math.pow(ex, 19.0)) / (1.0 - ex) * Math.pow(ex, 0.5);
    }

    private static double sg0(final double ex, final double[] p, final double[] ap) {
        return (g0(ap[1], p) + (g0(ap[2], p) * ex + g0(ap[3], p) * ex * ex
                + g0(ap[4], p) * Math.pow(ex, 3.0)
                + (g0(ap[5], p) * Math.pow(ex, 4.0) + g0(ap[6], p) * Math.pow(ex, 12.0))
                * (1.0 - Math.pow(ex, 8.0)) / (1.0 - ex))) / sumex(ex);
    }

    private double globe7(final double[] p, final Input input, final Flags flags) {
        final double[] t = new double[15];
        final double sr = 7.2722E-5;
        final double dgtr = 1.74533E-2;
        final double dr = 1.72142E-2;
        final double hr = 0.2618;
        final double tloc = input.lst;

        for (int j = 0; j < 14; j++) {
            t[j] = 0;
        }

        final double c = Math.sin(input.gLat * dgtr);
        final double s = Math.cos(input.gLat * dgtr);
        final double c2 = c * c;
        final double c4 = c2 * c2;
        final double s2 = s * s;

        plg[0][1] = c;
        plg[0][2] = 0.5 * (3.0 * c2 - 1.0);
        plg[0][3] = 0.5 * (5.0 * c * c2 - 3.0 * c);
        plg[0][4] = (35.0 * c4 - 30.0 * c2 + 3.0) / 8.0;
        plg[0][5] = (63.0 * c2 * c2 * c - 70.0 * c2 * c + 15.0 * c) / 8.0;
        plg[0][6] = (11.0 * c * plg[0][5] - 5.0 * plg[0][4]) / 6.0;
        plg[1][1] = s;
        plg[1][2] = 3.0 * c * s;
        plg[1][3] = 1.5 * (5.0 * c2 - 1.0) * s;
        plg[1][4] = 2.5 * (7.0 * c2 * c - 3.0 * c) * s;
        plg[1][5] = 1.875 * (21.0 * c4 - 14.0 * c2 + 1.0) * s;
        plg[1][6] = (11.0 * c * plg[1][5] - 6.0 * plg[1][4]) / 5.0;
        plg[2][2] = 3.0 * s2;
        plg[2][3] = 15.0 * s2 * c;
        plg[2][4] = 7.5 * (7.0 * c2 - 1.0) * s2;
        plg[2][5] = 3.0 * c * plg[2][4] - 2.0 * plg[2][3];
        plg[2][6] = (11.0 * c * plg[2][5] - 7.0 * plg[2][4]) / 4.0;
        plg[2][7] = (13.0 * c * plg[2][6] - 8.0 * plg[2][5]) / 5.0;
        plg[3][3] = 15.0 * s2 * s;
        plg[3][4] = 105.0 * s2 * s * c;
        plg[3][5] = (9.0 * c * plg[3][4] - 7.0 * plg[3][3]) / 2.0;
        plg[3][6] = (11.0 * c * plg[3][5] - 8.0 * plg[3][4]) / 3.0;

        if (!(((flags.sw[7] == 0) && (flags.sw[8] == 0)) && (flags.sw[14] == 0))) {
            stloc = Math.sin(hr * tloc);
            ctloc = Math.cos(hr * tloc);
            s2tloc = Math.sin(2.0 * hr * tloc);
            c2tloc = Math.cos(2.0 * hr * tloc);
            s3tloc = Math.sin(3.0 * hr * tloc);
            c3tloc = Math.cos(3.0 * hr * tloc);
        }

        final double cd32 = Math.cos(dr * (input.doy - p[31]));
        final double cd18 = Math.cos(2.0 * dr * (input.doy - p[17]));
        final double cd14 = Math.cos(dr * (input.doy - p[13]));
        final double cd39 = Math.cos(2.0 * dr * (input.doy - p[38]));

        // F10.7 EFFECT
        final double df = input.f107 - input.f107A;
        dfa = input.f107A - 150.0;
        t[0] = p[19] * df * (1.0 + p[59] * dfa) + p[20] * df * df + p[21] * dfa + p[29] * Math.pow(dfa, 2.0);
        final double f1 = 1.0 + (p[47] * dfa + p[19] * df + p[20] * df * df) * flags.swc[1];
        final double f2 = 1.0 + (p[49] * dfa + p[19] * df + p[20] * df * df) * flags.swc[1];

        // TIME INDEPENDENT
        t[1] = (p[1] * plg[0][2] + p[2] * plg[0][4] + p[22] * plg[0][6])
                + (p[14] * plg[0][2]) * dfa * flags.swc[1] + p[26] * plg[0][1];

        // SYMMETRICAL ANNUAL
        t[2] = p[18] * cd32;
        // SYMMETRICAL SEMIANNUAL
        t[3] = (p[15] + p[16] * plg[0][2]) * cd18;
        // ASYMMETRICAL ANNUAL
        t[4] = f1 * (p[9] * plg[0][1] + p[10] * plg[0][3]) * cd14;
        // ASYMMETRICAL SEMIANNUAL
        t[5] = p[37] * plg[0][1] * cd39;

        // DIURNAL
        if (flags.sw[7] != 0) {
            final double t71 = (p[11] * plg[1][2]) * cd14 * flags.swc[5];
            final double t72 = (p[12] * plg[1][2]) * cd14 * flags.swc[5];
            t[6] = f2 * ((p[3] * plg[1][1] + p[4] * plg[1][3] + p[27] * plg[1][5] + t71) * ctloc
                    + (p[6] * plg[1][1] + p[7] * plg[1][3] + p[28] * plg[1][5] + t72) * stloc);
        }

        // SEMIDIURNAL
        if (flags.sw[8] != 0) {
            final double t81 = (p[23] * plg[2][3] + p[35] * plg[2][5]) * cd14 * flags.swc[5];
            final double t82 = (p[33] * plg[2][3] + p[36] * plg[2][5]) * cd14 * flags.swc[5];
            t[7] = f2 * ((p[5] * plg[2][2] + p[41] * plg[2][4] + t81) * c2tloc
                    + (p[8] * plg[2][2] + p[42] * plg[2][4] + t82) * s2tloc);
        }

        // TERDIURNAL
        if (flags.sw[14] != 0) {
            t[13] = f2 * ((p[39] * plg[3][3] + (p[93] * plg[3][4] + p[46] * plg[3][6]) * cd14 * flags.swc[5]) * s3tloc
                    + (p[40] * plg[3][3] + (p[94] * plg[3][4] + p[48] * plg[3][6]) * cd14 * flags.swc[5]) * c3tloc);
        }

        // magnetic activity based on daily ap
        if (flags.sw[9] == -1) {
            final double[] ap = input.apArray;
            if (p[51] != 0) {
                double exp1 = Math.exp(-10800.0 * Math.sqrt(p[51] * p[51])
                        / (1.0 + p[138] * (45.0 - Math.sqrt(input.gLat * input.gLat))));
                if (exp1 > 0.99999) {
                    exp1 = 0.99999;
                }
                if (p[24] < 1.0E-4) {
                    p[24] = 1.0E-4;
                }
                apt[0] = sg0(exp1, p, ap);
                if (flags.sw[9] != 0) {
                    t[8] = apt[0] * (p[50] + p[96] * plg[0][2] + p[54] * plg[0][4]
                            + (p[125] * plg[0][1] + p[126] * plg[0][3] + p[127] * plg[0][5]) * cd14 * flags.swc[5]
                            + (p[128] * plg[1][1] + p[129] * plg[1][3] + p[130] * plg[1][5]) * flags.swc[7]
                            * Math.cos(hr * (tloc - p[131])));
                }
            }
        } else {
            final double apd = input.ap - 4.0;
            double p44 = p[43];
            final double p45 = p[44];
            if (p44 < 0) {
                p44 = 1.0E-5;
            }
            apdf = apd + (p45 - 1.0) * (apd + (Math.exp(-p44 * apd) - 1.0) / p44);
            if (flags.sw[9] != 0) {
                t[8] = apdf * (p[32] + p[45] * plg[0][2] + p[34] * plg[0][4]
                        + (p[100] * plg[0][1] + p[101] * plg[0][3] + p[102] * plg[0][5]) * cd14 * flags.swc[5]
                        + (p[121] * plg[1][1] + p[122] * plg[1][3] + p[123] * plg[1][5]) * flags.swc[7]
                        * Math.cos(hr * (tloc - p[124])));
            }
        }

        if ((flags.sw[10] != 0) && (input.gLong > -1000.0)) {
            // longitudinal
            if (flags.sw[11] != 0) {
                t[10] = (1.0 + p[80] * dfa * flags.swc[1])
                        * ((p[64] * plg[1][2] + p[65] * plg[1][4] + p[66] * plg[1][6]
                        + p[103] * plg[1][1] + p[104] * plg[1][3] + p[105] * plg[1][5]
                        + flags.swc[5] * (p[109] * plg[1][1] + p[110] * plg[1][3] + p[111] * plg[1][5]) * cd14)
                        * Math.cos(dgtr * input.gLong)
                        + (p[90] * plg[1][2] + p[91] * plg[1][4] + p[92] * plg[1][6]
                        + p[106] * plg[1][1] + p[107] * plg[1][3] + p[108] * plg[1][5]
                        + flags.swc[5] * (p[112] * plg[1][1] + p[113] * plg[1][3] + p[114] * plg[1][5]) * cd14)
                        * Math.sin(dgtr * input.gLong));
            }

            // ut and mixed ut, longitude
            if (flags.sw[12] != 0) {
                t[11] = (1.0 + p[95] * plg[0][1]) * (1.0 + p[81] * dfa * flags.swc[1])
                        * (1.0 + p[119] * plg[0][1] * flags.swc[5] * cd14)
                        * ((p[68] * plg[0][1] + p[69] * plg[0][3] + p[70] * plg[0][5])
                        * Math.cos(sr * (input.sec - p[71])));
                t[11] += flags.swc[11]
                        * (p[76] * plg[2][3] + p[77] * plg[2][5] + p[78] * plg[2][7])
                        * Math.cos(sr * (input.sec - p[79]) + 2.0 * dgtr * input.gLong)
                        * (1.0 + p[137] * dfa * flags.swc[1]);
            }

            // ut, longitude magnetic activity
            if (flags.sw[13] != 0) {
                if (flags.sw[9] == -1) {
                    if (p[51] != 0) {
                        t[12] = apt[0] * flags.swc[11] * (1.0 + p[132] * plg[0][1])
                                * ((p[52] * plg[1][2] + p[98] * plg[1][4] + p[67] * plg[1][6])
                                * Math.cos(dgtr * (input.gLong - p[97])))
                                + apt[0] * flags.swc[11] * flags.swc[5]
                                * (p[133] * plg[1][1] + p[134] * plg[1][3] + p[135] * plg[1][5])
                                * cd14 * Math.cos(dgtr * (input.gLong - p[136]))
                                + apt[0] * flags.swc[12]
                                * (p[55] * plg[0][1] + p[56] * plg[0][3] + p[57] * plg[0][5])
                                * Math.cos(sr * (input.sec - p[58]));
                    }
                } else {
                    t[12] = apdf * flags.swc[11] * (1.0 + p[120] * plg[0][1])
                            * ((p[60] * plg[1][2] + p[61] * plg[1][4] + p[62] * plg[1][6])
                            * Math.cos(dgtr * (input.gLong - p[63])))
                            + apdf * flags.swc[11] * flags.swc[5]
                            * (p[115] * plg[1][1] + p[116] * plg[1][3] + p[117] * plg[1][5])
                            * cd14 * Math.cos(dgtr * (input.gLong - p[118]))
                            + apdf * flags.swc[12]
                            * (p[83] * plg[0][1] + p[84] * plg[0][3] + p[85] * plg[0][5])
                            * Math.cos(sr * (input.sec - p[75]));
                }
            }
        }

        double tinf = p[30];
        for (int i = 0; i < 14; i++) {
            tinf += Math.abs(flags.sw[i + 1]) * t[i];
        }
        return tinf;
    }

    private double glob7s(final double[] p, final Input input, final Flags flags) {
        final double pset = 2.0;
        final double[] t = new double[14];
        final double dr = 1.72142E-2;
        final double dgtr = 1.74533E-2;

        if (p[99] == 0) {
            p[99] = pset;
        }
        for (int j = 0; j < 14; j++) {
            t[j] = 0.0;
        }
        final double cd32 = Math.cos(dr * (input.doy - p[31]));
        final double cd18 = Math.cos(2.0 * dr * (input.doy - p[17]));
        final double cd14 = Math.cos(dr * (input.doy - p[13]));
        final double cd39 = Math.cos(2.0 * dr * (input.doy - p[38]));

        t[0] = p[21] * dfa;
        t[1] = p[1] * plg[0][2] + p[2] * plg[0][4] + p[22] * plg[0][6] + p[26] * plg[0][1]
                + p[14] * plg[0][3] + p[59] * plg[0][5];
        t[2] = (p[18] + p[47] * plg[0][2] + p[29] * plg[0][4]) * cd32;
        t[3] = (p[15] + p[16] * plg[0][2] + p[30] * plg[0][4]) * cd18;
        t[4] = (p[9] * plg[0][1] + p[10] * plg[0][3] + p[20] * plg[0][5]) * cd14;
        t[5] = (p[37] * plg[0][1]) * cd39;

        if (flags.sw[7] != 0) {
            final double t71 = p[11] * plg[1][2] * cd14 * flags.swc[5];
            final double t72 = p[12] * plg[1][2] * cd14 * flags.swc[5];
            t[6] = ((p[3] * plg[1][1] + p[4] * plg[1][3] + t71) * ctloc
                    + (p[6] * plg[1][1] + p[7] * plg[1][3] + t72) * stloc);
        }

        if (flags.sw[8] != 0) {
            final double t81 = (p[23] * plg[2][3] + p[35] * plg[2][5]) * cd14 * flags.swc[5];
            final double t82 = (p[33] * plg[2][3] + p[36] * plg[2][5]) * cd14 * flags.swc[5];
            t[7] = ((p[5] * plg[2][2] + p[41] * plg[2][4] + t81) * c2tloc
                    + (p[8] * plg[2][2] + p[42] * plg[2][4] + t82) * s2tloc);
        }

        if (flags.sw[14] != 0) {
            t[13] = p[39] * plg[3][3] * s3tloc + p[40] * plg[3][3] * c3tloc;
        }

        if (flags.sw[9] != 0) {
            if (flags.sw[9] == 1) {
                t[8] = apdf * (p[32] + p[45] * plg[0][2] * flags.swc[2]);
            }
            if (flags.sw[9] == -1) {
                t[8] = (p[50] * apt[0] + p[96] * plg[0][2] * apt[0] * flags.swc[2]);
            }
        }

        if (!((flags.sw[10] == 0) || (flags.sw[11] == 0) || (input.gLong <= -1000.0))) {
            t[10] = (1.0 + plg[0][1] * (p[80] * flags.swc[5] * Math.cos(dr * (input.doy - p[81]))
                    + p[85] * flags.swc[6] * Math.cos(2.0 * dr * (input.doy - p[86])))
                    + p[83] * flags.swc[3] * Math.cos(dr * (input.doy - p[84]))
                    + p[87] * flags.swc[4] * Math.cos(2.0 * dr * (input.doy - p[88])))
                    * ((p[64] * plg[1][2] + p[65] * plg[1][4] + p[66] * plg[1][6]
                    + p[74] * plg[1][1] + p[75] * plg[1][3] + p[76] * plg[1][5]) * Math.cos(dgtr * input.gLong)
                    + (p[90] * plg[1][2] + p[91] * plg[1][4] + p[92] * plg[1][6]
                    + p[77] * plg[1][1] + p[78] * plg[1][3] + p[79] * plg[1][5]) * Math.sin(dgtr * input.gLong));
        }
        double tt = 0;
        for (int i = 0; i < 14; i++) {
            tt += Math.abs(flags.sw[i + 1]) * t[i];
        }
        return tt;
    }

    /** Neutral atmosphere model from the surface to the lower exosphere. */
    public void gtd7(final Input input, final Flags flags, final Output output) {
        final int mn3 = 5;
        final double[] zn3 = {32.5, 20.0, 15.0, 10.0, 0.0};
        final int mn2 = 4;
        final double[] zn2 = {72.5, 55.0, 45.0, 32.5};
        final double zmix = 62.5;
        final double[] tz = new double[1];

        tselec(flags);

        double xlat = input.gLat;
        if (flags.sw[2] == 0) {
            xlat = 45.0;
        }
        glatf(xlat);

        final double xmm = Nrlmsise00Data.pdm[2][4];

        final double altt = (input.alt > zn2[0]) ? input.alt : zn2[0];

        final double tmp = input.alt;
        input.alt = altt;
        final Output soutput = new Output();
        gts7(input, flags, soutput);
        input.alt = tmp;
        double dm28m;
        if (flags.sw[0] != 0) {
            dm28m = dm28 * 1.0E6;
        } else {
            dm28m = dm28;
        }
        output.t[0] = soutput.t[0];
        output.t[1] = soutput.t[1];
        if (input.alt >= zn2[0]) {
            System.arraycopy(soutput.d, 0, output.d, 0, 9);
            return;
        }

        // LOWER MESOSPHERE / UPPER STRATOSPHERE
        mesoTgn2[0] = mesoTgn1[1];
        mesoTn2[0] = mesoTn1[4];
        mesoTn2[1] = Nrlmsise00Data.pma[0][0] * Nrlmsise00Data.pavgm[0]
                / (1.0 - flags.sw[20] * glob7s(Nrlmsise00Data.pma[0], input, flags));
        mesoTn2[2] = Nrlmsise00Data.pma[1][0] * Nrlmsise00Data.pavgm[1]
                / (1.0 - flags.sw[20] * glob7s(Nrlmsise00Data.pma[1], input, flags));
        mesoTn2[3] = Nrlmsise00Data.pma[2][0] * Nrlmsise00Data.pavgm[2]
                / (1.0 - flags.sw[20] * flags.sw[22] * glob7s(Nrlmsise00Data.pma[2], input, flags));
        mesoTgn2[1] = Nrlmsise00Data.pavgm[8] * Nrlmsise00Data.pma[9][0]
                * (1.0 + flags.sw[20] * flags.sw[22] * glob7s(Nrlmsise00Data.pma[9], input, flags))
                * mesoTn2[3] * mesoTn2[3]
                / Math.pow(Nrlmsise00Data.pma[2][0] * Nrlmsise00Data.pavgm[2], 2.0);
        mesoTn3[0] = mesoTn2[3];

        if (input.alt < zn3[0]) {
            mesoTgn3[0] = mesoTgn2[1];
            mesoTn3[1] = Nrlmsise00Data.pma[3][0] * Nrlmsise00Data.pavgm[3]
                    / (1.0 - flags.sw[22] * glob7s(Nrlmsise00Data.pma[3], input, flags));
            mesoTn3[2] = Nrlmsise00Data.pma[4][0] * Nrlmsise00Data.pavgm[4]
                    / (1.0 - flags.sw[22] * glob7s(Nrlmsise00Data.pma[4], input, flags));
            mesoTn3[3] = Nrlmsise00Data.pma[5][0] * Nrlmsise00Data.pavgm[5]
                    / (1.0 - flags.sw[22] * glob7s(Nrlmsise00Data.pma[5], input, flags));
            mesoTn3[4] = Nrlmsise00Data.pma[6][0] * Nrlmsise00Data.pavgm[6]
                    / (1.0 - flags.sw[22] * glob7s(Nrlmsise00Data.pma[6], input, flags));
            mesoTgn3[1] = Nrlmsise00Data.pma[7][0] * Nrlmsise00Data.pavgm[7]
                    * (1.0 + flags.sw[22] * glob7s(Nrlmsise00Data.pma[7], input, flags))
                    * mesoTn3[4] * mesoTn3[4]
                    / Math.pow(Nrlmsise00Data.pma[6][0] * Nrlmsise00Data.pavgm[6], 2.0);
        }

        // LINEAR TRANSITION TO FULL MIXING BELOW zn2[0]
        double dmc = 0;
        if (input.alt > zmix) {
            dmc = 1.0 - (zn2[0] - input.alt) / (zn2[0] - zmix);
        }
        final double dz28 = soutput.d[2];

        // N2 density
        double dmr = soutput.d[2] / dm28m - 1.0;
        output.d[2] = densm(input.alt, dm28m, xmm, tz, mn3, zn3, mesoTn3, mesoTgn3,
                mn2, zn2, mesoTn2, mesoTgn2);
        output.d[2] = output.d[2] * (1.0 + dmr * dmc);

        // He density
        dmr = soutput.d[0] / (dz28 * Nrlmsise00Data.pdm[0][1]) - 1.0;
        output.d[0] = output.d[2] * Nrlmsise00Data.pdm[0][1] * (1.0 + dmr * dmc);

        output.d[1] = 0;
        output.d[8] = 0;

        // O2 density
        dmr = soutput.d[3] / (dz28 * Nrlmsise00Data.pdm[3][1]) - 1.0;
        output.d[3] = output.d[2] * Nrlmsise00Data.pdm[3][1] * (1.0 + dmr * dmc);

        // Ar density
        dmr = soutput.d[4] / (dz28 * Nrlmsise00Data.pdm[4][1]) - 1.0;
        output.d[4] = output.d[2] * Nrlmsise00Data.pdm[4][1] * (1.0 + dmr * dmc);

        output.d[6] = 0;
        output.d[7] = 0;

        // Total mass density
        output.d[5] = 1.66E-24 * (4.0 * output.d[0] + 16.0 * output.d[1] + 28.0 * output.d[2]
                + 32.0 * output.d[3] + 40.0 * output.d[4] + output.d[6] + 14.0 * output.d[7]);

        if (flags.sw[0] != 0) {
            output.d[5] = output.d[5] / 1000;
        }

        // temperature at altitude
        dd = densm(input.alt, 1.0, 0, tz, mn3, zn3, mesoTn3, mesoTgn3,
                mn2, zn2, mesoTn2, mesoTgn2);
        output.t[1] = tz[0];
    }

    /** Like {@link #gtd7} but d[5] is the effective total mass density for drag. */
    public void gtd7d(final Input input, final Flags flags, final Output output) {
        gtd7(input, flags, output);
        output.d[5] = 1.66E-24 * (4.0 * output.d[0] + 16.0 * output.d[1] + 28.0 * output.d[2]
                + 32.0 * output.d[3] + 40.0 * output.d[4] + output.d[6] + 14.0 * output.d[7]
                + 16.0 * output.d[8]);
        if (flags.sw[0] != 0) {
            output.d[5] = output.d[5] / 1000;
        }
    }

    /** Thermospheric portion of NRLMSISE-00 (alt &gt; 72.5 km). */
    public void gts7(final Input input, final Flags flags, final Output output) {
        final double[] zn1 = {120.0, 110.0, 100.0, 90.0, 72.5};
        final int mn1 = 5;
        final double dgtr = 1.74533E-2;
        final double dr = 1.72142E-2;
        final double[] alpha = {-0.38, 0.0, 0.0, 0.0, 0.17, 0.0, -0.38, 0.0, 0.0};
        final double[] altl = {200.0, 300.0, 160.0, 250.0, 240.0, 450.0, 320.0, 450.0};
        // Single scratch holder for the densu/densm temperature out-parameter.
        // In the C reference, intermediate densu calls write &output->t[1] but the
        // value only matters after the final temperature call below, so one shared
        // holder reproduces the semantics; output.t[1] is assigned from it at the end.
        final double[] tz = new double[1];

        final double[] pt = Nrlmsise00Data.pt;
        final double[] ps = Nrlmsise00Data.ps;
        final double[][] pd = Nrlmsise00Data.pd;
        final double[][] ptl = Nrlmsise00Data.ptl;
        final double[][] pma = Nrlmsise00Data.pma;
        final double[] ptm = Nrlmsise00Data.ptm;
        final double[][] pdm = Nrlmsise00Data.pdm;
        final double[][] pdl = Nrlmsise00Data.pdl;

        final double za = pdl[1][15];
        zn1[0] = za;
        for (int j = 0; j < 9; j++) {
            output.d[j] = 0;
        }

        double tinf;
        if (input.alt > zn1[0]) {
            tinf = ptm[0] * pt[0] * (1.0 + flags.sw[16] * globe7(pt, input, flags));
        } else {
            tinf = ptm[0] * pt[0];
        }
        output.t[0] = tinf;

        double g0;
        if (input.alt > zn1[4]) {
            g0 = ptm[3] * ps[0] * (1.0 + flags.sw[19] * globe7(ps, input, flags));
        } else {
            g0 = ptm[3] * ps[0];
        }
        final double tlb = ptm[1] * (1.0 + flags.sw[17] * globe7(pd[3], input, flags)) * pd[3][0];
        final double s = g0 / (tinf - tlb);

        if (input.alt < 300.0) {
            mesoTn1[1] = ptm[6] * ptl[0][0] / (1.0 - flags.sw[18] * glob7s(ptl[0], input, flags));
            mesoTn1[2] = ptm[2] * ptl[1][0] / (1.0 - flags.sw[18] * glob7s(ptl[1], input, flags));
            mesoTn1[3] = ptm[7] * ptl[2][0] / (1.0 - flags.sw[18] * glob7s(ptl[2], input, flags));
            mesoTn1[4] = ptm[4] * ptl[3][0]
                    / (1.0 - flags.sw[18] * flags.sw[20] * glob7s(ptl[3], input, flags));
            mesoTgn1[1] = ptm[8] * pma[8][0]
                    * (1.0 + flags.sw[18] * flags.sw[20] * glob7s(pma[8], input, flags))
                    * mesoTn1[4] * mesoTn1[4] / Math.pow(ptm[4] * ptl[3][0], 2.0);
        } else {
            mesoTn1[1] = ptm[6] * ptl[0][0];
            mesoTn1[2] = ptm[2] * ptl[1][0];
            mesoTn1[3] = ptm[7] * ptl[2][0];
            mesoTn1[4] = ptm[4] * ptl[3][0];
            mesoTgn1[1] = ptm[8] * pma[8][0] * mesoTn1[4] * mesoTn1[4] / Math.pow(ptm[4] * ptl[3][0], 2.0);
        }

        final double g28 = flags.sw[21] * globe7(pd[2], input, flags);

        final double zhf = pdl[1][24]
                * (1.0 + flags.sw[5] * pdl[0][24] * Math.sin(dgtr * input.gLat) * Math.cos(dr * (input.doy - pt[13])));
        output.t[0] = tinf;
        final double xmm = pdm[2][4];
        final double z = input.alt;

        // N2 density
        final double db28 = pdm[2][0] * Math.exp(g28) * pd[2][0];
        output.d[2] = densu(z, db28, tinf, tlb, 28.0, alpha[2], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        double ddLocal = output.d[2];
        final double zh28 = pdm[2][2] * zhf;
        final double zhm28 = pdm[2][3] * pdl[1][5];
        final double xmd = 28.0 - xmm;
        final double b28 = densu(zh28, db28, tinf, tlb, xmd, alpha[2] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z <= altl[2])) {
            dm28 = densu(z, b28, tinf, tlb, xmm, alpha[2], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            output.d[2] = dnet(output.d[2], dm28, zhm28, xmm, 28.0);
        }

        // He density
        final double g4 = flags.sw[21] * globe7(pd[0], input, flags);
        final double db04 = pdm[0][0] * Math.exp(g4) * pd[0][0];
        output.d[0] = densu(z, db04, tinf, tlb, 4.0, alpha[0], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z < altl[0])) {
            final double zh04 = pdm[0][2];
            final double b04 = densu(zh04, db04, tinf, tlb, 4.0 - xmm, alpha[0] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            dm04 = densu(z, b04, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            final double zhm04 = zhm28;
            output.d[0] = dnet(output.d[0], dm04, zhm04, xmm, 4.0);
            final double rl = Math.log(b28 * pdm[0][1] / b04);
            final double zc04 = pdm[0][4] * pdl[1][0];
            final double hc04 = pdm[0][5] * pdl[1][1];
            output.d[0] = output.d[0] * ccor(z, rl, hc04, zc04);
        }

        // O density
        final double g16 = flags.sw[21] * globe7(pd[1], input, flags);
        final double db16 = pdm[1][0] * Math.exp(g16) * pd[1][0];
        output.d[1] = densu(z, db16, tinf, tlb, 16.0, alpha[1], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z <= altl[1])) {
            final double zh16 = pdm[1][2];
            final double b16 = densu(zh16, db16, tinf, tlb, 16.0 - xmm, alpha[1] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            dm16 = densu(z, b16, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            final double zhm16 = zhm28;
            output.d[1] = dnet(output.d[1], dm16, zhm16, xmm, 16.0);
            double rl = pdm[1][1] * pdl[1][16] * (1.0 + flags.sw[1] * pdl[0][23] * (input.f107A - 150.0));
            final double hc16 = pdm[1][5] * pdl[1][3];
            final double zc16 = pdm[1][4] * pdl[1][2];
            final double hc216 = pdm[1][5] * pdl[1][4];
            output.d[1] = output.d[1] * ccor2(z, rl, hc16, zc16, hc216);
            final double hcc16 = pdm[1][7] * pdl[1][13];
            final double zcc16 = pdm[1][6] * pdl[1][12];
            final double rc16 = pdm[1][3] * pdl[1][14];
            output.d[1] = output.d[1] * ccor(z, rc16, hcc16, zcc16);
        }

        // O2 density
        final double g32 = flags.sw[21] * globe7(pd[4], input, flags);
        final double db32 = pdm[3][0] * Math.exp(g32) * pd[4][0];
        output.d[3] = densu(z, db32, tinf, tlb, 32.0, alpha[3], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if (flags.sw[15] != 0) {
            if (z <= altl[3]) {
                final double zh32 = pdm[3][2];
                final double b32 = densu(zh32, db32, tinf, tlb, 32.0 - xmm, alpha[3] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
                dm32 = densu(z, b32, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
                final double zhm32 = zhm28;
                output.d[3] = dnet(output.d[3], dm32, zhm32, xmm, 32.0);
                final double rl = Math.log(b28 * pdm[3][1] / b32);
                final double hc32 = pdm[3][5] * pdl[1][7];
                final double zc32 = pdm[3][4] * pdl[1][6];
                output.d[3] = output.d[3] * ccor(z, rl, hc32, zc32);
            }
            final double hcc32 = pdm[3][7] * pdl[1][22];
            final double hcc232 = pdm[3][7] * pdl[0][22];
            final double zcc32 = pdm[3][6] * pdl[1][21];
            final double rc32 = pdm[3][3] * pdl[1][23] * (1.0 + flags.sw[1] * pdl[0][23] * (input.f107A - 150.0));
            output.d[3] = output.d[3] * ccor2(z, rc32, hcc32, zcc32, hcc232);
        }

        // Ar density
        final double g40 = flags.sw[21] * globe7(pd[5], input, flags);
        final double db40 = pdm[4][0] * Math.exp(g40) * pd[5][0];
        output.d[4] = densu(z, db40, tinf, tlb, 40.0, alpha[4], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z <= altl[4])) {
            final double zh40 = pdm[4][2];
            final double b40 = densu(zh40, db40, tinf, tlb, 40.0 - xmm, alpha[4] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            dm40 = densu(z, b40, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            final double zhm40 = zhm28;
            output.d[4] = dnet(output.d[4], dm40, zhm40, xmm, 40.0);
            final double rl = Math.log(b28 * pdm[4][1] / b40);
            final double hc40 = pdm[4][5] * pdl[1][9];
            final double zc40 = pdm[4][4] * pdl[1][8];
            output.d[4] = output.d[4] * ccor(z, rl, hc40, zc40);
        }

        // Hydrogen density
        final double g1 = flags.sw[21] * globe7(pd[6], input, flags);
        final double db01 = pdm[5][0] * Math.exp(g1) * pd[6][0];
        output.d[6] = densu(z, db01, tinf, tlb, 1.0, alpha[6], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z <= altl[6])) {
            final double zh01 = pdm[5][2];
            final double b01 = densu(zh01, db01, tinf, tlb, 1.0 - xmm, alpha[6] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            dm01 = densu(z, b01, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            final double zhm01 = zhm28;
            output.d[6] = dnet(output.d[6], dm01, zhm01, xmm, 1.0);
            final double rl = Math.log(b28 * pdm[5][1] * Math.sqrt(pdl[1][17] * pdl[1][17]) / b01);
            final double hc01 = pdm[5][5] * pdl[1][11];
            final double zc01 = pdm[5][4] * pdl[1][10];
            output.d[6] = output.d[6] * ccor(z, rl, hc01, zc01);
            final double hcc01 = pdm[5][7] * pdl[1][19];
            final double zcc01 = pdm[5][6] * pdl[1][18];
            final double rc01 = pdm[5][3] * pdl[1][20];
            output.d[6] = output.d[6] * ccor(z, rc01, hcc01, zcc01);
        }

        // Atomic nitrogen density
        final double g14 = flags.sw[21] * globe7(pd[7], input, flags);
        final double db14 = pdm[6][0] * Math.exp(g14) * pd[7][0];
        output.d[7] = densu(z, db14, tinf, tlb, 14.0, alpha[7], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        if ((flags.sw[15] != 0) && (z <= altl[7])) {
            final double zh14 = pdm[6][2];
            final double b14 = densu(zh14, db14, tinf, tlb, 14.0 - xmm, alpha[7] - 1.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            dm14 = densu(z, b14, tinf, tlb, xmm, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
            final double zhm14 = zhm28;
            output.d[7] = dnet(output.d[7], dm14, zhm14, xmm, 14.0);
            final double rl = Math.log(b28 * pdm[6][1] * Math.sqrt(pdl[0][2] * pdl[0][2]) / b14);
            final double hc14 = pdm[6][5] * pdl[0][1];
            final double zc14 = pdm[6][4] * pdl[0][0];
            output.d[7] = output.d[7] * ccor(z, rl, hc14, zc14);
            final double hcc14 = pdm[6][7] * pdl[0][4];
            final double zcc14 = pdm[6][6] * pdl[0][3];
            final double rc14 = pdm[6][3] * pdl[0][5];
            output.d[7] = output.d[7] * ccor(z, rc14, hcc14, zcc14);
        }

        // Anomalous oxygen density
        final double g16h = flags.sw[21] * globe7(pd[8], input, flags);
        final double db16h = pdm[7][0] * Math.exp(g16h) * pd[8][0];
        final double tho = pdm[7][9] * pdl[0][6];
        ddLocal = densu(z, db16h, tho, tho, 16.0, alpha[8], tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        final double zsht = pdm[7][5];
        final double zmho = pdm[7][4];
        final double zsho = scalh(zmho, 16.0, tho);
        output.d[8] = ddLocal * Math.exp(-zsht / zsho * (Math.exp(-(z - zmho) / zsht) - 1.0));

        // total mass density
        output.d[5] = 1.66E-24 * (4.0 * output.d[0] + 16.0 * output.d[1] + 28.0 * output.d[2]
                + 32.0 * output.d[3] + 40.0 * output.d[4] + output.d[6] + 14.0 * output.d[7]);

        // temperature
        final double zTemp = Math.sqrt(input.alt * input.alt);
        densu(zTemp, 1.0, tinf, tlb, 0.0, 0.0, tz, ptm[5], s, mn1, zn1, mesoTn1, mesoTgn1);
        output.t[1] = tz[0];
        if (flags.sw[0] != 0) {
            for (int i = 0; i < 9; i++) {
                output.d[i] = output.d[i] * 1.0E6;
            }
            output.d[5] = output.d[5] / 1000;
        }
    }
}
