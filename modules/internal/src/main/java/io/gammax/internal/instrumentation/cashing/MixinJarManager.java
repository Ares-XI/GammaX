package io.gammax.internal.instrumentation.cashing;

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MixinJarManager implements AutoCloseable {

    public static final MixinJarManager instance = new MixinJarManager();

    private final Map<String, JarFile> jarFiles = new HashMap<>();

    public Map<String, JarFile> getJarFiles() {
        return jarFiles;
    }

    public JarFile getJarFile(String packageName) {
        String packagePath = packageName.replace('.', '/');

        for (Map.Entry<String, JarFile> entry : jarFiles.entrySet()) {
            JarFile jar = entry.getValue();
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String entryName = jarEntry.getName();

                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    System.out.println("[GammaX] Found package " + packageName + " in JAR: " + entry.getKey());
                    return jar;
                }
            }
        }

        System.out.println("[GammaX] Package " + packageName + " not found in any registered JAR");
        return null;
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

    private MixinJarManager() {}
}