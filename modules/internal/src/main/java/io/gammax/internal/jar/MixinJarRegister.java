package io.gammax.internal.jar;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MixinJarRegister {

    private final Map<String, JarFile> jarFiles = new HashMap<>();

    public Map<String, JarFile> getJarFiles() {
        return jarFiles;
    }

    public void append(URLClassLoader loader, String jarPath) {
        try {
            File jarFile = new File(jarPath);

            if (!jarFile.exists()) {
                System.err.println("[GammaX] JAR file not found: " + jarFile.getAbsolutePath());
                return;
            }

            String absolutePath = jarFile.getAbsolutePath();
            if (jarFiles.containsKey(absolutePath)) {
                System.out.println("[GammaX] JAR already registered: " + jarFile.getName());
            } else {
                JarFile jar = new JarFile(jarFile);
                jarFiles.put(absolutePath, jar);
                System.out.println("[GammaX] registered JAR: " + jarFile.getName());
            }

            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(loader, jarFile.toURI().toURL());

            System.out.println("[GammaX] added " + jarFile.getName() + " to classpath");

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | IOException e) {
            System.err.println("[GammaX] Error adding JAR to classloader: " + jarPath);
            e.printStackTrace(System.err);
        }
    }

    public JarFile getJarFile(String packageName) {
        String packagePath = packageName.replace('.', '/');

        for (Map.Entry<String, JarFile> entry : jarFiles.entrySet()) {
            JarFile jar = entry.getValue();
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String entryName = jarEntry.getName();

                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    System.out.println("[GammaX] Found package " + packageName + " in JAR: " + entry.getKey());
                    return jar;
                }
            }
        }

        System.out.println("[GammaX] Package " + packageName + " not found in any registered JAR");
        return null;
    }

    public void registerJar(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        jarFiles.put(jarFile.getAbsolutePath(), jar);
    }

    public void close() {
        for (JarFile jar : jarFiles.values()) {
            try {
                jar.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        jarFiles.clear();
    }
}