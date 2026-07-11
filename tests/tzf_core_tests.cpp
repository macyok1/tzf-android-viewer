#include "tzf_core.h"

#include <array>
#include <cassert>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <string>
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

    std::filesystem::remove(path);
    return 0;
}
