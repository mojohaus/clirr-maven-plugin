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
import net.sf.clirr.core.Checker;
import net.sf.clirr.core.ClassFilter;
import net.sf.clirr.core.PlainDiffListener;
import net.sf.clirr.core.Severity;
import net.sf.clirr.core.XmlDiffListener;
import net.sf.clirr.core.internal.bcel.BcelTypeArrayBuilder;
import net.sf.clirr.core.spi.JavaType;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.i18n.I18N;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Check for compatibility between two arbitrary artifact sets.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:jmcconnell@apache.org">Jesse McConnell</a>
 * @goal check-arbitrary
 * @phase verify
 * @execute phase="compile"
 */
public class ClirrArbitraryCheckMojo
    extends AbstractClirrMojo
{
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

    /**
     * @component
     */
    private I18N i18n;

    /**
     * List of artifacts to serve as a baseline to compare against.
     * 
     * @parameter
     * @required
     */
    protected ArtifactSpecification[] oldComparisonArtifacts;
    
    /**
     * List of artifacts to compare to baseline.
     * 
     * @parameter
     * @required
     */
    protected ArtifactSpecification[] newComparisonArtifacts;
    
    protected void doExecute()
        throws MojoExecutionException, MojoFailureException
    {   
        if ( oldComparisonArtifacts == null || oldComparisonArtifacts.length == 0 )
        {
            getLog().info( "Missing required oldComparisonArtifacts" ); 
        }

        if ( newComparisonArtifacts == null || newComparisonArtifacts.length == 0 )
        {
            getLog().info( "Missing required newComparisonArtifacts" ); 
        }

        
        ClirrDiffListener listener;
        try
        {
            listener = executeClirr( Severity.INFO );
        }
        catch ( MissingPreviousException e )
        {
            getLog().debug( e );
            getLog().info( "No previous version was found. Use 'comparisonArtifacts'"
                    + " for explicit configuration if you think this is wrong." );
            return;
        }

        Locale locale = Locale.getDefault();

        int errorCount = listener.getSeverityCount( Severity.ERROR );
        if ( failOnError && errorCount > 0 )
        {
            log( listener, Severity.ERROR );
            String message;
            if ( errorCount > 1 )
            {
                String[] args = new String[]{String.valueOf( errorCount )};
                message = i18n.format( "clirr-report", locale, "check.clirr.failure.errors", args );
            }
            else
            {
                message = i18n.getString( "clirr-report", locale, "check.clirr.failure.error" );
            }
            throw new MojoFailureException( message );
        }

        int warningCount = listener.getSeverityCount( Severity.WARNING );
        if ( failOnWarning && errorCount > 0 )
        {
            log( listener, Severity.WARNING );
            String message;
            if ( errorCount > 1 )
            {
                String[] args = new String[]{String.valueOf( errorCount )};
                message = i18n.format( "clirr-report", locale, "check.clirr.failure.warnings", args );
            }
            else
            {
                message = i18n.getString( "clirr-report", locale, "check.clirr.failure.warning" );
            }
            throw new MojoFailureException( message );
        }

        int infoCount = listener.getSeverityCount( Severity.INFO );
        String[] args =
            new String[]{String.valueOf( errorCount ), String.valueOf( warningCount ), String.valueOf( infoCount )};
        getLog().info( i18n.format( "clirr-report", locale, "check.clirr.success", args ) );
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
    
    protected JavaType[] resolveClasses( ArtifactSpecification[] artifacts, ClassFilter classFilter )
        throws MojoFailureException, MojoExecutionException
    {
        final Set artifactSet;

        artifactSet = resolveArtifacts( artifacts );
        Artifact a = null;
        for ( Iterator iter = artifactSet.iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();
            if ( a == null )
            {
                a = artifact;
            }
            getLog().debug( "Comparing to " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                                + artifact.getVersion() + ":" + artifact.getClassifier() + ":" + artifact.getType() );
        }

        try
        {
            for ( Iterator iter = artifactSet.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                resolver.resolve( artifact, project.getRemoteArtifactRepositories(), localRepository );
            }

            final List dependencies = getTransitiveDependencies( artifactSet );

            ClassLoader origDepCL = createClassLoader( dependencies, artifactSet );
            final File[] files = new File[artifactSet.size()];
            int i = 0;
            for ( Iterator iter = artifactSet.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                files[i++] = new File( localRepository.getBasedir(), localRepository.pathOf( artifact ) );
            }
            return BcelTypeArrayBuilder.createClassSet( files, origDepCL, classFilter );
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( "Failed to build project for previous artifact: " + e.getMessage(), e );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MissingPreviousException( "Error resolving previous version: " + e.getMessage(), e );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new MojoExecutionException( "Error finding previous version: " + e.getMessage(), e );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error creating classloader for previous version's classes", e );
        }
    }

    protected ClirrDiffListener executeClirr( Severity minSeverity )
        throws MojoExecutionException, MojoFailureException
    {
        ClirrDiffListener listener = new ClirrDiffListener();

        ClassFilter classFilter = new ClirrClassFilter( includes, excludes );

        JavaType[] origClasses = resolveClasses( oldComparisonArtifacts, classFilter );

        JavaType[] currentClasses = resolveClasses( newComparisonArtifacts, classFilter );

        // Create a Clirr checker and execute
        Checker checker = new Checker();

        List listeners = new ArrayList();

        listeners.add( listener );

        if ( xmlOutputFile != null )
        {
            try
            {
                listeners.add( new XmlDiffListener( xmlOutputFile.getAbsolutePath() ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error adding '" + xmlOutputFile + "' for output: " + e.getMessage(),
                                                  e );
            }
        }

        if ( textOutputFile != null )
        {
            try
            {
                listeners.add( new PlainDiffListener( textOutputFile.getAbsolutePath() ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error adding '" + textOutputFile + "' for output: " + e.getMessage(),
                                                  e );
            }
        }

        if ( logResults )
        {
            listeners.add( new LogDiffListener( getLog() ) );
        }

        checker.addDiffListener( new DelegatingListener( listeners, minSeverity ) );

        checker.reportDiffs( origClasses, currentClasses );

        return listener;
    }

}
