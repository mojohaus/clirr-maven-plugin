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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.module.xhtml.XhtmlSink;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.doxia.site.renderer.SiteRenderer;
import org.codehaus.plexus.util.StringInputStream;

import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Generate a report from the Clirr output.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @goal clirr
 */
public class ClirrReport
    extends AbstractClirrMojo
    implements MavenReport
{
    /**
     * Specifies the directory where the report will be generated
     *
     * @parameter default-value="${project.reporting.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    /**
     * @component
     */
    private SiteRenderer siteRenderer;

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

    public String getCategoryName()
    {
        return MavenReport.CATEGORY_PROJECT_REPORTS;
    }

    public void setReportOutputDirectory( File file )
    {
        outputDirectory = file;
    }

    public File getReportOutputDirectory()
    {
        return outputDirectory;
    }

    public boolean isExternalReport()
    {
        return false;
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        // TODO: push to a helper?
        Locale locale = Locale.getDefault();
        try
        {
            StringInputStream dummySiteDescriptor = new StringInputStream( "<project><body></body></project>" );
            XhtmlSink sink = siteRenderer.createSink( outputDirectory, getOutputName() + ".html",
                                                      outputDirectory.getAbsolutePath(), dummySiteDescriptor, "maven" );

            generate( sink, locale );

            siteRenderer.copyResources( outputDirectory.getAbsolutePath(), "maven" );
        }
        catch ( MavenReportException e )
        {
            throw new MojoExecutionException( "An error has occurred in " + getName( locale ) + " report generation.",
                                              e );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "An error has occurred in " + getName( locale ) + " report generation.",
                                              e );
        }
    }

    public void generate( Sink sink, Locale locale )
        throws MavenReportException
    {
        if ( !canGenerateReport() )
        {
            getLog().info( "Not generating report as there are no sources to compare" );
        }
        else
        {
            doReport( sink, locale );
        }
    }

    private void doReport( Sink sink, Locale locale )
        throws MavenReportException
    {
        Severity minSeverity = convertSeverity( this.minSeverity );
        ResourceBundle bundle = getBundle( locale );
        if ( minSeverity == null )
        {
            getLog().warn( bundle.getString( "report.clirr.error.invalid.minseverity" ) + ": '" + this
                .minSeverity + "'." );
        }

        if ( !htmlReport && xmlOutputFile == null && textOutputFile == null && !logResults )
        {
            getLog().error( bundle.getString( "report.clirr.error.noreports" ) );
        }
        else
        {
            ClirrDiffListener listener;
            try
            {
                listener = executeClirr( minSeverity );
            }
            catch ( MojoExecutionException e )
            {
                throw new MavenReportException( e.getMessage(), e );
            }
            catch ( MojoFailureException e )
            {
                throw new MavenReportException( e.getMessage() );
            }

            if ( htmlReport )
            {
                ClirrReportGenerator generator = new ClirrReportGenerator( sink, bundle, locale );

                generator.setEnableSeveritySummary( showSummary );

                generator.setMinSeverity( minSeverity );

                generator.generateReport( listener );
            }
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
        // TODO: improve - needs to at least check generated sources
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
