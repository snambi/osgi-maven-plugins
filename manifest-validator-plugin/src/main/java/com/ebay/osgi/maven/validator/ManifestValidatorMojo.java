package com.ebay.osgi.maven.validator;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.BundleException;

import com.ebay.osgi.maven.compiler.osgi.BundleResolver;

/**
 * Goal which touches a timestamp file.
 *
 * @goal validate
 * @phase process-sources
 * @requiresDependencyResolution compile
 */
public class ManifestValidatorMojo extends AbstractMojo{
	
    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;
    
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;
    

    public void execute() throws MojoExecutionException{

    	BundleResolver bundleresolver = new BundleResolver( getLog());
	    
    	// add the classpath ( jars and directories ) to BundleResolver
	    try {
			
	    	List<String> classpathElements = (List<String>) project.getCompileClasspathElements();
			
	    	for( String classpath : classpathElements){
	            try {
	                File cp = new File(classpath);
	                if (cp.exists()) {
	                	getLog().info("Bundle: " + cp.getAbsolutePath());
	                    bundleresolver.addBundle(cp);
	                }
	            } catch (BundleException e) {
	                getLog().error(e.getMessage(), e);
	            }
	    	}
	    	
	    	
	    	bundleresolver.resolveState();
	    	File outputDirectory = new File(project.getBuild().getOutputDirectory());
            BundleDescription b = bundleresolver.getBundleDescription(outputDirectory);
            if (b != null) {
                try {
                	bundleresolver.assertResolved(b);
                    getLog().info("OSGi bundle is resolved: " + b.getSymbolicName());
                } catch (BundleException e) {
                	bundleresolver.analyzeErrors(b);
                	String errorMessage = bundleresolver.reportErrors(b);
                    if(getLog().isDebugEnabled()) {
                    	getLog().debug(errorMessage);
                    }
                    throw new MojoExecutionException(errorMessage, e);
                    // FIXME: For now, only a warning is reported
                    // throw new CompilerException(e.getMessage(), e);
                    
                }
            }
			
		} catch (DependencyResolutionRequiredException e1) {
			e1.printStackTrace();
		} 
    }
}
