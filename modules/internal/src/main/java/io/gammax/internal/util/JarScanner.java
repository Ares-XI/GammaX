package io.gammax.internal.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JarScanner {
    public static List<File> findJarsWithMixins(File dir) {
        List<File> jars = new ArrayList<>();
        scan(dir, jars);
        return jars;
    }

    private static void scan(File dir, List<File> jars) {
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) scan(file, jars);
            if (file.isFile() && file.getName().endsWith(".jar")) jars.add(file);
        }
    }
}
