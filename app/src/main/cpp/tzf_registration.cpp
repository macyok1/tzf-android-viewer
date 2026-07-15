#include "tzf_registration.h"
#include "tzf_geometry.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>
#include <numeric>
#include <unordered_map>

namespace tzf {
namespace {

struct Vec3 { double x{}, y{}, z{}; };
struct Sample {
    Vec3 point;
    Vec3 normal;
    double planarity{};
    bool hasNormal{};
};
struct Cell { int x{}, y{}, z{}; bool operator==(const Cell&) const = default; };
struct CellHash {
    std::size_t operator()(const Cell& c) const noexcept {
        auto h = static_cast<std::uint64_t>(static_cast<std::uint32_t>(c.x)) * 73856093ULL;
        h ^= static_cast<std::uint64_t>(static_cast<std::uint32_t>(c.y)) * 19349663ULL;
        h ^= static_cast<std::uint64_t>(static_cast<std::uint32_t>(c.z)) * 83492791ULL;
        return static_cast<std::size_t>(h);
    }
};

Vec3 add(Vec3 a, Vec3 b) { return {a.x+b.x,a.y+b.y,a.z+b.z}; }
Vec3 sub(Vec3 a, Vec3 b) { return {a.x-b.x,a.y-b.y,a.z-b.z}; }
Vec3 mul(Vec3 a, double s) { return {a.x*s,a.y*s,a.z*s}; }
double dot(Vec3 a, Vec3 b) { return a.x*b.x+a.y*b.y+a.z*b.z; }
double length2(Vec3 a) { return dot(a,a); }
bool normalize(Vec3& a) { const auto l=std::sqrt(length2(a)); if(!std::isfinite(l)||l<1e-12)return false; a=mul(a,1/l);return true; }
Vec3 toVec(Point p) { return {p.x,p.y,p.z}; }

Cell cellFor(Vec3 p,double size){return {static_cast<int>(std::floor(p.x/size)),static_cast<int>(std::floor(p.y/size)),static_cast<int>(std::floor(p.z/size))};}

std::vector<Vec3> downsample(const std::vector<Point>& points,double voxel) {
    struct Sum { Vec3 value; std::size_t count{}; };
    std::unordered_map<Cell,Sum,CellHash> cells;
    cells.reserve(points.size()/2+1);
    for(const auto p:points){if(!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z))continue;auto& s=cells[cellFor(toVec(p),voxel)];s.value=add(s.value,toVec(p));++s.count;}
    std::vector<Vec3> result;result.reserve(cells.size());
    for(const auto& [_,s]:cells)result.push_back(mul(s.value,1.0/s.count));
    return result;
}

void limitSample(std::vector<Vec3>& points, std::size_t limit) {
    if (limit == 0 || points.size() <= limit) return;
    std::vector<Vec3> sampled;
    sampled.reserve(limit);
    const double stride = static_cast<double>(points.size()) / limit;
    for (std::size_t i = 0; i < limit; ++i)
        sampled.push_back(points[static_cast<std::size_t>(i * stride)]);
    points = std::move(sampled);
}

class Index {
public:
    Index(const std::vector<Vec3>& points,double cellSize):points_(points),cellSize_(cellSize){cells_.reserve(points.size()*2);for(std::size_t i=0;i<points.size();++i)cells_[cellFor(points[i],cellSize)].push_back(i);}
    template<class F> void nearby(Vec3 p,int radius,F&& visit)const{const auto c=cellFor(p,cellSize_);for(int z=-radius;z<=radius;++z)for(int y=-radius;y<=radius;++y)for(int x=-radius;x<=radius;++x){auto it=cells_.find({c.x+x,c.y+y,c.z+z});if(it!=cells_.end())for(auto i:it->second)visit(i);}}
    bool nearest(Vec3 p,double maximum,std::size_t& best,double& distance2)const{distance2=maximum*maximum;bool found=false;const int radius=std::max(1,static_cast<int>(std::ceil(maximum/cellSize_)));nearby(p,radius,[&](std::size_t i){const auto d=length2(sub(points_[i],p));if(d<distance2){distance2=d;best=i;found=true;}});return found;}
private:
    const std::vector<Vec3>& points_;double cellSize_;std::unordered_map<Cell,std::vector<std::size_t>,CellHash> cells_;
};

struct EigenSystem {
    std::array<double, 3> values{};
    std::array<Vec3, 3> vectors{};
};

EigenSystem symmetricEigenSystem(double matrix[3][3]) {
    double vectors[3][3]{{1,0,0},{0,1,0},{0,0,1}};
    for (int sweep = 0; sweep < 16; ++sweep) {
        int p = 0, q = 1;
        if (std::abs(matrix[0][2]) > std::abs(matrix[p][q])) { p = 0; q = 2; }
        if (std::abs(matrix[1][2]) > std::abs(matrix[p][q])) { p = 1; q = 2; }
        if (std::abs(matrix[p][q]) < 1e-12) break;
        const double app = matrix[p][p], aqq = matrix[q][q];
        const double apq = matrix[p][q];
        const double angle = .5 * std::atan2(2.0 * apq, aqq - app);
        const double c = std::cos(angle), s = std::sin(angle);
        for (int k = 0; k < 3; ++k) {
            if (k == p || k == q) continue;
            const double akp = matrix[k][p], akq = matrix[k][q];
            matrix[k][p] = matrix[p][k] = c * akp - s * akq;
            matrix[k][q] = matrix[q][k] = s * akp + c * akq;
        }
        matrix[p][p] = c*c*app - 2.0*s*c*apq + s*s*aqq;
        matrix[q][q] = s*s*app + 2.0*s*c*apq + c*c*aqq;
        matrix[p][q] = matrix[q][p] = 0;
        for (int k = 0; k < 3; ++k) {
            const double vkp = vectors[k][p], vkq = vectors[k][q];
            vectors[k][p] = c * vkp - s * vkq;
            vectors[k][q] = s * vkp + c * vkq;
        }
    }
    std::array<int, 3> order{0, 1, 2};
    std::sort(order.begin(), order.end(), [&](int a, int b) {
        return matrix[a][a] < matrix[b][b];
    });
    EigenSystem result;
    for (int i = 0; i < 3; ++i) {
        const int source = order[i];
        result.values[i] = std::max(0.0, matrix[source][source]);
        result.vectors[i] = {vectors[0][source], vectors[1][source],
                             vectors[2][source]};
    }
    return result;
}

std::vector<Sample> normals(const std::vector<Vec3>& points, double radius) {
    Index index(points, radius);
    std::vector<Sample> out(points.size());
    const double radiusSquared = radius * radius;
    for (std::size_t i = 0; i < points.size(); ++i) {
        out[i].point = points[i];
        std::size_t count = 0;
        Vec3 sum{};
        double products[3][3]{};
        index.nearby(points[i], 1, [&](std::size_t j) {
            if (length2(sub(points[j], points[i])) > radiusSquared) return;
            const Vec3 p = points[j];
            ++count;
            sum = add(sum, p);
            const double values[3]{p.x, p.y, p.z};
            for (int row = 0; row < 3; ++row)
                for (int column = row; column < 3; ++column)
                    products[row][column] += values[row] * values[column];
        });
        if (count < 8) continue;
        const Vec3 mean = mul(sum, 1.0 / count);
        const double means[3]{mean.x, mean.y, mean.z};
        double covariance[3][3]{};
        for (int row = 0; row < 3; ++row) {
            for (int column = row; column < 3; ++column) {
                covariance[row][column] = covariance[column][row] =
                    products[row][column] / count - means[row] * means[column];
            }
        }
        const auto eigen = symmetricEigenSystem(covariance);
        const double trace = eigen.values[0] + eigen.values[1] + eigen.values[2];
        if (trace <= 1e-12 || eigen.values[2] <= 1e-12) continue;
        Vec3 normal = eigen.vectors[0];
        if (!normalize(normal)) continue;
        const double planarity =
            (eigen.values[1] - eigen.values[0]) / eigen.values[2];
        const double surfaceVariation = eigen.values[0] / trace;
        out[i].normal = normal;
        out[i].planarity = std::clamp(planarity, 0.0, 1.0);
        out[i].hasNormal = out[i].planarity >= .08 && surfaceVariation <= .12;
    }
    return out;
}

Vec3 center(const std::vector<Point>& points){Vec3 c{};for(auto p:points)c=add(c,toVec(p));return points.empty()?c:mul(c,1.0/points.size());}

Vec3 transformPoint(Vec3 p,Vec3 pivot,const std::array<double,4>& t){double a=t[3]*3.14159265358979323846/180.0,c=std::cos(a),s=std::sin(a);p=sub(p,pivot);return {pivot.x+c*p.x-s*p.y+t[0],pivot.y+s*p.x+c*p.y+t[1],pivot.z+p.z+t[2]};}
Vec3 rotateZ(Vec3 p,double degrees){const double a=degrees*3.14159265358979323846/180.0,c=std::cos(a),s=std::sin(a);return {c*p.x-s*p.y,s*p.x+c*p.y,p.z};}
std::array<double,4> pivotTransform(Vec3 directTranslation,Vec3 pivot,double yaw){const Vec3 rotatedPivot=rotateZ(pivot,yaw);return {directTranslation.x-pivot.x+rotatedPivot.x,directTranslation.y-pivot.y+rotatedPivot.y,directTranslation.z,yaw};}

bool solve4(double a[4][4],double b[4],double x[4]){
    for(int i=0;i<4;++i){int pivot=i;for(int r=i+1;r<4;++r)if(std::abs(a[r][i])>std::abs(a[pivot][i]))pivot=r;if(std::abs(a[pivot][i])<1e-10)return false;for(int c=i;c<4;++c)std::swap(a[i][c],a[pivot][c]);std::swap(b[i],b[pivot]);double d=a[i][i];for(int c=i;c<4;++c)a[i][c]/=d;b[i]/=d;for(int r=0;r<4;++r)if(r!=i){double f=a[r][i];for(int c=i;c<4;++c)a[r][c]-=f*a[i][c];b[r]-=f*b[i];}}
    for(int i=0;i<4;++i)x[i]=b[i];return true;
}

struct Metrics {
    double rms{}, p95{}, overlap{}, consistency{}, confidence{};
    double reciprocalRatio{}, overlapBalance{}, residualStability{};
    double residualQuality{};
    std::vector<double> distances;
};

Metrics metrics(const std::vector<Vec3>& reference,
                const std::vector<Vec3>& moving, Vec3 pivot,
                const std::array<double, 4>& t, double maxDistance,
                double rmsLimit, double p95Limit) {
    Metrics m;
    std::vector<Vec3> transformed;
    transformed.reserve(moving.size());
    for (auto p : moving) transformed.push_back(transformPoint(p, pivot, t));
    Index refIndex(reference, maxDistance), movIndex(transformed, maxDistance);
    double sum = 0;
    std::size_t forward = 0, backward = 0, reciprocal = 0;
    for (std::size_t source = 0; source < transformed.size(); ++source) {
        std::size_t nearest{};
        double d2{};
        if (!refIndex.nearest(transformed[source], maxDistance, nearest, d2)) continue;
        m.distances.push_back(std::sqrt(d2));
        sum += d2;
        ++forward;
        std::size_t reverse{};
        double reverseDistance{};
        if (movIndex.nearest(reference[nearest], maxDistance, reverse,
                             reverseDistance) &&
            reverse == source) {
            ++reciprocal;
        }
    }
    for (auto p : reference) {
        std::size_t nearest{};
        double d2{};
        if (movIndex.nearest(p, maxDistance, nearest, d2)) {
            m.distances.push_back(std::sqrt(d2));
            sum += d2;
            ++backward;
        }
    }
    const double forwardOverlap = moving.empty() ? 0.0 :
        static_cast<double>(forward) / moving.size();
    const double backwardOverlap = reference.empty() ? 0.0 :
        static_cast<double>(backward) / reference.size();
    m.overlap = std::min(forwardOverlap, backwardOverlap);
    const double maximumOverlap = std::max(forwardOverlap, backwardOverlap);
    m.overlapBalance = maximumOverlap <= 0 ? 0.0 :
        m.overlap / maximumOverlap;
    m.reciprocalRatio = forward == 0 ? 0.0 :
        static_cast<double>(reciprocal) / forward;
    if (!m.distances.empty()) {
        m.rms = std::sqrt(sum / m.distances.size());
        std::sort(m.distances.begin(), m.distances.end());
        m.p95 = m.distances[static_cast<std::size_t>(
            (m.distances.size() - 1) * .95)];
        const double median = m.distances[m.distances.size() / 2];
        m.residualStability = std::clamp(
            1.0 - (m.p95 - median) / std::max(p95Limit, 1e-9),
            0.0, 1.0);
    }
    const double rmsQuality = rmsLimit > 0 ?
        std::clamp(1.0 - m.rms / rmsLimit, 0.0, 1.0) : 0.0;
    const double p95Quality = p95Limit > 0 ?
        std::clamp(1.0 - m.p95 / p95Limit, 0.0, 1.0) : 0.0;
    m.residualQuality = (rmsQuality + p95Quality) * .5;
    m.consistency =
        .15 * std::clamp(m.reciprocalRatio, 0.0, 1.0) +
        .45 * std::clamp(m.overlapBalance, 0.0, 1.0) +
        .40 * std::clamp(m.residualStability, 0.0, 1.0);
    return m;
}

Metrics surfaceMetrics(const std::vector<Vec3>& reference,
                       const std::vector<Vec3>& moving, Vec3 pivot,
                       const std::array<double, 4>& t, double maxDistance,
                       double rmsLimit, double p95Limit) {
    Metrics m;
    if (reference.empty() || moving.empty()) return m;
    const auto referenceSamples = normals(reference, maxDistance * 1.5);
    const auto movingSamples = normals(moving, maxDistance * 1.5);
    std::vector<Vec3> transformed;
    transformed.reserve(moving.size());
    for (const auto point : moving)
        transformed.push_back(transformPoint(point, pivot, t));
    Index referenceIndex(reference, maxDistance);
    Index movingIndex(transformed, maxDistance);
    const double yaw = t[3] * 3.14159265358979323846 / 180.0;
    const double c = std::cos(yaw), s = std::sin(yaw);
    const auto rotatedNormal = [&](Vec3 normal) {
        return Vec3{c * normal.x - s * normal.y,
                    s * normal.x + c * normal.y, normal.z};
    };
    std::size_t forward = 0, backward = 0, reciprocal = 0;
    for (std::size_t source = 0; source < transformed.size(); ++source) {
        std::size_t nearest{};
        double distance2{};
        if (!referenceIndex.nearest(transformed[source], maxDistance,
                                    nearest, distance2)) continue;
        ++forward;
        std::size_t reverse{};
        double reverseDistance{};
        if (movingIndex.nearest(reference[nearest], maxDistance, reverse,
                               reverseDistance) && reverse == source)
            ++reciprocal;
        if (!referenceSamples[nearest].hasNormal) continue;
        const double residual = std::abs(dot(
            sub(transformed[source], reference[nearest]),
            referenceSamples[nearest].normal));
        m.distances.push_back(residual);
    }
    for (std::size_t source = 0; source < reference.size(); ++source) {
        std::size_t nearest{};
        double distance2{};
        if (!movingIndex.nearest(reference[source], maxDistance,
                                 nearest, distance2)) continue;
        ++backward;
        if (!movingSamples[nearest].hasNormal) continue;
        const double residual = std::abs(dot(
            sub(reference[source], transformed[nearest]),
            rotatedNormal(movingSamples[nearest].normal)));
        m.distances.push_back(residual);
    }
    const double forwardOverlap = static_cast<double>(forward) / moving.size();
    const double backwardOverlap = static_cast<double>(backward) / reference.size();
    m.overlap = std::min(forwardOverlap, backwardOverlap);
    const double maximumOverlap = std::max(forwardOverlap, backwardOverlap);
    m.overlapBalance = maximumOverlap <= 0 ? 0 : m.overlap / maximumOverlap;
    m.reciprocalRatio = forward == 0 ? 0 :
        static_cast<double>(reciprocal) / forward;
    if (!m.distances.empty()) {
        std::sort(m.distances.begin(), m.distances.end());
        const std::size_t rmsCount = std::max<std::size_t>(
            1, static_cast<std::size_t>(m.distances.size() * .90));
        double trimmedSquares = 0;
        for (std::size_t i = 0; i < rmsCount; ++i)
            trimmedSquares += m.distances[i] * m.distances[i];
        m.rms = std::sqrt(trimmedSquares / rmsCount);
        m.p95 = m.distances[static_cast<std::size_t>(
            (m.distances.size() - 1) * .95)];
        const double median = m.distances[m.distances.size() / 2];
        m.residualStability = std::clamp(
            1.0 - (m.p95 - median) / std::max(p95Limit, 1e-9), 0.0, 1.0);
    }
    const double rmsQuality = std::clamp(
        1.0 - m.rms / std::max(rmsLimit, 1e-9), 0.0, 1.0);
    const double p95Quality = std::clamp(
        1.0 - m.p95 / std::max(p95Limit, 1e-9), 0.0, 1.0);
    m.residualQuality = (rmsQuality + p95Quality) * .5;
    m.consistency =
        .15 * std::clamp(m.reciprocalRatio, 0.0, 1.0) +
        .45 * std::clamp(m.overlapBalance, 0.0, 1.0) +
        .40 * std::clamp(m.residualStability, 0.0, 1.0);
    return m;
}

double cloudSpan(const std::vector<Point>& points){Vec3 lo{1e300,1e300,1e300},hi{-1e300,-1e300,-1e300};for(auto p:points){lo.x=std::min(lo.x,(double)p.x);lo.y=std::min(lo.y,(double)p.y);lo.z=std::min(lo.z,(double)p.z);hi.x=std::max(hi.x,(double)p.x);hi.y=std::max(hi.y,(double)p.y);hi.z=std::max(hi.z,(double)p.z);}return std::max({hi.x-lo.x,hi.y-lo.y,hi.z-lo.z});}
double spanOf(const std::vector<Point>& a,const std::vector<Point>& b){return std::max(cloudSpan(a),cloudSpan(b));}

std::vector<Vec3> controlSample(const std::vector<Point>& points,std::size_t limit){std::vector<Vec3> result;if(points.empty())return result;const std::size_t stride=std::max<std::size_t>(1,(points.size()+limit-1)/limit);result.reserve(std::min(points.size(),limit));for(std::size_t i=0;i<points.size();i+=stride)result.push_back(toVec(points[i]));return result;}

struct PoseVote { int count{}; Vec3 translation{}; };

double normalYaw(const PlaneDescriptor& plane){return std::atan2(plane.normal[1],plane.normal[0])*180.0/3.14159265358979323846;}
std::vector<std::array<double,4>> planeHypotheses(const std::vector<Point>& reference,const std::vector<Point>& moving,const GlobalRegistrationOptions& options){
    const double span=spanOf(reference,moving);PlaneExtractionOptions extraction;extraction.voxelSize=std::max(span/100.0,1e-3);extraction.normalRadius=extraction.voxelSize*4.0;extraction.maximumPlaneDistance=extraction.voxelSize*1.5;extraction.minimumSupport=16;extraction.maximumPlanes=24;extraction.cancellation=options.refinement.cancellation;
    const auto ref=extractPlanes(reference,extraction),mov=extractPlanes(moving,extraction);if(ref.empty()||mov.empty())return{};const Vec3 pivot=center(moving);std::vector<std::pair<double,std::array<double,4>>> scored;
    for(const auto& m:mov){if(std::abs(m.normal[2])>.35)continue;for(const auto& r:ref){if(std::abs(r.normal[2])>.35)continue;double yaw=std::remainder(normalYaw(r)-normalYaw(m),360.0);Vec3 mc{m.centroid[0],m.centroid[1],m.centroid[2]},rc{r.centroid[0],r.centroid[1],r.centroid[2]};Vec3 rotated=rotateZ(mc,yaw),translation=sub(rc,rotated);double zVotes=0,zSum=0;for(const auto& mh:mov)if(std::abs(mh.normal[2])>.9)for(const auto& rh:ref)if(std::abs(rh.normal[2])>.9){zSum+=rh.centroid[2]-mh.centroid[2];++zVotes;}if(zVotes>0)translation.z=zSum/zVotes;auto pose=pivotTransform(translation,pivot,yaw);double support=std::min((double)m.support,(double)r.support);double ratio=std::min(m.area,r.area)/std::max(m.area,r.area);scored.push_back({-(support*ratio),pose});}}
    std::stable_sort(scored.begin(),scored.end(),[](const auto&a,const auto&b){return a.first<b.first;});std::vector<std::array<double,4>> out;for(const auto& [_,candidate]:scored){bool duplicate=false;for(const auto& kept:out){double dx=candidate[0]-kept[0],dy=candidate[1]-kept[1],dz=candidate[2]-kept[2];if(std::sqrt(dx*dx+dy*dy+dz*dz)<span/50.0&&std::abs(std::remainder(candidate[3]-kept[3],360.0))<3.0){duplicate=true;break;}}if(!duplicate)out.push_back(candidate);if(out.size()>=48)break;}return out;
}

struct ProjectionCell {
    int count{};
    double sumX{}, sumY{}, sumZ{};
    double minZ{std::numeric_limits<double>::infinity()};
    double maxZ{-std::numeric_limits<double>::infinity()};
};

using ProjectionGrid = std::unordered_map<Cell, ProjectionCell, CellHash>;

ProjectionGrid buildProjection(const std::vector<Vec3>& points,
                               double cellSize) {
    ProjectionGrid grid;
    for (const auto& point : points) {
        Cell key{static_cast<int>(std::floor(point.x / cellSize)),
                 static_cast<int>(std::floor(point.y / cellSize)), 0};
        auto& cell = grid[key];
        ++cell.count;
        cell.sumX += point.x;
        cell.sumY += point.y;
        cell.sumZ += point.z;
        cell.minZ = std::min(cell.minZ, point.z);
        cell.maxZ = std::max(cell.maxZ, point.z);
    }
    return grid;
}

std::vector<ProjectionCell> projectionFeatures(const ProjectionGrid& grid,
                                               double cellSize,
                                               std::size_t limit) {
    std::vector<std::pair<double, ProjectionCell>> ranked;
    ranked.reserve(grid.size());
    for (const auto& [_, cell] : grid) {
        if (cell.count < 2) continue;
        const double range = std::max(0.0, cell.maxZ - cell.minZ);
        const double structural = 1.0 + std::min(8.0, range / cellSize);
        ranked.push_back({-cell.count * structural, cell});
    }
    std::stable_sort(ranked.begin(), ranked.end(),
                     [](const auto& a, const auto& b) {
                         return a.first < b.first;
                     });
    std::vector<ProjectionCell> out;
    const std::size_t stride = std::max<std::size_t>(
        1, (ranked.size() + limit - 1) / limit);
    for (std::size_t i = 0; i < ranked.size() && out.size() < limit;
         i += stride) {
        out.push_back(ranked[i].second);
    }
    return out;
}

Vec3 projectionCenter(const ProjectionCell& cell) {
    const double count = std::max(1, cell.count);
    return {cell.sumX / count, cell.sumY / count, cell.sumZ / count};
}

struct ProjectionVote {
    int count{};
    Vec3 translation{};
};

struct ProjectionPose {
    Vec3 translation{};
    double yaw{};
    double score{};
};

double projectionScore(const ProjectionGrid& reference,
                       const std::vector<Vec3>& moving, double cellSize,
                       const ProjectionPose& pose) {
    if (moving.empty()) return 0;
    std::size_t matches = 0;
    double heightScore = 0;
    for (const auto& point : moving) {
        const Vec3 rotated = rotateZ(point, pose.yaw);
        const Vec3 transformed = add(rotated, pose.translation);
        const Cell key{static_cast<int>(std::floor(transformed.x / cellSize)),
                       static_cast<int>(std::floor(transformed.y / cellSize)),
                       0};
        const auto found = reference.find(key);
        if (found == reference.end()) continue;
        const auto& cell = found->second;
        const double margin = cellSize * 1.5;
        if (transformed.z < cell.minZ - margin ||
            transformed.z > cell.maxZ + margin) continue;
        ++matches;
        const double meanZ = cell.sumZ / std::max(1, cell.count);
        heightScore += std::max(0.0, 1.0 -
            std::abs(transformed.z - meanZ) / std::max(cellSize * 3.0, 1e-6));
    }
    const double occupancy = static_cast<double>(matches) / moving.size();
    const double height = matches == 0 ? 0.0 : heightScore / matches;
    return occupancy * (.75 + .25 * height);
}

std::vector<double> structuralYawCandidates(
    const std::vector<Vec3>& reference, const std::vector<Vec3>& moving) {
    constexpr int bins = 72;
    struct Bin {
        double minZ{std::numeric_limits<double>::infinity()};
        double maxZ{-std::numeric_limits<double>::infinity()};
        double radius{};
        int count{};
    };
    const auto profile = [](const std::vector<Vec3>& points) {
        std::array<Bin, bins> raw{};
        for (const auto& point : points) {
            const double radius = std::hypot(point.x, point.y);
            if (!std::isfinite(radius) || radius < 1e-6) continue;
            double angle = std::atan2(point.y, point.x) * 180.0 /
                           3.14159265358979323846;
            if (angle < 0) angle += 360.0;
            const int index = std::min(
                bins - 1, static_cast<int>(angle / (360.0 / bins)));
            auto& bin = raw[index];
            bin.minZ = std::min(bin.minZ, point.z);
            bin.maxZ = std::max(bin.maxZ, point.z);
            bin.radius += std::log1p(radius);
            ++bin.count;
        }
        std::array<double, bins> values{};
        for (int i = 0; i < bins; ++i) {
            if (raw[i].count < 3) continue;
            const double vertical = std::max(0.0, raw[i].maxZ - raw[i].minZ);
            values[i] = std::log1p(vertical) +
                        .20 * raw[i].radius / raw[i].count;
        }
        std::array<double, bins> smooth{};
        for (int i = 0; i < bins; ++i)
            smooth[i] = .25 * values[(i + bins - 1) % bins] +
                        .50 * values[i] + .25 * values[(i + 1) % bins];
        const double mean = std::accumulate(smooth.begin(), smooth.end(), 0.0) /
                            bins;
        double energy = 0;
        for (double& value : smooth) {
            value -= mean;
            energy += value * value;
        }
        if (energy > 1e-12) {
            const double scale = 1.0 / std::sqrt(energy);
            for (double& value : smooth) value *= scale;
        }
        return smooth;
    };
    const auto ref = profile(reference), mov = profile(moving);
    std::vector<std::pair<double, double>> scored;
    for (int shift = 0; shift < bins; ++shift) {
        double score = 0;
        for (int i = 0; i < bins; ++i)
            score += mov[i] * ref[(i + shift) % bins];
        double yaw = shift * (360.0 / bins);
        if (yaw >= 180.0) yaw -= 360.0;
        scored.push_back({score, yaw});
    }
    std::stable_sort(scored.begin(), scored.end(),
                     [](const auto& a, const auto& b) {
                         return a.first > b.first;
                     });
    std::vector<double> result;
    for (const auto& [score, yaw] : scored) {
        if (!std::isfinite(score)) continue;
        bool separated = true;
        for (double kept : result)
            if (std::abs(std::remainder(yaw - kept, 360.0)) < 12.0)
                separated = false;
        if (separated) result.push_back(yaw);
        if (result.size() >= 8) break;
    }
    return result;
}

std::vector<std::array<double, 4>> projectionHypotheses(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const GlobalRegistrationOptions& options) {
    const double span = spanOf(reference, moving);
    if (!std::isfinite(span) || span <= 0) return {};
    const double coarseCell = std::max(span / 60.0, .02);
    const double fineCell = std::max(span / 160.0, .008);
    const auto coarseReferencePoints = downsample(reference, coarseCell);
    const auto coarseMovingPoints = downsample(moving, coarseCell);
    if (coarseReferencePoints.size() < 30 || coarseMovingPoints.size() < 30)
        return {};
    const auto referenceGrid = buildProjection(coarseReferencePoints, coarseCell);
    const auto movingGrid = buildProjection(coarseMovingPoints, coarseCell);
    const auto referenceFeatures = projectionFeatures(referenceGrid, coarseCell, 128);
    const auto movingFeatures = projectionFeatures(movingGrid, coarseCell, 128);
    if (referenceFeatures.size() < 8 || movingFeatures.size() < 8) return {};

    std::vector<double> yawCandidates = structuralYawCandidates(
        coarseReferencePoints, coarseMovingPoints);
    for (double yaw = -180.0; yaw < 180.0; yaw += options.yawStepDegrees) {
        bool duplicate = false;
        for (double known : yawCandidates)
            if (std::abs(std::remainder(yaw - known, 360.0)) < 2.0)
                duplicate = true;
        if (!duplicate) yawCandidates.push_back(yaw);
    }
    std::vector<ProjectionPose> coarseCandidates;
    for (double yaw : yawCandidates) {
        if (options.refinement.cancellation &&
            options.refinement.cancellation->load()) return {};
        std::unordered_map<Cell, ProjectionVote, CellHash> votes;
        for (const auto& movingCell : movingFeatures) {
            const Vec3 movingCenter = projectionCenter(movingCell);
            const Vec3 rotated = rotateZ(movingCenter, yaw);
            const double movingRange = movingCell.maxZ - movingCell.minZ;
            for (const auto& referenceCell : referenceFeatures) {
                const double referenceRange = referenceCell.maxZ - referenceCell.minZ;
                if (std::abs(referenceRange - movingRange) > span * .08)
                    continue;
                const Vec3 direct = sub(projectionCenter(referenceCell), rotated);
                const Cell voteKey{
                    static_cast<int>(std::llround(direct.x / coarseCell)),
                    static_cast<int>(std::llround(direct.y / coarseCell)), 0};
                auto& vote = votes[voteKey];
                ++vote.count;
                vote.translation = add(vote.translation, direct);
            }
        }
        std::vector<std::pair<Cell, ProjectionVote>> ranked(votes.begin(),
                                                            votes.end());
        std::stable_sort(ranked.begin(), ranked.end(),
                         [](const auto& a, const auto& b) {
                             return a.second.count > b.second.count;
                         });
        for (std::size_t i = 0; i < std::min<std::size_t>(4, ranked.size()); ++i) {
            const auto& vote = ranked[i].second;
            if (vote.count < 3) continue;
            ProjectionPose pose{mul(vote.translation, 1.0 / vote.count), yaw, 0};
            pose.score = projectionScore(referenceGrid, coarseMovingPoints,
                                         coarseCell, pose);
            if (pose.score >= .10) coarseCandidates.push_back(pose);
        }
    }
    std::stable_sort(coarseCandidates.begin(), coarseCandidates.end(),
                     [](const auto& a, const auto& b) {
                         return a.score > b.score;
                     });
    if (coarseCandidates.size() > 12) coarseCandidates.resize(12);
    if (coarseCandidates.empty()) return {};

    const auto fineReferencePoints = downsample(reference, fineCell);
    const auto fineMovingPoints = downsample(moving, fineCell);
    const auto fineReference = buildProjection(fineReferencePoints, fineCell);
    std::vector<ProjectionPose> fineCandidates;
    for (const auto& coarse : coarseCandidates) {
        for (double yaw = coarse.yaw - options.yawStepDegrees;
             yaw <= coarse.yaw + options.yawStepDegrees; yaw += 1.0) {
            if (options.refinement.cancellation &&
                options.refinement.cancellation->load()) return {};
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dy = -2; dy <= 2; ++dy) {
                    ProjectionPose pose = coarse;
                    pose.yaw = yaw;
                    pose.translation.x += dx * fineCell;
                    pose.translation.y += dy * fineCell;
                    pose.score = projectionScore(fineReference, fineMovingPoints,
                                                 fineCell, pose);
                    fineCandidates.push_back(pose);
                }
            }
        }
    }
    std::stable_sort(fineCandidates.begin(), fineCandidates.end(),
                     [](const auto& a, const auto& b) {
                         return a.score > b.score;
                     });
    const Vec3 pivot = center(moving);
    std::vector<std::array<double, 4>> out;
    for (const auto& candidate : fineCandidates) {
        if (candidate.score < .15) break;
        const auto transform = pivotTransform(candidate.translation, pivot,
                                              candidate.yaw);
        bool duplicate = false;
        for (const auto& kept : out) {
            const double dx = transform[0] - kept[0];
            const double dy = transform[1] - kept[1];
            const double dz = transform[2] - kept[2];
            if (std::sqrt(dx * dx + dy * dy + dz * dz) < fineCell * 3.0 &&
                std::abs(std::remainder(transform[3] - kept[3], 360.0)) < 2.0) {
                duplicate = true;
                break;
            }
        }
        if (!duplicate) out.push_back(transform);
        if (out.size() >= 24) break;
    }
    return out;
}

