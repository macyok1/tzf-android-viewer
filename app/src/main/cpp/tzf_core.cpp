#include "tzf_core.h"

#include <limits>
#include <cstring>
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
    const auto valueCount = static_cast<std::uint64_t>(width) * height;
    if (valueCount > std::numeric_limits<std::size_t>::max() / 4U ||
        encoded.size() != valueCount * 4U) {
        throw std::runtime_error("transpose/derive dimensions do not match data");
    }

    std::vector<std::uint32_t> derived(static_cast<std::size_t>(valueCount));
    for (std::size_t i = 0; i < derived.size(); ++i) {
        derived[i] = readU32(encoded.data() + i * 4U);
        if (i != 0) {
            derived[i] += derived[i - 1]; // uint32 wrap is intentional.
        }
    }

    std::vector<std::uint8_t> output(encoded.size());
    for (std::uint32_t x = 0; x < width; ++x) {
        for (std::uint32_t y = 0; y < height; ++y) {
            const auto source = static_cast<std::size_t>(x) * height + y;
            const auto destination = static_cast<std::size_t>(y) * width + x;
            const auto value = derived[source];
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

std::vector<std::uint8_t> decodeTilePayload(BinaryFile& file,
                                            const BlockDescriptor& block,
                                            const TileHeader& header) {
    constexpr std::uint32_t tileHeaderSize = 28;
    constexpr std::uint64_t snappyCodec = 0x839a721b429840b9ULL;
    constexpr std::uint64_t snappyTransposeDerive =
        0xafec6c11bc26b6c5ULL;
    if (header.codecId != snappyCodec &&
        header.codecId != snappyTransposeDerive) {
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
    return decoded;
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
