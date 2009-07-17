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

package org.apache.tuscany.maven.plugin.surefire;

import static org.osgi.framework.Constants.BUNDLE_MANIFESTVERSION;
import static org.osgi.framework.Constants.BUNDLE_NAME;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.BUNDLE_VERSION;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.booter.SurefireExecutionException;
import org.apache.maven.surefire.booter.shade.org.codehaus.plexus.util.StringUtils;
import org.apache.maven.surefire.report.BriefConsoleReporter;
import org.apache.maven.surefire.report.BriefFileReporter;
import org.apache.maven.surefire.report.ConsoleReporter;
import org.apache.maven.surefire.report.DetailedConsoleReporter;
import org.apache.maven.surefire.report.FileReporter;
import org.apache.maven.surefire.report.ForkingConsoleReporter;
import org.apache.maven.surefire.report.XMLReporter;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Derived from maven-surefire-plugin 2.4.3
 * Run tests using Surefire.
 * 
 * @requiresDependencyResolution test
 * @goal test
 * @phase test
 */
public class OSGiSurefirePlugin extends AbstractMojo {

    /**
     * Set this to 'true' to skip running tests, but still compile them. Its use is NOT RECOMMENDED, but quite
     * convenient on occasion.
     * 
     * @parameter expression="${skipTests}"
     * @since 2.4
     */
    protected boolean skipTests;

    /**
     * DEPRECATED This old parameter is just like skipTests, but bound to the old property maven.test.skip.exec.
     * Use -DskipTests instead; it's shorter.
     * 
     * @deprecated
     * @parameter expression="${maven.test.skip.exec}"
     * @since 2.3
     */
    protected boolean skipExec;

    /**
     * Set this to 'true' to bypass unit tests entirely. Its use is NOT RECOMMENDED, especially if you
     * enable it using the "maven.test.skip" property, because maven.test.skip disables both running the
     * tests and compiling the tests.  Consider using the skipTests parameter instead.
     * 
     * @parameter expression="${maven.test.skip}"
     */
    protected boolean skip;

    /**
     * Set this to true to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
     * occasion.
     * 
     * @parameter expression="${maven.test.failure.ignore}"
     */
    protected boolean testFailureIgnore;

    /**
     * The base directory of the project being tested. This can be obtained in your unit test by
     * System.getProperty("basedir").
     * 
     * @parameter expression="${basedir}"
     * @required
     */
    protected File basedir;

    /**
     * The directory containing generated test classes of the project being tested.
     * 
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    protected File testClassesDirectory;

    /**
     * The directory containing generated classes of the project being tested.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    protected File classesDirectory;

    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The classpath elements of the project being tested.
     * 
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    protected List classpathElements;

    /**
     * Additional elements to be appended to the classpath.
     * 
     * @parameter
     * @since 2.4
     */
    protected List additionalClasspathElements;

    /**
     * Base directory where all reports are written to.
     * 
     * @parameter expression="${project.build.directory}/surefire-osgi-reports"
     */
    protected File reportsDirectory;

    /**
     * The test source directory containing test class sources.
     * 
     * @parameter expression="${project.build.testSourceDirectory}"
     * @required
     * @since 2.2
     */
    protected File testSourceDirectory;

    /**
     * Specify this parameter to run individual tests by file name, overriding the <code>includes/excludes</code>
     * parameters.  Each pattern you specify here will be used to create an 
     * include pattern formatted like <code>**&#47;${test}.java</code>, so you can just type "-Dtest=MyTest"
     * to run a single test called "foo/MyTest.java".  This parameter will override the TestNG suiteXmlFiles
     * parameter.
     * 
     * @parameter expression="${test}"
     */
    protected String test;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be included in testing. When not
     * specified and when the <code>test</code> parameter is not specified, the default includes will be
     * <code>**&#47;Test*.java   **&#47;*Test.java   **&#47;*TestCase.java</code>.  This parameter is ignored if
     * TestNG suiteXmlFiles are specified.
     * 
     * @parameter
     */
    protected List includes;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be excluded in testing. When not
     * specified and when the <code>test</code> parameter is not specified, the default excludes will be
     * <code>**&#47;*$*</code> (which excludes all inner classes).  This parameter is ignored if
     * TestNG suiteXmlFiles are specified.
     * 
     * @parameter
     */
    protected List excludes;

    /**
     * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
     * System.setProperty( "localRepository").
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * List of System properties to pass to the JUnit tests.
     * 
     * @parameter
     */
    protected Properties systemProperties;

    /**
     * List of properties for configuring all TestNG related configurations. This is the new
     * preferred method of configuring TestNG.
     *
     * @parameter
     * @since 2.4
     */
    protected Properties properties;

    /**
     * Map of of plugin artifacts.
     * 
     * @parameter expression="${plugin.artifactMap}"
     * @required
     * @readonly
     */
    protected Map pluginArtifactMap;
    
