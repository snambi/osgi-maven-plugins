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
package org.apache.tuscany.maven.plugin;

import java.io.File;
import java.net.MalformedURLException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.tuscany.sca.node.Contribution;
import org.apache.tuscany.sca.node.Node;
import org.apache.tuscany.sca.node.NodeFactory;

/**
 * Maven Mojo to run the SCA contribution project in Tuscany.
 * Invoked with "mvn tuscany:run"
 * 
 * @goal run
 * @requiresDependencyResolution runtime
 * @execute phase="package"
 * @description Runs Tuscany directly from a SCA conribution maven project
 */
public class TuscanyRunMojo extends AbstractMojo {

    /**
     * The project artifactId.
     * 
     * @parameter expression="${project.artifactId}"
     * @required
     */
    protected String artifactId;

    /**
     * The project packaging.
     * 
     * @parameter expression=".${project.packaging}"
     * @required
     */
    protected String packaging;

    /**
     * The project build output directory
     * 
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    protected File buildDirectory;

    /**
     * The project build output directory
     * 
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    protected File finalName;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Starting Tuscany Runtime...");

        Contribution contribution = getProjectContribution();
        // TODO allow specifying dependent contributions

        Node node = NodeFactory.newInstance().createNode(null, contribution);
        node.start();

        waitForShutdown(node, getLog());

    }

    protected Contribution getProjectContribution() throws MojoExecutionException {
        try {

            String contribution =
                new File(buildDirectory.getParent(), finalName.getName() + packaging).toURI().toURL().toString();
            getLog().info("Project contribution: " + contribution);

            return new Contribution(contribution, contribution);

        } catch (MalformedURLException e) {
            throw new MojoExecutionException("", e);
        }
    }

    protected void waitForShutdown(Node node, Log log) {
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(node, log));
        synchronized (this) {
            try {
                log.info("Ctrl-C to end...");
                this.wait();
            } catch (InterruptedException e) {
                log.error(e);
            }
        }
    }

    protected static class ShutdownThread extends Thread {

        private Node node;
        private Log log;

        public ShutdownThread(Node node, Log log) {
            super();
            this.node = node;
            this.log = log;
        }

        @Override
        public void run() {
            try {

                log.info("Stopping Tuscany Runtime...");
                node.stop();
                node.destroy();

            } catch (Exception e) {
                log.error(e);
            }
        }
    }
}