struct Descriptor { Sample sample; std::array<double,12> values{}; bool valid{}; };

std::vector<Descriptor> describe(const std::vector<Sample>& samples,double radius,std::size_t limit){
    std::vector<Vec3> points;points.reserve(samples.size());for(const auto& sample:samples)points.push_back(sample.point);Index index(points,radius);const std::size_t stride=std::max<std::size_t>(1,samples.size()/limit);std::vector<Descriptor> out;
    for(std::size_t i=0;i<samples.size();i+=stride){if(!samples[i].hasNormal)continue;Descriptor descriptor;descriptor.sample=samples[i];int neighbours=0;index.nearby(samples[i].point,1,[&](std::size_t j){if(i==j||!samples[j].hasNormal)return;const Vec3 delta=sub(samples[j].point,samples[i].point);const double distance=std::sqrt(length2(delta));if(distance<=1e-6||distance>radius)return;const int distanceBin=std::min(2,static_cast<int>(distance/radius*3.0));const int normalBin=std::min(3,static_cast<int>(std::abs(dot(samples[i].normal,samples[j].normal))*4.0));descriptor.values[distanceBin*4+normalBin]+=1.0;++neighbours;});if(neighbours<8)continue;double norm=0;for(double value:descriptor.values)norm+=value*value;if(norm<=0)continue;for(double& value:descriptor.values)value/=std::sqrt(norm);descriptor.valid=true;out.push_back(descriptor);}return out;
}

