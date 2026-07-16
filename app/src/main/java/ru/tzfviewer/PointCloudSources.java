package ru.tzfviewer;

import java.io.File;
import java.io.IOException;

final class PointCloudSources {
    private PointCloudSources(){}
    static PointCloudSource open(ProjectModel.SourceType type,File local,File cache)throws IOException{
        return type==ProjectModel.SourceType.ASC?new AscPointCloudSource(local,cache):new TzfPointCloudSource(local);
    }
    static ProjectModel.SourceType infer(String name){return name!=null&&name.toLowerCase(java.util.Locale.ROOT).endsWith(".asc")?ProjectModel.SourceType.ASC:ProjectModel.SourceType.TZF;}
}
