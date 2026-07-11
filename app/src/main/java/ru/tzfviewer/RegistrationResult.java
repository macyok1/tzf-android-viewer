package ru.tzfviewer;

final class RegistrationResult {
    final boolean accepted;
    final double rms;
    final double p95;
    final double overlap;
    final int iterations;
    final String reason;
    final float[] transform;

    RegistrationResult(boolean accepted, double rms, double p95, double overlap,
                       int iterations, String reason, float[] transform) {
        this.accepted = accepted;
        this.rms = rms;
        this.p95 = p95;
        this.overlap = overlap;
        this.iterations = iterations;
        this.reason = reason;
        this.transform = transform;
    }
}
