package ru.tzfviewer;

final class AscExportOptions {
    enum Mode { FULL, RANDOM_PERCENT, RANDOM_TARGET, SPATIAL_SPACING, SPATIAL_TARGET }
    final Mode mode;final double value;
    private AscExportOptions(Mode mode,double value){this.mode=mode;this.value=value;}
    static AscExportOptions full(){return new AscExportOptions(Mode.FULL,0);}
    static AscExportOptions randomPercent(double percent){return new AscExportOptions(Mode.RANDOM_PERCENT,percent);}
    static AscExportOptions randomTarget(long points){return new AscExportOptions(Mode.RANDOM_TARGET,points);}
    static AscExportOptions spatialSpacing(double millimetres){return new AscExportOptions(Mode.SPATIAL_SPACING,millimetres);}
    static AscExportOptions spatialTarget(long points){return new AscExportOptions(Mode.SPATIAL_TARGET,points);}
}
