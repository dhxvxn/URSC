package org.ursc.trajectory.forces.drag.jb2008;

/**
 * Jacchia-Bowman 2008 (JB2008) thermospheric density model — a self-contained, dependency-free
 * Java implementation of the public FORTRAN routine by Bruce R. Bowman &amp; al.
 * (AIAA 2008-6438, "A New Empirical Thermospheric Density Model JB2008 Using New Solar and
 * Geomagnetic Indices").
 *
 * <p>The model numerically integrates the Jacchia-1970 diffusion equations with the JB2008
 * exospheric-temperature equation (driven by F10.7, S10, M10, Y10) and the DTC geomagnetic-storm
 * temperature correction ({@code dstdtc}). The core {@link #density} method is a pure function of
 * the modified Julian date, the Sun/satellite geometry, and the solar/geomagnetic indices, so it
 * can be validated directly against a reference implementation.</p>
 *
 * <p>Numeric coefficients (FZM, GTM, BDT_SUB, CDT_SUB, CXAMB, CHT, …) are Bowman's published JB2008
 * constants. This independent port was cross-checked against the Orekit JB2008 realisation.</p>
 */
public final class Jb2008 {

    /** Minimum altitude (m) for which the model is valid. */
    public static final double ALT_MIN = 90000.0;

    private static final double LOG10 = Math.log(10.0);
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double SEMI_PI = 0.5 * Math.PI;
    private static final double G0 = 9.80665;          // standard gravity (m/s^2)
    private static final double EARTH_RADIUS = 6356.766; // km (polar)

    /** Thermal diffusion coefficients (equation 6). */
    private static final double[] ALPHA = {0, 0, 0, 0, -0.38};
    /** Molecular weights: N2, O2, O, Ar, He, H. */
    private static final double[] AMW = {28.0134, 31.9988, 15.9994, 39.9480, 4.0026, 1.00797};
    /** Avogadro's number (molecules/kmol). */
    private static final double AVOGAD = 6.02257e26;
    /** Sea-level volume fractions: N2, O2, Ar, He. */
    private static final double[] FRAC = {0.78110, 0.20955, 9.3400e-3, 1.2890e-5};
    /** Universal gas constant (J/K/kmol). */
    private static final double RSTAR = 8.31432;
    /** Height step-size controls. */
    private static final double R1 = 0.010;
    private static final double R2 = 0.025;
    private static final double R3 = 0.075;
    /** Newton-Cotes 5-point quadrature weights. */
    private static final double[] WT = {0.311111111111111, 1.422222222222222, 0.533333333333333,
                                        1.422222222222222, 0.311111111111111};

    /** FZ global model values (1997-2006 fit). */
    private static final double[] FZM = {0.2689e+00, -0.1176e-01, 0.2782e-01, -0.2782e-01, 0.3470e-03};

    /** GT global model values (1997-2006 fit). */
    private static final double[] GTM = {
        -0.3633e+00, 0.8506e-01, 0.2401e+00, -0.1897e+00, -0.2554e+00,
        -0.1790e-01, 0.5650e-03, -0.6407e-03, -0.3418e-02, -0.1252e-02
    };

    /** DTC polynomial coefficients (300-800 km regime). */
    private static final double[] BDT_SUB = {
        -0.457512297e+01, -0.512114909e+01, -0.693003609e+02, 0.203716701e+03, 0.703316291e+03,
        -0.194349234e+04, 0.110651308e+04, -0.174378996e+03, 0.188594601e+04, -0.709371517e+04,
        0.922454523e+04, -0.384508073e+04, -0.645841789e+01, 0.409703319e+02, -0.482006560e+03,
        0.181870931e+04, -0.237389204e+04, 0.996703815e+03, 0.361416936e+02
    };

    /** DTC polynomial coefficients (120-300 km regime). */
    private static final double[] CDT_SUB = {
        -0.155986211e+02, -0.512114909e+01, -0.693003609e+02, 0.203716701e+03, 0.703316291e+03,
        -0.194349234e+04, 0.110651308e+04, -0.220835117e+03, 0.143256989e+04, -0.318481844e+04,
        0.328981513e+04, -0.135332119e+04, 0.199956489e+02, -0.127093998e+02, 0.212825156e+02,
        -0.275555432e+01, 0.110234982e+02, 0.148881951e+03, -0.751640284e+03, 0.637876542e+03,
        0.127093998e+02, -0.212825156e+02, 0.275555432e+01
    };

