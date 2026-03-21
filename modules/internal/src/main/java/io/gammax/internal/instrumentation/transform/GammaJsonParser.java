package io.gammax.internal.instrumentation.transform;

import com.google.gson.Gson;
import io.gammax.internal.instrumentation.cashing.JarManager;
import io.gammax.internal.util.data.GammaConfigFormat;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GammaJsonParser {

    public static final GammaJsonParser instance = new GammaJsonParser();

    private final Gson GSON = new Gson();

    public List<GammaConfigFormat> loadAllMixinConfigs() {
        List<GammaConfigFormat> result = new ArrayList<>();

        String[] extraDirs = {"libraries", "cache"};
        for (String dirName : extraDirs) {
            File dir = new File(dirName);
            if (dir.exists() && dir.isDirectory()) {
                for(File jarFile: findJarsWithMixins(dir)) {
                    try {
                        JarManager.instance.registerJar(jarFile);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }

        String command = System.getProperty("sun.java.command");
        if (command == null) return null;
        String[] parts = command.split(" ");

        for (String part : parts) {
            if (part.endsWith(".jar") && !part.startsWith("-javaagent:")) {
                File jarFile = new File(part);
                if (jarFile.exists()) {
                    try {
                        JarManager.instance.registerJar(jarFile);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }

        File pluginsDir = new File("plugins");
        if (pluginsDir.exists()) {
            File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        try (JarFile jar = new JarFile(jarFile)) {
                            if (jar.getJarEntry("gamma.json") != null) {
                                JarManager.instance.registerJar(jarFile);
                                parseConfigFromJar(jar, result);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
        return result;
    }

    private void parseConfigFromJar(JarFile jar, List<GammaConfigFormat> result) {
        try {
            JarEntry entry = jar.getJarEntry("gamma.json");
            try (InputStream is = jar.getInputStream(entry);
                 Reader reader = new InputStreamReader(is)) {

                GammaConfigFormat config = GSON.fromJson(reader, GammaConfigFormat.class);
                if (config != null) {
                    result.add(config);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private List<File> findJarsWithMixins(File dir) {
        List<File> jars = new ArrayList<>();
        scan(dir, jars);
        return jars;
    }

    private void scan(File dir, List<File> jars) {
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) scan(file, jars);
            if (file.isFile() && file.getName().endsWith(".jar")) jars.add(file);
        }
    }

    private GammaJsonParser() {}
}