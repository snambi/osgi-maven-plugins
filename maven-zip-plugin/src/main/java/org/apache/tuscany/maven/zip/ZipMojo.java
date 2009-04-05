package org.apache.tuscany.maven.zip;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.ManifestException;

/**
 * Build an SCA ZIP contribution.
 *
 * Based on code from the Maven WAR plugin 2.0.2 by Emmanuel Venisse
 * 
 * @goal zip
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class ZipMojo
    extends AbstractZipMojo
{
    /**
     * The directory for the generated contribution.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private String outputDirectory;

    /**
     * The name of the generated contribution.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String contributionName;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be an attachment instead.
     *
     * @parameter
     */
    private String classifier;

    /**
     * The Jar archiver.
     *
     */
    private ZipArchiver Archiver = new ZipArchiver();


    /**
     * @component
     */
    private MavenProjectHelper projectHelper;

    /**
     * Whether this is the main artifact being built. Set to <code>false</code> if you don't want to install or
     * deploy it to the local repository instead of the default one in an execution.
     *
     * @parameter expression="${primaryArtifact}" default-value="true"
     */
    private boolean primaryArtifact;

    // ----------------------------------------------------------------------
    // Implementation
    // ----------------------------------------------------------------------

    /**
     * Overload this to produce a test-war, for example.
     */
    protected String getClassifier()
    {
        return classifier;
    }

    protected static File getFile( File basedir, String finalName, String classifier )
    {
        if ( classifier == null )
        {
            classifier = "";
        }
        else if ( classifier.trim().length() > 0 && !classifier.startsWith( "-" ) )
        {
            classifier = "-" + classifier;
        }

        return new File( basedir, finalName + classifier + ".zip" );
    }

    /**
     * Executes the Mojo on the current project.
     *
     * @throws MojoExecutionException if an error occured while building the SCA ZIP contribution
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        File File = getFile( new File( outputDirectory ), contributionName, classifier );

        try
        {
            performPackaging( File );
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoExecutionException( "Error assembling SCA ZIP: " + e.getMessage(), e );
        }
        catch ( ManifestException e )
        {
            throw new MojoExecutionException( "Error assembling SCA ZIP", e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error assembling SCA ZIP", e );
        }
        catch ( ArchiverException e )
        {
            throw new MojoExecutionException( "Error assembling SCA ZIP: " + e.getMessage(), e );
        }
    }

    /**
     * Generates the SCA ZIP contribution according to the <tt>mode</tt> attribute.
     *
     * @param File the target war file
     * @throws IOException
     * @throws ArchiverException
     * @throws ManifestException
     * @throws DependencyResolutionRequiredException
     *
     */
    private void performPackaging( File File )
        throws IOException, ArchiverException, ManifestException, DependencyResolutionRequiredException,
        MojoExecutionException, MojoFailureException
    {
        buildExplodedWebapp( getZipDirectory() );

        //generate war file
        getLog().info( "Generating SCA ZIP contribution " + File.getAbsolutePath() );

        MavenArchiver archiver = new MavenArchiver();

        archiver.setArchiver( Archiver );

        archiver.setOutputFile( File );

        Archiver.addDirectory( getZipDirectory(), getIncludes(), getExcludes() );

        // create archive
        archiver.createArchive( getProject(), archive );

        String classifier = this.classifier;
        if ( classifier != null )
        {
            projectHelper.attachArtifact( getProject(), "", classifier, File );
        }
        else
        {
            Artifact artifact = getProject().getArtifact();
            if ( primaryArtifact )
            {
                artifact.setFile( File );
            }
            else if ( artifact.getFile() == null || artifact.getFile().isDirectory() )
            {
                artifact.setFile( File );
            }
        }
    }
}
