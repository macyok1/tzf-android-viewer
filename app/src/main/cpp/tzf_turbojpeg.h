#pragma once

#include <cstdint>
#include <vector>

namespace tzf {

[[nodiscard]] std::vector<std::uint8_t> decodeJpegTurbo(
    const std::vector<std::uint8_t>& jpeg, std::uint32_t expectedWidth,
    std::uint32_t expectedHeight);

} // namespace tzf
