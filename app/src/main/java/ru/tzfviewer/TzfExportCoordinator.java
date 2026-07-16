package ru.tzfviewer;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.util.Locale;

final class TzfExportCoordinator {
    private static final int BUFFER=1024*1024;
    private final ContentResolver resolver;
    private final File stagingDirectory;

    TzfExportCoordinator(ContentResolver resolver,File stagingDirectory){this.resolver=resolver;this.stagingDirectory=stagingDirectory;}

    DocumentFile exportCopy(Uri sourceUri,String sourceName,float[] worldPose,DocumentFile destination)throws IOException{
        File staged=stage(sourceUri,sourceName,worldPose);
        String name=uniqueName(destination,baseName(sourceName),".tzf");
        DocumentFile partial=destination.createFile("application/octet-stream",name+".partial");
        if(partial==null)throw new IOException("cannot create TZF output");
        try{copyFile(staged,partial.getUri());if(!partial.renameTo(name))throw new IOException("cannot publish TZF output");DocumentFile result=destination.findFile(name);return result==null?partial:result;}
        catch(IOException error){partial.delete();throw error;}
        finally{staged.delete();}
    }

    void overwrite(DocumentFile source,float[] worldPose)throws IOException{
        if(source==null||!source.isFile())throw new IOException("source TZF is not available");
        DocumentFile parent=source.getParentFile();
        if(parent==null)throw new IOException("source folder access is required");
        String sourceName=source.getName()==null?"scan.tzf":source.getName();
        File staged=stage(source.getUri(),sourceName,worldPose);
        DocumentFile backup=null;
        try{
            String backupName=uniqueBackupName(parent,sourceName);
            backup=parent.createFile("application/octet-stream",backupName);
            if(backup==null)throw new IOException("cannot create TZF backup");
            copyUri(source.getUri(),backup.getUri());
            copyFile(staged,source.getUri());
        }catch(IOException error){
            if(backup!=null&&backup.isFile())try{copyUri(backup.getUri(),source.getUri());}catch(IOException ignored){}
            throw error;
        }finally{staged.delete();}
    }

    private File stage(Uri sourceUri,String sourceName,float[] worldPose)throws IOException{
        if(worldPose==null||worldPose.length!=4)throw new IOException("invalid stitched TZF pose");
        if(!stagingDirectory.exists()&&!stagingDirectory.mkdirs())throw new IOException("cannot create TZF staging directory");
        String safe=(baseName(sourceName)+"___");File input=File.createTempFile(safe+"-", ".source",stagingDirectory);File output=File.createTempFile(safe+"-", ".tzf",stagingDirectory);
        try{copyUriToFile(sourceUri,input);TzfNative.writeStitchedTzf(input.getAbsolutePath(),output.getAbsolutePath(),worldPose);return output;}
        catch(IOException|RuntimeException error){output.delete();throw error;}
        finally{input.delete();}
    }

    private void copyUriToFile(Uri source,File destination)throws IOException{
        InputStream opened="file".equals(source.getScheme())?new FileInputStream(new File(source.getPath())):resolver.openInputStream(source);if(opened==null)throw new IOException("cannot read TZF source");
        try(InputStream input=opened;OutputStream output=new BufferedOutputStream(new FileOutputStream(destination),BUFFER)){copy(input,output);}
    }
    private void copyUri(Uri source,Uri destination)throws IOException{
        InputStream opened=resolver.openInputStream(source);OutputStream created=resolver.openOutputStream(destination,"wt");if(opened==null||created==null){if(opened!=null)opened.close();if(created!=null)created.close();throw new IOException("document access is unavailable");}
        try(InputStream input=opened;OutputStream output=new BufferedOutputStream(created,BUFFER)){copy(input,output);}
    }
    private void copyFile(File source,Uri destination)throws IOException{
        OutputStream created=resolver.openOutputStream(destination,"wt");if(created==null)throw new IOException("document is not writable");
        try(InputStream input=new BufferedInputStream(new FileInputStream(source),BUFFER);OutputStream output=new BufferedOutputStream(created,BUFFER)){copy(input,output);}
    }
    private static void copy(InputStream input,OutputStream output)throws IOException{byte[] buffer=new byte[BUFFER];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);output.flush();}

    private static String uniqueName(DocumentFile parent,String base,String extension){String candidate=base+extension;for(int suffix=2;parent.findFile(candidate)!=null;suffix++)candidate=base+"-"+suffix+extension;return candidate;}
    private static String uniqueBackupName(DocumentFile parent,String sourceName){String candidate=sourceName+".bak";for(int suffix=2;parent.findFile(candidate)!=null;suffix++)candidate=sourceName+".bak-"+suffix;return candidate;}
    static String baseName(String sourceName){String safe=(sourceName==null?"scan.tzf":sourceName).replaceAll("[\\\\/:*?\"<>|]","_").trim();if(safe.toLowerCase(Locale.ROOT).endsWith(".tzf"))safe=safe.substring(0,safe.length()-4);return safe.isEmpty()?"scan":safe;}
}