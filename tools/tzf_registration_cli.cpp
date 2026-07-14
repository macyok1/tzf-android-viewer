#include "tzf_core.h"
#include "tzf_registration.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <exception>
#include <iomanip>
#include <iostream>
#include <string>

namespace {

std::vector<tzf::Point> load(const std::string& path, std::uint32_t limit) {
    tzf::BinaryFile file(path);
    const auto header = tzf::parseFileHeader(file);
    const auto scan = tzf::parseScanInfo(file, header.scanInfoOffset);
    const auto directory =
        tzf::parseBlockDirectory(file, header.blockDirectoryOffset);
    const auto validation = tzf::validateBlockDirectory(directory, file.size());
    if (!validation.empty()) throw std::runtime_error(validation);
    const auto preview = tzf::decodePointCloudPreview(
        file, header, scan, directory, limit, 1);
    return tzf::xyzToPoints(preview.xyz);
}

std::array<double, 4> pivotTransform(
    const std::vector<tzf::Point>& points,
    const std::array<double, 4>& direct) {
    double px = 0, py = 0;
    for (const auto& point : points) {
        px += point.x;
        py += point.y;
    }
    px /= std::max<std::size_t>(1, points.size());
    py /= std::max<std::size_t>(1, points.size());
    const double radians = direct[3] * 3.14159265358979323846 / 180.0;
    const double rotatedX = std::cos(radians) * px - std::sin(radians) * py;
    const double rotatedY = std::sin(radians) * px + std::cos(radians) * py;
    return {direct[0] - px + rotatedX, direct[1] - py + rotatedY,
            direct[2], direct[3]};
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 3 && argc != 7) {
        std::cerr << "usage: tzf-registration-cli <reference.tzf> <moving.tzf>"
                     " [x y z yaw]\n";
        return 2;
    }
    try {
        const auto reference = load(argv[1], 400000);
        const auto moving = load(argv[2], 400000);
        tzf::RegistrationResult result;
        if (argc == 7) {
            const std::array<double, 4> direct{
                std::stod(argv[3]), std::stod(argv[4]),
                std::stod(argv[5]), std::stod(argv[6])};
            tzf::RegistrationOptions options;
            options.rmsLimit = 3.0;
            options.p95Limit = 8.0;
            options.millimetreScale = 1.0;
            options.adaptiveResidualLimits = true;
            options.maximumInitialTranslationMeters = 2000.0;
            options.maximumInitialTranslationRatio = .10;
            options.maximumInitialYawDelta = 10.0;
            result = tzf::registerConstrained(
                reference, moving, pivotTransform(moving, direct), options);
        } else {
            tzf::GlobalRegistrationOptions options;
            options.refinement.rmsLimit = 3.0;
            options.refinement.p95Limit = 8.0;
            options.refinement.millimetreScale = 1.0;
            options.refinement.adaptiveResidualLimits = true;
            result = tzf::registerGlobalConstrained(reference, moving, options);
        }
        std::cout << std::fixed << std::setprecision(9)
                  << "accepted=" << result.accepted << '\n'
                  << "reason=" << result.reason << '\n'
                  << "transform=" << result.transform[0] << ','
                  << result.transform[1] << ',' << result.transform[2] << ','
                  << result.transform[3] << '\n'
                  << "rms=" << result.rms << '\n'
                  << "p95=" << result.p95 << '\n'
                  << "overlap=" << result.overlap << '\n'
                  << "consistency=" << result.consistency << '\n'
                  << "confidence=" << result.confidence << '\n';
        return result.accepted ? 0 : 3;
    } catch (const std::exception& error) {
        std::cerr << "error=" << error.what() << '\n';
        return 1;
    }
}
