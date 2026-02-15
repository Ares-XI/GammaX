package io.gammax.internal.json;

import com.google.gson.Gson;
import io.gammax.internal.MixinRegistry;
import io.gammax.internal.util.JarScanner;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MixinJsonParser {
    private final Gson GSON = new Gson();

    public List<MixinConfigFormat> loadAllMixinConfigs() {
        List<MixinConfigFormat> result = new ArrayList<>();

        String[] extraDirs = {"libraries", "cache"};
        for (String dirName : extraDirs) {
            File dir = new File(dirName);
            if (dir.exists() && dir.isDirectory()) {
                for(File jarFile: JarScanner.findJarsWithMixins(dir)) {
                    try {
                        MixinRegistry.getJars().registerJar(jarFile);
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
                        MixinRegistry.getJars().registerJar(jarFile);
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
                            if (jar.getJarEntry("mixins.json") != null) {
                                MixinRegistry.getJars().registerJar(jarFile);
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

    private void parseConfigFromJar(JarFile jar, List<MixinConfigFormat> result) {
        try {
            JarEntry entry = jar.getJarEntry("mixins.json");
            try (InputStream is = jar.getInputStream(entry);
                 Reader reader = new InputStreamReader(is)) {

                MixinConfigFormat config = GSON.fromJson(reader, MixinConfigFormat.class);
                if (config != null) {
                    result.add(config);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void close() {
        MixinRegistry.getJars().close();
    }
}