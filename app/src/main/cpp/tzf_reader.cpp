#include "tzf_core.h"
#include "tzf_registration.h"

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

namespace {

std::vector<tzf::Point> decodeRegistrationPoints(const std::string& path,
                                                  std::uint32_t limit) {
    tzf::BinaryFile file(path);
    const auto header = tzf::parseFileHeader(file);
    const auto scan = tzf::parseScanInfo(file, header.scanInfoOffset);
    const auto directory =
        tzf::parseBlockDirectory(file, header.blockDirectoryOffset);
    const auto validation = tzf::validateBlockDirectory(directory, file.size());
    if (!validation.empty()) throw std::runtime_error(validation);
    return tzf::xyzToPoints(tzf::decodePointCloudPreview(
        file, header, scan, directory, limit, 1).xyz);
}

} // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ru_tzfviewer_TzfNative_decodePreview(JNIEnv* env, jclass,
                                          jstring path, jint maxPoints,
                                          jint tileStride) {
    if (path == nullptr || maxPoints <= 0 || tileStride <= 0) {
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
            static_cast<std::uint32_t>(maxPoints),
            static_cast<std::uint32_t>(tileStride));
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

extern "C" JNIEXPORT jobject JNICALL
Java_ru_tzfviewer_TzfNative_registerScans(JNIEnv* env, jclass,
                                          jstring referencePath,
                                          jstring movingPath,
                                          jfloatArray initialTransform,
                                          jdouble rmsLimit,
                                          jdouble p95Limit) {
    if (referencePath == nullptr || movingPath == nullptr ||
        initialTransform == nullptr || env->GetArrayLength(initialTransform) != 4) {
        throwIOException(env, "invalid registration arguments");
        return nullptr;
    }
    const char* referenceChars = env->GetStringUTFChars(referencePath, nullptr);
    if (referenceChars == nullptr) return nullptr;
    const char* movingChars = env->GetStringUTFChars(movingPath, nullptr);
    if (movingChars == nullptr) {
        env->ReleaseStringUTFChars(referencePath, referenceChars);
        return nullptr;
    }
    try {
        const std::string reference(referenceChars), moving(movingChars);
        env->ReleaseStringUTFChars(referencePath, referenceChars);
        env->ReleaseStringUTFChars(movingPath, movingChars);
        referenceChars = movingChars = nullptr;
        std::array<float, 4> initialFloats{};
        env->GetFloatArrayRegion(initialTransform, 0, 4, initialFloats.data());
        if (env->ExceptionCheck()) return nullptr;
        std::array<double, 4> initial{};
        std::copy(initialFloats.begin(), initialFloats.end(), initial.begin());
        tzf::RegistrationOptions options;
        options.rmsLimit = rmsLimit;
        options.p95Limit = p95Limit;
        const auto result = tzf::registerConstrained(
            decodeRegistrationPoints(reference, 400000),
            decodeRegistrationPoints(moving, 400000), initial, options);

        const auto resultClass = env->FindClass("ru/tzfviewer/RegistrationResult");
        if (resultClass == nullptr) return nullptr;
        const auto constructor = env->GetMethodID(
            resultClass, "<init>", "(ZDDDILjava/lang/String;[F)V");
        if (constructor == nullptr) return nullptr;
        std::array<float, 4> transform{};
        std::transform(result.transform.begin(), result.transform.end(),
                       transform.begin(), [](double value) {
                           return static_cast<float>(value);
                       });
        const auto transformArray = env->NewFloatArray(4);
        if (transformArray == nullptr) return nullptr;
        env->SetFloatArrayRegion(transformArray, 0, 4, transform.data());
        const auto reason = env->NewStringUTF(result.reason.c_str());
        return env->NewObject(resultClass, constructor,
                              static_cast<jboolean>(result.accepted),
                              result.rms, result.p95, result.overlap,
                              static_cast<jint>(result.iterations), reason,
                              transformArray);
    } catch (const std::exception& error) {
        if (referenceChars != nullptr)
            env->ReleaseStringUTFChars(referencePath, referenceChars);
        if (movingChars != nullptr)
            env->ReleaseStringUTFChars(movingPath, movingChars);
        throwIOException(env, error.what());
        return nullptr;
    }
}
