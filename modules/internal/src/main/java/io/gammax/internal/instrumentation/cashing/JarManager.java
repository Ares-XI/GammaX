package io.gammax.internal.instrumentation.cashing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

public class JarManager implements AutoCloseable {

    public static final JarManager instance = new JarManager();

    private final Map<String, JarFile> jarFiles = new HashMap<>();

    public Map<String, JarFile> getJarFiles() {
        return jarFiles;
    }

    public void registerJar(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        jarFiles.put(jarFile.getAbsolutePath(), jar);
    }

    @Override
    public void close() {
        for (JarFile jar : jarFiles.values()) {
            try {
                jar.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        jarFiles.clear();
    }

    private JarManager() {}
}