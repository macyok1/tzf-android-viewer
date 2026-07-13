#include "tzf_core.h"

#include <algorithm>
#include <limits>
#include <cstring>
#include <cmath>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace tzf {
namespace {

std::uint32_t readU32(const std::uint8_t* p) {
    return static_cast<std::uint32_t>(p[0]) |
           (static_cast<std::uint32_t>(p[1]) << 8U) |
           (static_cast<std::uint32_t>(p[2]) << 16U) |
           (static_cast<std::uint32_t>(p[3]) << 24U);
}

std::uint64_t readU64(const std::uint8_t* p) {
    return static_cast<std::uint64_t>(readU32(p)) |
           (static_cast<std::uint64_t>(readU32(p + 4)) << 32U);
}

std::int32_t readI32(const std::uint8_t* p) {
    const auto bits = readU32(p);
    std::int32_t value{};
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

bool rangeFits(std::uint64_t offset, std::uint64_t length,
               std::uint64_t total) {
    return offset <= total && length <= total - offset;
}

std::uint32_t readVarint32(const std::vector<std::uint8_t>& input,
                           std::size_t& cursor) {
    std::uint32_t result = 0;
    for (unsigned shift = 0; shift < 32; shift += 7) {
        if (cursor >= input.size()) {
            throw std::runtime_error("truncated Snappy length");
        }
        const auto byte = input[cursor++];
        result |= static_cast<std::uint32_t>(byte & 0x7fU) << shift;
        if ((byte & 0x80U) == 0) {
            return result;
        }
    }
    throw std::runtime_error("invalid Snappy length varint");
}

std::uint32_t readLittle(const std::vector<std::uint8_t>& input,
                         std::size_t& cursor, unsigned byteCount) {
    if (cursor > input.size() || byteCount > input.size() - cursor) {
        throw std::runtime_error("truncated Snappy tag");
    }
    std::uint32_t value = 0;
    for (unsigned i = 0; i < byteCount; ++i) {
        value |= static_cast<std::uint32_t>(input[cursor++]) << (8U * i);
    }
    return value;
}

void appendCopy(std::vector<std::uint8_t>& output, std::uint32_t offset,
                std::uint32_t length, std::uint32_t expectedSize) {
    if (offset == 0 || offset > output.size()) {
        throw std::runtime_error("invalid Snappy copy offset");
    }
    if (length > expectedSize - output.size()) {
        throw std::runtime_error("Snappy copy exceeds declared size");
    }
    for (std::uint32_t i = 0; i < length; ++i) {
        output.push_back(output[output.size() - offset]);
    }
}

std::vector<std::uint8_t> transposeU32(
    const std::vector<std::uint32_t>& source, std::uint32_t width,
    std::uint32_t height) {
    std::vector<std::uint8_t> output(source.size() * 4U);
    for (std::uint32_t x = 0; x < width; ++x) {
        for (std::uint32_t y = 0; y < height; ++y) {
            const auto sourceIndex =
                static_cast<std::size_t>(x) * height + y;
            const auto destination =
                static_cast<std::size_t>(y) * width + x;
            const auto value = source[sourceIndex];
            output[destination * 4U] = static_cast<std::uint8_t>(value);
            output[destination * 4U + 1] =
                static_cast<std::uint8_t>(value >> 8U);
            output[destination * 4U + 2] =
                static_cast<std::uint8_t>(value >> 16U);
            output[destination * 4U + 3] =
                static_cast<std::uint8_t>(value >> 24U);
        }
    }
    return output;
}

std::vector<std::uint32_t> bytesToU32(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height) {
    const auto valueCount = static_cast<std::uint64_t>(width) * height;
    if (valueCount > std::numeric_limits<std::size_t>::max() / 4U ||
        encoded.size() != valueCount * 4U) {
        throw std::runtime_error("transform dimensions do not match data");
    }
    std::vector<std::uint32_t> values(static_cast<std::size_t>(valueCount));
    for (std::size_t i = 0; i < values.size(); ++i) {
        values[i] = readU32(encoded.data() + i * 4U);
    }
    return values;
}

const ComponentBlocks& requireComponent(const BlockDirectory& directory,
                                        std::uint32_t id) {
    for (const auto& component : directory.components) {
        if (component.id == id) {
            return component;
        }
    }
    throw std::runtime_error("required TZF component is missing");
}

} // namespace

BinaryFile::BinaryFile(const std::filesystem::path& path)
    : stream_(path, std::ios::binary) {
    if (!stream_) {
        throw std::runtime_error("cannot open TZF file");
    }
    stream_.seekg(0, std::ios::end);
    const auto end = stream_.tellg();
    if (end < 0) {
        throw std::runtime_error("cannot determine TZF file size");
    }
    size_ = static_cast<std::uint64_t>(end);
}

FileHeader parseFileHeader(BinaryFile& file) {
    constexpr std::uint32_t minimumHeaderSize = 0x4c;
    const auto bytes = file.read(0, minimumHeaderSize);
    FileHeader header;
    header.headerSize = readU32(bytes.data() + 0x0c);
    header.scanInfoOffset = readU64(bytes.data() + 0x10);
    header.fileEndOffset = readU64(bytes.data() + 0x28);
    header.blockDirectoryOffset = readU64(bytes.data() + 0x40);
    if (header.headerSize < minimumHeaderSize ||
        header.headerSize > file.size() ||
        header.scanInfoOffset >= file.size() ||
        header.blockDirectoryOffset >= file.size() ||
        header.fileEndOffset > file.size()) {
        throw std::runtime_error("invalid TZF main header offsets");
    }
    return header;
}

ScanInfo parseScanInfo(BinaryFile& file, std::uint64_t scanInfoOffset) {
    constexpr std::uint32_t requiredSize = 0x40;
    const auto bytes = file.read(scanInfoOffset, requiredSize);
    ScanInfo info;
    info.width = readU32(bytes.data() + 0x30);
    info.height = readU32(bytes.data() + 0x34);
    info.tileSize = readU32(bytes.data() + 0x38);
    info.validPointCount = readU32(bytes.data() + 0x3c);
    if (info.width == 0 || info.height == 0 || info.tileSize == 0) {
        throw std::runtime_error("invalid TZF scan dimensions");
    }
    return info;
}

std::vector<std::uint8_t> BinaryFile::read(std::uint64_t offset,
                                           std::uint64_t length) {
    if (!rangeFits(offset, length, size_)) {
        throw std::runtime_error("TZF read is outside file bounds");
    }
    if (length > static_cast<std::uint64_t>(
                     std::numeric_limits<std::size_t>::max())) {
        throw std::runtime_error("TZF read is too large for this platform");
    }

    std::vector<std::uint8_t> result(static_cast<std::size_t>(length));
    stream_.clear();
    stream_.seekg(static_cast<std::streamoff>(offset), std::ios::beg);
    if (!stream_) {
        throw std::runtime_error("cannot seek in TZF file");
    }
    stream_.read(reinterpret_cast<char*>(result.data()),
                 static_cast<std::streamsize>(result.size()));
    if (stream_.gcount() != static_cast<std::streamsize>(result.size())) {
        throw std::runtime_error("short read from TZF file");
    }
    return result;
}

BlockDirectory parseBlockDirectory(BinaryFile& file, std::uint64_t tableOffset) {
    constexpr std::uint64_t headerSize = 0x14;
    constexpr std::uint32_t descriptorSize = 12;

    const auto header = file.read(tableOffset, headerSize);
    BlockDirectory directory;
    directory.fileOffset = tableOffset;
    for (std::size_t i = 0; i < directory.signature.size(); ++i) {
        directory.signature[i] = header[i];
    }
    directory.headerValue = readU32(header.data() + 0x08);
    directory.payloadSize = readU32(header.data() + 0x0c);
    directory.componentCount = readU32(header.data() + 0x10);

    const auto payload = file.read(tableOffset + 0x10,
                                   directory.payloadSize);
    std::size_t cursor = 4; // componentCount is the first payload field.
    directory.components.reserve(directory.componentCount);
    for (std::uint32_t componentIndex = 0;
         componentIndex < directory.componentCount; ++componentIndex) {
        if (cursor > payload.size() || payload.size() - cursor < 8) {
            throw std::runtime_error("truncated block component header");
        }
        ComponentBlocks component;
        component.id = readU32(payload.data() + cursor);
        const auto descriptorCount = readU32(payload.data() + cursor + 4);
        cursor += 8;
        if (descriptorCount > (payload.size() - cursor) / descriptorSize) {
            throw std::runtime_error("truncated block descriptor array");
        }
        component.blocks.reserve(descriptorCount);
        for (std::uint32_t i = 0; i < descriptorCount; ++i) {
            const auto* descriptor = payload.data() + cursor;
            component.blocks.push_back(
                {readU64(descriptor), readU32(descriptor + 8)});
            cursor += descriptorSize;
        }
        directory.components.push_back(std::move(component));
    }
    if (cursor != payload.size()) {
        throw std::runtime_error("unparsed bytes remain in block directory");
    }
    return directory;
}

TileHeader parseTileHeader(BinaryFile& file, const BlockDescriptor& block) {
    constexpr std::uint32_t tileHeaderSize = 28;
    if (block.size < tileHeaderSize) {
        throw std::runtime_error("TZF tile is smaller than its header");
    }
    const auto bytes = file.read(block.offset, tileHeaderSize);
    TileHeader header;
    header.marker = readU32(bytes.data());
    header.width = readU32(bytes.data() + 4);
    header.height = readU32(bytes.data() + 8);
    header.uncompressedSize = readU32(bytes.data() + 12);
    const auto scaleBits = readU32(bytes.data() + 16);
    static_assert(sizeof(header.scale) == sizeof(scaleBits));
    std::memcpy(&header.scale, &scaleBits, sizeof(header.scale));
    header.codecId = readU64(bytes.data() + 20);
    return header;
}

std::vector<std::uint8_t> decodeSnappy(
    const std::vector<std::uint8_t>& compressed) {
    std::size_t cursor = 0;
    const auto expectedSize = readVarint32(compressed, cursor);
    std::vector<std::uint8_t> output;
    output.reserve(expectedSize);

    while (cursor < compressed.size() && output.size() < expectedSize) {
        const auto tag = compressed[cursor++];
        const auto type = tag & 0x03U;
        if (type == 0) {
            std::uint32_t length = tag >> 2U;
            if (length < 60) {
                ++length;
            } else {
                const auto byteCount = length - 59;
                length = readLittle(compressed, cursor, byteCount) + 1;
            }
            if (cursor > compressed.size() ||
                length > compressed.size() - cursor ||
                length > expectedSize - output.size()) {
                throw std::runtime_error("invalid Snappy literal length");
            }
            output.insert(output.end(), compressed.begin() + cursor,
                          compressed.begin() + cursor + length);
            cursor += length;
            continue;
        }

        std::uint32_t length = 0;
        std::uint32_t offset = 0;
        if (type == 1) {
            length = 4 + ((tag >> 2U) & 0x07U);
            offset = ((static_cast<std::uint32_t>(tag) & 0xe0U) << 3U) |
                     readLittle(compressed, cursor, 1);
        } else if (type == 2) {
            length = 1 + (tag >> 2U);
            offset = readLittle(compressed, cursor, 2);
        } else {
            length = 1 + (tag >> 2U);
            offset = readLittle(compressed, cursor, 4);
        }
        appendCopy(output, offset, length, expectedSize);
    }

    if (output.size() != expectedSize || cursor != compressed.size()) {
        throw std::runtime_error("Snappy stream size mismatch");
    }
    return output;
}

std::vector<std::uint8_t> undoTransposeDerive(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height) {
    auto derived = bytesToU32(encoded, width, height);
    for (std::size_t i = 0; i < derived.size(); ++i) {
        if (i != 0) {
            derived[i] += derived[i - 1]; // uint32 wrap is intentional.
        }
    }
    return transposeU32(derived, width, height);
}

std::vector<std::uint8_t> undoRowDeriveTranspose(
    const std::vector<std::uint8_t>& encoded, std::uint32_t width,
    std::uint32_t height) {
    auto derived = bytesToU32(encoded, width, height);
    for (std::uint32_t x = 0; x < width; ++x) {
        const auto rowStart = static_cast<std::size_t>(x) * height;
        for (std::uint32_t y = 1; y < height; ++y) {
            derived[rowStart + y] += derived[rowStart + y - 1];
        }
    }
    return transposeU32(derived, width, height);
}

std::vector<std::uint8_t> decodeTilePayload(BinaryFile& file,
                                            const BlockDescriptor& block,
                                            const TileHeader& header) {
    constexpr std::uint32_t tileHeaderSize = 28;
    constexpr std::uint64_t snappyCodec = 0x839a721b429840b9ULL;
    constexpr std::uint64_t snappyTransposeDerive =
        0xafec6c11bc26b6c5ULL;
    constexpr std::uint64_t fwhaSnap2 = 0xa4fd4753b90ff7acULL;
    constexpr std::uint64_t fwvaSnap2 = 0xa0da156d68656d0fULL;
    constexpr std::uint64_t fwsdSnap2 = 0x93d2c23edc95d64dULL;
    if (header.codecId != snappyCodec &&
        header.codecId != snappyTransposeDerive &&
        header.codecId != fwhaSnap2 && header.codecId != fwvaSnap2 &&
        header.codecId != fwsdSnap2) {
        throw std::runtime_error("tile codec is not a Snappy variant");
    }
    const auto compressed =
        file.read(block.offset + tileHeaderSize, block.size - tileHeaderSize);
    auto decoded = decodeSnappy(compressed);
    if (decoded.size() != header.uncompressedSize) {
        throw std::runtime_error("tile uncompressed size mismatch");
    }
    if (header.codecId == snappyTransposeDerive) {
        return undoTransposeDerive(decoded, header.width, header.height);
    }
    if (header.codecId == fwvaSnap2 || header.codecId == fwsdSnap2) {
        return undoRowDeriveTranspose(decoded, header.width, header.height);
    }
    if (header.codecId == fwhaSnap2) {
        return transposeU32(bytesToU32(decoded, header.width, header.height),
                            header.width, header.height);
    }
    return decoded;
}

std::vector<float> decodeIntensityLine(
    BinaryFile& file, const ScanInfo& scanInfo,
    const BlockDirectory& directory, std::uint32_t lineIndex,
    const JpegDecoder& jpegDecoder) {
    constexpr std::uint32_t tileHeaderSize = 28;
    constexpr std::uint64_t jpegCodec = 0xb2ebc9519992d032ULL;
    if (scanInfo.tileSize != 512 || lineIndex >= scanInfo.height ||
        !jpegDecoder) {
        throw std::runtime_error("invalid intensity line request");
    }
    const auto tileColumns =
        (scanInfo.width + scanInfo.tileSize - 1U) / scanInfo.tileSize;
    const auto tileRows =
        (scanInfo.height + scanInfo.tileSize - 1U) / scanInfo.tileSize;
    const auto& intensity = requireComponent(directory, 4);
    if (intensity.blocks.size() !=
        static_cast<std::uint64_t>(tileColumns) * tileRows) {
        throw std::runtime_error("intensity tile count does not match scan");
    }

    std::vector<float> result(scanInfo.width);
    const auto tileY = lineIndex / scanInfo.tileSize;
    const auto localY = lineIndex % scanInfo.tileSize;
    for (std::uint32_t tileX = 0; tileX < tileColumns; ++tileX) {
        const auto tileIndex = static_cast<std::size_t>(tileX) * tileRows + tileY;
        const auto& block = intensity.blocks[tileIndex];
        const auto header = parseTileHeader(file, block);
        if (header.codecId != jpegCodec || header.width != scanInfo.tileSize ||
            header.height != scanInfo.tileSize || block.size < tileHeaderSize) {
            throw std::runtime_error("unsupported intensity tile");
        }
        const auto jpeg = file.read(block.offset + tileHeaderSize,
                                    block.size - tileHeaderSize);
        const auto pixels = jpegDecoder(jpeg, header.width, header.height);
        const auto pixelCount =
            static_cast<std::uint64_t>(header.width) * header.height;
        if (pixels.size() != pixelCount) {
            throw std::runtime_error("JPEG decoder returned unexpected size");
        }

        const auto firstX = tileX * scanInfo.tileSize;
        const auto xCount =
            std::min(scanInfo.tileSize, scanInfo.width - firstX);
        for (std::uint32_t localX = 0; localX < xCount; ++localX) {
            const auto pixelIndex =
                static_cast<std::size_t>(localX) * header.width + localY;
            result[firstX + localX] =
                static_cast<float>(pixels[pixelIndex]) / 255.0F;
        }
    }
    return result;
}

std::vector<SphericalPoint> decodeSphericalLine(
    BinaryFile& file, const BlockDirectory& directory,
    std::uint32_t scanWidth, std::uint32_t scanHeight,
    std::uint32_t lineIndex) {
    constexpr std::uint32_t tileSize = 512;
    if (scanWidth == 0 || scanHeight == 0 || lineIndex >= scanHeight) {
        throw std::runtime_error("invalid TZF scan line dimensions");
    }
    const auto tileColumns = (scanWidth + tileSize - 1U) / tileSize;
    const auto tileRows = (scanHeight + tileSize - 1U) / tileSize;
    const auto tileCount = static_cast<std::uint64_t>(tileColumns) * tileRows;
    const auto& rhoComponent = requireComponent(directory, 1);
    const auto& polarComponent = requireComponent(directory, 2);
    const auto& azimuthComponent = requireComponent(directory, 3);
    if (rhoComponent.blocks.size() != tileCount ||
        polarComponent.blocks.size() != tileCount ||
        azimuthComponent.blocks.size() != tileCount) {
        throw std::runtime_error("TZF component tile count does not match scan");
    }

    std::vector<SphericalPoint> result(scanWidth);
    const auto tileY = lineIndex / tileSize;
    const auto localY = lineIndex % tileSize;
    for (std::uint32_t tileX = 0; tileX < tileColumns; ++tileX) {
        const auto tileIndex = static_cast<std::size_t>(tileX) * tileRows + tileY;
        const std::array<const BlockDescriptor*, 3> blocks{
            &rhoComponent.blocks[tileIndex],
            &polarComponent.blocks[tileIndex],
            &azimuthComponent.blocks[tileIndex]};
        std::array<TileHeader, 3> headers;
        std::array<std::vector<std::uint8_t>, 3> channels;
        for (std::size_t channel = 0; channel < channels.size(); ++channel) {
            headers[channel] = parseTileHeader(file, *blocks[channel]);
            channels[channel] =
                decodeTilePayload(file, *blocks[channel], headers[channel]);
            if (headers[channel].width != tileSize ||
                headers[channel].height != tileSize ||
                headers[channel].scale == 0.0F) {
                throw std::runtime_error("unsupported TZF tile geometry or scale");
            }
        }

        const auto firstX = tileX * tileSize;
        const auto xCount = std::min(tileSize, scanWidth - firstX);
        for (std::uint32_t localX = 0; localX < xCount; ++localX) {
            const auto valueIndex =
                static_cast<std::size_t>(localX) * tileSize + localY;
            const auto rho = readU32(channels[0].data() + valueIndex * 4U);
            const auto polar = readI32(channels[1].data() + valueIndex * 4U);
            const auto azimuth =
                readI32(channels[2].data() + valueIndex * 4U);
            auto& point = result[firstX + localX];
            point.rho = static_cast<float>(rho) / headers[0].scale;
            point.polar = static_cast<float>(polar) / headers[1].scale;
            point.azimuth =
                static_cast<float>(azimuth) / headers[2].scale;
        }
    }
    return result;
}

std::vector<SphericalPoint> decodeSphericalLine(
    BinaryFile& file, const FileHeader& fileHeader,
    const ScanInfo& scanInfo, const BlockDirectory& directory,
    std::uint32_t lineIndex) {
    (void)fileHeader;
    if (scanInfo.tileSize != 512) {
        throw std::runtime_error("unsupported TZF tile size");
    }
    return decodeSphericalLine(file, directory, scanInfo.width,
                               scanInfo.height, lineIndex);
}

PointCloudPreview decodePointCloudPreview(
    BinaryFile& file, const FileHeader& fileHeader,
    const ScanInfo& scanInfo, const BlockDirectory& directory,
    std::uint32_t maxPoints, std::uint32_t tileStride) {
    (void)fileHeader;
    constexpr std::uint32_t tileSize = 512;
    if (scanInfo.tileSize != tileSize || maxPoints == 0 || tileStride == 0) {
        throw std::runtime_error("unsupported TZF preview request");
    }

    const auto tileColumns = (scanInfo.width + tileSize - 1U) / tileSize;
    const auto tileRows = (scanInfo.height + tileSize - 1U) / tileSize;
    const auto tileCount = static_cast<std::uint64_t>(tileColumns) * tileRows;
    const auto& rhoComponent = requireComponent(directory, 1);
    const auto& polarComponent = requireComponent(directory, 2);
    const auto& azimuthComponent = requireComponent(directory, 3);
    if (rhoComponent.blocks.size() != tileCount ||
        polarComponent.blocks.size() != tileCount ||
        azimuthComponent.blocks.size() != tileCount) {
        throw std::runtime_error("TZF component tile count does not match scan");
    }

    const auto gridPointCount = static_cast<std::uint64_t>(scanInfo.width) *
                                scanInfo.height;
    const auto stride = maxPoints >= scanInfo.validPointCount ? 1U :
        std::max<std::uint32_t>(1U, static_cast<std::uint32_t>(std::ceil(
            std::sqrt(static_cast<double>(gridPointCount) / maxPoints))));
    PointCloudPreview preview;
    preview.sourcePointCount = scanInfo.validPointCount;
    preview.xyz.reserve(static_cast<std::size_t>(maxPoints) * 3U);

    for (std::uint32_t tileX = 0; tileX < tileColumns; tileX += tileStride) {
        for (std::uint32_t tileY = 0; tileY < tileRows; tileY += tileStride) {
            const auto tileIndex =
                static_cast<std::size_t>(tileX) * tileRows + tileY;
            const std::array<const BlockDescriptor*, 3> blocks{
                &rhoComponent.blocks[tileIndex],
                &polarComponent.blocks[tileIndex],
                &azimuthComponent.blocks[tileIndex]};
            std::array<TileHeader, 3> headers;
            std::array<std::vector<std::uint8_t>, 3> channels;
            for (std::size_t channel = 0; channel < channels.size(); ++channel) {
                headers[channel] = parseTileHeader(file, *blocks[channel]);
                channels[channel] =
                    decodeTilePayload(file, *blocks[channel], headers[channel]);
                if (headers[channel].width != tileSize ||
                    headers[channel].height != tileSize ||
                    headers[channel].scale == 0.0F) {
                    throw std::runtime_error("unsupported TZF tile geometry or scale");
                }
            }

            const auto firstX = tileX * tileSize;
            const auto firstY = tileY * tileSize;
            const auto xCount = std::min(tileSize, scanInfo.width - firstX);
            const auto yCount = std::min(tileSize, scanInfo.height - firstY);
            for (std::uint32_t localX = 0; localX < xCount; ++localX) {
                const auto x = firstX + localX;
                if (x % stride != 0) continue;
                for (std::uint32_t localY = 0; localY < yCount; ++localY) {
                    const auto y = firstY + localY;
                    if (y % stride != 0) continue;
                    const auto valueIndex =
                        static_cast<std::size_t>(localX) * tileSize + localY;
                    const auto rho = readU32(channels[0].data() + valueIndex * 4U);
                    if (rho == 0) continue;
                    const SphericalPoint spherical{
                        static_cast<float>(rho) / headers[0].scale,
                        static_cast<float>(readI32(channels[2].data() +
                                                   valueIndex * 4U)) /
                            headers[2].scale,
                        static_cast<float>(readI32(channels[1].data() +
                                                   valueIndex * 4U)) /
                            headers[1].scale};
                    const auto point = sphericalToXyz(spherical);
                    if (!std::isfinite(point.x) || !std::isfinite(point.y) ||
                        !std::isfinite(point.z)) continue;
                    preview.xyz.insert(preview.xyz.end(),
                                       {point.x, point.y, point.z});
                    if (preview.xyz.size() / 3U >= maxPoints) return preview;
                }
            }
        }
    }
    return preview;
}

PreviewSession::PreviewSession(const std::filesystem::path& path)
    : file_(path), fileHeader_(parseFileHeader(file_)),
      scanInfo_(parseScanInfo(file_, fileHeader_.scanInfoOffset)),
      directory_(parseBlockDirectory(file_, fileHeader_.blockDirectoryOffset)) {
    const auto validation = validateBlockDirectory(directory_, file_.size());
    if (!validation.empty()) throw std::runtime_error(validation);
}

void PreviewSession::prepare(std::uint32_t maxPoints) {
    if (maxPoints == 0) throw std::runtime_error("preview point limit is zero");
    constexpr std::uint32_t tileSize = 512;
    if (scanInfo_.tileSize != tileSize) {
        throw std::runtime_error("unsupported TZF tile size");
    }
    tileColumns_ = (scanInfo_.width + tileSize - 1U) / tileSize;
    tileRows_ = (scanInfo_.height + tileSize - 1U) / tileSize;
    const auto gridPointCount = static_cast<std::uint64_t>(scanInfo_.width) *
                                scanInfo_.height;
    sampleStride_ = maxPoints >= scanInfo_.validPointCount ? 1U :
        std::max<std::uint32_t>(1U, static_cast<std::uint32_t>(std::ceil(
            std::sqrt(static_cast<double>(gridPointCount) / maxPoints))));
    maxPoints_ = maxPoints;
    rewind();
}

std::vector<float> PreviewSession::nextChunk(std::uint32_t maxPoints) {
    constexpr std::uint32_t tileSize = 512;
    if (maxPoints == 0 || finished()) return {};
    std::vector<float> chunk;
    chunk.reserve(static_cast<std::size_t>(std::min(
        maxPoints, maxPoints_ - emittedPoints_)) * 3U);
    const auto& rhoComponent = requireComponent(directory_, 1);
    const auto& polarComponent = requireComponent(directory_, 2);
    const auto& azimuthComponent = requireComponent(directory_, 3);
    const auto expectedTiles = static_cast<std::uint64_t>(tileColumns_) * tileRows_;
    if (rhoComponent.blocks.size() != expectedTiles ||
        polarComponent.blocks.size() != expectedTiles ||
        azimuthComponent.blocks.size() != expectedTiles) {
        throw std::runtime_error("TZF component tile count does not match scan");
    }

    while (chunk.size() / 3U < maxPoints && !finished()) {
        if (!tileLoaded_) {
            const auto tileIndex = static_cast<std::size_t>(tileX_) * tileRows_ + tileY_;
            const std::array<const BlockDescriptor*,3> blocks{
                &rhoComponent.blocks[tileIndex], &polarComponent.blocks[tileIndex],
                &azimuthComponent.blocks[tileIndex]};
            for (std::size_t channel = 0; channel < tileChannels_.size(); ++channel) {
                tileHeaders_[channel] = parseTileHeader(file_, *blocks[channel]);
                tileChannels_[channel] = decodeTilePayload(
                    file_, *blocks[channel], tileHeaders_[channel]);
                if (tileHeaders_[channel].width != tileSize ||
                    tileHeaders_[channel].height != tileSize ||
                    tileHeaders_[channel].scale == 0.0F) {
                    throw std::runtime_error("unsupported TZF tile geometry or scale");
                }
            }
            tileLoaded_ = true;
        }

        const auto firstX = tileX_ * tileSize;
        const auto firstY = tileY_ * tileSize;
        const auto xCount = std::min(tileSize, scanInfo_.width - firstX);
        const auto yCount = std::min(tileSize, scanInfo_.height - firstY);
        while (localX_ < xCount && chunk.size() / 3U < maxPoints &&
               emittedPoints_ < maxPoints_) {
            const auto x = firstX + localX_;
            if (x % sampleStride_ != 0) { ++localX_; localY_ = 0; continue; }
            while (localY_ < yCount && chunk.size() / 3U < maxPoints &&
                   emittedPoints_ < maxPoints_) {
                const auto y = firstY + localY_;
                const auto valueIndex = static_cast<std::size_t>(localX_) * tileSize + localY_;
                ++localY_;
                if (y % sampleStride_ != 0) continue;
                const auto rho = readU32(tileChannels_[0].data() + valueIndex * 4U);
                if (rho == 0) continue;
                const SphericalPoint spherical{
                    static_cast<float>(rho) / tileHeaders_[0].scale,
                    static_cast<float>(readI32(tileChannels_[2].data() + valueIndex * 4U)) /
                        tileHeaders_[2].scale,
                    static_cast<float>(readI32(tileChannels_[1].data() + valueIndex * 4U)) /
                        tileHeaders_[1].scale};
                const auto point = sphericalToXyz(spherical);
                if (!std::isfinite(point.x) || !std::isfinite(point.y) ||
                    !std::isfinite(point.z)) continue;
                chunk.insert(chunk.end(), {point.x, point.y, point.z});
                ++emittedPoints_;
            }
            if (localY_ >= yCount) { ++localX_; localY_ = 0; }
        }
        if (localX_ >= xCount) {
            localX_ = localY_ = 0;
            tileLoaded_ = false;
            for (auto& channel : tileChannels_) channel.clear();
            if (++tileY_ >= tileRows_) { tileY_ = 0; ++tileX_; }
        }
    }
    return chunk;
}

void PreviewSession::rewind() noexcept {
    emittedPoints_ = tileX_ = tileY_ = localX_ = localY_ = 0;
    tileLoaded_ = false;
    for (auto& channel : tileChannels_) channel.clear();
}

bool PreviewSession::finished() const noexcept {
    return maxPoints_ == 0 || emittedPoints_ >= maxPoints_ || tileX_ >= tileColumns_;
}

Point sphericalToXyz(const SphericalPoint& point) {
    const auto sinPolar = std::sin(point.polar);
    return {
        point.rho * sinPolar * std::cos(point.azimuth),
        point.rho * sinPolar * std::sin(point.azimuth),
        point.rho * std::cos(point.polar),
    };
}

std::string validateBlockDirectory(const BlockDirectory& directory,
                                   std::uint64_t fileSize) {
    for (std::size_t componentIndex = 0;
         componentIndex < directory.components.size(); ++componentIndex) {
        for (std::size_t blockIndex = 0;
             blockIndex < directory.components[componentIndex].blocks.size();
             ++blockIndex) {
            const auto& block =
                directory.components[componentIndex].blocks[blockIndex];
            if (!rangeFits(block.offset, block.size, fileSize)) {
                std::ostringstream message;
                message << "component " << componentIndex << ", block "
                        << blockIndex << " is outside TZF file: offset="
                        << block.offset << ", size=" << block.size;
                return message.str();
            }
        }
    }
    return {};
}

} // namespace tzf
