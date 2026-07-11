#include "tzf_core.h"
#include "tzf_registration.h"

#include <jni.h>

#include <algorithm>
#include <exception>
#include <stdexcept>
#include <string>
#include <atomic>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace {

std::mutex previewMutex;
std::unordered_map<jlong, std::shared_ptr<tzf::PreviewSession>> previewSessions;
std::atomic<jlong> nextPreviewHandle{1};

std::shared_ptr<tzf::PreviewSession> requirePreview(jlong handle) {
    std::lock_guard<std::mutex> lock(previewMutex);
    const auto found = previewSessions.find(handle);
    if (found == previewSessions.end()) throw std::runtime_error("preview session is closed");
    return found->second;
}

void throwIOException(JNIEnv* env, const std::string& message) {
    const auto type = env->FindClass("java/io/IOException");
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_ru_tzfviewer_TzfNative_openPreviewSession(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) { throwIOException(env, "preview path is null"); return 0; }
    const char* chars = env->GetStringUTFChars(path, nullptr); if (chars == nullptr) return 0;
    try { auto session=std::make_shared<tzf::PreviewSession>(chars);env->ReleaseStringUTFChars(path,chars);const auto handle=nextPreviewHandle.fetch_add(1);std::lock_guard<std::mutex> lock(previewMutex);previewSessions.emplace(handle,std::move(session));return handle; }
    catch(const std::exception& e){env->ReleaseStringUTFChars(path,chars);throwIOException(env,e.what());return 0;}
}

extern "C" JNIEXPORT void JNICALL
Java_ru_tzfviewer_TzfNative_preparePreviewSession(JNIEnv* env,jclass,jlong handle,jint maxPoints){try{if(maxPoints<=0)throw std::runtime_error("invalid preview limit");requirePreview(handle)->prepare(static_cast<std::uint32_t>(maxPoints));}catch(const std::exception& e){throwIOException(env,e.what());}}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ru_tzfviewer_TzfNative_nextPreviewChunk(JNIEnv* env,jclass,jlong handle,jint maxPoints){try{if(maxPoints<=0)throw std::runtime_error("invalid chunk limit");const auto chunk=requirePreview(handle)->nextChunk(static_cast<std::uint32_t>(maxPoints));const auto out=env->NewFloatArray(static_cast<jsize>(chunk.size()));if(out!=nullptr&&!chunk.empty())env->SetFloatArrayRegion(out,0,static_cast<jsize>(chunk.size()),chunk.data());return out;}catch(const std::exception& e){throwIOException(env,e.what());return nullptr;}}

extern "C" JNIEXPORT void JNICALL
Java_ru_tzfviewer_TzfNative_closePreviewSession(JNIEnv*,jclass,jlong handle){std::lock_guard<std::mutex> lock(previewMutex);previewSessions.erase(handle);}

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
