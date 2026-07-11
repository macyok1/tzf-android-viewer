#pragma once

#include <array>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <functional>
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

struct SphericalPoint {
    float rho{};
    float azimuth{};
    float polar{};
};

struct Point {
    float x{};
    float y{};
    float z{};
};

struct PointCloudPreview {
    std::vector<float> xyz;
    std::uint64_t sourcePointCount{};
};

struct FileHeader {
    std::uint32_t headerSize{};
    std::uint64_t scanInfoOffset{};
    std::uint64_t fileEndOffset{};
    std::uint64_t blockDirectoryOffset{};
};

struct ScanInfo {
    std::uint32_t width{};
    std::uint32_t height{};
    std::uint32_t tileSize{};
    std::uint32_t validPointCount{};
};

using JpegDecoder = std::function<std::vector<std::uint8_t>(
    const std::vector<std::uint8_t>& jpeg, std::uint32_t expectedWidth,
    std::uint32_t expectedHeight)>;

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

class PreviewSession {
public:
    explicit PreviewSession(const std::filesystem::path& path);
    void prepare(std::uint32_t maxPoints);
    [[nodiscard]] std::vector<float> nextChunk(std::uint32_t maxPoints);
    void rewind() noexcept { cursor_ = 0; }
    [[nodiscard]] bool finished() const noexcept { return cursor_ >= xyz_.size(); }
    [[nodiscard]] std::uint64_t sourcePointCount() const noexcept { return scanInfo_.validPointCount; }
    [[nodiscard]] std::uint32_t preparedPointCount() const noexcept { return static_cast<std::uint32_t>(xyz_.size()/3U); }
private:
    BinaryFile file_;
    FileHeader fileHeader_;
    ScanInfo scanInfo_;
    BlockDirectory directory_;
    std::vector<float> xyz_;
    std::size_t cursor_{};
};

[[nodiscard]] BlockDirectory parseBlockDirectory(BinaryFile& file,
                                                 std::uint64_t tableOffset);
[[nodiscard]] FileHeader parseFileHeader(BinaryFile& file);
[[nodiscard]] ScanInfo parseScanInfo(BinaryFile& file,
                                     std::uint64_t scanInfoOffset);
[[nodiscard]] TileHeader parseTileHeader(BinaryFile& file,
                                         const BlockDescriptor& block);
[[nodiscard]] std::vector<std::uint8_t> decodeSnappy(
    const std::vector<std::uint8_t>& compressed);
[[nodiscard]] std::vector<std::uint8_t> undoTransposeDerive(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height);
[[nodiscard]] std::vector<std::uint8_t> undoRowDeriveTranspose(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height);
[[nodiscard]] std::vector<std::uint8_t> decodeTilePayload(
    BinaryFile& file, const BlockDescriptor& block,
    const TileHeader& header);
[[nodiscard]] std::vector<float> decodeIntensityLine(
    BinaryFile& file, const ScanInfo& scanInfo,
    const BlockDirectory& directory, std::uint32_t lineIndex,
    const JpegDecoder& jpegDecoder);
[[nodiscard]] std::vector<SphericalPoint> decodeSphericalLine(
    BinaryFile& file, const BlockDirectory& directory,
    std::uint32_t scanWidth, std::uint32_t scanHeight,
    std::uint32_t lineIndex);
[[nodiscard]] std::vector<SphericalPoint> decodeSphericalLine(
    BinaryFile& file, const FileHeader& fileHeader,
    const ScanInfo& scanInfo, const BlockDirectory& directory,
    std::uint32_t lineIndex);
[[nodiscard]] PointCloudPreview decodePointCloudPreview(
    BinaryFile& file, const FileHeader& fileHeader,
    const ScanInfo& scanInfo, const BlockDirectory& directory,
    std::uint32_t maxPoints, std::uint32_t tileStride = 1);
[[nodiscard]] Point sphericalToXyz(const SphericalPoint& point);
[[nodiscard]] std::string validateBlockDirectory(
    const BlockDirectory& directory, std::uint64_t fileSize);

} // namespace tzf
