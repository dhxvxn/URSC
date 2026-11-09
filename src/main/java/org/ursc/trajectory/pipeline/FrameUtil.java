package org.ursc.trajectory.pipeline;

import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Exact state and covariance conversions between GCRF, ITRF and EME2000.
 *
 * <p>Velocity conversion uses the full time derivative of the GCRF/ITRF rotation (central
 * difference), not a constant Earth-spin rate: the constant-rate shortcut leaves a
 * ~2 mm/s velocity error which grows to hundreds of metres in a one-day prediction.</p>
 *
 * <p>EME2000 is related to GCRF by the constant IERS 2010 frame bias
 * B = R1(-eta0) R2(xi0) R3(da0) (about 23 mas, i.e. ~0.8 m at LEO radius).</p>
 */
public final class FrameUtil {

    private static final double AS = Math.PI / (180.0 * 3600.0);
    /** Frame-bias constants as in SOFA iauBp00 (IERS 2003/2010). */
    private static final double XI0 = -0.041775 * AS * Math.sin(84381.448 * AS);
    private static final double ETA0 = -0.0068192 * AS;
    private static final double DA0 = -0.0146 * AS;
    /** GCRF -> EME2000 frame-bias matrix. */
    static final double[][] BIAS = mul(mul(r1(-ETA0), r2(XI0)), r3(DA0));

    private FrameUtil() {
    }

    /** Position/velocity pair. */
    public static final class PV {
        public final Vector3D r;
        public final Vector3D v;

        public PV(final Vector3D r, final Vector3D v) {
            this.r = r;
            this.v = v;
        }
    }