double descriptorDistance(const Descriptor& a,const Descriptor& b){double sum=0;for(std::size_t i=0;i<a.values.size();++i){const double delta=a.values[i]-b.values[i];sum+=delta*delta;}return sum;}

std::vector<std::array<double,4>> descriptorHypotheses(const std::vector<Point>& reference,const std::vector<Point>& moving,const GlobalRegistrationOptions& options){
    const double span=spanOf(reference,moving),voxel=std::max(span/80.0,1e-3);const auto refSamples=normals(downsample(reference,voxel),voxel*5.5),movSamples=normals(downsample(moving,voxel),voxel*5.5);const auto ref=describe(refSamples,voxel*5.5,320),mov=describe(movSamples,voxel*5.5,320);if(ref.size()<12||mov.size()<12)return {};
    std::vector<std::size_t> movingBest(mov.size()),referenceBest(ref.size());std::vector<double> movingDistance(mov.size(),std::numeric_limits<double>::infinity());for(std::size_t m=0;m<mov.size();++m)for(std::size_t r=0;r<ref.size();++r){const double distance=descriptorDistance(mov[m],ref[r]);if(distance<movingDistance[m]){movingDistance[m]=distance;movingBest[m]=r;}}
    for(std::size_t r=0;r<ref.size();++r){double best=std::numeric_limits<double>::infinity();for(std::size_t m=0;m<mov.size();++m){const double distance=descriptorDistance(ref[r],mov[m]);if(distance<best){best=distance;referenceBest[r]=m;}}}
    std::vector<std::pair<std::size_t,std::size_t>> matches;for(std::size_t m=0;m<mov.size();++m)if(referenceBest[movingBest[m]]==m&&movingDistance[m]<.75)matches.push_back({m,movingBest[m]});if(matches.size()<4)return {};
    const Vec3 pivot=center(moving);std::vector<std::array<double,4>> out;const std::size_t stride=std::max<std::size_t>(1,matches.size()/80);for(std::size_t ai=0;ai<matches.size();ai+=stride)for(std::size_t bi=ai+stride;bi<matches.size();bi+=stride){const auto& ma=mov[matches[ai].first].sample.point;const auto& mb=mov[matches[bi].first].sample.point;const auto& ra=ref[matches[ai].second].sample.point;const auto& rb=ref[matches[bi].second].sample.point;const Vec3 dm=sub(mb,ma),dr=sub(rb,ra);if(std::hypot(dm.x,dm.y)<voxel||std::hypot(dr.x,dr.y)<voxel)continue;const double yaw=std::atan2(dm.x*dr.y-dm.y*dr.x,dm.x*dr.x+dm.y*dr.y)*180.0/3.14159265358979323846;const Vec3 direct=sub(ra,rotateZ(ma,yaw));const auto hypothesis=pivotTransform(direct,pivot,yaw);bool distinct=true;for(const auto& old:out){const double dx=hypothesis[0]-old[0],dy=hypothesis[1]-old[1],dz=hypothesis[2]-old[2];if(std::sqrt(dx*dx+dy*dy+dz*dz)<voxel*3&&std::abs(std::remainder(hypothesis[3]-old[3],360.0))<3){distinct=false;break;}}if(distinct)out.push_back(hypothesis);if(out.size()>=180)return out;}
    return out;
}

