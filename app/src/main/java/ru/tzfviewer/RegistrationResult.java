package ru.tzfviewer;

final class RegistrationResult {
    final boolean accepted;
    final double rms;
    final double p95;
    final double overlap;
    final double consistency;
    final double confidence;
    final double correctionTranslation;
    final double correctionYaw;
    final int iterations;
    final String reason;
    final float[] transform;

    RegistrationResult(boolean accepted, double rms, double p95, double overlap,
                       double consistency, double confidence,
                       double correctionTranslation, double correctionYaw,
                       int iterations, String reason, float[] transform) {
        this.accepted = accepted;
        this.rms = rms;
        this.p95 = p95;
        this.overlap = overlap;
        this.consistency = consistency;
        this.confidence = confidence;
        this.correctionTranslation = correctionTranslation;
        this.correctionYaw = correctionYaw;
        this.iterations = iterations;
        this.reason = reason;
        this.transform = transform;
    }
}
