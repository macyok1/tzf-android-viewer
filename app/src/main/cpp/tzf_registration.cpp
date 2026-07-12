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

struct Metrics{double rms{},p95{},overlap{};std::vector<double> distances;};
Metrics metrics(const std::vector<Vec3>& reference,const std::vector<Vec3>& moving,Vec3 pivot,const std::array<double,4>& t,double maxDistance){Metrics m;std::vector<Vec3> transformed;transformed.reserve(moving.size());for(auto p:moving)transformed.push_back(transformPoint(p,pivot,t));Index refIndex(reference,maxDistance),movIndex(transformed,maxDistance);double sum=0;std::size_t forward=0,backward=0;for(auto q:transformed){std::size_t nearest{};double d2{};if(refIndex.nearest(q,maxDistance,nearest,d2)){m.distances.push_back(std::sqrt(d2));sum+=d2;++forward;}}for(auto p:reference){std::size_t nearest{};double d2{};if(movIndex.nearest(p,maxDistance,nearest,d2)){m.distances.push_back(std::sqrt(d2));sum+=d2;++backward;}}double forwardOverlap=moving.empty()?0.0:(double)forward/moving.size(),backwardOverlap=reference.empty()?0.0:(double)backward/reference.size();m.overlap=std::min(forwardOverlap,backwardOverlap);if(!m.distances.empty()){m.rms=std::sqrt(sum/m.distances.size());std::sort(m.distances.begin(),m.distances.end());m.p95=m.distances[static_cast<std::size_t>((m.distances.size()-1)*.95)];}return m;}

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

} // namespace

std::vector<Point> xyzToPoints(const std::vector<float>& xyz){std::vector<Point> out;out.reserve(xyz.size()/3);for(std::size_t i=0;i+2<xyz.size();i+=3)out.push_back({xyz[i],xyz[i+1],xyz[i+2]});return out;}

RegistrationResult registerConstrained(const std::vector<Point>& reference,const std::vector<Point>& moving,const std::array<double,4>& initial,const RegistrationOptions& options){RegistrationResult result;result.transform=initial;if(reference.size()<100||moving.size()<100){result.reason="not enough points";return result;}const double span=spanOf(reference,moving);if(!std::isfinite(span)||span<=0){result.reason="invalid cloud bounds";return result;}const Vec3 pivot=center(moving);const std::array<double,3> voxels{span/24.0,span/48.0,span/96.0};
    for(double voxel:voxels){if(options.cancellation&&options.cancellation->load()){result.reason="cancelled";return result;}auto ref=downsample(reference,voxel),mov=downsample(moving,voxel);if(ref.size()<30||mov.size()<30){result.reason="not enough spatial coverage";return result;}auto samples=normals(ref,voxel*5.5);Index index(ref,voxel*2.5);const double maxDistance=voxel*3.0;
        for(int iteration=0;iteration<options.iterationsPerLevel;++iteration){if(options.cancellation&&options.cancellation->load()){result.reason="cancelled";return result;}double normal[4][4]{},rhs[4]{};std::size_t used=0;double absSum=0;std::size_t sourceIndex=0;for(auto source:mov){if((++sourceIndex&4095U)==0&&options.cancellation&&options.cancellation->load()){result.reason="cancelled";return result;}Vec3 p=transformPoint(source,pivot,result.transform);std::size_t nearest{};double d2{};if(!index.nearest(p,maxDistance,nearest,d2)||!samples[nearest].hasNormal)continue;const Vec3 n=samples[nearest].normal;double residual=dot(sub(p,samples[nearest].point),n);double huber=voxel;double weight=std::abs(residual)<=huber?1.0:huber/std::abs(residual);Vec3 relative=sub(p,{pivot.x+result.transform[0],pivot.y+result.transform[1],pivot.z+result.transform[2]});double j[4]{n.x,n.y,n.z,(-relative.y*n.x+relative.x*n.y)};for(int r=0;r<4;++r){rhs[r]+=-weight*j[r]*residual;for(int c=0;c<4;++c)normal[r][c]+=weight*j[r]*j[c];}absSum+=std::abs(residual);++used;}if(used<30||static_cast<double>(used)/mov.size()<options.minimumOverlap){result.reason="insufficient overlap";return result;}double delta[4]{};if(!solve4(normal,rhs,delta)){result.reason="degenerate geometry";return result;}result.transform[0]+=delta[0];result.transform[1]+=delta[1];result.transform[2]+=delta[2];result.transform[3]+=delta[3]*180.0/3.14159265358979323846;if(std::isfinite(options.maximumInitialTranslationRatio)){const double dx=result.transform[0]-initial[0],dy=result.transform[1]-initial[1],dz=result.transform[2]-initial[2];if(std::sqrt(dx*dx+dy*dy+dz*dz)>span*options.maximumInitialTranslationRatio){result.reason="local refinement moved too far";return result;}}if(std::isfinite(options.maximumInitialYawDelta)&&std::abs(std::remainder(result.transform[3]-initial[3],360.0))>options.maximumInitialYawDelta){result.reason="local refinement rotated too far";return result;}++result.iterations;if(std::sqrt(delta[0]*delta[0]+delta[1]*delta[1]+delta[2]*delta[2])<voxel*1e-4&&std::abs(delta[3])<1e-5)break;if(!std::isfinite(absSum)){result.reason="registration diverged";return result;}}
    }
    auto controlRef=controlSample(reference,120000),controlMov=controlSample(moving,120000);auto m=metrics(controlRef,controlMov,pivot,result.transform,std::max(options.p95Limit*4.0,span/100.0));result.rms=m.rms;result.p95=m.p95;result.overlap=m.overlap;if(m.overlap<options.minimumOverlap)result.reason="insufficient validation overlap";else{result.accepted=true;if(m.rms>options.rmsLimit)result.reason="quality warning: RMS exceeds threshold";else if(m.p95>options.p95Limit)result.reason="quality warning: P95 exceeds threshold";else result.reason="accepted";}return result;}

