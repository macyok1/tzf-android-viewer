#include "tzf_core.h"

#include <jni.h>

#include <algorithm>
#include <exception>
#include <stdexcept>
#include <string>

namespace {

void throwIOException(JNIEnv* env, const std::string& message) {
    const auto type = env->FindClass("java/io/IOException");
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
    }
}

} // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ru_tzfviewer_TzfNative_decodePreview(JNIEnv* env, jclass,
                                          jstring path, jint maxPoints) {
    if (path == nullptr || maxPoints <= 0) {
        throwIOException(env, "Не задан файл или лимит точек");
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return nullptr;
    try {
        const std::string nativePath(chars);
        env->ReleaseStringUTFChars(path, chars);
        chars = nullptr;
        tzf::BinaryFile file(nativePath);
        const auto header = tzf::parseFileHeader(file);
        const auto scan = tzf::parseScanInfo(file, header.scanInfoOffset);
        const auto directory =
            tzf::parseBlockDirectory(file, header.blockDirectoryOffset);
        const auto validation = tzf::validateBlockDirectory(directory, file.size());
        if (!validation.empty()) throw std::runtime_error(validation);
        const auto preview = tzf::decodePointCloudPreview(
            file, header, scan, directory,
            static_cast<std::uint32_t>(maxPoints));
        if (preview.xyz.empty()) {
            throw std::runtime_error("TZF не содержит точек для предпросмотра");
        }
        const auto result = env->NewFloatArray(
            static_cast<jsize>(preview.xyz.size()));
        if (result == nullptr) return nullptr;
        env->SetFloatArrayRegion(result, 0,
                                 static_cast<jsize>(preview.xyz.size()),
                                 preview.xyz.data());
        return result;
    } catch (const std::exception& error) {
        if (chars != nullptr) env->ReleaseStringUTFChars(path, chars);
        throwIOException(env, error.what());
        return nullptr;
    }
}
