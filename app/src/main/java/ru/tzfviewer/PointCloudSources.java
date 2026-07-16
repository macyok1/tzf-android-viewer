package ru.tzfviewer;

import java.io.File;
import java.io.IOException;

final class PointCloudSources {
    private PointCloudSources(){}
    static PointCloudSource open(ProjectModel.SourceType type,File local)throws IOException{
        if(type==ProjectModel.SourceType.ASC){
            if(local==null)throw new IOException("ASC cache is unavailable");
            return new AscPointCloudSource(local,-1);
        }
        if(local==null)throw new IOException("TZF source is unavailable");
        return new TzfPointCloudSource(local);
    }
    static ProjectModel.SourceType infer(String name){return name!=null&&name.toLowerCase(java.util.Locale.ROOT).endsWith(".asc")?ProjectModel.SourceType.ASC:ProjectModel.SourceType.TZF;}
}