package org.ursc.trajectory.math;

/**
 * Immutable 3-dimensional Euclidean vector.
 *
 * <p>Deliberately dependency-free so the physics core can be audited without
 * pulling in a linear-algebra library. All operations return new instances.</p>
 */
public final class Vector3D {

    /** Null vector (0, 0, 0). */
    public static final Vector3D ZERO = new Vector3D(0, 0, 0);
    /** First canonical basis vector (1, 0, 0). */
    public static final Vector3D PLUS_I = new Vector3D(1, 0, 0);
    /** Second canonical basis vector (0, 1, 0). */
    public static final Vector3D PLUS_J = new Vector3D(0, 1, 0);
    /** Third canonical basis vector (0, 0, 1). */
    public static final Vector3D PLUS_K = new Vector3D(0, 0, 1);

    private final double x;
    private final double y;
    private final double z;

    public Vector3D(final double x, final double y, final double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Build a vector from a length-3 array. */
    public Vector3D(final double[] v) {
        this(v[0], v[1], v[2]);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /** @return the components as a new {@code double[3]} array. */
    public double[] toArray() {
        return new double[] {x, y, z};
    }

    public Vector3D add(final Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    /** @return {@code this + factor * other} without creating an intermediate vector. */
    public Vector3D add(final double factor, final Vector3D other) {
        return new Vector3D(x + factor * other.x, y + factor * other.y, z + factor * other.z);
    }

    public Vector3D subtract(final Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public Vector3D scalarMultiply(final double a) {
        return new Vector3D(a * x, a * y, a * z);
    }

    public Vector3D negate() {
        return new Vector3D(-x, -y, -z);
    }

    public double dotProduct(final Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3D crossProduct(final Vector3D other) {
        return new Vector3D(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    /** @return the Euclidean norm (length). */
    public double getNorm() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** @return the squared Euclidean norm (cheaper than {@link #getNorm()}). */
    public double getNormSq() {
        return x * x + y * y + z * z;
    }

    /**
     * @return a unit vector in the same direction.
     * @throws IllegalStateException if this is the zero vector
     */
    public Vector3D normalize() {
        final double n = getNorm();
        if (n == 0.0) {
            throw new IllegalStateException("cannot normalize a zero-norm vector");
        }
        return new Vector3D(x / n, y / n, z / n);
    }

    /** @return the un-oriented angle (radians, in [0, pi]) between two vectors. */
    public static double angle(final Vector3D v1, final Vector3D v2) {
        final double normProduct = v1.getNorm() * v2.getNorm();
        if (normProduct == 0.0) {
            throw new IllegalStateException("cannot compute angle with a zero-norm vector");
        }
        double cos = v1.dotProduct(v2) / normProduct;
        // guard against round-off driving the argument outside [-1, 1]
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.acos(cos);
    }

    public double distance(final Vector3D other) {
        return subtract(other).getNorm();
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ", " + z + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vector3D)) {
            return false;
        }
        final Vector3D v = (Vector3D) o;
        return Double.compare(v.x, x) == 0
                && Double.compare(v.y, y) == 0
                && Double.compare(v.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) ^ Double.hashCode(y) * 31 ^ Double.hashCode(z) * 131;
    }
}
