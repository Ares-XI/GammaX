package io.gammax.internal;

import io.gammax.internal.instrumentation.loaders.JarFileClassLoader;
import io.gammax.internal.instrumentation.cashing.MixinJarManager;
import io.gammax.internal.instrumentation.cashing.MixinRegistry;
import io.gammax.internal.instrumentation.transform.MixinTransformer;
import io.gammax.internal.util.MixinJarCreator;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

public class MixinStart {

    private static Method addUrl;

    public static final List<Path> paths;

    static {
        try {
            paths = MixinJarCreator.createAllMixinJars();
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

        MixinRegistry.instance.loadCache();
        inst.addTransformer(MixinTransformer.instance);

        try {
            addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace(System.err);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MixinRegistry.instance.clearCache();
            MixinJarManager.instance.close();
            JarFileClassLoader.instance.close();
        }));
    }
}
