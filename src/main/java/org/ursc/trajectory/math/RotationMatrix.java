package org.ursc.trajectory.math;

/**
 * Immutable 3x3 rotation matrix together with convenience factory methods for the
 * elementary rotations about the coordinate axes.
 *
 * <p>Rows are stored explicitly. A rotation is applied to a column vector as
 * {@code m * v}.</p>
 */
public final class RotationMatrix {

    /** Identity rotation. */
    public static final RotationMatrix IDENTITY =
            new RotationMatrix(1, 0, 0, 0, 1, 0, 0, 0, 1);

    private final double m00, m01, m02;
    private final double m10, m11, m12;
    private final double m20, m21, m22;

    public RotationMatrix(final double m00, final double m01, final double m02,
                          final double m10, final double m11, final double m12,
                          final double m20, final double m21, final double m22) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
        this.m20 = m20; this.m21 = m21; this.m22 = m22;
    }

    /** Rotation of coordinates by angle {@code theta} about the X axis. */
    public static RotationMatrix rotationX(final double theta) {
        final double c = Math.cos(theta);
        final double s = Math.sin(theta);
        return new RotationMatrix(
                1, 0, 0,
                0, c, s,
                0, -s, c);
    }

    /** Rotation of coordinates by angle {@code theta} about the Y axis. */
    public static RotationMatrix rotationY(final double theta) {
        final double c = Math.cos(theta);
        final double s = Math.sin(theta);
        return new RotationMatrix(
                c, 0, -s,
                0, 1, 0,
                s, 0, c);
    }

    /** Rotation of coordinates by angle {@code theta} about the Z axis. */
    public static RotationMatrix rotationZ(final double theta) {
        final double c = Math.cos(theta);
        final double s = Math.sin(theta);
        return new RotationMatrix(
                c, s, 0,
                -s, c, 0,
                0, 0, 1);
    }

    /** Apply this rotation to a vector. */
    public Vector3D applyTo(final Vector3D v) {
        final double x = v.getX();
        final double y = v.getY();
        final double z = v.getZ();
        return new Vector3D(
                m00 * x + m01 * y + m02 * z,
                m10 * x + m11 * y + m12 * z,
                m20 * x + m21 * y + m22 * z);
    }

    /** Apply the inverse (transpose) of this rotation to a vector. */
    public Vector3D applyInverseTo(final Vector3D v) {
        final double x = v.getX();
        final double y = v.getY();
        final double z = v.getZ();
        return new Vector3D(
                m00 * x + m10 * y + m20 * z,
                m01 * x + m11 * y + m21 * z,
                m02 * x + m12 * y + m22 * z);
    }

    /** @return the transpose, which for a rotation matrix is its inverse. */
    public RotationMatrix transpose() {
        return new RotationMatrix(
                m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22);
    }

    /** @return the matrix product {@code this * other}. */
    public RotationMatrix multiply(final RotationMatrix o) {
        return new RotationMatrix(
                m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
                m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
                m00 * o.m02 + m01 * o.m12 + m02 * o.m22,

                m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
                m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
                m10 * o.m02 + m11 * o.m12 + m12 * o.m22,

                m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
                m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
                m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }
}