    /**
     * @parameter expression="${plugin.groupId}"
     * @required
     * @readonly
     */
    protected String pluginGroupId;
    /**
     * @parameter expression="${plugin.artifactId}"
     * @required
     * @readonly
     */
    protected String pluginArtifactId;
    /**
     * @parameter expression="${plugin.version}"
     * @required
     * @readonly
     */
    protected String pluginVersion;

    /**
     * Map of of project artifacts.
     * 
     * @parameter expression="${project.artifactMap}"
     * @required
     * @readonly
     */
    protected Map projectArtifactMap;

    /**
     * Option to print summary of test suites or just print the test cases that has errors.
     * 
     * @parameter expression="${surefire.printSummary}" default-value="true"
     */
    protected boolean printSummary;

    /**
     * Selects the formatting for the test report to be generated. Can be set as brief or plain.
     * 
     * @parameter expression="${surefire.reportFormat}" default-value="brief"
     */
    protected String reportFormat;

    /**
     * Option to generate a file test report or just output the test report to the console.
     * 
     * @parameter expression="${surefire.useFile}" default-value="true"
     */
    protected boolean useFile;

    /**
     * When forking, set this to true to redirect the unit test standard output to a file (found in
     * reportsDirectory/testName-output.txt).
     * 
     * @parameter expression="${maven.test.redirectTestOutputToFile}" default-value="false"
     * @since 2.3
     */
    protected boolean redirectTestOutputToFile;

    /**
     * Set this to "true" to cause a failure if there are no tests to run. Defaults to false.
     * 
     * @parameter expression="${failIfNoTests}"
     * @since 2.4
     */
    protected Boolean failIfNoTests;

    /**
     * Option to specify the forking mode. Can be "never", "once" or "always". "none" and "pertest" are also accepted
     * for backwards compatibility.
     * 
     * @parameter expression="${forkMode}" default-value="once"
     * @since 2.1
     */
    protected String forkMode;

    /**
     * Option to specify the jvm (or path to the java executable) to use with the forking options. For the default, the
     * jvm will be the same as the one used to run Maven.
     * 
     * @parameter expression="${jvm}"
     * @since 2.1
     */
    protected String jvm;

    /**
     * Arbitrary JVM options to set on the command line.
     * 
     * @parameter expression="${argLine}"
     * @since 2.1
     */
    protected String argLine;

    /**
     * Attach a debugger to the forked JVM.  If set to "true", the process will suspend and 
     * wait for a debugger to attach on port 5005.  If set to some other string, that
     * string will be appended to the argLine, allowing you to configure arbitrary
     * debuggability options (without overwriting the other options specified in the argLine).
     * 
     * @parameter expression="${maven.surefire.debug}"
     * @since 2.4
     */
    protected String debugForkedProcess;

    /**
     * Kill the forked test process after a certain number of seconds.  If set to 0,
     * wait forever for the process, never timing out.
     * 
     * @parameter expression="${surefire.timeout}"
     * @since 2.4
     */
    protected int forkedProcessTimeoutInSeconds;

    /**
     * Additional environments to set on the command line.
     * 
     * @parameter
     * @since 2.1.3
     */
    protected Map environmentVariables = new HashMap();

    /**
     * Command line working directory.
     * 
     * @parameter expression="${basedir}"
     * @since 2.1.3
     */
    protected File workingDirectory;

    /**
     * When false it makes tests run using the standard classloader delegation instead of the default Maven isolated
     * classloader. Only used when forking (forkMode is not "none").<br/> Setting it to false helps with some problems
     * caused by conflicts between xml parsers in the classpath and the Java 5 provider parser.
     * 
     * @parameter expression="${childDelegation}" default-value="false"
     * @since 2.1
     */
    protected boolean childDelegation;

    /**
     * (TestNG only) Groups for this test. Only classes/methods/etc decorated with one of the groups specified here will be included
     * in test run, if specified.  This parameter is overridden if suiteXmlFiles are specified.
     * 
     * @parameter expression="${groups}"
     * @since 2.2
     */
    protected String groups;

    /**
     * (TestNG only) Excluded groups. Any methods/classes/etc with one of the groups specified in this list will specifically not be
     * run.  This parameter is overridden if suiteXmlFiles are specified.
     * 
     * @parameter expression="${excludedGroups}"
     * @since 2.2
     */
    protected String excludedGroups;

    /**
     * (TestNG only) List of TestNG suite xml file locations, seperated by commas. Note that suiteXmlFiles is incompatible
     * with several other parameters on this plugin, like includes/excludes.  This parameter is ignored if
     * the "test" parameter is specified (allowing you to run a single test instead of an entire suite).
     * 
     * @parameter
     * @since 2.2
     */
    protected File[] suiteXmlFiles;

    /**
     * Allows you to specify the name of the JUnit artifact. If not set, <code>junit:junit</code> will be used.
     * 
     * @parameter expression="${junitArtifactName}" default-value="junit:junit"
     * @since 2.3.1
     */
    protected String junitArtifactName;

