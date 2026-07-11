#include "tzf_core.h"

#include <charconv>
#include <iomanip>
#include <fstream>
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

void writeLine(std::ostream& output,
               const std::vector<tzf::SphericalPoint>& points) {
    output << "row,rho,azimuth,polar,x,y,z\n" << std::setprecision(9);
    for (std::size_t i = 0; i < points.size(); ++i) {
        const auto xyz = tzf::sphericalToXyz(points[i]);
        output << i << ',' << points[i].rho << ',' << points[i].azimuth << ','
               << points[i].polar << ',' << xyz.x << ',' << xyz.y << ','
               << xyz.z << '\n';
    }
}

} // namespace

int main(int argc, char** argv) {
    const auto lineMode = argc >= 3 && std::string_view(argv[2]) == "--line";
    const auto componentMode =
        argc >= 3 && std::string_view(argv[2]) == "--component";
    if ((lineMode && argc != 4 && argc != 5) ||
        (componentMode && argc != 4) ||
        (!lineMode && !componentMode && argc != 2)) {
        std::cerr
            << "usage:\n"
            << "  tzf-cli <file.tzf>\n"
            << "  tzf-cli <file.tzf> --component <component-index>\n"
            << "  tzf-cli <file.tzf> --line <line> [output.csv]\n";
        return 2;
    }
    try {
        tzf::BinaryFile file(argv[1]);
        const auto header = tzf::parseFileHeader(file);
        const auto scanInfo = tzf::parseScanInfo(file, header.scanInfoOffset);
        const auto directory =
            tzf::parseBlockDirectory(file, header.blockDirectoryOffset);
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
                  << "component-count: " << directory.componentCount << "\n"
                  << "scan-size: " << scanInfo.width << "x" << scanInfo.height
                  << "\nvalid-points: " << scanInfo.validPointCount << "\n";
        if (lineMode) {
            const auto points = tzf::decodeSphericalLine(
                file, header, scanInfo, directory,
                static_cast<std::uint32_t>(parseOffset(argv[3])));
            if (argc == 5) {
                std::ofstream output(argv[4], std::ios::trunc);
                if (!output) {
                    throw std::runtime_error("cannot create CSV output");
                }
                writeLine(output, points);
            } else {
                writeLine(std::cout, points);
            }
        } else if (componentMode) {
            printComponent(
                file, directory,
                static_cast<std::size_t>(parseOffset(argv[3])));
        }
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "error: " << error.what() << "\n";
        return 1;
    }
}
