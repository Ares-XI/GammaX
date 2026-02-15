package io.gammax.internal;

import java.lang.instrument.Instrumentation;

public class MixinStart {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=====================================");
        System.out.println("|| GammaX started! Version: 1.0 alpha");
        System.out.println("=====================================");

        MixinRegistry.loadMixins();
        inst.addTransformer(new MixinTransformer());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MixinRegistry.clearCache();
            MixinRegistry.getJars().close();
            MixinRegistry.getMixinClassLoader().clearCache();
        }));
    }
}
