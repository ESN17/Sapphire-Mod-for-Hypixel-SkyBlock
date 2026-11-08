package com.sorakoi.sbtranslate.sync;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** GET only; no account information, authentication, uploads, or game-thread I/O. */
public interface DictionaryTransport {
    String index(URI uri) throws IOException;
    void download(URI uri,Path destination) throws IOException;

    final class Http implements DictionaryTransport {
        private final int connectMillis,readMillis;
        public Http(){this(8000,10000);}
        public Http(int connectMillis,int readMillis){this.connectMillis=connectMillis;this.readMillis=readMillis;}
        @Override public String index(URI uri) throws IOException {
            var bytes=new ByteArrayOutputStream();fetch(uri,bytes,1024*1024,20000);
            return bytes.toString(StandardCharsets.UTF_8);
        }
        @Override public void download(URI uri,Path destination) throws IOException {
            try(var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)) {
                fetch(uri,out,DictionaryArchive.MAX_ZIP_BYTES,120000);
            }
        }
        private void fetch(URI original,OutputStream output,long limit,long totalMillis) throws IOException {
            URI uri=original;long deadline=System.nanoTime()+totalMillis*1_000_000;
            for(int redirect=0;redirect<4;redirect++) {
                var connection=(HttpURLConnection)uri.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(connectMillis);connection.setReadTimeout(readMillis);
                connection.setRequestProperty("User-Agent","Sapphire-Dictionary/1");connection.setRequestProperty("Accept-Encoding","identity");
                try {
                    int code=connection.getResponseCode();
                    if(code==301 || code==302 || code==303 || code==307 || code==308) {
                        String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirect missing location");
                        URI next=uri.resolve(location);
                        boolean sameScheme=uri.getScheme().equals(next.getScheme()),upgrade=uri.getScheme().equals("http") && next.getScheme().equals("https");
                        if(!uri.getHost().equalsIgnoreCase(next.getHost()==null?"":next.getHost()) || !(sameScheme || upgrade)
                                || sameScheme && uri.getPort()!=next.getPort() || next.getUserInfo()!=null || next.getQuery()!=null || next.getFragment()!=null)
                            throw new IOException("Unsafe dictionary redirect");
                        String base=DictionaryIndex.directory(original).getPath();
                        if(!next.getPath().equals(original.getPath()) && !next.getPath().equals(base))throw new IOException("Unexpected dictionary redirect path");
                        uri=next;continue;
                    }
                    if(code!=200)throw new IOException("Dictionary HTTP status "+code);
                    long declared=connection.getContentLengthLong();if(declared>limit)throw new IOException("Download too large");
                    long size=0;byte[] buffer=new byte[65536];
                    try(var in=connection.getInputStream()) {
                        for(int n;(n=in.read(buffer))!=-1;) {
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Dictionary update interrupted");
                            if(System.nanoTime()>deadline)throw new SocketTimeoutException("Dictionary transfer deadline exceeded");
                            size+=n;if(size>limit)throw new IOException("Download too large");output.write(buffer,0,n);
                        }
                    }
                    if(declared>=0 && size!=declared)throw new EOFException("Truncated dictionary response");
                    return;
                }finally{connection.disconnect();}
            }
            throw new IOException("Too many dictionary redirects");
        }
    }
}
