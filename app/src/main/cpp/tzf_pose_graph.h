#pragma once
#include <array>
#include <atomic>
#include <cstddef>
#include <string>
#include <vector>
namespace tzf {
struct PoseGraphEdge { std::size_t reference{},moving{}; std::array<double,4> relative{}; double weight{1}; double overlap{}; double rms{}; bool enabled{true}; };
struct PoseGraphOptions { int maximumIterations{80}; double translationTolerance{1e-5}; double yawToleranceDegrees{1e-4}; double robustTranslationScale{.05}; double robustYawScaleDegrees{2}; const std::atomic_bool* cancellation{}; };
struct PoseGraphResult { bool accepted{}; std::vector<std::array<double,4>> poses; std::vector<double> edgeResiduals; int iterations{}; std::string reason; };
[[nodiscard]] PoseGraphResult optimizePoseGraph(std::size_t stationCount,std::size_t fixedStation,const std::vector<std::array<double,4>>& initial,const std::vector<PoseGraphEdge>& edges,const PoseGraphOptions& options={});
}
