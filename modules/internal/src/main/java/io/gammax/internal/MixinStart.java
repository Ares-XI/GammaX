package io.gammax.internal;

import io.gammax.internal.instrumentation.cashing.MixinClassLoader;
import io.gammax.internal.instrumentation.cashing.MixinJarManager;
import io.gammax.internal.instrumentation.MixinRegistry;
import io.gammax.internal.instrumentation.transform.MixinTransformer;

import java.lang.instrument.Instrumentation;

public class MixinStart {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=====================================");
        System.out.println("|| GammaX started! Version: 1.0 beta");
        System.out.println("=====================================");

        MixinRegistry.instance.loadCache();
        inst.addTransformer(MixinTransformer.instance);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MixinRegistry.instance.clearCache();
            MixinJarManager.instance.close();
            MixinClassLoader.instance.close();
        }));
    }
}