    /** Mbar polynomial coefficients. */
    private static final double[] CXAMB = {28.15204, -8.5586e-2, +1.2840e-4, -1.0056e-5, -1.0210e-5,
                                           +1.5044e-6, +9.9826e-8};

    /** High-altitude density correction coefficients. */
    private static final double[] CHT = {0.22, -0.20e-02, 0.115e-02, -0.211e-05};

    /** Total mass density (kg/m^3) at the given position/time and solar/geomagnetic state.
     *
     * @param dateMJD modified Julian date (with day fraction)
     * @param sunRA right ascension of the Sun (rad)
     * @param sunDecli declination of the Sun (rad)
     * @param satLon right ascension / longitude of the position (rad)
     * @param satLat latitude of the position (rad)
     * @param satAlt height of the position (m)
     * @param f10 10.7-cm solar flux, 1-day lagged
     * @param f10B 81-day centered F10.7, 1-day lagged
     * @param s10 EUV index, 1-day lagged
     * @param s10B 81-day centered EUV index, 1-day lagged
     * @param xm10 Mg II index, 2-day lagged
     * @param xm10B 81-day centered Mg II index, 2-day lagged
     * @param y10 X-ray/Lya index, 5-day lagged
     * @param y10B 81-day centered X-ray/Lya index, 5-day lagged
     * @param dstdtc temperature change from the Dst index (K)
     * @return total mass density (kg/m^3)
     */
    public double density(final double dateMJD, final double sunRA, final double sunDecli,
                          final double satLon, final double satLat, final double satAlt,
                          final double f10, final double f10B, final double s10, final double s10B,
                          final double xm10, final double xm10B, final double y10, final double y10B,
                          final double dstdtc) {

        if (satAlt < ALT_MIN) {
            throw new IllegalArgumentException("altitude below JB2008 minimum (" + satAlt + " < " + ALT_MIN + ")");
        }
        final double altKm = satAlt / 1000.0;

        // Equation (14): exospheric temperature Tc
        final double fn = Math.min(1.0, Math.pow(f10B / 240.0, 0.25));
        final double fsb = f10B * fn + s10B * (1.0 - fn);
        final double tsubc = 392.4 + 3.227 * fsb + 0.298 * (f10 - f10B) + 2.259 * (s10 - s10B)
                + 0.312 * (xm10 - xm10B) + 0.178 * (y10 - y10B);

        // Equation (15)
        final double eta = 0.5 * Math.abs(satLat - sunDecli);
        final double theta = 0.5 * Math.abs(satLat + sunDecli);

        // Equation (16)
        final double h = satLon - sunRA;
        final double tau = h - 0.64577182 + 0.10471976 * Math.sin(h + 0.75049158);
        final double solarTime = solarTimeHour(h);

        // Equation (17)
        final double tsubl = tSubL(eta, theta, tau, tsubc);

        // local-solar-time / latitude dTc correction
        final double dtclst = dTc(f10, solarTime, satLat, altKm);

        // local exospheric temperature (JB2008: add the Dst storm correction)
        final double tInf = tsubl + dstdtc + dtclst;

        // Equation (9), (11)
        final double tsubx = tSubX(tInf);
        final double gsubx = gSubX(tsubx);
        final double[] tc = {tsubx, gsubx, (tInf - tsubx) / SEMI_PI, 0.0};
        tc[3] = gsubx / tc[2];

        // Equation (5): integrate 90 -> min(alt,105)
        final double z1 = 90.0;
        final double z2 = Math.min(altKm, 105.0);
        double al = Math.log(z2 / z1);
        int n = (int) Math.floor(al / R1) + 1;
        double zr = Math.exp(al / n);
        final double mb1 = mBar(z1);
        final double tloc1 = localTemp(z1, tc);
        double zend = z1;
        double sub2 = 0.0;
        double ain = mb1 * gravity(z1) / tloc1;
        double mb2 = 0.0;
        double tloc2 = 0.0;
        double z = 0.0;
        double gravl = 0.0;

        for (int i = 0; i < n; ++i) {
            z = zend;
            zend = zr * z;
            final double dz = 0.25 * (zend - z);
            double sum1 = WT[0] * ain;
            for (int j = 1; j < 5; ++j) {
                z += dz;
                mb2 = mBar(z);
                tloc2 = localTemp(z, tc);
                gravl = gravity(z);
                ain = mb2 * gravl / tloc2;
                sum1 += WT[j] * ain;
            }
            sub2 += dz * sum1;
        }
        double rho = 3.46e-6 * mb2 * tloc1 / Math.exp(sub2 / RSTAR) / (mb1 * tloc2);

        // Equation (2)
        final double anm = AVOGAD * rho;
        final double an = anm / mb2;

        // Equation (3)/(4)
        double fact2 = anm / 28.960;
        final double[] aln = new double[6];
        aln[0] = Math.log(FRAC[0] * fact2);
        aln[3] = Math.log(FRAC[2] * fact2);
        aln[4] = Math.log(FRAC[3] * fact2);
        aln[1] = Math.log(fact2 * (1.0 + FRAC[1]) - an);
        aln[2] = Math.log(2.0 * (an - fact2));

        if (altKm <= 105.0) {
            aln[5] = aln[4] - 25.0;
        } else {
            // Equation (6): integrate z -> min(alt,500)
            al = Math.log(Math.min(altKm, 500.0) / z);
            n = 1 + (int) Math.floor(al / R2);
            zr = Math.exp(al / n);
            sub2 = 0.0;
            ain = gravl / tloc2;
            double tloc3 = 0.0;
            for (int i = 0; i < n; ++i) {
                z = zend;
                zend = zr * z;
                final double dz = 0.25 * (zend - z);
                double sum1 = WT[0] * ain;
                for (int j = 1; j < 5; ++j) {
                    z += dz;
                    tloc3 = localTemp(z, tc);
                    gravl = gravity(z);
                    ain = gravl / tloc3;
                    sum1 += WT[j] * ain;
                }
                sub2 += dz * sum1;
            }

            // integrate min(alt,500) -> max(alt,500)
            al = Math.log(Math.max(altKm, 500.0) / z);
            final double r = (altKm > 500.0) ? R3 : R2;
            n = 1 + (int) Math.floor(al / r);
            zr = Math.exp(al / n);
            double sum3 = 0.0;
            double tloc4 = 0.0;
            for (int i = 0; i < n; ++i) {
                z = zend;
                zend = zr * z;
                final double dz = 0.25 * (zend - z);
                double sum1 = WT[0] * ain;
                for (int j = 1; j < 5; ++j) {
                    z += dz;
                    tloc4 = localTemp(z, tc);
                    gravl = gravity(z);
                    ain = gravl / tloc4;
                    sum1 += WT[j] * ain;
                }
                sum3 += dz * sum1;
            }

            final double altr;
            final double hSign;
            if (altKm <= 500.0) {
                altr = Math.log(tloc3 / tloc2);
                fact2 = sub2 / RSTAR;
                hSign = 1.0;
            } else {
                altr = Math.log(tloc4 / tloc2);
                fact2 = (sub2 + sum3) / RSTAR;
                hSign = -1.0;
            }
            for (int i = 0; i < 5; ++i) {
                aln[i] = aln[i] - (1.0 + ALPHA[i]) * altr - fact2 * AMW[i];
            }

            // Equation (7): hydrogen
            final double al10t5 = Math.log10(tInf);
            final double alnh5 = (5.5 * al10t5 - 39.40) * al10t5 + 73.13;
            aln[5] = LOG10 * (alnh5 + 6.0) + hSign * (Math.log(tloc4 / tloc3) + sum3 * AMW[5] / RSTAR);
        }

        // Equation (24): J70 seasonal-latitudinal variation
        final double dlrsl = dlrsl(altKm, dateMJD, satLat);

        // Equation (23): semiannual variation (new JB2008 model)
        double dlrsa = 0.0;
        if (z < 2000.0) {
            dlrsa = semian(dayOfYear(dateMJD), altKm, f10B, s10B, xm10B);
        }

        final double dlr = LOG10 * (dlrsl + dlrsa);
        for (int i = 0; i < 6; ++i) {
            aln[i] += dlr;
        }

        double sumnm = 0.0;
        for (int i = 0; i < 6; ++i) {
            sumnm += Math.exp(aln[i]) * AMW[i];
        }
        rho = sumnm / AVOGAD;

        // high-altitude exospheric correction
        rho *= densityCorrectionFactor(altKm, f10B);
        return rho;
    }

