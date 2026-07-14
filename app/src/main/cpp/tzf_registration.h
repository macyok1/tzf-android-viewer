#pragma once

#include "tzf_core.h"

#include <array>
#include <atomic>
#include <cstddef>
#include <limits>
#include <string>
#include <vector>

namespace tzf {

struct RegistrationOptions {
    std::size_t maxInputPoints{400000};
    int iterationsPerLevel{30};
    double rmsLimit{0.003};
    double p95Limit{0.008};
    double minimumOverlap{0.25};
    double minimumConsistency{0.55};
    double minimumConfidence{73.0};
    double millimetreScale{.001};
    bool adaptiveResidualLimits{};
    double maximumInitialTranslationMeters{std::numeric_limits<double>::infinity()};
    double maximumInitialTranslationRatio{std::numeric_limits<double>::infinity()};
    double maximumInitialYawDelta{std::numeric_limits<double>::infinity()};
    const std::atomic_bool* cancellation{};
};

struct RegistrationResult {
    bool accepted{};
    std::array<double, 4> transform{};
    double rms{};
    double p95{};
    double overlap{};
    double consistency{};
    double confidence{};
    double correctionTranslation{};
    double correctionYaw{};
    int iterations{};
    std::string reason;
};

struct GlobalRegistrationOptions {
    RegistrationOptions refinement{};
    double yawStepDegrees{10.0};
    double ambiguityRatio{0.03};
    double minimumConfidence{91.0};
};

[[nodiscard]] RegistrationResult registerConstrained(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const std::array<double, 4>& initialTransform,
    const RegistrationOptions& options = {});

[[nodiscard]] RegistrationResult registerGlobalConstrained(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const GlobalRegistrationOptions& options = {});

[[nodiscard]] std::vector<Point> xyzToPoints(const std::vector<float>& xyz);

} // namespace tzf
