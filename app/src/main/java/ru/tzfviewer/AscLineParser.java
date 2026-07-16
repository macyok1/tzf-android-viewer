package ru.tzfviewer;

final class AscLineParser {
    private AscLineParser(){}

    static float[] xyz(String line){
        float[] output=new float[3];
        return xyz(line,output)?output:null;
    }

    static boolean xyz(String line,float[] output){
        if(line==null||output==null||output.length<3)return false;
        String clean=line.trim();
        if(clean.startsWith("\uFEFF"))clean=clean.substring(1).trim();
        if(clean.isEmpty()||clean.startsWith("#")||clean.startsWith("//")||clean.startsWith(";"))return false;
        int cursor=0;
        for(int component=0;component<3;component++){
            while(cursor<clean.length()&&separator(clean.charAt(cursor)))cursor++;
            int start=cursor;
            while(cursor<clean.length()&&!separator(clean.charAt(cursor)))cursor++;
            if(start==cursor)return false;
            try{output[component]=Float.parseFloat(clean.substring(start,cursor));}
            catch(NumberFormatException ignored){return false;}
            if(!Float.isFinite(output[component]))return false;
        }
        return true;
    }

    private static boolean separator(char value){return Character.isWhitespace(value)||value==','||value==';';}
}