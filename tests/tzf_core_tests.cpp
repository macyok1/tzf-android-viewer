#include "tzf_core.h"
#include "tzf_registration.h"

#include <array>
#include <cassert>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <cmath>
#include <vector>

namespace {

void appendU32(std::vector<std::uint8_t>& bytes, std::uint32_t value) {
    for (unsigned shift = 0; shift < 32; shift += 8) {
        bytes.push_back(static_cast<std::uint8_t>(value >> shift));
    }
}

void appendU64(std::vector<std::uint8_t>& bytes, std::uint64_t value) {
    appendU32(bytes, static_cast<std::uint32_t>(value));
    appendU32(bytes, static_cast<std::uint32_t>(value >> 32U));
}

void appendDescriptor(std::vector<std::uint8_t>& bytes, std::uint64_t offset,
                      std::uint32_t size) {
    appendU64(bytes, offset);
    appendU32(bytes, size);
}

std::filesystem::path writeFixture() {
    std::vector<std::uint8_t> bytes(16, 0);
    const std::array<std::uint8_t, 8> signature{1, 2, 3, 4, 5, 6, 7, 8};
    bytes.insert(bytes.end(), signature.begin(), signature.end());
    appendU32(bytes, 200);
    appendU32(bytes, 48);
    appendU32(bytes, 1);
    appendU32(bytes, 1);
    appendU32(bytes, 3);
    appendDescriptor(bytes, 100, 32);
    appendDescriptor(bytes, 140, 32);
    appendDescriptor(bytes, 180, 32);
    bytes.resize(256, 0);
    bytes[100] = 100;
    bytes[104] = 2;
    bytes[108] = 2;

    const auto path = std::filesystem::temp_directory_path() /
                      "tzf-core-directory-test.bin";
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    output.write(reinterpret_cast<const char*>(bytes.data()),
                 static_cast<std::streamsize>(bytes.size()));
    return path;
}

} // namespace

