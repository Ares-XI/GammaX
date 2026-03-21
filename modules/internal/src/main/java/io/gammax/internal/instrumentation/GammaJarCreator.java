package io.gammax.internal.instrumentation;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GammaJarCreator { //TODO TODO

    private static final Path CACHE_DIR = Paths.get("mixin/.cache");
    private static final Path PLUGINS_DIR = Paths.get("plugins");

    public static List<Path> createAllMixinJars() throws IOException {
        System.out.println("[MixinJarCreator] Starting mixin JAR creation...");

        Files.createDirectories(CACHE_DIR);

        List<Path> createdJars = new ArrayList<>();

        if (!Files.exists(PLUGINS_DIR)) {
            System.out.println("[MixinJarCreator] Plugins directory not found: " + PLUGINS_DIR);
            return createdJars;
        }

        List<Path> pluginJars = new ArrayList<>();

        try (Stream<Path> stream = Files.list(PLUGINS_DIR)) {
            stream.filter(path -> path.toString().endsWith(".jar")).forEach(pluginJars::add);
        }

        System.out.println("[MixinJarCreator] Found " + pluginJars.size() + " plugin JARs");

        for (Path pluginPath : pluginJars) {
            try {
                Optional<Path> jarPath = createMixinJarForPlugin(pluginPath);
                jarPath.ifPresent(createdJars::add);
            } catch (Exception e) {
                System.err.println("[MixinJarCreator] Error processing " + pluginPath + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        System.out.println("[MixinJarCreator] Created " + createdJars.size() + " mixin JARs");
        return createdJars;
    }

    public static Optional<Path> createMixinJarForPlugin(Path pluginPath) throws IOException {
        String pluginName = pluginPath.getFileName().toString().replace(".jar", "");
        System.out.println("[MixinJarCreator] Processing plugin: " + pluginName);

        Map<String, byte[]> mixinClasses = new HashMap<>();
        Map<String, byte[]> accessorClasses = new HashMap<>();
        Map<String, byte[]> interfaceClasses = new HashMap<>();
        Map<String, byte[]> allClasses = new HashMap<>();

        int totalClasses = 0;

        try (ZipFile zipFile = new ZipFile(pluginPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (!entryName.endsWith(".class")) continue;

                totalClasses++;
                String className = entryName.replace('/', '.').replace(".class", "");

                byte[] classBytes = readEntry(zipFile, entry);
                allClasses.put(className, classBytes);

                MixinType type = analyzeClass(className, classBytes);

                switch (type) {
                    case MIXIN:
                        mixinClasses.put(className, classBytes);
                        System.out.println("  [Mixin] " + className);
                        break;
                    case ACCESSOR:
                        accessorClasses.put(className, classBytes);
                        System.out.println("  [Accessor] " + className);
                        break;
                    case INTERFACE:
                        interfaceClasses.put(className, classBytes);
                        System.out.println("  [Interface] " + className);
                        break;
                }
            }
        }

        System.out.println("  Total classes scanned: " + totalClasses);

        if (!mixinClasses.isEmpty() || !accessorClasses.isEmpty() || !interfaceClasses.isEmpty()) {
            Path jarPath = createJarFile(pluginName, mixinClasses, accessorClasses, interfaceClasses, allClasses);
            return Optional.of(jarPath);
        }

        System.out.println("  No mixins found in " + pluginName);
        return Optional.empty();
    }

    private static Path createJarFile(String pluginName, Map<String, byte[]> mixinClasses, Map<String, byte[]> accessorClasses, Map<String, byte[]> interfaceClasses, Map<String, byte[]> allClasses) throws IOException {

        String timestamp = String.format("%tY%<tm%<td-%<tH%<tM", System.currentTimeMillis());
        String jarName = pluginName + "-mixin-" + timestamp + ".jar";
        Path jarPath = CACHE_DIR.resolve(jarName);

        if (Files.exists(jarPath)) {
            System.out.println("  Mixin JAR already exists: " + jarPath);
            return jarPath;
        }

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {

            for (Map.Entry<String, byte[]> entry : mixinClasses.entrySet()) addClassToJar(jos, entry.getKey(), entry.getValue());
            for (Map.Entry<String, byte[]> entry : accessorClasses.entrySet()) addClassToJar(jos, entry.getKey(), entry.getValue());
            for (Map.Entry<String, byte[]> entry : interfaceClasses.entrySet()) addClassToJar(jos, entry.getKey(), entry.getValue());

            addManifest(jos, pluginName, mixinClasses.size(), accessorClasses.size(), interfaceClasses.size());

            addClassList(jos, "mixin-classes.txt", mixinClasses.keySet());
            addClassList(jos, "accessor-classes.txt", accessorClasses.keySet());
            addClassList(jos, "interface-classes.txt", interfaceClasses.keySet());
        }

        System.out.println("  ✓ Created mixin JAR: " + jarPath);
        System.out.println("    - Mixins: " + mixinClasses.size());
        System.out.println("    - Accessors: " + accessorClasses.size());
        System.out.println("    - Interfaces: " + interfaceClasses.size());

        return jarPath;
    }

    private static void addClassToJar(JarOutputStream jos, String className, byte[] bytecode) throws IOException {
        String entryName = className.replace('.', '/') + ".class";
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        jos.write(bytecode);
        jos.closeEntry();
    }

    private static void addManifest(JarOutputStream jos, String pluginName, int mixinCount, int accessorCount, int interfaceCount) throws IOException {
        JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
        jos.putNextEntry(manifestEntry);

        String manifest = String.join("\r\n",
                "Manifest-Version: 1.0",
                "Created-By: MixinJarCreator",
                "Plugin-Name: " + pluginName,
                "Mixin-Count: " + mixinCount,
                "Accessor-Count: " + accessorCount,
                "Interface-Count: " + interfaceCount,
                "Created-Time: " + new Date(),
                ""
        );

        jos.write(manifest.getBytes());
        jos.closeEntry();
    }

    private static void addClassList(JarOutputStream jos, String fileName, Set<String> classes) throws IOException {
        if (classes.isEmpty()) return;

        JarEntry listEntry = new JarEntry("META-INF/" + fileName);
        jos.putNextEntry(listEntry);

        for (String className : classes) jos.write((className + "\n").getBytes());
        jos.closeEntry();
    }

    private static byte[] readEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream is = zipFile.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    private static MixinType analyzeClass(String className, byte[] classBytes) {

        if (className.contains("mixin") || className.contains("Mixin")) {
            return MixinType.MIXIN;
        }
        if (className.contains("access") || className.contains("Accessor")) {
            return MixinType.ACCESSOR;
        }
        if (className.contains("interface") || className.contains("Interface")) {
            return MixinType.INTERFACE;
        }
        return MixinType.NONE;
    }

    public static void cleanOldJars(int days) throws IOException {
        if (!Files.exists(CACHE_DIR)) return;

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);

        try (Stream<Path> stream = Files.list(CACHE_DIR)) {
            stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                try {
                    FileTime lastModified = Files.getLastModifiedTime(path);
                    if (lastModified.toMillis() < cutoffTime) {
                        Files.deleteIfExists(path);
                        System.out.println("[MixinJarCreator] Deleted old cache: " + path);
                    }
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static List<Path> getAllMixinJars() throws IOException {
        if (!Files.exists(CACHE_DIR)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(CACHE_DIR)) {
            return stream.filter(path -> path.toString().endsWith(".jar")).sorted().toList();
        }
    }

    private enum MixinType {
        MIXIN,
        ACCESSOR,
        INTERFACE,
        NONE
    }
}