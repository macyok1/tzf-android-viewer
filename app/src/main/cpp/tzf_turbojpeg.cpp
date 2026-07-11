#include "tzf_turbojpeg.h"

#include <turbojpeg.h>

#include <memory>
#include <stdexcept>
#include <string>
#include <type_traits>

namespace tzf {
namespace {

struct TurboDestroy {
    void operator()(tjhandle handle) const noexcept {
        if (handle != nullptr) {
            tj3Destroy(handle);
        }
    }
};

using TurboHandle =
    std::unique_ptr<std::remove_pointer_t<tjhandle>, TurboDestroy>;

[[noreturn]] void throwTurbo(tjhandle handle, const char* stage) {
    throw std::runtime_error(std::string(stage) + ": " +
                             (handle ? tj3GetErrorStr(handle)
                                     : "cannot create TurboJPEG decoder"));
}

} // namespace

std::vector<std::uint8_t> decodeJpegTurbo(
    const std::vector<std::uint8_t>& jpeg, std::uint32_t expectedWidth,
    std::uint32_t expectedHeight) {
    TurboHandle handle(tj3Init(TJINIT_DECOMPRESS));
    if (!handle) {
        throwTurbo(nullptr, "TurboJPEG init");
    }
    if (tj3DecompressHeader(handle.get(), jpeg.data(), jpeg.size()) < 0) {
        throwTurbo(handle.get(), "TurboJPEG header");
    }
    const auto width = tj3Get(handle.get(), TJPARAM_JPEGWIDTH);
    const auto height = tj3Get(handle.get(), TJPARAM_JPEGHEIGHT);
    if (width <= 0 || height <= 0 ||
        static_cast<std::uint32_t>(width) != expectedWidth ||
        static_cast<std::uint32_t>(height) != expectedHeight) {
        throw std::runtime_error("unexpected JPEG intensity dimensions");
    }

    std::vector<std::uint8_t> pixels(
        static_cast<std::size_t>(width) * height);
    if (tj3Decompress8(handle.get(), jpeg.data(), jpeg.size(), pixels.data(),
                       width, TJPF_GRAY) < 0) {
        throwTurbo(handle.get(), "TurboJPEG decode");
    }
    return pixels;
}

} // namespace tzf
