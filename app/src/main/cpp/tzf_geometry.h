#pragma once
#include "tzf_core.h"
#include <array>
#include <atomic>
#include <cstddef>
#include <vector>
namespace tzf {
struct PlaneDescriptor { std::array<double,3> normal{}; double offset{}; std::array<double,3> centroid{}; std::size_t support{}; double area{}; double extent{}; double planarity{}; };
struct PlaneExtractionOptions { double voxelSize{}; double normalRadius{}; double maximumNormalAngleDegrees{12}; double maximumPlaneDistance{}; std::size_t minimumNeighbours{8}; std::size_t minimumSupport{20}; double minimumPlanarity{.55}; std::size_t maximumPlanes{64}; const std::atomic_bool* cancellation{}; };
[[nodiscard]] std::vector<Point> voxelCentroids(const std::vector<Point>& points,double voxelSize);
[[nodiscard]] std::vector<PlaneDescriptor> extractPlanes(const std::vector<Point>& points,const PlaneExtractionOptions& options={});
}
