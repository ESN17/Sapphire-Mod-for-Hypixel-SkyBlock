package com.sorakoi.sbtranslate.sync;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Runs in a separate JVM so Windows has released the running mod JAR before replacement. */
public final class InstallModUpdate {
    public static String digest(Path path) throws IOException {
        try {
            var sha = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) sha.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static void replace(Path staged, Path target, String newHash, String oldHash) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !digest(target).equals(oldHash)
                || !Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS) || !digest(staged).equals(newHash))
            throw new IOException("Mod file changed after the update was prepared");
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void main(String[] args) throws Exception {
        var parent = ProcessHandle.of(Long.parseLong(args[0]));
        if (parent.isPresent()) parent.get().onExit().get();
        replace(Path.of(args[1]), Path.of(args[2]), args[3], args[4]);
    }
}
