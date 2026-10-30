package com.sorakoi.sbtranslate.translation;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Upgrade only untouched bundled files; user edits always win. */
public final class BundledDictionaryInstaller {
    public static boolean install(Path root,String file,byte[] bundled,String previousHash) throws IOException {
        Path path=root.resolve(file).normalize();
        if(!path.startsWith(root.normalize()))throw new IOException("Invalid dictionary path");
        if(Files.exists(path)) {
            byte[] existing=Files.readAllBytes(path);
            if(java.util.Arrays.equals(existing,bundled) || previousHash==null || !java.util.List.of(previousHash.split(",")).contains(hash(existing)))return false;
            Path backup=root.resolve("dictionary-backups").resolve(hash(existing)).resolve(file);
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
