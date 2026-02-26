package io.gammax.internal;

import io.gammax.internal.format.*;
import io.gammax.internal.jar.MixinJarRegister;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.net.URLClassLoader;
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
        unsupportedPaths.add("org/objectweb/asm/");
        unsupportedPaths.add("io/gammax/internal/");
    }

    @Override
    public byte[] transform(
            ClassLoader loader, String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classFile
    ) {
        if(className == null) return null;
        for(String str: unsupportedPaths) if(className.startsWith(str)) return null;

        byte[] bytecode = classFile;

        if(MixinRegistry.isTargetPath(className.replace("/", "."))) {
            for (MixinClass mixin : MixinRegistry.getCache()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {
                    ClassReader reader = new ClassReader(bytecode);
                    ClassNode classNode = new ClassNode();

                    reader.accept(classNode, ClassReader.EXPAND_FRAMES);

                    for (Class<?> iface : mixin.getInterfaces()) {
                        String ifaceName = iface.getName().replace(".", "/");
                        MixinJarRegister registry = MixinRegistry.getMixinJarRegister();
                        registry.append((URLClassLoader) loader, new File(registry.getJarFile(iface.getName()).getName()).getName());
                        if (!classNode.interfaces.contains(ifaceName)) classNode.interfaces.add(ifaceName);
                    }

                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

                    classNode.accept(writer);
                    bytecode = writer.toByteArray();
                    for (UniqueField uniqueField : mixin.uniqueFields) bytecode = uniqueField.addField(bytecode);
                    for (UniqueMethod uniqueMethod : mixin.uniqueMethods) bytecode = uniqueMethod.addMethod(bytecode);
                    for (InjectMethod injectMethod : mixin.injectMethods) bytecode = injectMethod.inject(bytecode);
                    return bytecode;
                }
            }
        }

        return null;
    }
}