    /**
     * Allows you to specify the name of the TestNG artifact. If not set, <code>org.testng:testng</code> will be used.
     * 
     * @parameter expression="${testNGArtifactName}" default-value="org.testng:testng"
     * @since 2.3.1
     */
    protected String testNGArtifactName;

    /**
     * (TestNG only) The attribute thread-count allows you to specify how many threads should be allocated for this execution. Only
     * makes sense to use in conjunction with parallel.
     * 
     * @parameter expression="${threadCount}"
     * @since 2.2
     */
    protected int threadCount;

    /**
     * (TestNG only) When you use the parallel attribute, TestNG will try to run all your test methods in separate threads, except for
     * methods that depend on each other, which will be run in the same thread in order to respect their order of
     * execution.
     * 
     * @parameter expression="${parallel}"
     * @todo test how this works with forking, and console/file output parallelism
     * @since 2.2
     */
    protected String parallel;

    /**
     * Whether to trim the stack trace in the reports to just the lines within the test, or show the full trace.
     * 
     * @parameter expression="${trimStackTrace}" default-value="true"
     * @since 2.2
     */
    protected boolean trimStackTrace;

    /**
     * Resolves the artifacts needed.
     * 
     * @component
     */
    protected ArtifactResolver artifactResolver;

    /**
     * Creates the artifact
     * 
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * The plugin remote repositories declared in the pom.
     * 
     * @parameter expression="${project.pluginArtifactRepositories}"
     * @since 2.2
     */
    protected List remoteRepositories;

    /**
     * For retrieval of artifact's metadata.
     * 
     * @component
     */
    protected ArtifactMetadataSource metadataSource;

    protected static final String BRIEF_REPORT_FORMAT = "brief";

    protected static final String PLAIN_REPORT_FORMAT = "plain";

    protected Properties originalSystemProperties;

    /**
     * Flag to disable the generation of report files in xml format.
     * 
     * @parameter expression="${disableXmlReport}" default-value="false"
     * @since 2.2
     */
    protected boolean disableXmlReport;

    /**
     * Option to pass dependencies to the system's classloader instead of using an isolated class loader when forking.
     * Prevents problems with JDKs which implement the service provider lookup mechanism by using the system's
     * classloader.  Default value is "true".
     * 
     * @parameter expression="${surefire.useSystemClassLoader}"
     * @since 2.3
     */
    protected Boolean useSystemClassLoader;

    /**
     * By default, Surefire forks your tests using a manifest-only jar; set this parameter
     * to "false" to force it to launch your tests with a plain old Java classpath.
     * (See http://maven.apache.org/plugins/maven-surefire-plugin/examples/class-loading.html
     * for a more detailed explanation of manifest-only jars and their benefits.)
     * 
     * Default value is "true".  Beware, setting this to "false" may cause your tests to
     * fail on Windows if your classpath is too long.
     * 
     * @parameter expression="${surefire.useManifestOnlyJar}" default-value="true"
     * @since 2.4.3
     */
    protected boolean useManifestOnlyJar;

    /**
     * By default, Surefire enables JVM assertions for the execution of your test cases. To disable the assertions, set
     * this flag to <code>false</code>.
     * 
     * @parameter expression="${enableAssertions}" default-value="true"
     * @since 2.3.1
     */
    protected boolean enableAssertions;

    /**
     * The current build session instance. 
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project.getPackaging().equals("pom")) {
            return;
        }

        if (verifyParameters()) {
            OSGiSurefireBooter surefireBooter = constructSurefireBooter();
            
            Log log = getLog();
            Set<String> jarFiles = new HashSet<String>();
            
            /*
            for (Object o : project.getArtifacts()) {
                Artifact a = (Artifact)o;
                if ("pom".equals(a.getType())) {
                    // Skip pom projects
                    continue;
                }
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Adding: " + a);
                    }
                    jarFiles.add(a.getFile().toURI().toURL());
                } catch (MalformedURLException e) {
                    getLog().error(e);
                }
            }
            */

            /*
             * Add org.apache.tuscany.sca:tuscany-extensibility-osgi module
             */
            Artifact ext = getArtifact("org.apache.tuscany.sca", "tuscany-extensibility-equinox");
            if (log.isDebugEnabled()) {
                log.debug("Adding: " + ext);
            }
            jarFiles.add(ext.getFile().getAbsolutePath());

            Artifact con = getArtifact("org.apache.tuscany.sca", "tuscany-contribution-osgi");
            if (log.isDebugEnabled()) {
                log.debug("Adding: " + con);
            }
            jarFiles.add(con.getFile().getAbsolutePath());            

