package io.gammax.internal.instrumentation.transform;

import io.gammax.internal.format.*;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.UniqueField;
import io.gammax.internal.format.functional.UniqueMethod;
import io.gammax.internal.instrumentation.MixinRegistry;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MixinTransformer implements ClassFileTransformer {

    public static final MixinTransformer instance = new MixinTransformer();

    private static final List<String> unsupportedPaths = new ArrayList<>();

    static {
        unsupportedPaths.add("java/");
        unsupportedPaths.add("jdk/");
        unsupportedPaths.add("sun/");
        unsupportedPaths.add("com/google/gson/");
        unsupportedPaths.add("org/intellij/");
        unsupportedPaths.add("org/jetbrains/");
        unsupportedPaths.add("org/objectweb/asm/");
        unsupportedPaths.add("io/gammax/");
    }

    @Override
    public byte[] transform(
            ClassLoader loader, String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] bytecode
    ) {
        if(className == null) return null;
        for(String str: unsupportedPaths) if(className.startsWith(str)) return null;

        if(MixinRegistry.instance.isTargetPath(className.replace("/", "."))) {
            for (MixinClass mixin : MixinRegistry.instance.getCache()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {

                    List<InjectMethod> injectors = Arrays.asList(mixin.injectMethods);
                    injectors.sort(Comparator.comparingInt(InjectMethod::getPriority));

                    for (UniqueField uniqueField : mixin.uniqueFields) bytecode = uniqueField.modify(bytecode);
                    for (UniqueMethod injectMethod : mixin.uniqueMethods) bytecode = injectMethod.modify(bytecode);
                    for (InjectMethod injectMethod : injectors) bytecode = injectMethod.modify(bytecode);
                    for (InterfaceImplementation implementation: mixin.interfaceImplementations) bytecode = implementation.modify(bytecode);

                    return bytecode;
                }
            }
        }

        return null;
    }

    private MixinTransformer() {}
}