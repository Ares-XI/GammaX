package io.gammax.internal;

import io.gammax.internal.format.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class MixinTransformer implements ClassFileTransformer {

    private static final List<String> unsupportedPaths = new ArrayList<>();

    static {
        unsupportedPaths.add("java/");
        unsupportedPaths.add("jdk/");
        unsupportedPaths.add("sun/");
        unsupportedPaths.add("com/google/");
        unsupportedPaths.add("org/intellij/");
        unsupportedPaths.add("org/jetbrains/");
        unsupportedPaths.add("javassist/");
        unsupportedPaths.add("io/gammax/internal/");
    }

    @Override
    public byte[] transform(
            ClassLoader loader, String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) {
        if(className == null) return null;
        for(String str: unsupportedPaths) if(className.startsWith(str)) return null;

        byte[] bytecode = classfileBuffer;

        if(MixinRegistry.isTargetPath(className.replace("/", "."))) {
            for (MixinClass mixin : MixinRegistry.getMixins()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {
                    for (UniqueField uniqueField : mixin.uniqueFields) bytecode = uniqueField.addField(bytecode);
                    for (UniqueMethod uniqueMethod : mixin.uniqueMethods) bytecode = uniqueMethod.addMethod(bytecode);
                    for (ShadowField shadowField : mixin.shadowFields) bytecode = shadowField.provideField(bytecode);
                    for (ShadowMethod shadowMethod : mixin.shadowMethods) bytecode = shadowMethod.provideMethod(bytecode);
                    return bytecode;
                }
            }
        }

        return null;
    }
}