std::vector<std::array<double,4>> featureHypotheses(const std::vector<Point>& reference,const std::vector<Point>& moving,const GlobalRegistrationOptions& options){
    auto descriptorCandidates=descriptorHypotheses(reference,moving,options);if(!descriptorCandidates.empty())return descriptorCandidates;
    const double span=spanOf(reference,moving),voxel=std::max(span/80.0,1e-3),cellSize=std::max(voxel*2.0,span/160.0);
    const auto ref=downsample(reference,voxel),mov=downsample(moving,voxel);
    if(ref.size()<30||mov.size()<30)return {};
    const auto refFeatures=normals(ref,voxel*5.5),movFeatures=normals(mov,voxel*5.5);const Vec3 pivot=center(moving);
    std::vector<std::array<double,4>> hypotheses;
    const std::size_t refStride=std::max<std::size_t>(1,refFeatures.size()/300),movStride=std::max<std::size_t>(1,movFeatures.size()/300);
    for(double yaw=-180.0;yaw<180.0;yaw+=options.yawStepDegrees){
        if(options.refinement.cancellation&&options.refinement.cancellation->load())return {};
        std::unordered_map<Cell,PoseVote,CellHash> votes;
        for(std::size_t mi=0;mi<movFeatures.size();mi+=movStride){const auto& m=movFeatures[mi];if(!m.hasNormal)continue;const Vec3 rotatedPoint=rotateZ(m.point,yaw),rotatedNormal=rotateZ(m.normal,yaw);
            for(std::size_t ri=0;ri<refFeatures.size();ri+=refStride){const auto& r=refFeatures[ri];if(!r.hasNormal||std::abs(dot(r.normal,rotatedNormal))<.82)continue;const Vec3 translation=sub(r.point,rotatedPoint);auto& vote=votes[cellFor(translation,cellSize)];++vote.count;vote.translation=add(vote.translation,translation);}
        }
        std::vector<std::pair<Cell,PoseVote>> best;best.reserve(votes.size());for(const auto& entry:votes)if(entry.second.count>=3)best.push_back(entry);std::sort(best.begin(),best.end(),[](const auto& a,const auto& b){return a.second.count>b.second.count;});
        const std::size_t limit=std::min<std::size_t>(3,best.size());for(std::size_t i=0;i<limit;++i)hypotheses.push_back(pivotTransform(mul(best[i].second.translation,1.0/best[i].second.count),pivot,yaw));
    }
    return hypotheses;
}

