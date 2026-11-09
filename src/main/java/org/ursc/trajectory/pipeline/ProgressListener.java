package org.ursc.trajectory.pipeline;

/** Progress, log and cancellation hooks for long runs (used by the GUI job runner). */
public interface ProgressListener {

    /** @param fraction overall progress 0..1 */
    void stage(String name, double fraction);

    void log(String message);

    /** Polled between steps; returning true aborts the run. */
    default boolean cancelled() {
        return false;
    }

    ProgressListener NONE = new ProgressListener() {
        public void stage(final String name, final double fraction) {
        }

        public void log(final String message) {
        }
    };
}
