package ru.tzfviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;

final class UpdateSecurity {
    private UpdateSecurity(){}
    static String sha256(File file)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(FileInputStream input=new FileInputStream(file)){byte[] buffer=new byte[1024*1024];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}StringBuilder out=new StringBuilder(64);for(byte value:digest.digest())out.append(String.format("%02x",value&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