struct Correspondence {
    double residual{}, absolute{}, distance{};
    std::array<double,4> jacobian{};
    Vec3 offset{}, relative{};
};

bool refineIteration(const std::vector<Vec3>& reference,
                     const std::vector<Sample>& samples,
                     const Index& referenceIndex,
                     const std::vector<Vec3>& moving, Vec3 pivot,
                     std::array<double,4>& transform, double voxel,
                     double minimumOverlap, double delta[4]) {
    const double maximum = voxel * 2.5;
    std::vector<Vec3> transformed;
    transformed.reserve(moving.size());
    for (auto point : moving)
        transformed.push_back(transformPoint(point, pivot, transform));
    std::vector<Correspondence> pairs;
    pairs.reserve(moving.size());
    std::size_t forwardMatches = 0;
    for (const Vec3 point : transformed) {
        std::size_t nearest{};
        double distance2{};
        if (!referenceIndex.nearest(point, maximum, nearest, distance2)) continue;
        ++forwardMatches;
        if (!samples[nearest].hasNormal) continue;
        const Vec3 normal = samples[nearest].normal;
        const Vec3 offset = sub(point, samples[nearest].point);
        const double residual = dot(offset, normal);
        const Vec3 relative = sub(point, {pivot.x + transform[0],
                                         pivot.y + transform[1],
                                         pivot.z + transform[2]});
        pairs.push_back({residual, std::abs(residual), std::sqrt(distance2),
                         {normal.x, normal.y, normal.z,
                          -relative.y * normal.x + relative.x * normal.y},
                         offset, relative});
    }
    const double forwardOverlap = moving.empty() ? 0.0 :
        static_cast<double>(forwardMatches) / moving.size();
    if (pairs.size() < 30 || forwardOverlap < minimumOverlap) return false;
    std::sort(pairs.begin(), pairs.end(), [](const auto& a, const auto& b) {
        return a.absolute + .03 * a.distance <
               b.absolute + .03 * b.distance;
    });
    const std::size_t keep = std::max<std::size_t>(
        30, static_cast<std::size_t>(pairs.size() * .80));
    double normalMatrix[4][4]{}, rhs[4]{};
    const auto addEquation = [&](const std::array<double,4>& jacobian,
                                 double residual, double weight) {
        for (int row = 0; row < 4; ++row) {
            rhs[row] += -weight * jacobian[row] * residual;
            for (int column = 0; column < 4; ++column)
                normalMatrix[row][column] +=
                    weight * jacobian[row] * jacobian[column];
        }
    };
    for (std::size_t i = 0; i < keep; ++i) {
        const auto& pair = pairs[i];
        const double weight = pair.absolute <= voxel * .5 ? 1.0 :
                              voxel * .5 / pair.absolute;
        addEquation(pair.jacobian, pair.residual, weight);
        const double pointWeight = .05 * weight *
            std::min(1.0, voxel / std::max(pair.distance, 1e-9));
        addEquation({1, 0, 0, -pair.relative.y}, pair.offset.x, pointWeight);
        addEquation({0, 1, 0,  pair.relative.x}, pair.offset.y, pointWeight);
        addEquation({0, 0, 1, 0}, pair.offset.z, pointWeight);
    }
    if (!solve4(normalMatrix, rhs, delta)) return false;
    const double translationLength = std::sqrt(
        delta[0]*delta[0] + delta[1]*delta[1] + delta[2]*delta[2]);
    if (translationLength > voxel * .75) {
        const double scale = voxel * .75 / translationLength;
        for (int i = 0; i < 3; ++i) delta[i] *= scale;
    }
    const double yawLimit = 1.5 * 3.14159265358979323846 / 180.0;
    delta[3] = std::clamp(delta[3], -yawLimit, yawLimit);
    for (int i = 0; i < 3; ++i) transform[i] += delta[i];
    transform[3] += delta[3] * 180.0 / 3.14159265358979323846;
    return true;
}

