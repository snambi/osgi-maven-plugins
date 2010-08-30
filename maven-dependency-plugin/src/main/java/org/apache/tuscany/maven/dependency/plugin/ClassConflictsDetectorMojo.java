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
package org.apache.tuscany.maven.dependency.plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * A Maven plugin that check for class conflicts for the dependency jars
 * 
 * @goal check-class-conflicts
 * @phase validate
 * @requiresDependencyResolution test
 * @description check for class conflicts for the dependency jars
 */
public class ClassConflictsDetectorMojo extends AbstractMojo {

    /**
     * The project to build the bundle for.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The root directory to scan the jar files. If not set, we'll check the project dependencies
     *
     * @parameter 
     */
    private File root;

    /**
     * @parameter default-value="false"
     */
    private boolean ignoreTestScope;

    /**
     * @parameter default-value="true"
     */
    private boolean verbose;

    /**
     * @parameter default-value="false"
     */
    private boolean skip;

    /**
     * @parameter default-value="false"
     */
    private boolean failOnConflicts;

    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }

        Log log = getLog();
        LogWrapper wrapper = new MavenLogWrapper(log);

        try {
            int conflicts = 0;
            if (root != null) {
                conflicts = ClassPathHellDetector.check(root, wrapper, verbose || wrapper.isDebugEnabled());
            } else {
                conflicts = ClassPathHellDetector.check(getJarFiles(log), wrapper, verbose || wrapper.isDebugEnabled());
            }
            if (conflicts >= 1 && failOnConflicts) {
                throw new MojoExecutionException("Conflicting/overlapping classes are found");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    private Set<File> getJarFiles(Log log) {
        Set<File> files = new HashSet<File>();
        for (Object a : project.getArtifacts()) {
            Artifact artifact = (Artifact)a;
            if (ignoreTestScope && Artifact.SCOPE_TEST.equals(artifact.getScope())) {
                continue;
            }
            if (artifact.isResolved()) {
                if ("jar".equals(artifact.getType())) {
                    files.add(artifact.getFile());
                }
            }
        }
        return files;
    }

    private static class MavenLogWrapper implements LogWrapper {
        private Log log;

        public MavenLogWrapper(Log log) {
            super();
            this.log = log;
        }

        public boolean isDebugEnabled() {
            return log.isDebugEnabled();
        }

        public void debug(String msg) {
            log.debug(msg);

        }

        public void info(String msg) {
            log.info(msg);
        }

        public void warn(String msg) {
            log.warn(msg);
        }

        public void error(String msg) {
            log.error(msg);
        }

        public boolean isInfoEnabled() {
            return log.isInfoEnabled();
        }

        public boolean isWarnEnabled() {
            return log.isWarnEnabled();
        }

        public boolean isErrorEnabled() {
            return log.isErrorEnabled();
        }

    }

}
