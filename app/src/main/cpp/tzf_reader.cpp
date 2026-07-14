#include "tzf_core.h"
#include "tzf_registration.h"
#include "tzf_pose_graph.h"

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
std::atomic_bool registrationCancelled{false};

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

extern "C" JNIEXPORT jlong JNICALL
Java_ru_tzfviewer_TzfNative_previewSessionSourcePointCount(JNIEnv* env,jclass,jlong handle){try{return static_cast<jlong>(requirePreview(handle)->sourcePointCount());}catch(const std::exception& e){throwIOException(env,e.what());return 0;}}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ru_tzfviewer_TzfNative_previewSessionInitialPose(JNIEnv* env,jclass,jlong handle){try{const auto session=requirePreview(handle);const auto size=session->hasInitialPose()?4:0;const auto out=env->NewFloatArray(size);if(out!=nullptr&&size==4){const auto pose=session->initialPose();env->SetFloatArrayRegion(out,0,4,pose.data());}return out;}catch(const std::exception& e){throwIOException(env,e.what());return nullptr;}}

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

jobject makeRegistrationResult(JNIEnv* env,
                               const tzf::RegistrationResult& result) {
    const auto resultClass =
        env->FindClass("ru/tzfviewer/RegistrationResult");
    if (resultClass == nullptr) return nullptr;
    const auto constructor = env->GetMethodID(
        resultClass, "<init>",
        "(ZDDDDDDDILjava/lang/String;[F)V");
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
    return env->NewObject(
        resultClass, constructor, static_cast<jboolean>(result.accepted),
        result.rms, result.p95, result.overlap, result.consistency,
        result.confidence, result.correctionTranslation, result.correctionYaw,
        static_cast<jint>(result.iterations), reason, transformArray);
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
        registrationCancelled.store(false);
        tzf::RegistrationOptions options;
        options.rmsLimit = rmsLimit;
        options.p95Limit = p95Limit;
        options.maximumInitialTranslationMeters = 2000.0;
        options.maximumInitialTranslationRatio = .10;
        options.maximumInitialYawDelta = 10.0;
        options.cancellation = &registrationCancelled;
        const auto result = tzf::registerConstrained(
            decodeRegistrationPoints(reference, 400000),
            decodeRegistrationPoints(moving, 400000), initial, options);

        return makeRegistrationResult(env, result);
    } catch (const std::exception& error) {
        if (referenceChars != nullptr)
            env->ReleaseStringUTFChars(referencePath, referenceChars);
        if (movingChars != nullptr)
            env->ReleaseStringUTFChars(movingPath, movingChars);
        throwIOException(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_ru_tzfviewer_TzfNative_registerPointClouds(JNIEnv* env, jclass,
                                                 jfloatArray referenceXyz,
                                                 jfloatArray movingXyz,
                                                 jfloatArray initialTransform,
                                                 jdouble rmsLimit,
                                                 jdouble p95Limit) {
    if (referenceXyz == nullptr || movingXyz == nullptr || initialTransform == nullptr ||
        env->GetArrayLength(initialTransform) != 4 ||
        env->GetArrayLength(referenceXyz) % 3 != 0 || env->GetArrayLength(movingXyz) % 3 != 0) {
        throwIOException(env, "invalid registration point arrays"); return nullptr;
    }
    try {
        std::vector<float> reference(static_cast<std::size_t>(env->GetArrayLength(referenceXyz)));
        std::vector<float> moving(static_cast<std::size_t>(env->GetArrayLength(movingXyz)));
        env->GetFloatArrayRegion(referenceXyz, 0, static_cast<jsize>(reference.size()), reference.data());
        env->GetFloatArrayRegion(movingXyz, 0, static_cast<jsize>(moving.size()), moving.data());
        std::array<float,4> initialFloats{};
        env->GetFloatArrayRegion(initialTransform,0,4,initialFloats.data());
        if (env->ExceptionCheck()) return nullptr;
        std::array<double,4> initial{}; std::copy(initialFloats.begin(),initialFloats.end(),initial.begin());
        registrationCancelled.store(false);
        tzf::RegistrationOptions options; options.rmsLimit=rmsLimit; options.p95Limit=p95Limit;options.maximumInitialTranslationMeters=2000.0;options.maximumInitialTranslationRatio=.10;options.maximumInitialYawDelta=10.0;options.cancellation=&registrationCancelled;
        const auto result=tzf::registerConstrained(tzf::xyzToPoints(reference),tzf::xyzToPoints(moving),initial,options);
        return makeRegistrationResult(env,result);
    } catch(const std::exception& error){throwIOException(env,error.what());return nullptr;}
}

extern "C" JNIEXPORT jobject JNICALL
Java_ru_tzfviewer_TzfNative_registerPointCloudsGlobal(JNIEnv* env, jclass,
                                                       jfloatArray referenceXyz,
                                                       jfloatArray movingXyz,
                                                       jdouble rmsLimit,
                                                       jdouble p95Limit) {
    if (referenceXyz == nullptr || movingXyz == nullptr ||
        env->GetArrayLength(referenceXyz) % 3 != 0 || env->GetArrayLength(movingXyz) % 3 != 0) {
        throwIOException(env, "invalid global registration point arrays"); return nullptr;
    }
    try {
        std::vector<float> reference(static_cast<std::size_t>(env->GetArrayLength(referenceXyz)));
        std::vector<float> moving(static_cast<std::size_t>(env->GetArrayLength(movingXyz)));
        env->GetFloatArrayRegion(referenceXyz,0,static_cast<jsize>(reference.size()),reference.data());
        env->GetFloatArrayRegion(movingXyz,0,static_cast<jsize>(moving.size()),moving.data());
        if(env->ExceptionCheck())return nullptr;
        registrationCancelled.store(false);
        tzf::GlobalRegistrationOptions options;options.refinement.rmsLimit=rmsLimit;options.refinement.p95Limit=p95Limit;options.refinement.cancellation=&registrationCancelled;
        const auto result=tzf::registerGlobalConstrained(tzf::xyzToPoints(reference),tzf::xyzToPoints(moving),options);
        return makeRegistrationResult(env,result);
    }catch(const std::exception& error){throwIOException(env,error.what());return nullptr;}
}

extern "C" JNIEXPORT void JNICALL
Java_ru_tzfviewer_TzfNative_cancelRegistration(JNIEnv*,jclass){registrationCancelled.store(true);}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ru_tzfviewer_TzfNative_optimizePoseGraph(JNIEnv* env,jclass,jfloatArray initialPoses,jfloatArray encodedEdges,jint fixedStation){
    if(initialPoses==nullptr||encodedEdges==nullptr||env->GetArrayLength(initialPoses)%4!=0||env->GetArrayLength(encodedEdges)%7!=0){throwIOException(env,"invalid pose graph arrays");return nullptr;}
    try{std::vector<float> posesRaw((std::size_t)env->GetArrayLength(initialPoses)),edgesRaw((std::size_t)env->GetArrayLength(encodedEdges));env->GetFloatArrayRegion(initialPoses,0,(jsize)posesRaw.size(),posesRaw.data());env->GetFloatArrayRegion(encodedEdges,0,(jsize)edgesRaw.size(),edgesRaw.data());if(env->ExceptionCheck())return nullptr;std::vector<std::array<double,4>> poses(posesRaw.size()/4);for(std::size_t i=0;i<poses.size();++i)for(int j=0;j<4;++j)poses[i][j]=posesRaw[i*4+j];std::vector<tzf::PoseGraphEdge> edges;edges.reserve(edgesRaw.size()/7);for(std::size_t i=0;i<edgesRaw.size();i+=7){tzf::PoseGraphEdge edge;edge.reference=(std::size_t)edgesRaw[i];edge.moving=(std::size_t)edgesRaw[i+1];for(int j=0;j<4;++j)edge.relative[j]=edgesRaw[i+2+j];edge.weight=edgesRaw[i+6];edges.push_back(edge);}tzf::PoseGraphOptions options;options.robustTranslationScale=50.0;options.maximumEdgeResidual=150.0;options.maximumMeanEdgeResidual=50.0;options.cancellation=&registrationCancelled;registrationCancelled.store(false);auto result=tzf::optimizePoseGraph(poses.size(),(std::size_t)fixedStation,poses,edges,options);if(!result.accepted){throwIOException(env,result.reason.c_str());return nullptr;}std::vector<float> flattened(result.poses.size()*4);for(std::size_t i=0;i<result.poses.size();++i)for(int j=0;j<4;++j)flattened[i*4+j]=(float)result.poses[i][j];jfloatArray output=env->NewFloatArray((jsize)flattened.size());if(output!=nullptr)env->SetFloatArrayRegion(output,0,(jsize)flattened.size(),flattened.data());return output;}catch(const std::exception& error){throwIOException(env,error.what());return nullptr;}
}
