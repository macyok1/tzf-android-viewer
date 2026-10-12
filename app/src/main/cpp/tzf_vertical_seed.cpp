#include "tzf_vertical_seed.h"
#include <algorithm>
#include <cmath>
#include <map>
#include <set>

namespace tzf {
namespace {
using Column = std::pair<long long, long long>;
using Layers = std::set<long long>;
using Grid = std::map<Column, Layers>;
}

std::array<double, 4> seedVerticalTranslation(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const std::array<double, 4>& transform, double mm,
    const std::atomic_bool* cancellation) {
    if (reference.empty() || moving.empty() || !std::isfinite(mm) || mm <= 0)
        return transform;
    for (double value : transform) if (!std::isfinite(value)) return transform;
    double px = 0, py = 0;
    std::size_t count = 0;
    for (auto p : moving) {
        if (!std::isfinite(p.x) || !std::isfinite(p.y) || !std::isfinite(p.z)) continue;
        px += p.x; py += p.y; ++count;
    }
    if (!count) return transform;
    px /= count; py /= count;
    const double xyCell = 200 * mm, zCell = 50 * mm;
    const double yaw = transform[3] * 3.14159265358979323846 / 180;
    const double c = std::cos(yaw), s = std::sin(yaw);
    const auto grid = [&](const std::vector<Point>& points, bool apply) {
        Grid out;
        const std::size_t stride = std::max<std::size_t>(1, (points.size() + 39999) / 40000);
        for (std::size_t i = 0; i < points.size(); i += stride) {
            if (cancellation && cancellation->load()) break;
            auto p = points[i];
            if (!std::isfinite(p.x) || !std::isfinite(p.y) || !std::isfinite(p.z)) continue;
            double x = p.x, y = p.y, z = p.z;
            if (apply) {
                x = px + c * (p.x-px) - s * (p.y-py) + transform[0];
                y = py + s * (p.x-px) + c * (p.y-py) + transform[1];
                z += transform[2];
            }
            out[{std::llround(x / xyCell), std::llround(y / xyCell)}]
                .insert(std::llround(z / zCell));
        }
        return out;
    };
    const auto ref = grid(reference, false), mov = grid(moving, true);
    // Equal weight per XY column prevents densely sampled vertical walls
    // from drowning out the floor/ceiling that constrains scanner height.
    constexpr int range = 40; // +/- two metres, expressed in input units.
    std::array<double, range * 2 + 1> scores{};
    std::size_t shared = 0;
    for (const auto& [column, layers] : mov) {
        if (cancellation && cancellation->load()) return transform;
        const auto found = ref.find(column);
        if (found == ref.end()) continue;
        ++shared;
        const auto& target = found->second;
        const double weight = 1.0 / std::max(layers.size(), target.size());
        for (int offset = -range; offset <= range; ++offset) {
            for (auto layer : layers) {
                if (target.contains(layer + offset)) scores[offset + range] += weight;
            }
        }
    }
    if (shared < 30) return transform;
    int best = 0;
    for (int offset = -range; offset <= range; ++offset) {
        const double score = scores[offset + range];
        if (score > scores[best + range] + 1e-9 ||
            (std::abs(score - scores[best + range]) <= 1e-9 && std::abs(offset) < std::abs(best)))
            best = offset;
    }
    if (scores[best + range] < shared * .25 ||
        scores[best + range] < scores[range] + shared * .08) return transform;
    // Repeated levels or featureless walls do not determine a unique height.
    // Adjacent bins belong to the same peak and are left for ICP refinement.
    for (int offset = -range; offset <= range; ++offset) {
        if (std::abs(offset - best) > 1 &&
            scores[offset + range] >= scores[best + range] - shared * .02)
            return transform;
    }
    auto result = transform;
    result[2] += best * zCell;
    return result;
}
}
