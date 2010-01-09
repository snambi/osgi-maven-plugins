/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

package org.apache.tuscany.maven.bundle.plugin;

import static org.apache.tuscany.maven.bundle.plugin.AggregatedBundleActivator.BUNDLE_ACTIVATOR_LIST;
import static org.apache.tuscany.maven.bundle.plugin.HeaderParser.merge;
import static org.osgi.framework.Constants.BUNDLE_ACTIVATOR;
import static org.osgi.framework.Constants.BUNDLE_CLASSPATH;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.tuscany.maven.bundle.plugin.HeaderParser.HeaderClause;

/**
 * @version $Rev$ $Date$
 * @goal aggregate-modules
 * @phase process-resources
 * @requiresDependencyResolution test
 * @description Generate an aggregated bundle that contains all the modules and 3rd party jars
 */
public class BundleAggregatorMojo extends AbstractMojo {
    /**
     * Root directory.
     *
     *  @parameter expression="${project.build.directory}/modules"
     */
    private File rootDirectory;

    /**
     * Aggregated bundle
     *
     *  @parameter expression="${project.build.directory}/singlebundle/tuscany-bundle.jar"
     */
    private File targetBundleFile;

    /**
     * @parameter default-value== "org.apache.tuscany.sca.bundle";
     */
    private String bundleName = "org.apache.tuscany.sca.bundle";

    /**
     * @parameter default-value== "2.0.0";
     */
    private String bundleVersion = "2.0.0";

    // private static final Logger logger = Logger.getLogger(BundleAggregatorMojo.class.getName());

