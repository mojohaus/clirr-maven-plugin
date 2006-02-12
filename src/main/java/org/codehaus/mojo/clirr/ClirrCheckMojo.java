package org.codehaus.mojo.clirr;

/*
 * Copyright 2006 The Codehaus
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

import net.sf.clirr.core.ApiDifference;
import net.sf.clirr.core.Severity;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Iterator;

/**
 * Perform a violation check against the last checkstyle run to see if there are any violations.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @goal check
 * @phase verify
 * @execute phase="compile"
 * @requiresDependencyResolution compile
 * @todo i18n
 */
public class ClirrCheckMojo
    extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * @component
     */
    private ArtifactFactory factory;

    /**
     * @parameter default-value="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    /**
     * The classes of this project to compare the last release against.
     *
     * @parameter default-value="${project.build.outputDirectory}
     */
    private File classesDirectory;

    /**
     * Version to compare the current code against.
     *
     * @parameter expression="${comparisonVersion}" default-value="(,${project.version})"
     */
    private String comparisonVersion;

    /**
     * A text output file to render to. If omitted, no output is rendered to a text file.
     *
     * @parameter expression="${textOutputFile}"
     */
    private File textOutputFile;

    /**
     * An XML file to render to. If omitted, no output is rendered to an XML file.
     *
     * @parameter expression="${xmlOutputFile}"
     */
    private File xmlOutputFile;

    /**
     * A list of classes to include. Anything not included is excluded. If omitted, all are assumed to be included.
     * Values are specified in path pattern notation, e.g. <code>org/codehaus/mojo/**</code>.
     *
     * @parameter
     */
    private String[] includes;

    /**
     * A list of classes to exclude. These classes are excluded from the list of classes that are included.
     * Values are specified in path pattern notation, e.g. <code>org/codehaus/mojo/**</code>.
     *
     * @parameter
     */
    private String[] excludes;

    /**
     * Whether to log the results to the console or not.
     *
     * @parameter expression="${logResults}" default-value="false"
     */
    private boolean logResults;

    /**
     * Whether to fail on errors.
     *
     * @parameter expression="${failOnError}" default-value="true"
     */
    private boolean failOnError;

    /**
     * Whether to fail on warnings.
     *
     * @parameter expression="${failOnWarning}" default-value="false"
     */
    private boolean failOnWarning;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        ClirrExecutor executor = new ClirrExecutor();
        if ( logResults )
        {
            executor.setLog( getLog() );
        }
        executor.setTextOutputFile( textOutputFile );
        executor.setXmlOutputFile( xmlOutputFile );
        executor.setIncludes( includes );
        executor.setExcludes( excludes );
        executor.setComparisonVersion( comparisonVersion );
        executor.setClassesDirectory( classesDirectory );
        executor.execute( project, resolver, metadataSource, localRepository, factory, getLog() );

        ClirrDiffListener listener = executor.getListener();
        int errorCount = listener.getSeverityCount( Severity.ERROR );
        if ( failOnError && errorCount > 0 )
        {
            log( listener, Severity.ERROR );
            throw new MojoFailureException( "There were " + errorCount + " errors" );
        }

        int warningCount = listener.getSeverityCount( Severity.WARNING );
        if ( failOnWarning && errorCount > 0 )
        {
            log( listener, Severity.WARNING );
            throw new MojoFailureException( "There were " + warningCount + " errors" );
        }

        int infoCount = listener.getSeverityCount( Severity.INFO );
        getLog().info( "Succeeded with " + errorCount + " errors; " + warningCount + " warnings; and " + infoCount +
            " other changes" );
    }

    private void log( ClirrDiffListener listener, Severity severity )
    {
        if ( !logResults )
        {
            LogDiffListener l = new LogDiffListener( getLog() );
            for ( Iterator i = listener.getApiDifferences().iterator(); i.hasNext(); )
            {
                ApiDifference difference = (ApiDifference) i.next();
                if ( difference.getMaximumSeverity().equals( severity ) )
                {
                    l.reportDiff( difference );
                }
            }
        }
    }
}
