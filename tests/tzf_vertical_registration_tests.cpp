#include "tzf_registration.h"
#include "tzf_vertical_seed.h"
#include <cmath>
#include <iostream>
#include <vector>

int main() {
    int failures = 0;
    for (double scale : {1.0, 1000.0}) {
        for (double height : {-.8, .8, -.47, .83, 0.0}) {
            std::vector<tzf::Point> moving, reference;
            for (int a = -20; a <= 20; ++a) {
                for (int b = -20; b <= 20; ++b) {
                    const float u = a * .1F, v = b * .1F;
                    moving.push_back({float(-2 * scale), float(u * scale), float(v * scale)});
                    moving.push_back({float(u * scale), float(2 * scale), float(v * scale)});
                    moving.push_back({float(u * scale), float(v * scale), float(-2 * scale)});
                }
            }
            for (auto p : moving) {
                p.z += float(height * scale);
                reference.push_back(p);
            }
            tzf::RegistrationOptions options;
            options.millimetreScale = .001 * scale;
            options.rmsLimit = .03 * scale;
            options.p95Limit = .06 * scale;
            options.minimumOverlap = .5;
            const auto result = tzf::registerConstrained(reference, moving, {0,0,0,0}, options);
            const bool pass = result.accepted && std::abs(result.transform[2] / scale - height) < .03
                && std::hypot(result.transform[0], result.transform[1]) / scale < .03;
            std::cout << "scale=" << scale << " height=" << height << " accepted=" << result.accepted
                      << " z=" << result.transform[2] / scale << " reason=" << result.reason << '\n';
            failures += !pass;
            if (height == .8) {
                auto guarded = options;
                guarded.maximumInitialTranslationMeters = .2 * scale;
                const auto limited = tzf::registerConstrained(reference, moving, {0,0,0,0}, guarded);
                failures += limited.accepted || limited.transform != std::array<double,4>{0,0,0,0};
                std::atomic_bool cancelled{true};
                guarded.cancellation = &cancelled;
                const auto stopped = tzf::registerConstrained(reference, moving, {0,0,0,0}, guarded);
                failures += stopped.accepted || stopped.reason != "cancelled";
                // Sparse moving floor, duplicate reference walls: occupancy
                // support must not depend on point density.
                std::vector<tzf::Point> sparse;
                for (std::size_t i = 0; i < moving.size(); ++i)
                    if (i % 3 != 2 || i % 9 == 2) sparse.push_back(moving[i]);
                auto dense = reference;
                for (std::size_t i = 0; i < reference.size(); ++i)
                    if (i % 3 != 2) dense.push_back(reference[i]);
                const auto density = tzf::registerConstrained(dense, sparse, {0,0,0,0}, options);
                failures += !density.accepted || std::abs(density.transform[2] / scale - height) >= .03;
                std::cout << "guards=" << !limited.accepted << " cancel=" << !stopped.accepted
                          << " densityZ=" << density.transform[2] / scale << '\n';
                const auto noOverlap = tzf::seedVerticalTranslation(reference, moving,
                    {20*scale,20*scale,0,0}, options.millimetreScale);
                failures += noOverlap[2] != 0;
            }
        }
    }
    return failures ? 1 : 0;
}