    /** Semiannual density variation (JB2008). */
    private static double semian(final double day, final double altKm,
                                 final double f10B, final double s10B, final double xm10B) {
        final double htz = altKm / 1000.0;
        double fsmb = f10B - 0.70 * s10B - 0.04 * xm10B;
        final double fzz = FZM[0] + fsmb * (FZM[1] + htz * (FZM[2] + FZM[3] * htz + FZM[4] * fsmb));
        fsmb = f10B - 0.75 * s10B - 0.37 * xm10B;
        final double t = TWO_PI * (day - 1.0) / 365.0;
        final double sin1 = Math.sin(t);
        final double cos1 = Math.cos(t);
        final double sin2 = Math.sin(2.0 * t);
        final double cos2 = Math.cos(2.0 * t);
        final double gtz = GTM[0] + GTM[1] * sin1 + GTM[2] * cos1 + GTM[3] * sin2 + GTM[4] * cos2
                + fsmb * (GTM[5] + GTM[6] * sin1 + GTM[7] * cos1 + GTM[8] * sin2 + GTM[9] * cos2);
        return Math.max(1.0e-6, fzz) * gtz;
    }

    private static double solarTimeHour(final double h) {
        final double t = Math.toDegrees(h + Math.PI) / 15.0;
        if (t >= 24.0) {
            return t - 24.0;
        }
        if (t < 0.0) {
            return t + 24.0;
        }
        return t;
    }