            String name = project.getBuild().getFinalName();
            String mainBundleName = null;
            File mainJar = new File(project.getBuild().getDirectory(), name + "-osgi.jar");
            File testJar = new File(project.getBuild().getDirectory(), name + "-osgi-tests.jar");
            try {
                Manifest manifest = createMainBundle();
                mainBundleName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME);
                int sc = mainBundleName.indexOf(';');
                if (sc != -1) {
                    mainBundleName = mainBundleName.substring(0, sc);
                }
                generateJar(classesDirectory, mainJar, manifest);
                Manifest testManifest = createTestFragment(manifest);
                generateJar(testClassesDirectory, testJar, testManifest);
                jarFiles.add(mainJar.getAbsolutePath());
                jarFiles.add(testJar.getAbsolutePath());
            } catch (IOException e) {
                getLog().error(e);
            }

            if (log.isDebugEnabled()) {
                log.debug("Main bundle: " + mainBundleName);
            }
            surefireBooter.setMainBundleName(mainBundleName);
            for (String url : jarFiles) {
                surefireBooter.addClassPathUrl(url);
            }            

            getLog().info("Surefire report directory: " + reportsDirectory);

            int result;
            try {
                result = surefireBooter.run();
            } catch (SurefireBooterForkException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (SurefireExecutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            if (originalSystemProperties != null && !surefireBooter.isForking()) {
                // restore system properties, only makes sense when not forking..
                System.setProperties(originalSystemProperties);
            }

            if (result == 0)
                return;

            String msg;

            if (result == OSGiSurefireBooter.NO_TESTS_EXIT_CODE) {
                if ((failIfNoTests == null) || !failIfNoTests.booleanValue())
                    return;
                // TODO: i18n
                throw new MojoFailureException(
                                               "No tests were executed!  (Set -DfailIfNoTests=false to ignore this error.)");
            } else {
                // TODO: i18n
                msg =
                    "There are test failures.\n\nPlease refer to " + reportsDirectory
                        + " for the individual test results.";

            }

            if (testFailureIgnore) {
                getLog().error(msg);
            } else {
                throw new MojoFailureException(msg);
            }
        }
    }

    protected boolean verifyParameters() throws MojoFailureException {
        if (skip || skipTests || skipExec) {
            getLog().info("Tests are skipped.");
            return false;
        }

        if (!testClassesDirectory.exists()) {
            if (failIfNoTests != null && failIfNoTests.booleanValue()) {
                throw new MojoFailureException("No tests to run!");
            }
            getLog().info("No tests to run.");
            return false;
        }

        if (useSystemClassLoader != null && ForkConfiguration.FORK_NEVER.equals(forkMode)) {
            getLog().warn("useSystemClassloader setting has no effect when not forking");
        }

        return true;
    }

    /**
     * Converts old TestNG configuration parameters over to new properties based configuration
     * method. (if any are defined the old way)
     */
    private void convertTestNGParameters() {
        if (properties == null) {
            properties = new Properties();
        }

        if (this.parallel != null) {
            properties.setProperty("parallel", this.parallel);
        }
        if (this.excludedGroups != null) {
            properties.setProperty("excludegroups", this.excludedGroups);
        }
        if (this.groups != null) {
            properties.setProperty("groups", this.groups);
        }

        if (this.threadCount > 0) {
            properties.setProperty("threadcount", new Integer(this.threadCount).toString());
        }
    }

    private OSGiSurefireBooter constructSurefireBooter() throws MojoExecutionException, MojoFailureException {
        OSGiSurefireBooter surefireBooter = new OSGiSurefireBooter();

        // Build up the surefire boot classpath
        // * org.apache.tuscany.sca:tuscany-maven-surefire-osgi-plugin (non-transitive 
        //   to exclude maven dependencies 
        // * org.apache.tuscany.sca:tuscany-node-launcher-equinox (transitive)
        // * org.apache.maven.surefire:surefire-booter (transitive)
        // Get the artifact for the OSGi surefire plugin
        Artifact osgiArtifact =
            artifactFactory.createArtifact(pluginGroupId,
                                           pluginArtifactId,
                                           pluginVersion,
                                           Artifact.SCOPE_TEST,
                                           "maven-plugin");
        try {
            artifactResolver.resolve(osgiArtifact, remoteRepositories, localRepository);
            surefireBooter.addSurefireBootClassPathUrl(osgiArtifact.getFile().getAbsolutePath());
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to resolve " + osgiArtifact);
        } 
        
        Artifact launcher = (Artifact) pluginArtifactMap.get("org.apache.tuscany.sca:tuscany-node-launcher-equinox");

        // Look up the surefire-booter
        Artifact surefireArtifact = (Artifact)pluginArtifactMap.get("org.apache.maven.surefire:surefire-booter");
        if (surefireArtifact == null) {
            throw new MojoExecutionException("Unable to locate surefire-booter in the list of plugin artifacts");
        }

        surefireArtifact.isSnapshot(); // TODO: this is ridiculous, but it fixes getBaseVersion to be -SNAPSHOT if
        // needed

        Artifact junitArtifact;
        Artifact testNgArtifact;
        try {
            addArtifact(surefireBooter, surefireArtifact);
            addArtifact(surefireBooter, launcher);

            junitArtifact = (Artifact)projectArtifactMap.get(junitArtifactName);
            // SUREFIRE-378, junit can have an alternate artifact name
            if (junitArtifact == null && "junit:junit".equals(junitArtifactName)) {
                junitArtifact = (Artifact)projectArtifactMap.get("junit:junit-dep");
            }

            // TODO: this is pretty manual, but I'd rather not require the plugin > dependencies section right now
            testNgArtifact = (Artifact)projectArtifactMap.get(testNGArtifactName);

            if (testNgArtifact != null) {
                VersionRange range = VersionRange.createFromVersionSpec("[4.7,)");
                if (!range.containsVersion(new DefaultArtifactVersion(testNgArtifact.getVersion()))) {
                    throw new MojoFailureException(
                                                   "TestNG support requires version 4.7 or above. You have declared version " + testNgArtifact
                                                       .getVersion());
                }

                convertTestNGParameters();

                if (this.testClassesDirectory != null) {
                    properties.setProperty("testng.test.classpath", testClassesDirectory.getAbsolutePath());
                }

                addArtifact(surefireBooter, testNgArtifact);

                // The plugin uses a JDK based profile to select the right testng. We might be explicity using a
                // different one since its based on the source level, not the JVM. Prune using the filter.
                addProvider(surefireBooter, "surefire-testng", surefireArtifact.getBaseVersion(), testNgArtifact);
            } else if (junitArtifact != null && junitArtifact.getBaseVersion().startsWith("4")) {
                addProvider(surefireBooter, "surefire-junit4", surefireArtifact.getBaseVersion(), null);
            } else {
                // add the JUnit provider as default - it doesn't require JUnit to be present,
                // since it supports POJO tests.
                addProvider(surefireBooter, "surefire-junit", surefireArtifact.getBaseVersion(), null);
            }
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Unable to locate required surefire provider dependency: " + e
                .getMessage(), e);
        } catch (InvalidVersionSpecificationException e) {
            throw new MojoExecutionException("Error determining the TestNG version requested: " + e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Error to resolving surefire provider dependency: " + e.getMessage(), e);
        }

        if (suiteXmlFiles != null && suiteXmlFiles.length > 0 && test == null) {
            if (testNgArtifact == null) {
                throw new MojoExecutionException("suiteXmlFiles is configured, but there is no TestNG dependency");
            }

            // TODO: properties should be passed in here too
            surefireBooter.addTestSuite("org.apache.maven.surefire.testng.TestNGXmlTestSuite",
                                        new Object[] {suiteXmlFiles, testSourceDirectory.getAbsolutePath(),
                                                      testNgArtifact.getVersion(), testNgArtifact.getClassifier(),
                                                      properties, reportsDirectory});
        } else {
            List includes;
            List excludes;

            if (test != null) {
                // Check to see if we are running a single test. The raw parameter will
                // come through if it has not been set.

                // FooTest -> **/FooTest.java

                includes = new ArrayList();

                excludes = new ArrayList();

                if (failIfNoTests == null) {
                    failIfNoTests = Boolean.TRUE;
                }

                String[] testRegexes = StringUtils.split(test, ",");

                for (int i = 0; i < testRegexes.length; i++) {
                    String testRegex = testRegexes[i];
                    if (testRegex.endsWith(".java")) {
                        testRegex = testRegex.substring(0, testRegex.length() - 5);
                    }
                    // Allow paths delimited by '.' or '/'
                    testRegex = testRegex.replace('.', '/');
                    includes.add("**/" + testRegex + ".java");
                }
            } else {
                includes = this.includes;

                excludes = this.excludes;

                // defaults here, qdox doesn't like the end javadoc value
                // Have to wrap in an ArrayList as surefire expects an ArrayList instead of a List for some reason
                if (includes == null || includes.size() == 0) {
                    includes =
                        new ArrayList(Arrays
                            .asList(new String[] {"**/Test*.java", "**/*Test.java", "**/*TestCase.java"}));
                }
                if (excludes == null || excludes.size() == 0) {
                    excludes = new ArrayList(Arrays.asList(new String[] {"**/*$*"}));
                }
            }

            if (testNgArtifact != null) {
                surefireBooter.addTestSuite("org.apache.maven.surefire.testng.TestNGDirectoryTestSuite",
                                            new Object[] {testClassesDirectory, includes, excludes,
                                                          testSourceDirectory.getAbsolutePath(),
                                                          testNgArtifact.getVersion(), testNgArtifact.getClassifier(),
                                                          properties, reportsDirectory});
            } else {
                String junitDirectoryTestSuite;
                if (junitArtifact != null && junitArtifact.getBaseVersion() != null
                    && junitArtifact.getBaseVersion().startsWith("4")) {
                    junitDirectoryTestSuite = "org.apache.maven.surefire.junit4.JUnit4DirectoryTestSuite";
                } else {
                    junitDirectoryTestSuite = "org.apache.maven.surefire.junit.JUnitDirectoryTestSuite";
                }

                // fall back to JUnit, which also contains POJO support. Also it can run
                // classes compiled against JUnit since it has a dependency on JUnit itself.
                surefireBooter.addTestSuite(junitDirectoryTestSuite, new Object[] {testClassesDirectory, includes,
                                                                                   excludes});
            }
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        getLog().debug("Test Classpath :");

        classpathElements.remove(classesDirectory.getAbsolutePath());
        classpathElements.remove(testClassesDirectory.getAbsolutePath());
        /*
        // Check if we need to add configured classes/test classes directories here.
        // If they are configured, we should remove the default to avoid conflicts.
        if (!project.getBuild().getOutputDirectory().equals(classesDirectory.getAbsolutePath())) {
            classpathElements.remove(project.getBuild().getOutputDirectory());
            classpathElements.add(classesDirectory.getAbsolutePath());
        }
        if (!project.getBuild().getTestOutputDirectory().equals(testClassesDirectory.getAbsolutePath())) {
            classpathElements.remove(project.getBuild().getTestOutputDirectory());
            classpathElements.add(testClassesDirectory.getAbsolutePath());
        }
        */

        for (Iterator i = classpathElements.iterator(); i.hasNext();) {
            String classpathElement = (String)i.next();

            getLog().debug("  " + classpathElement);

            surefireBooter.addClassPathUrl(classpathElement);
        }

        Toolchain tc = getToolchain();

        if (tc != null) {
            getLog().info("Toolchain in surefire-plugin: " + tc);
            if (ForkConfiguration.FORK_NEVER.equals(forkMode)) {
                forkMode = ForkConfiguration.FORK_ONCE;
            }
            if (jvm != null) {
                getLog().warn("Toolchains are ignored, 'executable' parameter is set to " + jvm);
            } else {
                jvm = tc.findTool("java"); //NOI18N
            }
        }

        if (additionalClasspathElements != null) {
            for (Iterator i = additionalClasspathElements.iterator(); i.hasNext();) {
                String classpathElement = (String)i.next();

                getLog().debug("  " + classpathElement);

                surefireBooter.addClassPathUrl(classpathElement);
            }
        }

        // ----------------------------------------------------------------------
        // Forking
        // ----------------------------------------------------------------------

        ForkConfiguration fork = new ForkConfiguration();

        fork.setForkMode(forkMode);

        processSystemProperties(!fork.isForking());

        if (getLog().isDebugEnabled()) {
            showMap(systemProperties, "system property");
        }

        if (fork.isForking()) {
            useSystemClassLoader = useSystemClassLoader == null ? Boolean.TRUE : useSystemClassLoader;
            fork.setUseSystemClassLoader(useSystemClassLoader.booleanValue());
            fork.setUseManifestOnlyJar(useManifestOnlyJar);

            fork.setSystemProperties(systemProperties);

            if ("true".equals(debugForkedProcess)) {
                debugForkedProcess =
                    "-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005";
            }

            fork.setDebugLine(debugForkedProcess);

            if (jvm == null || "".equals(jvm)) {
                // use the same JVM as the one used to run Maven (the "java.home" one)
                jvm = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                getLog().debug("Using JVM: " + jvm);
            }

            fork.setJvmExecutable(jvm);

            if (workingDirectory != null) {
                fork.setWorkingDirectory(workingDirectory);
            } else {
                fork.setWorkingDirectory(basedir);
            }

            fork.setArgLine(argLine);

            fork.setEnvironmentVariables(environmentVariables);

            if (getLog().isDebugEnabled()) {
                showMap(environmentVariables, "environment variable");

                fork.setDebug(true);
            }

            if (argLine != null) {
                List args = Arrays.asList(argLine.split(" "));
                if (args.contains("-da") || args.contains("-disableassertions")) {
                    enableAssertions = false;
                }
            }
        }

        surefireBooter.setFailIfNoTests(failIfNoTests == null ? false : failIfNoTests.booleanValue());

        surefireBooter.setForkedProcessTimeoutInSeconds(forkedProcessTimeoutInSeconds);

        surefireBooter.setRedirectTestOutputToFile(redirectTestOutputToFile);

        surefireBooter.setForkConfiguration(fork);

        surefireBooter.setChildDelegation(childDelegation);

        surefireBooter.setEnableAssertions(enableAssertions);

        surefireBooter.setReportsDirectory(reportsDirectory);

        addReporters(surefireBooter, fork.isForking());

        return surefireBooter;
    }

    private void showMap(Map map, String setting) {
        for (Iterator i = map.keySet().iterator(); i.hasNext();) {
            String key = (String)i.next();
            String value = (String)map.get(key);
            getLog().debug("Setting " + setting + " [" + key + "]=[" + value + "]");
        }
    }

    private void addProvider(OSGiSurefireBooter surefireBooter,
                             String provider,
                             String version,
                             Artifact filteredArtifact) throws ArtifactNotFoundException, ArtifactResolutionException {
        Artifact providerArtifact =
            artifactFactory.createDependencyArtifact("org.apache.maven.surefire", provider, VersionRange
                .createFromVersion(version), "jar", null, Artifact.SCOPE_TEST);
        ArtifactResolutionResult result = resolveArtifact(filteredArtifact, providerArtifact);

        for (Iterator i = result.getArtifacts().iterator(); i.hasNext();) {
            Artifact artifact = (Artifact)i.next();

            String key = ArtifactUtils.versionlessKey(artifact);
            if("junit:junit".equals(key) || "jnuit:junit-dep".equals(key)) {
                // Skip junit as it will be pulled from the test case dependencies
                continue;
            }
            getLog().debug("Adding to surefire test classpath: " + artifact.getFile().getAbsolutePath());

            surefireBooter.addSurefireClassPathUrl(artifact.getFile().getAbsolutePath());
        }
    }

    private ArtifactResolutionResult resolveArtifact(Artifact filteredArtifact, Artifact providerArtifact)
        throws ArtifactResolutionException, ArtifactNotFoundException {
        ArtifactFilter filter = null;
        if (filteredArtifact != null) {
            filter =
                new ExcludesArtifactFilter(Collections.singletonList(filteredArtifact.getGroupId() + ":"
                    + filteredArtifact.getArtifactId()));
        }

        Artifact originatingArtifact = artifactFactory.createBuildArtifact("dummy", "dummy", "1.0", "jar");

        return artifactResolver.resolveTransitively(Collections.singleton(providerArtifact),
                                                    originatingArtifact,
                                                    localRepository,
                                                    remoteRepositories,
                                                    metadataSource,
                                                    filter);
    }

    private void addArtifact(OSGiSurefireBooter surefireBooter, Artifact surefireArtifact)
        throws ArtifactNotFoundException, ArtifactResolutionException {
        ArtifactResolutionResult result = resolveArtifact(null, surefireArtifact);

        for (Iterator i = result.getArtifacts().iterator(); i.hasNext();) {
            Artifact artifact = (Artifact)i.next();

            getLog().debug("Adding to surefire booter test classpath: " + artifact.getFile().getAbsolutePath());

            surefireBooter.addSurefireBootClassPathUrl(artifact.getFile().getAbsolutePath());
        }
    }

    protected void processSystemProperties(boolean setInSystem) {
        if (systemProperties == null) {
            systemProperties = new Properties();
        }

        originalSystemProperties = (Properties)System.getProperties().clone();

        // We used to take all of our system properties and dump them in with the
        // user specified properties for SUREFIRE-121, causing SUREFIRE-491.
        // Not gonna do THAT any more... but I'm leaving this code here in case
        // we need it later when we try to fix SUREFIRE-121 again.

        // Get the properties from the MavenSession instance to make embedded use work correctly
        Properties userSpecifiedProperties = (Properties)session.getExecutionProperties().clone();
        userSpecifiedProperties.putAll(systemProperties);
        //systemProperties = userSpecifiedProperties;

        systemProperties.setProperty("basedir", basedir.getAbsolutePath());
        systemProperties.setProperty("user.dir", workingDirectory.getAbsolutePath());

        systemProperties.setProperty("localRepository", localRepository.getBasedir());

        if (setInSystem) {
            // Add all system properties configured by the user
            Iterator iter = systemProperties.keySet().iterator();

            while (iter.hasNext()) {
                String key = (String)iter.next();

                String value = systemProperties.getProperty(key);

                System.setProperty(key, value);
            }
        }
    }

    /**
     * <p>
     * Adds Reporters that will generate reports with different formatting.
     * <p>
     * The Reporter that will be added will be based on the value of the parameter useFile, reportFormat, and
     * printSummary.
     * 
     * @param surefireBooter The surefire booter that will run tests.
     * @param forking
     */
    private void addReporters(OSGiSurefireBooter surefireBooter, boolean forking) {
        Boolean trimStackTrace = Boolean.valueOf(this.trimStackTrace);
        if (useFile) {
            if (printSummary) {
                if (forking) {
                    surefireBooter.addReport(ForkingConsoleReporter.class.getName(), new Object[] {trimStackTrace});
                } else {
                    surefireBooter.addReport(ConsoleReporter.class.getName(), new Object[] {trimStackTrace});
                }
            }

            if (BRIEF_REPORT_FORMAT.equals(reportFormat)) {
                surefireBooter.addReport(BriefFileReporter.class.getName(), new Object[] {reportsDirectory,
                                                                                          trimStackTrace});
            } else if (PLAIN_REPORT_FORMAT.equals(reportFormat)) {
                surefireBooter.addReport(FileReporter.class.getName(), new Object[] {reportsDirectory, trimStackTrace});
            }
        } else {
            if (BRIEF_REPORT_FORMAT.equals(reportFormat)) {
                surefireBooter.addReport(BriefConsoleReporter.class.getName(), new Object[] {trimStackTrace});
            } else if (PLAIN_REPORT_FORMAT.equals(reportFormat)) {
                surefireBooter.addReport(DetailedConsoleReporter.class.getName(), new Object[] {trimStackTrace});
            }
        }

        if (!disableXmlReport) {
            surefireBooter.addReport(XMLReporter.class.getName(), new Object[] {reportsDirectory, trimStackTrace});
        }
    }

    /**
     * @return SurefirePlugin Returns the skipExec.
     */
    public boolean isSkipExec() {
        return this.skipTests;
    }

    /**
     * @param skipExec the skipExec to set
     */
    public void setSkipExec(boolean skipExec) {
        this.skipTests = skipExec;
    }

    //TODO remove the part with ToolchainManager lookup once we depend on
    //3.0.9 (have it as prerequisite). Define as regular component field then.
    private Toolchain getToolchain() {
        Toolchain tc = null;
        try {
            if (session != null) //session is null in tests..
            {
                ToolchainManager toolchainManager =
                    (ToolchainManager)session.getContainer().lookup(ToolchainManager.ROLE);
                if (toolchainManager != null) {
                    tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
                }
            }
        } catch (ComponentLookupException componentLookupException) {
            //just ignore, could happen in pre-3.0.9 builds..
        }
        return tc;
    }
    
    protected Artifact getArtifact(String groupId, String artifactId) throws MojoExecutionException {
        Artifact artifact;
        VersionRange vr;
        try {
            vr = VersionRange.createFromVersionSpec(project.getVersion());
        } catch (InvalidVersionSpecificationException e1) {
            vr = VersionRange.createFromVersion(project.getVersion());
        }
        artifact = artifactFactory.createDependencyArtifact(groupId, artifactId, vr, "jar", null, Artifact.SCOPE_TEST);

        try {
            artifactResolver.resolve(artifact, remoteRepositories, localRepository);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Unable to resolve artifact.", e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Unable to find artifact.", e);
        }

        return artifact;
    }

    private void generateJar(File root, File jar, Manifest mf) throws IOException {
        getLog().info("Generating " + jar.toString());
        FileOutputStream fos = new FileOutputStream(jar);
        JarOutputStream jos = mf != null ? new JarOutputStream(fos, mf) : new JarOutputStream(fos);
        addDir(jos, root, root);
        jos.close();
    }

    /**
     * Convert the maven version into OSGi version 
     * @param mavenVersion
     * @return
     */
    static String osgiVersion(String mavenVersion) {
        ArtifactVersion ver = new DefaultArtifactVersion(mavenVersion);
        String qualifer = ver.getQualifier();
        if (qualifer != null) {
            StringBuffer buf = new StringBuffer(qualifer);
            for (int i = 0; i < buf.length(); i++) {
                char c = buf.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    // Keep as-is
                } else {
                    buf.setCharAt(i, '_');
                }
            }
            qualifer = buf.toString();
        }
        Version osgiVersion =
            new Version(ver.getMajorVersion(), ver.getMinorVersion(), ver.getIncrementalVersion(), qualifer);
        String version = osgiVersion.toString();
        return version;
    }

    private Manifest createMainBundle() throws IOException {
        File mf = new File(project.getBasedir(), "META-INF/MANIFEST.MF");
        Manifest manifest = null;
        if (mf.isFile()) {
            manifest = new Manifest(new FileInputStream(mf));
            String bundleName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME);
            if (bundleName != null) {
                return manifest;
            }
        }
        if (manifest == null) {
            manifest = new Manifest();
        }
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue(BUNDLE_MANIFESTVERSION, "2");
        attributes.putValue(BUNDLE_SYMBOLICNAME, project.getGroupId() + "." + project.getArtifactId());
        attributes.putValue(BUNDLE_NAME, project.getName());
        attributes.putValue(BUNDLE_VERSION, osgiVersion(project.getVersion()));
        attributes.putValue(Constants.DYNAMICIMPORT_PACKAGE, "*");
        return manifest;
    }

    private Manifest createTestFragment(Manifest mf) {
        // Create a manifest
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue(BUNDLE_MANIFESTVERSION, "2");
        String host = mf.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME);
        int sc = host.indexOf(';');
        if (sc != -1) {
            host = host.substring(0, sc);
        }
        attributes.putValue(BUNDLE_SYMBOLICNAME, host + ".tests");
        attributes.putValue(BUNDLE_NAME, mf.getMainAttributes().getValue(BUNDLE_NAME) + " Tests");
        attributes.putValue(BUNDLE_VERSION, mf.getMainAttributes().getValue(BUNDLE_VERSION));
        attributes.putValue(Constants.FRAGMENT_HOST, host + ";bundle-version=\""
            + mf.getMainAttributes().getValue(BUNDLE_VERSION)
            + "\"");
        // The main bundle may not have the dependency on JUNIT
        attributes.putValue(Constants.DYNAMICIMPORT_PACKAGE, "*");
        return manifest;
    }

    private void addDir(JarOutputStream jos, File root, File dir) throws IOException, FileNotFoundException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                addDir(jos, root, file);
            } else if (file.isFile()) {
                // getLog().info(file.toString());
                String uri = root.toURI().relativize(file.toURI()).toString();
                if ("META-INF/MANIFEST.MF".equals(uri)) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(uri);
                jos.putNextEntry(entry);
                byte[] buf = new byte[4096];
                FileInputStream in = new FileInputStream(file);
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
        }
    }    
}
