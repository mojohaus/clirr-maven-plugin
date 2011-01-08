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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.site.decoration.Body;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.Skin;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Generate a report from the Clirr output.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @goal clirr
 * @execute phase="compile"
 */
public class ClirrReport
    extends AbstractClirrMojo
    implements MavenReport
{
    /**
     * Specifies the directory where the report will be generated.
     *
     * @parameter default-value="${project.reporting.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    /**
     * @component
     */
    private Renderer siteRenderer;

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
     * Link the violation line numbers to the source Xref. This will create links
     * if the JXR Plugin is being used.
     *
     * @parameter expression="${linkXRef}" default-value="true"
     */
    private boolean linkXRef;

    /**
     * Location of the Xrefs to link to.
     *
     * @parameter expression="${xrefLocation}" default-value="${project.build.directory}/site/xref"
     */
    private File xrefLocation;

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

    private File getSkinArtifactFile()
        throws MojoFailureException, MojoExecutionException
    {
        Skin skin = Skin.getDefaultSkin();

        String version = skin.getVersion();
        Artifact artifact;
        try
        {
            if ( version == null )
            {
                version = Artifact.RELEASE_VERSION;
            }
            VersionRange versionSpec = VersionRange.createFromVersionSpec( version );
            artifact = factory.createDependencyArtifact( skin.getGroupId(), skin.getArtifactId(), versionSpec, "jar",
                                                         null, null );

            resolver.resolve( artifact, project.getRemoteArtifactRepositories(), localRepository );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new MojoFailureException( "The skin version '" + version + "' is not valid: " + e.getMessage() );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Unable to find skin", e );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new MojoFailureException( "The skin does not exist: " + e.getMessage() );
        }

        return artifact.getFile();
    }

    protected void doExecute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !canGenerateReport() )
        {
            return;
        }

        // TODO: push to a helper? Could still be improved by taking more of the site information from the site plugin
        try
        {
            DecorationModel model = new DecorationModel();
            model.setBody( new Body() );
            Map attributes = new HashMap();
            attributes.put( "outputEncoding", "UTF-8" );
            Locale locale = Locale.getDefault();
            SiteRenderingContext siteContext = siteRenderer.createContextForSkin( getSkinArtifactFile(), attributes,
                                                                                  model, getName( locale ), locale );

            RenderingContext context = new RenderingContext( outputDirectory, getOutputName() + ".html" );

            SiteRendererSink sink = new SiteRendererSink( context );
            generate( sink, locale );

            outputDirectory.mkdirs();

            Writer writer = new FileWriter( new File( outputDirectory, getOutputName() + ".html" ) );

            siteRenderer.generateDocument( writer, sink, siteContext );

            siteRenderer.copyResources( siteContext, new File( project.getBasedir(), "src/site/resources" ),
                                        outputDirectory );
        }
        catch ( RendererException e )
        {
            throw new MojoExecutionException(
                "An error has occurred in " + getName( Locale.ENGLISH ) + " report generation.", e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException(
                "An error has occurred in " + getName( Locale.ENGLISH ) + " report generation.", e );
        }
        catch ( MavenReportException e )
        {
            throw new MojoExecutionException(
                "An error has occurred in " + getName( Locale.ENGLISH ) + " report generation.", e );
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
            catch ( MissingPreviousException e )
            {
                getLog().error( bundle.getString( "report.clirr.error.nopredecessor" ) );
                return;
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

                generator.setCurrentVersion( project.getVersion() );

                if ( comparisonVersion != null )
                {
                    generator.setComparisonVersion( comparisonVersion );
                }

                if ( linkXRef )
                {
                    String relativePath =
                        PathTool.getRelativePath( outputDirectory.getAbsolutePath(), xrefLocation.getAbsolutePath() );
                    if ( StringUtils.isEmpty( relativePath ) )
                    {
                        relativePath = ".";
                    }
                    relativePath = relativePath + "/" + xrefLocation.getName();
                    if ( xrefLocation.exists() )
                    {
                        // XRef was already generated by manual execution of a lifecycle binding
                        generator.setXrefLocation( relativePath );
                    }
                    else
                    {
                        // Not yet generated - check if the report is on its way
                        for ( Iterator reports = project.getReportPlugins().iterator(); reports.hasNext(); )
                        {
                            ReportPlugin report = (ReportPlugin) reports.next();

                            String artifactId = report.getArtifactId();
                            if ( "maven-jxr-plugin".equals( artifactId ) || "jxr-maven-plugin".equals( artifactId ) )
                            {
                                generator.setXrefLocation( relativePath );
                            }
                        }
                    }

                    if ( generator.getXrefLocation() == null )
                    {
                        getLog().warn( "Unable to locate Source XRef to link to - DISABLED" );
                    }
                }
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
        try
        {
            return canGenerate();
        }
        catch ( MojoFailureException e )
        {
            getLog().error( "Can't generate Clirr report: " + e.getMessage() );
            return false;
        }
        catch ( MojoExecutionException e )
        {
            getLog().error( "Can't generate Clirr report: " + e.getMessage(), e );
            return false;
        }
    }

    // eventually, we must replace this with the o.a.m.d.s.Sink class as a parameter
    public void generate( org.codehaus.doxia.sink.Sink sink, Locale locale )
        throws MavenReportException
    {
        generate( (Sink) sink, locale );

    }
}
