#pragma once
#include "tzf_registration.h"

namespace tzf {
// Transform rotates around moving centroid, as in registerConstrained.
[[nodiscard]] std::array<double, 4> seedVerticalTranslation(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const std::array<double, 4>& transform, double millimetreScale,
    const std::atomic_bool* cancellation = nullptr);
}