void estimateCandidateZ(const ProjectionGrid& reference,
                        const std::vector<Vec3>& moving, Vec3 pivot,
                        double cellSize, std::array<double, 4>& transform) {
    std::vector<double> offsets;
    const std::size_t stride = std::max<std::size_t>(1, moving.size() / 8000);
    for (std::size_t i = 0; i < moving.size(); i += stride) {
        const Vec3 point = transformPoint(moving[i], pivot, transform);
        const Cell key{static_cast<int>(std::floor(point.x / cellSize)),
                       static_cast<int>(std::floor(point.y / cellSize)), 0};
        const auto found = reference.find(key);
        if (found == reference.end() || found->second.count < 2) continue;
        const double meanZ = found->second.sumZ / found->second.count;
        offsets.push_back(meanZ - point.z);
    }
    if (offsets.size() < 30) return;
    const auto middle = offsets.begin() + offsets.size() / 2;
    std::nth_element(offsets.begin(), middle, offsets.end());
    const double correction = *middle;
    if (std::isfinite(correction) && std::abs(correction) <= cellSize * 4.0)
        transform[2] += correction;
}

double quickHypothesisScore(const Index& referenceIndex,
                            const std::vector<Vec3>& moving, Vec3 pivot,
                            const std::array<double, 4>& transform,
                            double maximumDistance) {
    if (moving.empty()) return -std::numeric_limits<double>::infinity();
    const std::size_t stride = std::max<std::size_t>(1, moving.size() / 6000);
    std::size_t tested = 0, matches = 0;
    double distance = 0;
    for (std::size_t i = 0; i < moving.size(); i += stride) {
        ++tested;
        std::size_t nearest{};
        double distance2{};
        if (!referenceIndex.nearest(
                transformPoint(moving[i], pivot, transform), maximumDistance,
                nearest, distance2)) continue;
        ++matches;
        distance += std::sqrt(distance2);
    }
    if (tested == 0 || matches < 20) return -1;
    const double overlap = static_cast<double>(matches) / tested;
    const double meanDistance = distance / matches;
    return overlap - .30 * meanDistance / maximumDistance;
}

