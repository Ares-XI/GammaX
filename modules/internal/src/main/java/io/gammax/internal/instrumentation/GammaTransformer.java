package io.gammax.internal.instrumentation;

import io.gammax.internal.GammaStart;
import io.gammax.internal.format.*;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.UniqueField;
import io.gammax.internal.format.functional.UniqueMethod;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;

public class GammaTransformer implements ClassFileTransformer {

    public static final GammaTransformer instance = new GammaTransformer();

    private static final List<String> unsupportedPaths = new ArrayList<>();

    private static boolean isUnlock = false;

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

        if ((className.startsWith("org/bukkit/") || className.startsWith("net/minecraft/server/")) && !isUnlock) {
            for (Path path : GammaStart.getPaths()) {
                try (JarFile jarFile = new JarFile(String.valueOf(path))) {
                    GammaStart.getAddUrl().setAccessible(true);
                    GammaStart.getAddUrl().invoke(loader, path.toUri().toURL());
                    GammaStart.getAddUrl().setAccessible(false);
                    System.out.println("Added to classloader: " + jarFile.getName());
                } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                    System.err.println("Failed to add JAR to classloader: " + path);
                    e.printStackTrace(System.err);
                } finally {
                    isUnlock = true;
                }
            }

            System.out.println("System loader getted");
            System.out.println("class of getted: " + className);
            System.out.println("parent: " + loader.getParent().getParent());
        }

        if(GammaCacheRegistry.instance.isTargetPath(className.replace("/", "."))) {
            for (MixinClass mixin : GammaCacheRegistry.instance.getCache()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {
                    List<InjectMethod> injectors = Arrays.asList(mixin.injectMethods);
                    injectors.sort(Comparator.comparingInt(InjectMethod::getPriority));

                    for (UniqueField uniqueField : mixin.uniqueFields) bytecode = uniqueField.modify(bytecode);
                    for (UniqueMethod injectMethod : mixin.uniqueMethods) bytecode = injectMethod.modify(bytecode);
                    for (InjectMethod injectMethod : injectors) bytecode = injectMethod.modify(bytecode);
                    for (InterfaceImplementation implementation: mixin.interfaceImplementations) {
                        bytecode = implementation.modify(bytecode);
                    }

                    return bytecode;
                }
            }
        }

        return null;
    }

    private List<File> findJarFilesRecursively(File rootDir) {
        List<File> jarFiles = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return jarFiles;
        }

        File[] files = rootDir.listFiles();
        if (files == null) return jarFiles;

        for (File file : files) {
            if (file.isDirectory()) {
                // Рекурсивно обходим поддиректории
                jarFiles.addAll(findJarFilesRecursively(file));
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                jarFiles.add(file);
            }
        }

        return jarFiles;
    }

    private GammaTransformer() {}
}