package ru.tzfviewer;

final class AscLineParser {
    private AscLineParser(){}
    static float[] xyz(String line){
        if(line==null)return null;
        String clean=line.trim();if(clean.startsWith("\uFEFF"))clean=clean.substring(1).trim();
        if(clean.isEmpty()||clean.startsWith("#")||clean.startsWith("//")||clean.startsWith(";"))return null;
        String[] values=clean.split("[\\s,;]+",-1);
        if(values.length<3)return null;
        try{
            float x=Float.parseFloat(values[0]),y=Float.parseFloat(values[1]),z=Float.parseFloat(values[2]);
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return null;
            return new float[]{x,y,z};
        }catch(NumberFormatException ignored){return null;}
    }
}