RegistrationResult registerGlobalConstrained(const std::vector<Point>& reference,const std::vector<Point>& moving,const GlobalRegistrationOptions& options){
    RegistrationResult rejected;if(reference.size()<100||moving.size()<100){rejected.reason="not enough points";return rejected;}
    if(!std::isfinite(options.yawStepDegrees)||options.yawStepDegrees<5.0||options.yawStepDegrees>90.0){rejected.reason="invalid yaw step";return rejected;}
    const double distinctTranslation=std::max(spanOf(reference,moving)/100.0,options.refinement.p95Limit*2.0);
    RegistrationResult best,second;double bestScore=std::numeric_limits<double>::infinity(),secondScore=bestScore;
    auto hypotheses=planeHypotheses(reference,moving,options);
    auto featureCandidates=featureHypotheses(reference,moving,options);
    hypotheses.insert(hypotheses.end(),featureCandidates.begin(),featureCandidates.end());
    if(hypotheses.empty()){const Vec3 referenceCenter=center(reference),movingCenter=center(moving),translation=sub(referenceCenter,movingCenter);for(double yaw=-180.0;yaw<180.0;yaw+=options.yawStepDegrees)hypotheses.push_back(pivotTransform(translation,movingCenter,yaw));}
    for(const auto& initial:hypotheses){
        if(options.refinement.cancellation&&options.refinement.cancellation->load()){rejected.reason="cancelled";return rejected;}
        auto candidate=registerConstrained(reference,moving,initial,options.refinement);
        if(!candidate.accepted)continue;
        const double score=candidate.rms+candidate.p95+(1.0-candidate.overlap)*options.refinement.p95Limit;
        if(best.accepted){double dx=candidate.transform[0]-best.transform[0],dy=candidate.transform[1]-best.transform[1],dz=candidate.transform[2]-best.transform[2];double da=std::remainder(candidate.transform[3]-best.transform[3],360.0);if(std::sqrt(dx*dx+dy*dy+dz*dz)<distinctTranslation&&std::abs(da)<1.0){if(score<bestScore){best=candidate;bestScore=score;}continue;}}
        if(score<bestScore){second=best;secondScore=bestScore;best=candidate;bestScore=score;}else if(score<secondScore){second=candidate;secondScore=score;}
    }
    if(!best.accepted){rejected.reason="no global hypothesis";return rejected;}
    if(second.accepted&&secondScore<=bestScore*(1.0+options.ambiguityRatio)){
        best.accepted=false;best.reason="ambiguous global hypotheses";return best;
    }
    best.reason="accepted global";return best;
}

} // namespace tzf
