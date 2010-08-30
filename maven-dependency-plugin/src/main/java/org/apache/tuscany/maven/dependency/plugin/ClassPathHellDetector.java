package org.apache.tuscany.maven.dependency.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scanning the folder to find jar files and detect class conflicts
 */
public class ClassPathHellDetector {

    /**
     * arg[0]: The root directory
     * @param args
     */
    public static void main(String[] args) throws Exception {
        boolean verbose = Boolean.parseBoolean(System.getProperty("verbose", "true"));
        File root = new File(".");
        if (args.length >= 1) {
            root = new File(args[0]);
        }
        LogWrapper log = new ConsoleLogWrapper();
        check(root, log, verbose);
    }

    /**
     * Recursively check the jar files under the given root directory
     * @param root The root directory
     * @param log The log
     * @param verbose Indicate if the list of classes will be reported
     * @return The number of conflicts at jar level
     * @throws IOException
     */
    public static int check(File root, LogWrapper log, boolean verbose) throws IOException {
        Set<File> jarFiles = findJarFiles(root);
        return check(jarFiles, log, verbose);
    }

    /**
     * Check the given list of jars to find out conflicting classes
     * @param jarFiles The list of jar files
     * @param log The log
     * @param verbose Indicate if the list of classes will be reported
     * @return The number of conflicts at jar level
     * @throws IOException
     */
    public static int check(Set<File> jarFiles, LogWrapper log, boolean verbose) throws IOException {
        Map<String, Collection<ClassFile>> classToFileMapping = new HashMap<String, Collection<ClassFile>>();
        for (File f : jarFiles) {
            if (log.isDebugEnabled()) {
                log.debug("Scanning " + f);
            }
            for (ClassFile classFile : listClasses(f)) {
                Collection<ClassFile> files = classToFileMapping.get(classFile.name);
                if (files == null) {
                    files = new ArrayList<ClassFile>();
                    classToFileMapping.put(classFile.name, files);
                }
                files.add(classFile);
            }
        }

        Map<String, Set<String>> conflicts = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Collection<ClassFile>> entry : classToFileMapping.entrySet()) {
            Collection<ClassFile> files = entry.getValue();
            if (files.size() > 1) {
                Set<File> jars = new HashSet<File>();
                for (ClassFile cf : files) {
                    jars.add(cf.jarFile);
                }
                List<File> sorted = new ArrayList<File>(jars);
                Collections.sort(sorted);
                String conflict = sorted.toString();
                Set<String> classes = conflicts.get(conflict);
                if (classes == null) {
                    classes = new HashSet<String>();
                    conflicts.put(conflict, classes);
                }
                classes.add(entry.getKey());
            }
        }

        int size = conflicts.size();
        if (size >= 1) {
            log.warn("Number of conflicts: " + conflicts.size());
        } else {
            log.info("No class conflicts are found.");
        }

        for (Map.Entry<String, Set<String>> c : conflicts.entrySet()) {
            log.warn("");
            log.warn("Conflicting jars: " + c.getKey());

            if (verbose) {
                log.warn("Conflicting or overlapping classes [X: size, ?: crc]: ");
                List<String> list = new ArrayList<String>(c.getValue());
                Collections.sort(list);
                for (String cls : list) {
                    Collection<ClassFile> classFiles = classToFileMapping.get(cls);
                    String flag = compare(classFiles);
                    if (!" ".equals(flag)) {
                        // Class files with different size or crc
                        log.warn("  " + flag + " " + cls);
                    } else {
                        // Class files with same size and crc
                        log.warn("  " + flag + " " + cls);
                    }
                }
            }
        }

        return conflicts.size();
    }

    private static String compare(Collection<ClassFile> files) {
        long size = 0;
        long crc = 0;
        String name = null;
        for (ClassFile f : files) {
            if (name != null && !f.name.equals(name)) {
                // Different name
                return "X";
            }
            if (size != 0 && f.size != size) {
                // Different size
                return "X";
            }
            if (crc != 0 && f.crc != crc) {
                // Different crc
                return "?";
            }
            size = f.size;
            crc = f.crc;
            name = f.name;
        }
        return " ";
    }

    /**
     * List all class files within a jar
     * @param file
     * @return
     * @throws IOException
     */
    private static Collection<ClassFile> listClasses(File file) throws IOException {
        Collection<ClassFile> classFiles = new ArrayList<ClassPathHellDetector.ClassFile>();
        JarFile jarFile = new JarFile(file);
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class")) {
                ClassFile cls = new ClassFile(file, entry.getName(), entry.getSize(), entry.getCrc());
                classFiles.add(cls);
            }
        }
        jarFile.close();
        return classFiles;

    }

    private static Set<File> findJarFiles(File root) throws IOException {
        Set<File> jarFiles = new HashSet<File>();
        traverse(jarFiles, root, new HashSet<File>());
        return jarFiles;
    }

    /**
     * Recursively traverse a root directory
     *
     * @param fileList
     * @param file
     * @param root
     * @param visited The visited directories
     * @throws IOException
     */
    private static void traverse(Set<File> fileList, File file, Set<File> visited) throws IOException {
        if (file.isFile()) {
            fileList.add(file);
        } else if (file.isDirectory()) {
            File dir = file.getCanonicalFile();
            if (!visited.contains(dir)) {
                // [rfeng] Add the canonical file into the visited set to avoid duplicate navigation of directories
                // following the symbolic links
                visited.add(dir);

                File[] files = file.listFiles(new FileFilter() {

                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().endsWith(".jar");
                    }
                });
                for (File f : files) {
                    if (!f.getName().startsWith(".")) {
                        traverse(fileList, f, visited);
                    }
                }
            }
        }
    }

    /**
     * Description of a class file within the jar
     */
    private static class ClassFile {
        private File jarFile;
        private String name;
        private long size;
        private long crc;

        public ClassFile(File jarFile, String name, long size, long crc) {
            super();
            this.jarFile = jarFile;
            this.name = name;
            this.size = size;
            this.crc = crc;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int)(crc ^ (crc >>> 32));
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + (int)(size ^ (size >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ClassFile other = (ClassFile)obj;
            if (crc != other.crc)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (size != other.size)
                return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ClassFile [jarFile=").append(jarFile).append(", name=").append(name).append(", size=")
                .append(size).append(", crc=").append(crc).append("]");
            return builder.toString();
        }
    }

}
