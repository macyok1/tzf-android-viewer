#include "tzf_registration.h"

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

bool solve4(double a[4][4],double b[4],double x[4]){
    for(int i=0;i<4;++i){int pivot=i;for(int r=i+1;r<4;++r)if(std::abs(a[r][i])>std::abs(a[pivot][i]))pivot=r;if(std::abs(a[pivot][i])<1e-10)return false;for(int c=i;c<4;++c)std::swap(a[i][c],a[pivot][c]);std::swap(b[i],b[pivot]);double d=a[i][i];for(int c=i;c<4;++c)a[i][c]/=d;b[i]/=d;for(int r=0;r<4;++r)if(r!=i){double f=a[r][i];for(int c=i;c<4;++c)a[r][c]-=f*a[i][c];b[r]-=f*b[i];}}
    for(int i=0;i<4;++i)x[i]=b[i];return true;
}

struct Metrics{double rms{},p95{},overlap{};std::vector<double> distances;};
Metrics metrics(const std::vector<Vec3>& reference,const std::vector<Vec3>& moving,Vec3 pivot,const std::array<double,4>& t,double maxDistance){Index index(reference,maxDistance);Metrics m;double sum=0;for(auto p:moving){auto q=transformPoint(p,pivot,t);std::size_t nearest{};double d2{};if(index.nearest(q,maxDistance,nearest,d2)){m.distances.push_back(std::sqrt(d2));sum+=d2;}}m.overlap=moving.empty()?0.0:static_cast<double>(m.distances.size())/moving.size();if(!m.distances.empty()){m.rms=std::sqrt(sum/m.distances.size());std::sort(m.distances.begin(),m.distances.end());m.p95=m.distances[static_cast<std::size_t>((m.distances.size()-1)*.95)];}return m;}

double spanOf(const std::vector<Point>& a,const std::vector<Point>& b){Vec3 lo{1e300,1e300,1e300},hi{-1e300,-1e300,-1e300};for(const auto& list:{&a,&b})for(auto p:*list){lo.x=std::min(lo.x,(double)p.x);lo.y=std::min(lo.y,(double)p.y);lo.z=std::min(lo.z,(double)p.z);hi.x=std::max(hi.x,(double)p.x);hi.y=std::max(hi.y,(double)p.y);hi.z=std::max(hi.z,(double)p.z);}return std::max({hi.x-lo.x,hi.y-lo.y,hi.z-lo.z});}

} // namespace

std::vector<Point> xyzToPoints(const std::vector<float>& xyz){std::vector<Point> out;out.reserve(xyz.size()/3);for(std::size_t i=0;i+2<xyz.size();i+=3)out.push_back({xyz[i],xyz[i+1],xyz[i+2]});return out;}

RegistrationResult registerConstrained(const std::vector<Point>& reference,const std::vector<Point>& moving,const std::array<double,4>& initial,const RegistrationOptions& options){RegistrationResult result;result.transform=initial;if(reference.size()<100||moving.size()<100){result.reason="not enough points";return result;}const double span=spanOf(reference,moving);if(!std::isfinite(span)||span<=0){result.reason="invalid cloud bounds";return result;}const Vec3 pivot=center(moving);const std::array<double,3> voxels{span/24.0,span/48.0,span/96.0};
    for(double voxel:voxels){auto ref=downsample(reference,voxel),mov=downsample(moving,voxel);if(ref.size()<30||mov.size()<30){result.reason="not enough spatial coverage";return result;}auto samples=normals(ref,voxel*2.5);Index index(ref,voxel*2.5);const double maxDistance=voxel*3.0;
        for(int iteration=0;iteration<options.iterationsPerLevel;++iteration){double normal[4][4]{},rhs[4]{};std::size_t used=0;double absSum=0;for(auto source:mov){Vec3 p=transformPoint(source,pivot,result.transform);std::size_t nearest{};double d2{};if(!index.nearest(p,maxDistance,nearest,d2)||!samples[nearest].hasNormal)continue;const Vec3 n=samples[nearest].normal;double residual=dot(sub(p,samples[nearest].point),n);double huber=voxel;double weight=std::abs(residual)<=huber?1.0:huber/std::abs(residual);Vec3 relative=sub(p,{pivot.x+result.transform[0],pivot.y+result.transform[1],pivot.z+result.transform[2]});double j[4]{n.x,n.y,n.z,(-relative.y*n.x+relative.x*n.y)};for(int r=0;r<4;++r){rhs[r]+=-weight*j[r]*residual;for(int c=0;c<4;++c)normal[r][c]+=weight*j[r]*j[c];}absSum+=std::abs(residual);++used;}if(used<30||static_cast<double>(used)/mov.size()<options.minimumOverlap){result.reason="insufficient overlap";return result;}double delta[4]{};if(!solve4(normal,rhs,delta)){result.reason="degenerate geometry";return result;}result.transform[0]+=delta[0];result.transform[1]+=delta[1];result.transform[2]+=delta[2];result.transform[3]+=delta[3]*180.0/3.14159265358979323846;++result.iterations;if(std::sqrt(delta[0]*delta[0]+delta[1]*delta[1]+delta[2]*delta[2])<voxel*1e-4&&std::abs(delta[3])<1e-5)break;if(!std::isfinite(absSum)){result.reason="registration diverged";return result;}}
    }
    auto controlRef=downsample(reference,span/150.0),controlMov=downsample(moving,span/150.0);auto m=metrics(controlRef,controlMov,pivot,result.transform,std::max(options.p95Limit*4.0,span/100.0));result.rms=m.rms;result.p95=m.p95;result.overlap=m.overlap;if(m.overlap<options.minimumOverlap)result.reason="insufficient validation overlap";else if(m.rms>options.rmsLimit)result.reason="RMS exceeds threshold";else if(m.p95>options.p95Limit)result.reason="P95 exceeds threshold";else{result.accepted=true;result.reason="accepted";}return result;}

} // namespace tzf