std::array<double, 4> localProjectionSeed(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    Vec3 pivot, const std::array<double, 4>& initial, double span,
    double millimetreScale, const std::atomic_bool* cancellation) {
    const double cellSize = std::clamp(
        span / 500.0, 150.0 * millimetreScale,
        400.0 * millimetreScale);
    auto referencePoints = downsample(reference, cellSize);
    auto movingPoints = downsample(moving, cellSize);
    limitSample(referencePoints, 40000);
    limitSample(movingPoints, 12000);
    if (referencePoints.size() < 30 || movingPoints.size() < 30) return initial;
    const auto referenceGrid = buildProjection(referencePoints, cellSize);
    const auto directPose = [&](const std::array<double, 4>& transform) {
        const Vec3 rotatedPivot = rotateZ(pivot, transform[3]);
        return ProjectionPose{{transform[0] + pivot.x - rotatedPivot.x,
                               transform[1] + pivot.y - rotatedPivot.y,
                               transform[2]}, transform[3], 0};
    };
    const auto score = [&](const std::array<double, 4>& transform) {
        return projectionScore(referenceGrid, movingPoints, cellSize,
                               directPose(transform));
    };
    std::array<double, 4> best = initial;
    const double initialScore = score(initial);
    double bestScore = initialScore;
    const auto search = [&](const std::array<double, 4>& center,
                            double translationStep, int translationSteps,
                            double yawStep, int yawSteps) {
        for (int yaw = -yawSteps; yaw <= yawSteps; ++yaw) {
            for (int x = -translationSteps; x <= translationSteps; ++x) {
                for (int y = -translationSteps; y <= translationSteps; ++y) {
                    if (cancellation && cancellation->load()) return;
                    auto candidate = center;
                    candidate[0] += x * translationStep;
                    candidate[1] += y * translationStep;
                    candidate[3] += yaw * yawStep;
                    const double candidateScore = score(candidate);
                    if (candidateScore > bestScore) {
                        best = candidate;
                        bestScore = candidateScore;
                    }
                }
            }
        }
    };
    search(initial, cellSize, 4, 2.0, 2);
    const auto coarseBest = best;
    search(coarseBest, cellSize * .5, 2, .5, 2);
    const double requiredGain = std::max(.015, initialScore * .04);
    return bestScore >= initialScore + requiredGain ? best : initial;
}

std::vector<std::array<double, 4>> rankHypotheses(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    std::vector<std::array<double, 4>> hypotheses, double span,
    double millimetreScale, const std::atomic_bool* cancellation) {
    if (hypotheses.empty()) return {};
    const double voxel = std::clamp(
        span / 100.0, 200.0 * millimetreScale,
        1000.0 * millimetreScale);
    const auto coarseReference = downsample(reference, voxel);
    const auto coarseMoving = downsample(moving, voxel);
    const Vec3 pivot = center(moving);
    const Index referenceIndex(coarseReference, voxel * 3.0);
    const auto projection = buildProjection(coarseReference, voxel * 1.5);
    std::vector<std::pair<double, std::array<double, 4>>> scored;
    scored.reserve(hypotheses.size());
    for (auto hypothesis : hypotheses) {
        if (cancellation && cancellation->load()) return {};
        estimateCandidateZ(projection, coarseMoving, pivot, voxel * 1.5,
                           hypothesis);
        const double score = quickHypothesisScore(
            referenceIndex, coarseMoving, pivot, hypothesis, voxel * 3.0);
        if (score > .02) scored.push_back({score, hypothesis});
    }
    std::stable_sort(scored.begin(), scored.end(),
                     [](const auto& a, const auto& b) {
                         return a.first > b.first;
                     });
    std::vector<std::array<double, 4>> ranked;
    for (const auto& [_, hypothesis] : scored) {
        bool duplicate = false;
        for (const auto& kept : ranked) {
            const double dx = hypothesis[0] - kept[0];
            const double dy = hypothesis[1] - kept[1];
            const double dz = hypothesis[2] - kept[2];
            if (std::sqrt(dx * dx + dy * dy + dz * dz) < voxel * 1.5 &&
                std::abs(std::remainder(hypothesis[3] - kept[3], 360.0)) < 2.0) {
                duplicate = true;
                break;
            }
        }
        if (!duplicate) ranked.push_back(hypothesis);
        if (ranked.size() >= 3) break;
    }
    return ranked;
}

} // namespace

std::vector<Point> xyzToPoints(const std::vector<float>& xyz){std::vector<Point> out;out.reserve(xyz.size()/3);for(std::size_t i=0;i+2<xyz.size();i+=3)out.push_back({xyz[i],xyz[i+1],xyz[i+2]});return out;}

