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

import net.sf.clirr.core.Severity;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.site.renderer.SiteRenderer;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Generate a report from the Clirr output.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @goal clirr
 * @requiresDependencyResolution compile
 * @execute phase="compile"
 */
public class ClirrReport
    extends AbstractMavenReport
{
    /**
     * Specifies the directory where the report will be generated
     *
     * @parameter default-value="${project.reporting.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private SiteRenderer siteRenderer;

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
     * Show only messages of this severity or higher. Valid values are <code>info</code>, <code>warning</code> and <code>error</code>.
     *
     * @parameter expression="${minSeverity}" default-value="warning"
     */
    private String minSeverity;

    /**
     * Whether to show the summary of the number of errors, warnings and informational messages.
     *
     * @parameter expression="${showSummary}" default-value="true"
     */
    private boolean showSummary;

    /**
     * Whether to render the HTML report or not.
     *
     * @parameter expression="${htmlReport}" default-value="true"
     */
    private boolean htmlReport;

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

    protected SiteRenderer getSiteRenderer()
    {
        return siteRenderer;
    }

    protected String getOutputDirectory()
    {
        return outputDirectory.getAbsolutePath();
    }

    protected MavenProject getProject()
    {
        return project;
    }

    protected void executeReport( Locale locale )
        throws MavenReportException
    {
        if ( !canGenerateReport() )
        {
            getLog().info( "Not generating report as there are no sources to compare" );
        }
        else
        {
            doReport( locale );
        }
    }

    private void doReport( Locale locale )
        throws MavenReportException
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

        Severity minSeverity = convertSeverity( this.minSeverity );
        ResourceBundle bundle = getBundle( locale );
        if ( minSeverity == null )
        {
            getLog().warn( bundle.getString( "report.clirr.error.invalid.minseverity" ) + ": '" + this
                .minSeverity + "'." );
        }
        else
        {
            executor.setMinSeverity( minSeverity );
        }

        if ( !htmlReport && xmlOutputFile == null && textOutputFile == null && !logResults )
        {
            getLog().error( bundle.getString( "report.clirr.error.noreports" ) );
        }
        else
        {
            try
            {
                executor.execute( project, resolver, metadataSource, localRepository, factory, getLog() );
            }
            catch ( MojoExecutionException e )
            {
                throw new MavenReportException( e.getMessage(), e );
            }
            catch ( MojoFailureException e )
            {
                throw new MavenReportException( e.getMessage() );
            }
        }

        if ( htmlReport )
        {
            ClirrReportGenerator generator = new ClirrReportGenerator( getSink(), bundle, locale );

            generator.setEnableSeveritySummary( showSummary );

            generator.setMinSeverity( minSeverity );

            generator.generateReport( executor.getListener() );
        }
    }

    public String getDescription( Locale locale )
    {
        return getBundle( locale ).getString( "report.clirr.description" );
    }

    public String getName( Locale locale )
    {
        return getBundle( locale ).getString( "report.clirr.name" );
    }

    public String getOutputName()
    {
        return "clirr-report";
    }

    private ResourceBundle getBundle( Locale locale )
    {
        return ResourceBundle.getBundle( "clirr-report", locale, getClass().getClassLoader() );
    }

    public boolean canGenerateReport()
    {
        return new File( project.getBuild().getSourceDirectory() ).exists();
    }

    private static Severity convertSeverity( String minSeverity )
    {
        Severity s;
        if ( "info".equals( minSeverity ) )
        {
            s = Severity.INFO;
        }
        else if ( "warning".equals( minSeverity ) )
        {
            s = Severity.WARNING;
        }
        else if ( "error".equals( minSeverity ) )
        {
            s = Severity.ERROR;
        }
        else
        {
            s = null;
        }
        return s;
    }
}
