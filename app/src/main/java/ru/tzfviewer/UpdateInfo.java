package ru.tzfviewer;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateInfo {
    final int versionCode;
    final String versionName, apkUrl, sha256;
    final int minSdk;

    UpdateInfo(int versionCode,String versionName,String apkUrl,String sha256,int minSdk){
        if(versionCode<=0||versionName.isEmpty()||!isAllowedUrl(apkUrl)||!sha256.matches("[0-9a-fA-F]{64}")||minSdk<=0)throw new IllegalArgumentException("invalid update manifest");
        this.versionCode=versionCode;this.versionName=versionName;this.apkUrl=apkUrl;this.sha256=sha256.toLowerCase(Locale.ROOT);this.minSdk=minSdk;
    }

    static UpdateInfo parse(String json){return new UpdateInfo(integer(json,"versionCode"),string(json,"versionName"),string(json,"apkUrl"),string(json,"sha256"),integer(json,"minSdk"));}
    boolean isNewerThan(int installedVersionCode){return versionCode>installedVersionCode;}
    static boolean isAllowedUrl(String value){try{URI uri=URI.create(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1)return false;String host=uri.getHost();return "github.com".equalsIgnoreCase(host)||"objects.githubusercontent.com".equalsIgnoreCase(host)||"release-assets.githubusercontent.com".equalsIgnoreCase(host);}catch(Exception ignored){return false;}}
    private static int integer(String json,String key){Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(\\d+)").matcher(json);if(!m.find())throw new IllegalArgumentException("missing "+key);return Integer.parseInt(m.group(1));}
    private static String string(String json,String key){Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);if(!m.find())throw new IllegalArgumentException("missing "+key);return m.group(1).replace("\\/","/");}
}