RegistrationResult registerConstrained(
    const std::vector<Point>& reference, const std::vector<Point>& moving,
    const std::array<double, 4>& initial, const RegistrationOptions& options) {
    RegistrationResult result;
    result.transform = initial;
    auto reject = [&](const char* reason) {
        result.accepted = false;
        result.transform = initial;
        result.reason = reason;
        return result;
    };
    if (reference.size() < 100 || moving.size() < 100)
        return reject("not enough points");
    const double span = spanOf(reference, moving);
    if (!std::isfinite(span) || span <= 0)
        return reject("invalid cloud bounds");
    if (!std::isfinite(options.millimetreScale) ||
        options.millimetreScale <= 0)
        return reject("invalid registration unit scale");
    const double mm = options.millimetreScale;
    const Vec3 pivot = center(moving);
    result.transform = localProjectionSeed(
        reference, moving, pivot, initial, span, mm, options.cancellation);
    const std::array<double, 4> voxels{
        std::clamp(span / 80.0, 250.0 * mm, 1500.0 * mm),
        std::clamp(span / 200.0, 100.0 * mm, 500.0 * mm),
        std::clamp(span / 400.0, 50.0 * mm, 250.0 * mm),
        std::clamp(span / 600.0, 40.0 * mm, 200.0 * mm)};
    for (double voxel : voxels) {
        if (options.cancellation && options.cancellation->load())
            return reject("cancelled");
        auto ref = downsample(reference, voxel);
        auto mov = downsample(moving, voxel);
        limitSample(ref, options.maxInputPoints);
        limitSample(mov, options.maxInputPoints);
        if (ref.size() < 30 || mov.size() < 30)
            return reject("not enough spatial coverage");
        auto samples = normals(ref, voxel * 4.0);
        Index index(ref, voxel * 2.5);
        for (int iteration = 0; iteration < options.iterationsPerLevel;
             ++iteration) {
            if (options.cancellation && options.cancellation->load())
                return reject("cancelled");
            double delta[4]{};
            if (!refineIteration(ref, samples, index, mov, pivot,
                                 result.transform, voxel,
                                 options.minimumOverlap, delta)) {
                return reject(iteration == 0 ? "insufficient overlap" :
                                               "degenerate geometry");
            }
            const double dx = result.transform[0] - initial[0];
            const double dy = result.transform[1] - initial[1];
            const double dz = result.transform[2] - initial[2];
            result.correctionTranslation = std::sqrt(dx * dx + dy * dy + dz * dz);
            result.correctionYaw = std::abs(std::remainder(
                result.transform[3] - initial[3], 360.0));
            const double ratioLimit = span * options.maximumInitialTranslationRatio;
            if ((std::isfinite(options.maximumInitialTranslationMeters) &&
                 result.correctionTranslation >
                     options.maximumInitialTranslationMeters) ||
                (std::isfinite(options.maximumInitialTranslationRatio) &&
                 result.correctionTranslation > ratioLimit)) {
                return reject("local refinement moved too far");
            }
            if (std::isfinite(options.maximumInitialYawDelta) &&
                result.correctionYaw > options.maximumInitialYawDelta) {
                return reject("local refinement rotated too far");
            }
            ++result.iterations;
            if (std::sqrt(delta[0] * delta[0] + delta[1] * delta[1] +
                           delta[2] * delta[2]) < voxel * 2e-3 &&
                std::abs(delta[3]) < 1e-4) {
                break;
            }
        }
    }
    const double validationVoxel = voxels.back();
    const double effectiveRmsLimit = options.adaptiveResidualLimits ?
        std::max(options.rmsLimit,
                  std::clamp(validationVoxel * .25, 4.0 * mm, 8.0 * mm)) :
        options.rmsLimit;
    const double effectiveP95Limit = options.adaptiveResidualLimits ?
        std::max(options.p95Limit,
                  std::clamp(validationVoxel * .40, 8.0 * mm, 15.0 * mm)) :
        options.p95Limit;
    auto controlRef = downsample(reference, validationVoxel);
    auto controlMov = downsample(moving, validationVoxel);
    limitSample(controlRef, options.maxInputPoints);
    limitSample(controlMov, options.maxInputPoints);
    auto quality = surfaceMetrics(
        controlRef, controlMov, pivot, result.transform,
        validationVoxel * 3.0, effectiveRmsLimit, effectiveP95Limit);
    if (quality.distances.size() < 30)
        return reject("not enough surface support");
    result.rms = quality.rms;
    result.p95 = quality.p95;
    result.overlap = quality.overlap;
    const double translationScale =
        std::max(span * .05, effectiveP95Limit * 10.0);
    const double translationStability = std::clamp(
        1.0 - result.correctionTranslation / translationScale, 0.0, 1.0);
    const double yawStability = std::clamp(
        1.0 - result.correctionYaw / 10.0, 0.0, 1.0);
    const double refinementStability =
        (translationStability + yawStability) * .5;
    result.consistency = .75 * quality.consistency +
                         .25 * refinementStability;
    result.confidence = 73.0 + 27.0 *
        (.25 * std::clamp(result.consistency, 0.0, 1.0) +
         .75 * quality.residualQuality);
    if (result.overlap < options.minimumOverlap)
        return reject("insufficient validation overlap");
    if (result.consistency < options.minimumConsistency)
        return reject("insufficient reciprocal consistency");
    if (result.rms > effectiveRmsLimit)
        return reject("RMS exceeds threshold");
    if (result.p95 > effectiveP95Limit)
        return reject("P95 exceeds threshold");
    if (result.confidence < options.minimumConfidence)
        return reject("low registration confidence");
    result.accepted = true;
    result.reason = "accepted";
    return result;
}

RegistrationResult registerGlobalConstrained(const std::vector<Point>& reference,const std::vector<Point>& moving,const GlobalRegistrationOptions& options){
    RegistrationResult rejected;if(reference.size()<100||moving.size()<100){rejected.reason="not enough points";return rejected;}
    if(!std::isfinite(options.yawStepDegrees)||options.yawStepDegrees<5.0||options.yawStepDegrees>90.0){rejected.reason="invalid yaw step";return rejected;}
    const double distinctTranslation=std::max(spanOf(reference,moving)/100.0,options.refinement.p95Limit*2.0);
    RegistrationResult best,second,bestRejected;double bestScore=std::numeric_limits<double>::infinity(),secondScore=bestScore,rejectedScore=-std::numeric_limits<double>::infinity();
    auto hypotheses = projectionHypotheses(reference, moving, options);
    auto planeCandidates = planeHypotheses(reference, moving, options);
    hypotheses.insert(hypotheses.end(), planeCandidates.begin(),
                      planeCandidates.end());
    if (hypotheses.size() < 16) {
        auto featureCandidates = featureHypotheses(reference, moving, options);
        hypotheses.insert(hypotheses.end(), featureCandidates.begin(),
                          featureCandidates.end());
    }
    if(hypotheses.empty()){const Vec3 referenceCenter=center(reference),movingCenter=center(moving),translation=sub(referenceCenter,movingCenter);for(double yaw=-180.0;yaw<180.0;yaw+=options.yawStepDegrees)hypotheses.push_back(pivotTransform(translation,movingCenter,yaw));}
    auto rankedHypotheses = rankHypotheses(
        reference, moving, hypotheses, spanOf(reference, moving),
        options.refinement.millimetreScale,
        options.refinement.cancellation);
    if (!rankedHypotheses.empty()) hypotheses = std::move(rankedHypotheses);
    for(const auto& initial:hypotheses){
        if(options.refinement.cancellation&&options.refinement.cancellation->load()){rejected.reason="cancelled";return rejected;}
        auto candidate=registerConstrained(reference,moving,initial,options.refinement);
        if(!candidate.accepted){const double diagnostic=candidate.confidence+candidate.overlap*20.0+candidate.consistency*10.0-std::min(10.0,candidate.rms/std::max(options.refinement.rmsLimit,1e-9))-std::min(10.0,candidate.p95/std::max(options.refinement.p95Limit,1e-9));if(bestRejected.reason.empty()||diagnostic>rejectedScore){bestRejected=candidate;rejectedScore=diagnostic;}continue;}
        const double score=candidate.rms+candidate.p95+(1.0-candidate.overlap)*options.refinement.p95Limit;
        if(best.accepted){double dx=candidate.transform[0]-best.transform[0],dy=candidate.transform[1]-best.transform[1],dz=candidate.transform[2]-best.transform[2];double da=std::remainder(candidate.transform[3]-best.transform[3],360.0);if(std::sqrt(dx*dx+dy*dy+dz*dz)<distinctTranslation&&std::abs(da)<1.0){if(score<bestScore){best=candidate;bestScore=score;}continue;}}
        if(score<bestScore){second=best;secondScore=bestScore;best=candidate;bestScore=score;}else if(score<secondScore){second=candidate;secondScore=score;}
    }
    if(!best.accepted){if(!bestRejected.reason.empty())return bestRejected;rejected.reason="no global hypothesis";return rejected;}
    if (second.accepted) {
        const double separation = (secondScore - bestScore) /
            std::max(bestScore, 1e-12);
        best.confidence = std::min(
            best.confidence,
            73.0 + 27.0 * std::clamp(
                separation / std::max(options.ambiguityRatio, 1e-6),
                0.0, 1.0));
        if (secondScore <= bestScore * (1.0 + options.ambiguityRatio)) {
            best.accepted = false;
            best.reason = "ambiguous global hypotheses";
            return best;
        }
    }
    if (best.confidence < options.minimumConfidence) {
        best.reason = "check registration";
        return best;
    }
    best.reason="accepted global";return best;
}

} // namespace tzf
