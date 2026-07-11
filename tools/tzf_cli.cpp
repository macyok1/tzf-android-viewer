#include "tzf_core.h"

#include <charconv>
#include <iomanip>
#include <iostream>
#include <stdexcept>
#include <string_view>

namespace {

std::uint64_t parseOffset(const char* text) {
    std::string_view value(text);
    int base = 10;
    if (value.starts_with("0x") || value.starts_with("0X")) {
        value.remove_prefix(2);
        base = 16;
    }
    std::uint64_t result{};
    const auto parsed = std::from_chars(value.data(), value.data() + value.size(),
                                        result, base);
    if (parsed.ec != std::errc{} || parsed.ptr != value.data() + value.size()) {
        throw std::runtime_error("invalid table offset");
    }
    return result;
}

void printComponent(tzf::BinaryFile& file,
                    const tzf::BlockDirectory& directory, std::size_t index) {
    if (index >= directory.components.size()) {
        throw std::runtime_error("component index is outside block directory");
    }
    const auto& component = directory.components[index];
    std::cout << "component[" << index << "]: id=" << component.id
              << ", blocks=" << component.blocks.size() << "\n";
    for (std::size_t i = 0; i < component.blocks.size(); ++i) {
        const auto& block = component.blocks[i];
        const auto tile = tzf::parseTileHeader(file, block);
        std::cout << "  block[" << i << "]: offset=0x" << std::hex
                  << std::uppercase << block.offset << std::dec
                  << ", size=" << block.size << ", tile=" << tile.width
                  << "x" << tile.height << ", raw="
                  << tile.uncompressedSize << ", scale=" << tile.scale
                  << ", codec=0x" << std::hex << std::uppercase
                  << tile.codecId << std::dec << "\n";
    }
}

} // namespace

int main(int argc, char** argv) {
    if (argc < 3 || argc > 4) {
        std::cerr << "usage: tzf-cli <file.tzf> <directory-offset> [component-index]\n";
        return 2;
    }
    try {
        tzf::BinaryFile file(argv[1]);
        const auto directory = tzf::parseBlockDirectory(file, parseOffset(argv[2]));
        const auto validation =
            tzf::validateBlockDirectory(directory, file.size());
        if (!validation.empty()) {
            throw std::runtime_error(validation);
        }

        std::cout << "file-size: " << file.size() << "\n"
                  << "table-offset: 0x" << std::hex << std::uppercase
                  << directory.fileOffset << std::dec << "\n"
                  << "header-value: " << directory.headerValue << "\n"
                  << "payload-size: " << directory.payloadSize << "\n"
                  << "component-count: " << directory.componentCount << "\n";
        printComponent(
            file, directory,
            argc == 4 ? static_cast<std::size_t>(parseOffset(argv[3])) : 0U);
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "error: " << error.what() << "\n";
        return 1;
    }
}
