package com.sorakoi.sbtranslate.sync;

import java.util.Locale;

/** One notice per update phase and connection; completed downloads never replay stale progress. */
public final class DictionaryUpdateNotice {
    private Object connection;private boolean sent;private DictionaryUpdater.Result result;
    private String progress;
    public void checking(){result=null;progress=null;sent=false;}
    public void downloading(String version){result=null;progress="已检测到新版译名库（"+version+"），正在下载中…";sent=false;}
    public void result(DictionaryUpdater.Result result){this.result=result;progress=null;sent=false;}
    public void disconnect(){connection=null;sent=false;}
    public String poll(Object current,String address) {
        if(current==null){disconnect();return null;}
        if(current!=connection){connection=current;sent=false;}
        if(sent || result==null && progress==null || !hypixel(address))return null;
        sent=true;return result==null?progress:result.message();
    }
    public static boolean hypixel(String address) {
        if(address==null)return false;String host=address.toLowerCase(Locale.ROOT).strip().split(":",2)[0];
        if(host.endsWith("."))host=host.substring(0,host.length()-1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
