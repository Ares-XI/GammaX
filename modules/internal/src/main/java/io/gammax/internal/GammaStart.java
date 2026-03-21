package io.gammax.internal;

import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.instrumentation.GammaCacheRegistry;
import io.gammax.internal.instrumentation.GammaTransformer;
import io.gammax.internal.instrumentation.GammaJarCreator;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class GammaStart {

    private static Method addUrl;

    private static List<Path> paths;

    public static Method getAddUrl() {
        return addUrl;
    }

    public static List<Path> getPaths() {
        return paths;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=====================================");
        System.out.println("|| GammaX started! Version: 1.0 beta");
        System.out.println("=====================================");

        registerLibraries();
        registerClose();

        try {
            paths = GammaJarCreator.createAllMixinJars();
            addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
        } catch (IOException | NoSuchMethodException e) {
            e.printStackTrace(System.err);
        }

        GammaCacheRegistry.instance.loadCache();
        inst.addTransformer(GammaTransformer.instance);
    }

    private static void registerLibraries() {
        String[] extraDirs = {"libraries", "cache", "versions"};

        for(String extraDir: extraDirs) {
            try(Stream<Path> stream = Files.walk(Paths.get(extraDir)).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))) {
                stream.forEach(path -> {
                    try {
                        GammaClassLoader.instance.registerJar(path.toFile());
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static void registerClose() { //TODO TODO
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GammaCacheRegistry.instance.clearCache();
                GammaClassLoader.instance.close();
                File cacheDir = new File("mixin/.cache");
                if (cacheDir.exists()) deleteRecursively(cacheDir);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }));
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) deleteRecursively(f);
        }
        file.delete();
    }
}
