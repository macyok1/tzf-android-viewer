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
struct Sample { Vec3 point; Vec3 normal; bool hasNormal{}; };
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
Vec3 cross(Vec3 a, Vec3 b) { return {a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x}; }
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

class Index {
public:
    Index(const std::vector<Vec3>& points,double cellSize):points_(points),cellSize_(cellSize){cells_.reserve(points.size()*2);for(std::size_t i=0;i<points.size();++i)cells_[cellFor(points[i],cellSize)].push_back(i);}
    template<class F> void nearby(Vec3 p,int radius,F&& visit)const{const auto c=cellFor(p,cellSize_);for(int z=-radius;z<=radius;++z)for(int y=-radius;y<=radius;++y)for(int x=-radius;x<=radius;++x){auto it=cells_.find({c.x+x,c.y+y,c.z+z});if(it!=cells_.end())for(auto i:it->second)visit(i);}}
    bool nearest(Vec3 p,double maximum,std::size_t& best,double& distance2)const{distance2=maximum*maximum;bool found=false;const int radius=std::max(1,static_cast<int>(std::ceil(maximum/cellSize_)));nearby(p,radius,[&](std::size_t i){const auto d=length2(sub(points_[i],p));if(d<distance2){distance2=d;best=i;found=true;}});return found;}
private:
    const std::vector<Vec3>& points_;double cellSize_;std::unordered_map<Cell,std::vector<std::size_t>,CellHash> cells_;
};

std::vector<Sample> normals(const std::vector<Vec3>& points,double radius){
    Index index(points,radius);std::vector<Sample> out(points.size());
    for(std::size_t i=0;i<points.size();++i){out[i].point=points[i];std::array<std::pair<double,std::size_t>,16> near{};for(auto& n:near)n={std::numeric_limits<double>::infinity(),0};index.nearby(points[i],1,[&](std::size_t j){if(i==j)return;double d=length2(sub(points[j],points[i]));if(d>radius*radius)return;auto worst=std::max_element(near.begin(),near.end());if(d<worst->first)*worst={d,j};});std::sort(near.begin(),near.end());if(!std::isfinite(near[1].first))continue;Vec3 a=sub(points[near[0].second],points[i]);Vec3 best{};double bestArea=0;for(std::size_t k=1;k<near.size()&&std::isfinite(near[k].first);++k){auto n=cross(a,sub(points[near[k].second],points[i]));double area=length2(n);if(area>bestArea){bestArea=area;best=n;}}if(bestArea>radius*radius*radius*radius*1e-6&&normalize(best)){out[i].normal=best;out[i].hasNormal=true;}}
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

    std::vector<ProjectionPose> coarseCandidates;
    for (double yaw = -180.0; yaw < 180.0; yaw += options.yawStepDegrees) {
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

struct Correspondence { double residual{},absolute{}; std::array<double,4> jacobian{}; };
bool refineIteration(const std::vector<Vec3>& reference,const std::vector<Sample>& samples,const Index& referenceIndex,const std::vector<Vec3>& moving,Vec3 pivot,std::array<double,4>& transform,double voxel,double minimumOverlap,double delta[4]){
    const double maximum=voxel*3.0;std::vector<Vec3> transformed;transformed.reserve(moving.size());for(auto p:moving)transformed.push_back(transformPoint(p,pivot,transform));Index movingIndex(transformed,voxel*2.5);std::vector<Correspondence> pairs;pairs.reserve(moving.size());
    std::size_t forwardMatches=0;for(std::size_t sourceIndex=0;sourceIndex<transformed.size();++sourceIndex){const Vec3 p=transformed[sourceIndex];std::size_t nearest{};double distance2{};if(!referenceIndex.nearest(p,maximum,nearest,distance2)||!samples[nearest].hasNormal)continue;++forwardMatches;std::size_t reciprocal{};double reciprocalDistance{};if(!movingIndex.nearest(reference[nearest],maximum,reciprocal,reciprocalDistance)||reciprocal!=sourceIndex)continue;const Vec3 n=samples[nearest].normal;const double residual=dot(sub(p,samples[nearest].point),n);const Vec3 relative=sub(p,{pivot.x+transform[0],pivot.y+transform[1],pivot.z+transform[2]});pairs.push_back({residual,std::abs(residual),{n.x,n.y,n.z,-relative.y*n.x+relative.x*n.y}});}
    const double forwardOverlap=moving.empty()?0.0:static_cast<double>(forwardMatches)/moving.size();const double reciprocalSupport=forwardMatches==0?0.0:static_cast<double>(pairs.size())/forwardMatches;if(pairs.size()<30||forwardOverlap<minimumOverlap||reciprocalSupport<.10)return false;std::sort(pairs.begin(),pairs.end(),[](const auto&a,const auto&b){return a.absolute<b.absolute;});const std::size_t keep=std::max<std::size_t>(30,static_cast<std::size_t>(pairs.size()*.85));double normal[4][4]{},rhs[4]{};for(std::size_t i=0;i<keep;++i){const auto& pair=pairs[i];const double weight=pair.absolute<=voxel?1.0:voxel/pair.absolute;for(int r=0;r<4;++r){rhs[r]+=-weight*pair.jacobian[r]*pair.residual;for(int c=0;c<4;++c)normal[r][c]+=weight*pair.jacobian[r]*pair.jacobian[c];}}if(!solve4(normal,rhs,delta))return false;for(int i=0;i<3;++i)transform[i]+=delta[i];transform[3]+=delta[3]*180.0/3.14159265358979323846;return true;
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
    const Vec3 pivot = center(moving);
    const std::array<double, 3> voxels{span / 24.0, span / 48.0,
                                       span / 96.0};
    for (double voxel : voxels) {
        if (options.cancellation && options.cancellation->load())
            return reject("cancelled");
        auto ref = downsample(reference, voxel);
        auto mov = downsample(moving, voxel);
        if (ref.size() < 30 || mov.size() < 30)
            return reject("not enough spatial coverage");
        auto samples = normals(ref, voxel * 5.5);
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
                          delta[2] * delta[2]) < voxel * 1e-4 &&
                std::abs(delta[3]) < 1e-5) {
                break;
            }
        }
    }
    auto controlRef = controlSample(reference, 120000);
    auto controlMov = controlSample(moving, 120000);
    auto quality = metrics(controlRef, controlMov, pivot, result.transform,
                           std::max(options.p95Limit * 4.0, span / 100.0),
                           options.rmsLimit, options.p95Limit);
    result.rms = quality.rms;
    result.p95 = quality.p95;
    result.overlap = quality.overlap;
    const double translationScale =
        std::max(span * .05, options.p95Limit * 10.0);
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
    if (result.rms > options.rmsLimit)
        return reject("RMS exceeds threshold");
    if (result.p95 > options.p95Limit)
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
    if (hypotheses.empty()) {
        hypotheses = planeHypotheses(reference, moving, options);
        auto featureCandidates = featureHypotheses(reference, moving, options);
        hypotheses.insert(hypotheses.end(), featureCandidates.begin(),
                          featureCandidates.end());
    }
    if(hypotheses.empty()){const Vec3 referenceCenter=center(reference),movingCenter=center(moving),translation=sub(referenceCenter,movingCenter);for(double yaw=-180.0;yaw<180.0;yaw+=options.yawStepDegrees)hypotheses.push_back(pivotTransform(translation,movingCenter,yaw));}
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
        best.accepted = false;
        best.reason = "low global confidence";
        return best;
    }
    best.reason="accepted global";return best;
}

} // namespace tzf
