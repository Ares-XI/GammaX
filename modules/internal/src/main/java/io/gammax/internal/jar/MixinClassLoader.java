package io.gammax.internal.jar;

import io.gammax.internal.MixinRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MixinClassLoader extends ClassLoader {
    private final Map<String, byte[]> byteCache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (classCache.containsKey(name)) {
            return classCache.get(name);
        }

        byte[] bytes = getClassBytes(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }

        byteCache.put(name, bytes);
        Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
        classCache.put(name, clazz);
        return clazz;
    }

    public byte[] getClassBytes(String className) {
        if (byteCache.containsKey(className)) {
            return byteCache.get(className);
        }

        String classPath = className.replace('.', '/') + ".class";
        Map<String, JarFile> jarFiles = MixinRegistry.getJars().getJarFiles();

        for (JarFile jar : jarFiles.values()) {
            try {
                JarEntry entry = jar.getJarEntry(classPath);
                if (entry != null) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] data = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, bytesRead);
                        }

                        return buffer.toByteArray();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }

        return null;
    }


    public void clearCache() {
        byteCache.clear();
        classCache.clear();
    }
}