int main() {
    const tzf::SphericalPoint spherical{16861.1F, 2.5112741F,
                                        0.001558F};
    const auto xyz = tzf::sphericalToXyz(spherical);
    assert(std::abs(xyz.x - (-21.2216148F)) < 0.001F);
    assert(std::abs(xyz.y - 15.4833479F) < 0.001F);
    assert(std::abs(xyz.z - 16861.0801F) < 0.01F);

    const std::vector<std::uint8_t> snappy{
        10, 0x10, 'h', 'e', 'l', 'l', 'o', 0x12, 5, 0};
    const auto uncompressed = tzf::decodeSnappy(snappy);
    assert(std::string(uncompressed.begin(), uncompressed.end()) ==
           "hellohello");

    const std::vector<std::uint8_t> deltas{
        1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0,
        4, 0, 0, 0, 5, 0, 0, 0, 6, 0, 0, 0};
    const auto restored = tzf::undoTransposeDerive(deltas, 2, 3);
    const std::vector<std::uint8_t> expected{
        1, 0, 0, 0, 10, 0, 0, 0, 3, 0, 0, 0,
        15, 0, 0, 0, 6, 0, 0, 0, 21, 0, 0, 0};
    assert(restored == expected);

    const std::vector<std::uint8_t> rowDeltas{
        1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0,
        4, 0, 0, 0, 5, 0, 0, 0, 6, 0, 0, 0};
    const auto rowRestored = tzf::undoRowDeriveTranspose(rowDeltas, 2, 3);
    const std::vector<std::uint8_t> rowExpected{
        1, 0, 0, 0, 4, 0, 0, 0, 3, 0, 0, 0,
        9, 0, 0, 0, 6, 0, 0, 0, 15, 0, 0, 0};
    assert(rowRestored == rowExpected);

    const auto path = writeFixture();
    tzf::BinaryFile file(path);
    const auto directory = tzf::parseBlockDirectory(file, 16);

    assert(directory.payloadSize == 48);
    assert(directory.componentCount == 1);
    assert(directory.components.size() == 1);
    assert(directory.components[0].id == 1);
    assert(directory.components[0].blocks.size() == 3);
    assert(directory.components[0].blocks[0].offset == 100);
    assert(directory.components[0].blocks[1].size == 32);
    assert(directory.components[0].blocks[2].offset == 180);
    assert(tzf::validateBlockDirectory(directory, file.size()).empty());
    const auto tile =
        tzf::parseTileHeader(file, directory.components[0].blocks[0]);
    assert(tile.marker == 100);
    assert(tile.width == 2);
    assert(tile.height == 2);

    auto invalid = directory;
    invalid.components[0].blocks[2] = {255, 2};
    assert(!tzf::validateBlockDirectory(invalid, file.size()).empty());

    std::vector<tzf::Point> moving;
    for (int face = 0; face < 3; ++face) {
        for (int a = -10; a <= 10; ++a) {
            for (int b = -10; b <= 10; ++b) {
                const float u = a * 0.1F, v = b * 0.1F;
                if (face == 0) moving.push_back({-1.0F, u, v});
                if (face == 1) moving.push_back({u, 1.0F, v});
                if (face == 2) moving.push_back({u, v, -1.0F});
            }
        }
    }
    constexpr double angle = 7.0 * 3.14159265358979323846 / 180.0;
    const double cosine = std::cos(angle), sine = std::sin(angle);
    tzf::Point movingCenter{};
    for (const auto point : moving) {
        movingCenter.x += point.x; movingCenter.y += point.y; movingCenter.z += point.z;
    }
    movingCenter.x /= moving.size(); movingCenter.y /= moving.size(); movingCenter.z /= moving.size();
    std::vector<tzf::Point> reference;
    reference.reserve(moving.size());
    for (const auto point : moving) {
        const double x = point.x - movingCenter.x;
        const double y = point.y - movingCenter.y;
        reference.push_back({
            static_cast<float>(movingCenter.x + cosine * x - sine * y + 0.15),
            static_cast<float>(movingCenter.y + sine * x + cosine * y - 0.08),
            point.z + 0.04F});
    }
    tzf::RegistrationOptions registrationOptions;
    registrationOptions.rmsLimit = 0.03;
    registrationOptions.p95Limit = 0.06;
    registrationOptions.minimumOverlap = 0.5;
    const auto registration = tzf::registerConstrained(
        reference, moving, {0.10, -0.04, 0.02, 4.0}, registrationOptions);
    if (!registration.accepted) {
        std::cerr << "registration rejected: " << registration.reason
                  << " rms=" << registration.rms << " p95=" << registration.p95
                  << " overlap=" << registration.overlap << " transform="
                  << registration.transform[0] << ',' << registration.transform[1]
                  << ',' << registration.transform[2] << ',' << registration.transform[3]
                  << '\n';
    }
    assert(registration.accepted);
    assert(std::abs(registration.transform[0] - 0.15) < 0.02);
    assert(std::abs(registration.transform[1] + 0.08) < 0.02);
    assert(std::abs(registration.transform[2] - 0.04) < 0.02);
    assert(std::abs(registration.transform[3] - 7.0) < 0.5);

    auto warningOptions = registrationOptions;
    warningOptions.rmsLimit = 1e-12;
    warningOptions.p95Limit = 1e-12;
    const auto warning = tzf::registerConstrained(
        reference, moving, {0.10, -0.04, 0.02, 4.0}, warningOptions);
    assert(warning.accepted);
    assert(warning.reason.starts_with("quality warning:"));

    const auto rejected = tzf::registerConstrained(
        std::vector<tzf::Point>(20), std::vector<tzf::Point>(20), {});
    assert(!rejected.accepted);
    assert(rejected.reason == "not enough points");

    std::filesystem::remove(path);
    return 0;
}
