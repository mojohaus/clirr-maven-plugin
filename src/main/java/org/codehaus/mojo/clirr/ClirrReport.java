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

import net.sf.clirr.core.Checker;
import net.sf.clirr.core.CheckerException;
import net.sf.clirr.core.ClassSelector;
import net.sf.clirr.core.PlainDiffListener;
import net.sf.clirr.core.Severity;
import net.sf.clirr.core.XmlDiffListener;
import net.sf.clirr.core.internal.bcel.BcelJavaType;
import net.sf.clirr.core.internal.bcel.BcelTypeArrayBuilder;
import net.sf.clirr.core.spi.JavaType;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassLoaderRepository;
import org.apache.bcel.util.Repository;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.site.renderer.SiteRenderer;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

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

    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

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
            return;
        }

        // Find the previous version JAR and resolve it
        try
        {
            VersionRange range = VersionRange.createFromVersionSpec( comparisonVersion );

            Artifact previousArtifact = factory.createDependencyArtifact( project.getGroupId(), project.getArtifactId(),
                                                                          range, project.getPackaging(), null,
                                                                          Artifact.SCOPE_COMPILE );

            if ( !previousArtifact.getVersionRange().isSelectedVersionKnown( previousArtifact ) )
            {
                getLog().debug( "Searching for versions in range: " + previousArtifact.getVersionRange() );
                List availableVersions = metadataSource.retrieveAvailableVersions( previousArtifact, localRepository,
                                                                                   project.getRemoteArtifactRepositories() );
                ArtifactVersion version = range.matchVersion( availableVersions );
                previousArtifact.selectVersion( version.toString() );
            }

            getLog().info( "Comparing to version: " + previousArtifact.getVersion() );

            // TODO: better way? Can't use previousArtifact as the originatingArtifact, it culls everything out
            //  perhaps resolve the artifact itself (not the pom artifact), then load th epom and get dependencies
            Artifact dummy = factory.createProjectArtifact( "dummy", "dummy", "1.0" );
            ArtifactResolutionResult result = resolver.resolveTransitively( Collections.singleton( previousArtifact ),
                                                                            dummy, localRepository,
                                                                            project.getRemoteArtifactRepositories(),
                                                                            metadataSource, null );

            ClassLoader origDepCL = createClassLoader( result.getArtifacts(), previousArtifact );
            ClassLoader currentDepCL = createClassLoader( project.getArtifacts(), null );

            // a selector that selects everything
            // TODO: filter classes?
            ClassSelector classSelector = new ClassSelector( ClassSelector.MODE_UNLESS );

            File file = new File( localRepository.getBasedir(), localRepository.pathOf( previousArtifact ) );
            JavaType[] origClasses = BcelTypeArrayBuilder.createClassSet( new File[]{file}, origDepCL, classSelector );

            JavaType[] currentClasses = createClassSet( classesDirectory, currentDepCL, classSelector );

            // Create a Clirr checker and execute
            Checker checker = new Checker();

            List listeners = new ArrayList();

            ClirrDiffListener listener = null;
            if ( htmlReport )
            {
                listener = new ClirrDiffListener();

                listeners.add( listener );
            }

            if ( xmlOutputFile != null )
            {
                listeners.add( new XmlDiffListener( xmlOutputFile.getAbsolutePath() ) );
            }

            if ( textOutputFile != null )
            {
                listeners.add( new PlainDiffListener( textOutputFile.getAbsolutePath() ) );
            }

            if ( listeners.isEmpty() )
            {
                getLog().error( "No listeners were configured!" );
            }
            else
            {
                Severity minSeverity = ClirrDiffListener.convertSeverity( this.minSeverity );
                if ( minSeverity == null )
                {
                    getLog().warn( "Invalid minimum severity: '" + this.minSeverity + "', ignoring." );
                }

                checker.addDiffListener( new DelegatingListener( listeners, minSeverity ) );

                checker.reportDiffs( origClasses, currentClasses );

                if ( htmlReport )
                {
                    ClirrReportGenerator generator = new ClirrReportGenerator( getSink(), getBundle( locale ), locale );

                    generator.setEnableSeveritySummary( showSummary );

                    generator.setMinSeverity( minSeverity );

                    generator.generateReport( listener );
                }
            }
        }
        catch ( ArtifactResolutionException e )
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch ( ArtifactNotFoundException e )
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch ( MalformedURLException e )
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch ( IOException e )
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch ( ArtifactMetadataRetrievalException e )
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch ( InvalidVersionSpecificationException e )
        {
            // TODO
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public static JavaType[] createClassSet( File classes, ClassLoader thirdPartyClasses, ClassSelector classSelector )
        throws MalformedURLException
    {
        ClassLoader classLoader = new URLClassLoader( new URL[]{classes.toURL()}, thirdPartyClasses );

        Repository repository = new ClassLoaderRepository( classLoader );

        List selected = new ArrayList();

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( classes );
        scanner.setIncludes( new String[]{"**/*.class"} );
        scanner.scan();

        String[] files = scanner.getIncludedFiles();

        for ( int i = 0; i < files.length; i++ )
        {
            File f = new File( classes, files[i] );
            JavaClass clazz = extractClass( f, repository );
            if ( classSelector.isSelected( clazz ) )
            {
                selected.add( new BcelJavaType( clazz ) );
                repository.storeClass( clazz );
            }
        }

        JavaType[] ret = new JavaType[selected.size()];
        selected.toArray( ret );
        return ret;
    }

    private static JavaClass extractClass( File f, Repository repository )
        throws CheckerException
    {
        InputStream is = null;
        try
        {
            is = new FileInputStream( f );

            ClassParser parser = new ClassParser( is, f.getName() );
            JavaClass clazz = parser.parse();
            clazz.setRepository( repository );
            return clazz;
        }
        catch ( IOException ex )
        {
            throw new CheckerException( "Cannot read " + f, ex );
        }
        finally
        {
            IOUtil.close( is );
        }
    }

    private static ClassLoader createClassLoader( Set artifacts, Artifact previousArtifact )
        throws MalformedURLException
    {
        URLClassLoader cl = null;
        if ( !artifacts.isEmpty() )
        {
            List urls = new ArrayList( artifacts.size() );
            for ( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if ( !artifact.equals( previousArtifact ) )
                {
                    urls.add( artifact.getFile().toURL() );
                }
            }
            if ( !urls.isEmpty() )
            {
                cl = new URLClassLoader( (URL[]) urls.toArray( EMPTY_URL_ARRAY ) );
            }
        }
        return cl;
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
}