    private static double tSubL(final double eta, final double theta, final double tau, final double tsubc) {
        final double cosEta = Math.pow(Math.cos(eta), 2.5);
        final double sinTheta = Math.pow(Math.sin(theta), 2.5);
        final double cosTau = Math.abs(Math.cos(0.5 * tau));
        final double df = sinTheta + (cosEta - sinTheta) * cosTau * cosTau * cosTau;
        return tsubc * (1.0 + 0.31 * df);
    }

    private static double tSubX(final double tInf) {
        return 444.3807 + 0.02385 * tInf - 392.8292 * Math.exp(-0.0021357 * tInf);
    }

    private static double gSubX(final double tSubX) {
        return 0.054285714 * (tSubX - 183.0);
    }

    private static double localTemp(final double zz, final double[] tc) {
        final double dz = zz - 125.0;
        if (dz <= 0.0) {
            return ((-9.8204695e-6 * dz - 7.3039742e-4) * dz * dz + 1.0) * dz * tc[1] + tc[0];
        }
        return tc[0] + tc[2] * Math.atan(tc[3] * dz * (1.0 + 4.5e-6 * Math.pow(dz, 2.5)));
    }

    private static double mBar(final double zz) {
        final double dz = zz - 100.0;
        double amb = CXAMB[6];
        for (int i = 5; i >= 0; --i) {
            amb = dz * amb + CXAMB[i];
        }
        return amb;
    }

    private static double gravity(final double zz) {
        final double t = 1.0 + zz / EARTH_RADIUS;
        return G0 / (t * t);
    }

    private static double dayOfYear(final double dateMJD) {
        final double d1950 = dateMJD - 33281;
        int iyday = (int) d1950;
        final double frac = d1950 - iyday;
        iyday = iyday + 364;
        int itemp = iyday / 1461;
        iyday = iyday - itemp * 1461;
        itemp = iyday / 365;
        if (itemp >= 3) {
            itemp = 3;
        }
        iyday = iyday - 365 * itemp + 1;
        return iyday + frac;
    }

    private static double dlrsl(final double altKm, final double dateMJD, final double satLat) {
        double capPhi = ((dateMJD - 36204.0) / 365.2422) % 1;
        final int signum = (satLat >= 0) ? 1 : -1;
        final double sinLat = Math.sin(satLat);
        final double hm90 = altKm - 90.0;
        return 0.02 * hm90 * Math.exp(-0.045 * hm90) * signum * Math.sin(TWO_PI * capPhi + 1.72) * sinLat * sinLat;
    }

    private static double densityCorrectionFactor(final double altKm, final double f10B) {
        if (altKm >= 1000.0 && altKm < 1500.0) {
            final double zeta = (altKm - 1000.0) * 0.002;
            final double f15c = CHT[0] + CHT[1] * f10B + CHT[2] * 1500.0 + CHT[3] * f10B * 1500.0;
            final double f15cZeta = (CHT[2] + CHT[3] * f10B) * 500.0;
            final double fex2 = 3.0 * f15c - f15cZeta - 3.0;
            final double fex3 = f15cZeta - 2.0 * f15c + 2.0;
            return 1.0 + zeta * zeta * (fex2 + fex3 * zeta);
        }
        if (altKm >= 1500.0) {
            return CHT[0] + CHT[1] * f10B + CHT[2] * altKm + CHT[3] * f10B * altKm;
        }
        return 1.0;
    }

