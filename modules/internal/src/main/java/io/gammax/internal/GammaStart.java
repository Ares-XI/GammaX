package io.gammax.internal;

import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.instrumentation.CacheRegistry;
import io.gammax.internal.instrumentation.GammaTransformer;
import io.gammax.internal.instrumentation.GammaJarCreator;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

public class GammaStart {

    private static Method addUrl;

    public static final List<Path> paths;

    static {
        try {
            paths = GammaJarCreator.createAllMixinJars();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Method getAddUrl() {
        return addUrl;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=====================================");
        System.out.println("|| GammaX started! Version: 1.0 beta");
        System.out.println("=====================================");

        CacheRegistry.instance.loadCache();
        inst.addTransformer(GammaTransformer.instance);

        try {
            addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(System.err);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CacheRegistry.instance.clearCache();
//            JarManager.instance.close();
            GammaClassLoader.instance.close();
        }));
    }
}