    public static void aggregateBundles(Log log,
                                        File root,
                                        File[] files,
                                        File targetBundleFile,
                                        String bundleName,
                                        String bundleVersion) throws Exception {
        targetBundleFile.getParentFile().mkdirs();
        Set<File> jarFiles = new HashSet<File>();
        List<Manifest> manifests = new ArrayList<Manifest>();
        for (File child : files) {
            try {
                Manifest manifest = null;
                if (child.isDirectory()) {
                    File mf = new File(child, "META-INF/MANIFEST.MF");
                    if (mf.isFile()) {
                        FileInputStream is = new FileInputStream(mf);
                        manifest = new Manifest(is);
                        is.close();
                        if (manifest != null) {
                            String classpath = manifest.getMainAttributes().getValue("Bundle-ClassPath");
                            if (classpath != null) {
                                for (HeaderClause clause : HeaderParser.parse(classpath)) {
                                    if (clause.getValue().equals(".")) {
                                        continue;
                                    } else {
                                        jarFiles.add(new File(child, clause.getValue()));
                                    }
                                }
                            } else {
                                // 
                            }
                        }
                    }
                } else if (child.getName().endsWith(".jar")) {
                    JarFile jar = new JarFile(child);
                    manifest = jar.getManifest();
                    jar.close();
                    if (manifest != null) {
                        String id = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
                        if (id != null && (id.startsWith("org.eclipse.") || id
                            .startsWith("org.apache.tuscany.sca.gateway"))) {
                            manifest = null;
                        } else {
                            jarFiles.add(child);
                        }
                    }
                }
                if (manifest == null) {
                    continue;
                }

                log.debug("Bundle file: " + child);
                manifests.add(manifest);
            } catch (Exception e) {
                throw e;
            }
        }
        Manifest merged = new Manifest();
        Attributes attributes = merged.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("Bundle-ManifestVersion", "2");
        attributes.putValue("Bundle-License", "http://www.apache.org/licenses/LICENSE-2.0.txt");
        attributes.putValue("Bundle-DocURL", "http://www.apache.org/");
        attributes.putValue("Bundle-RequiredExecutionEnvironment", "J2SE-1.5,JavaSE-1.6");
        attributes.putValue("Bundle-Vendor", "The Apache Software Foundation");
        attributes.putValue("Bundle-Version", bundleVersion);
        attributes.putValue("Bundle-SymbolicName", bundleName);
        attributes.putValue("SCA-Version", "1.1");
        attributes.putValue("Bundle-Name", bundleName);
        attributes.putValue("Bundle-ActivationPolicy", "lazy");
        for (Manifest mf : manifests) {
            for (Map.Entry<Object, Object> e : mf.getMainAttributes().entrySet()) {
                Attributes.Name key = (Attributes.Name)e.getKey();
                String name = key.toString();
                String oldValue = attributes.getValue(name);
                String value = (String)e.getValue();
                if (name.equals("Export-Package") || name.equals("Import-Package")
                    || name.equals("Require-Bundle")
                    || name.equals("DynamicImport-Package")
                    || name.equals("Bundle-ClassPath")
                    || name.equals("Private-Package")
                    || name.equals("Bundle-Description")) {
                    attributes.putValue(name, merge(oldValue, value));
                } else if (name.equals(BUNDLE_ACTIVATOR)) {
                    oldValue = attributes.getValue(BUNDLE_ACTIVATOR_LIST);
                    attributes.putValue(BUNDLE_ACTIVATOR_LIST, merge(oldValue, value));
                } else if (name.equals("Main-Class") || name.startsWith("Eclipse-") || name.startsWith("Bundle-")) {
                    // Ignore
                } else {
                    // Ignore
                    // attributes.putValue(name, value);
                }
            }
        }
        log.info("Generating " + targetBundleFile);
        attributes.putValue(BUNDLE_ACTIVATOR, AggregatedBundleActivator.class.getName());
        String bundleClassPath = attributes.getValue(BUNDLE_CLASSPATH);
        bundleClassPath = merge(bundleClassPath, ".");
        for (File f : jarFiles) {
            bundleClassPath = merge(bundleClassPath, f.getName());
        }
        attributes.putValue(BUNDLE_CLASSPATH, bundleClassPath);

        FileOutputStream fos = new FileOutputStream(targetBundleFile);
        JarOutputStream bundle = new JarOutputStream(fos, merged);

        for (File file : jarFiles) {
            log.info("Adding " + file);
            addEntry(bundle, file.getName(), file);
        }

        String classFile = AggregatedBundleActivator.class.getName().replace(".", "/") + ".class";
        InputStream classStream = BundleAggregatorMojo.class.getClassLoader().getResourceAsStream(classFile);
        addEntry(bundle, classFile, classStream);
        bundle.close();
    }

    private static void addDir(JarOutputStream jos, File root, File dir) throws IOException, FileNotFoundException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                addDir(jos, root, file);
            } else if (file.isFile()) {
                // getLog().info(file.toString());
                String uri = root.toURI().relativize(file.toURI()).toString();
                if ("META-INF/MANIFEST.MF".equals(uri)) {
                    continue;
                }
                addEntry(jos, uri, file);
            }
        }
    }

    private static void addEntry(JarOutputStream jos, String name, File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        addEntry(jos, name, in);
    }

    private static final byte[] buf = new byte[4096];

    private static void addEntry(JarOutputStream jos, String name, InputStream in) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        jos.putNextEntry(entry);
        for (;;) {
            int len = in.read(buf);
            if (len > 0) {
                jos.write(buf, 0, len);
            } else {
                break;
            }
        }
        in.close();
        jos.closeEntry();
    }

    private static void generateJar(File root, File jar, Manifest mf) throws IOException {
        FileOutputStream fos = new FileOutputStream(jar);
        JarOutputStream jos = mf != null ? new JarOutputStream(fos, mf) : new JarOutputStream(fos);
        addDir(jos, root, root);
        jos.close();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Log log = getLog();
            if (!rootDirectory.isDirectory()) {
                log.warn(rootDirectory + " is not a directory");
                return;
            }
            File[] files = rootDirectory.listFiles();
            aggregateBundles(log, rootDirectory, files, targetBundleFile, bundleName, bundleVersion);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }
}