    private static double dTc(final double f10, final double solarTime, final double satLat, final double satAlt) {
        final double st = solarTime / 24.0;
        final double cs = Math.cos(satLat);
        final double fs = (f10 - 100.0) / 100.0;

        if (satAlt >= 120 && satAlt <= 200) {
            final double dtc200 = poly2CDTC(fs, st, cs);
            final double dtc200dz = poly1CDTC(fs, st, cs);
            final double cc = 3.0 * dtc200 - dtc200dz;
            final double dd = dtc200 - cc;
            final double zp = (satAlt - 120.0) / 80.0;
            return zp * zp * (cc + dd * zp);
        } else if (satAlt > 200.0 && satAlt <= 240.0) {
            final double hh = (satAlt - 200.0) / 50.0;
            return poly1CDTC(fs, st, cs) * hh + poly2CDTC(fs, st, cs);
        } else if (satAlt > 240.0 && satAlt <= 300.0) {
            final double hh = 0.8;
            final double bb = poly1CDTC(fs, st, cs);
            final double aa = bb * hh + poly2CDTC(fs, st, cs);
            final double p2BDT = poly2BDTC(st);
            final double dtc300 = poly1BDTC(fs, st, cs, 3 * p2BDT);
            final double dtc300dz = cs * p2BDT;
            final double cc = 3.0 * dtc300 - dtc300dz - 3.0 * aa - 2.0 * bb;
            final double dd = dtc300 - aa - bb - cc;
            final double zp = (satAlt - 240.0) / 60.0;
            return aa + zp * (bb + zp * (cc + zp * dd));
        } else if (satAlt > 300.0 && satAlt <= 600.0) {
            final double hh = satAlt / 100.0;
            return poly1BDTC(fs, st, cs, hh * poly2BDTC(st));
        } else if (satAlt > 600.0 && satAlt <= 800.0) {
            final double poly2 = poly2BDTC(st);
            final double aa = poly1BDTC(fs, st, cs, 6 * poly2);
            final double bb = cs * poly2;
            final double cc = -(3.0 * aa + 4.0 * bb) / 4.0;
            final double dd = (aa + bb) / 4.0;
            final double zp = (satAlt - 600.0) / 100.0;
            return aa + zp * (bb + zp * (cc + zp * dd));
        }
        return 0.0;
    }

    private static double poly1CDTC(final double fs, final double st, final double cs) {
        return CDT_SUB[0]
                + fs * (CDT_SUB[1] + st * (CDT_SUB[2] + st * (CDT_SUB[3] + st * (CDT_SUB[4] + st * (CDT_SUB[5] + st * CDT_SUB[6])))))
                + cs * st * (CDT_SUB[7] + st * (CDT_SUB[8] + st * (CDT_SUB[9] + st * (CDT_SUB[10] + st * CDT_SUB[11]))))
                + cs * (CDT_SUB[12] + fs * (CDT_SUB[13] + st * (CDT_SUB[14] + st * CDT_SUB[15])));
    }

    private static double poly2CDTC(final double fs, final double st, final double cs) {
        return CDT_SUB[16] + st * cs * (CDT_SUB[17] + st * (CDT_SUB[18] + st * CDT_SUB[19]))
                + fs * cs * (CDT_SUB[20] + st * (CDT_SUB[21] + st * CDT_SUB[22]));
    }

    private static double poly1BDTC(final double fs, final double st, final double cs, final double hp) {
        return BDT_SUB[0]
                + fs * (BDT_SUB[1] + st * (BDT_SUB[2] + st * (BDT_SUB[3] + st * (BDT_SUB[4] + st * (BDT_SUB[5] + st * BDT_SUB[6])))))
                + cs * (st * (BDT_SUB[7] + st * (BDT_SUB[8] + st * (BDT_SUB[9] + st * (BDT_SUB[10] + st * BDT_SUB[11])))) + hp + BDT_SUB[18]);
    }

    private static double poly2BDTC(final double st) {
        return BDT_SUB[12] + st * (BDT_SUB[13] + st * (BDT_SUB[14] + st * (BDT_SUB[15] + st * (BDT_SUB[16] + st * BDT_SUB[17]))));
    }
}