    // passive rotation matrices (IERS conventions)
    private static double[][] r1(final double a) {
        final double c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{1, 0, 0}, {0, c, s}, {0, -s, c}};
    }

    private static double[][] r2(final double a) {
        final double c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{c, 0, -s}, {0, 1, 0}, {s, 0, c}};
    }

    private static double[][] r3(final double a) {
        final double c = Math.cos(a), s = Math.sin(a);
        return new double[][] {{c, s, 0}, {-s, c, 0}, {0, 0, 1}};
    }

    static double[][] mul(final double[][] a, final double[][] b) {
        final int n = a.length, m = b[0].length, k = b.length;
        final double[][] o = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double s = 0;
                for (int q = 0; q < k; q++) {
                    s += a[i][q] * b[q][j];
                }
                o[i][j] = s;
            }
        }
        return o;
    }

    static double[][] transpose(final double[][] a) {
        final double[][] o = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                o[j][i] = a[i][j];
            }
        }
        return o;
    }

    static Vector3D apply(final double[][] m, final Vector3D v) {
        return new Vector3D(
                m[0][0] * v.getX() + m[0][1] * v.getY() + m[0][2] * v.getZ(),
                m[1][0] * v.getX() + m[1][1] * v.getY() + m[1][2] * v.getZ(),
                m[2][0] * v.getX() + m[2][1] * v.getY() + m[2][2] * v.getZ());
    }

    /** 3x3 matrix of a frame transform's rotation, built column by column. */
    private static double[][] matrix(final Transform t) {
        final Vector3D[] e = {Vector3D.PLUS_I, Vector3D.PLUS_J, Vector3D.PLUS_K};
        final double[][] m = new double[3][3];
        for (int j = 0; j < 3; j++) {
            final Vector3D c = t.transformVector(e[j]);
            m[0][j] = c.getX();
            m[1][j] = c.getY();
            m[2][j] = c.getZ();
        }
        return m;
    }

    /**
     * Rotation R (from->to) and its time derivative at {@code date}; the derivative uses a
     * 5-point (4th-order) central difference, accurate to the ~1e-9 m/s roundoff level.
     */
    private static double[][][] rotationAndRate(final boolean toItrf, final AbsoluteDate date) {
        final double h = 0.5;
        final double[][] r = matrix(transform(toItrf, date));
        final double[][] p1 = matrix(transform(toItrf, date.shiftedBy(h)));
        final double[][] m1 = matrix(transform(toItrf, date.shiftedBy(-h)));
        final double[][] p2 = matrix(transform(toItrf, date.shiftedBy(2 * h)));
        final double[][] m2 = matrix(transform(toItrf, date.shiftedBy(-2 * h)));
        final double[][] rd = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                rd[i][j] = (-p2[i][j] + 8 * p1[i][j] - 8 * m1[i][j] + m2[i][j]) / (12 * h);
            }
        }
        return new double[][][] {r, rd};
    }

    private static Transform transform(final boolean toItrf, final AbsoluteDate date) {
        return toItrf ? FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), date)
                      : FramesFactory.getTransform(FramesFactory.getITRF(), FramesFactory.getGCRF(), date);
    }

    public static PV gcrfToItrf(final Vector3D r, final Vector3D v, final AbsoluteDate date) {
        return convert(true, r, v, date);
    }

    public static PV itrfToGcrf(final Vector3D r, final Vector3D v, final AbsoluteDate date) {
        return convert(false, r, v, date);
    }

    private static PV convert(final boolean toItrf, final Vector3D r, final Vector3D v, final AbsoluteDate date) {
        final double[][][] rr = rotationAndRate(toItrf, date);
        final Vector3D rOut = apply(rr[0], r);
        final Vector3D vOut = apply(rr[0], v).add(apply(rr[1], r));
        return new PV(rOut, vOut);
    }

    public static PV gcrfToEme2000(final Vector3D r, final Vector3D v) {
        return new PV(apply(BIAS, r), apply(BIAS, v));
    }

    public static PV eme2000ToGcrf(final Vector3D r, final Vector3D v) {
        final double[][] bt = transpose(BIAS);
        return new PV(apply(bt, r), apply(bt, v));
    }

    /** Rotate a 3x3 position covariance from ITRF to GCRF at {@code date}. */
    public static double[][] covItrfToGcrf(final double[][] c, final AbsoluteDate date) {
        final double[][] r = matrix(transform(false, date));
        return mul(mul(r, c), transpose(r));
    }

    /**
     * Transform a 6x6 GCRF position/velocity covariance to {@code frame}:
     * {@code J C J^T} with {@code J = [[R, 0], [Rdot, R]]}.
     */
    public static double[][] cov6FromGcrf(final double[][] c, final RunConfig.OutputFrame frame,
                                          final AbsoluteDate date) {
        double[][] r;
        double[][] rd;
        if (frame == RunConfig.OutputFrame.GCRF) {
            return copy(c);
        } else if (frame == RunConfig.OutputFrame.EME2000) {
            r = BIAS;
            rd = new double[3][3];
        } else {
            final double[][][] rr = rotationAndRate(true, date);
            r = rr[0];
            rd = rr[1];
        }
        final double[][] j = new double[6][6];
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                j[a][b] = r[a][b];
                j[a + 3][b + 3] = r[a][b];
                j[a + 3][b] = rd[a][b];
            }
        }
        return mul(mul(j, c), transpose(j));
    }

    /** Express a GCRF state in the output frame. */
    public static PV fromGcrf(final Vector3D r, final Vector3D v, final RunConfig.OutputFrame frame,
                              final AbsoluteDate date) {
        switch (frame) {
            case ITRF:
                return gcrfToItrf(r, v, date);
            case EME2000:
                return gcrfToEme2000(r, v);
            case GCRF:
            default:
                return new PV(r, v);
        }
    }

    /** Convert a state given in {@code frame} to GCRF. */
    public static PV toGcrf(final Vector3D r, final Vector3D v, final RunConfig.OutputFrame frame,
                            final AbsoluteDate date) {
        switch (frame) {
            case ITRF:
                return itrfToGcrf(r, v, date);
            case EME2000:
                return eme2000ToGcrf(r, v);
            case GCRF:
            default:
                return new PV(r, v);
        }
    }

    static double[][] copy(final double[][] a) {
        final double[][] o = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            o[i] = a[i].clone();
        }
        return o;
    }
}
