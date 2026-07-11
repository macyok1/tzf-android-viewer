#pragma once

#include <array>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

namespace tzf {

struct BlockDescriptor {
    std::uint64_t offset{};
    std::uint32_t size{};

    [[nodiscard]] bool empty() const noexcept { return size == 0; }
};

struct TileHeader {
    std::uint32_t marker{};
    std::uint32_t width{};
    std::uint32_t height{};
    std::uint32_t uncompressedSize{};
    float scale{};
    std::uint64_t codecId{};
};

struct ComponentBlocks {
    std::uint32_t id{};
    std::vector<BlockDescriptor> blocks;
};

struct BlockDirectory {
    std::uint64_t fileOffset{};
    std::array<std::uint8_t, 8> signature{};
    std::uint32_t headerValue{};
    std::uint32_t payloadSize{};
    std::uint32_t componentCount{};
    std::vector<ComponentBlocks> components;
};

class BinaryFile {
public:
    explicit BinaryFile(const std::filesystem::path& path);

    [[nodiscard]] std::uint64_t size() const noexcept { return size_; }
    [[nodiscard]] std::vector<std::uint8_t> read(std::uint64_t offset,
                                                  std::uint64_t length);

private:
    std::ifstream stream_;
    std::uint64_t size_{};
};

[[nodiscard]] BlockDirectory parseBlockDirectory(BinaryFile& file,
                                                 std::uint64_t tableOffset);
[[nodiscard]] TileHeader parseTileHeader(BinaryFile& file,
                                         const BlockDescriptor& block);
[[nodiscard]] std::vector<std::uint8_t> decodeSnappy(
    const std::vector<std::uint8_t>& compressed);
[[nodiscard]] std::vector<std::uint8_t> undoTransposeDerive(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height);
[[nodiscard]] std::vector<std::uint8_t> decodeTilePayload(
    BinaryFile& file, const BlockDescriptor& block,
    const TileHeader& header);
[[nodiscard]] std::string validateBlockDirectory(
    const BlockDirectory& directory, std::uint64_t fileSize);

} // namespace tzf
