package com.sorakoi.sbtranslate.translation;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Upgrade only untouched bundled files; user edits always win. */
public final class BundledDictionaryInstaller {
    @FunctionalInterface public interface Resource { byte[] read() throws IOException; }
    public static boolean install(Path root,String file,byte[] bundled,String previousHash) throws IOException {
        return install(root,file,()->bundled,previousHash);
    }
    /** Read bundled ZIP entries only when a missing or known-old local file needs them. */
    public static boolean install(Path root,String file,Resource resource,String previousHash) throws IOException {
        return install(root,file,resource,previousHash,null);
    }
    public static boolean install(Path root,String file,Resource resource,String previousHash,String bundledHash) throws IOException {
        Path path=root.resolve(file).normalize();
        if(!path.startsWith(root.normalize()))throw new IOException("Invalid dictionary path");
        byte[] existing=null;String existingHash=null;
        if(Files.exists(path)) {
            if(previousHash==null)return false;
            existing=Files.readAllBytes(path);existingHash=hash(existing);
            if(existingHash.equals(bundledHash))return false;
            if(!java.util.List.of(previousHash.split(",")).contains(existingHash))return false;
        }
        byte[] bundled=resource.read();
        if(bundledHash!=null && !hash(bundled).equals(bundledHash))throw new IOException("Bundled dictionary hash mismatch: "+file);
        if(existing!=null) {
            if(java.util.Arrays.equals(existing,bundled))return false;
            Path backup=root.resolve("dictionary-backups").resolve(existingHash).resolve(file);
            Files.createDirectories(backup.getParent());if(!Files.exists(backup))Files.copy(path,backup);
        }
        Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".upgrade.tmp");
        Files.write(tmp,bundled);
        try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        return true;
    }
    private static String hash(byte[] data) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
