package org.codehaus.mojo.clirr;

/*
 * Copyright 2006 The Apache Software Foundation.
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
import net.sf.clirr.core.ClassFilter;
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
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
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
import java.util.Set;

/**
 * Base parameters for Clirr check and report.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @todo i18n exceptions, log messages
 * @requiresDependencyResolution compile
 * @execute phase="compile"
 */
public abstract class AbstractClirrMojo
    extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @component
     */
    protected ArtifactResolver resolver;

    /**
     * @component
     */
    protected ArtifactFactory factory;

    /**
     * @parameter default-value="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * @component
     */
    protected ArtifactMetadataSource metadataSource;

    /**
     * The classes of this project to compare the last release against.
     *
     * @parameter default-value="${project.build.outputDirectory}
     */
    protected File classesDirectory;

    /**
     * Version to compare the current code against.
     *
     * @parameter expression="${comparisonVersion}" default-value="(,${project.version})"
     */
    protected String comparisonVersion;

    /**
     * A text output file to render to. If omitted, no output is rendered to a text file.
     *
     * @parameter expression="${textOutputFile}"
     */
    protected File textOutputFile;

    /**
     * An XML file to render to. If omitted, no output is rendered to an XML file.
     *
     * @parameter expression="${xmlOutputFile}"
     */
    protected File xmlOutputFile;

    /**
     * A list of classes to include. Anything not included is excluded. If omitted, all are assumed to be included.
     * Values are specified in path pattern notation, e.g. <code>org/codehaus/mojo/**</code>.
     *
     * @parameter
     */
    protected String[] includes;

    /**
     * A list of classes to exclude. These classes are excluded from the list of classes that are included.
     * Values are specified in path pattern notation, e.g. <code>org/codehaus/mojo/**</code>.
     *
     * @parameter
     */
    protected String[] excludes;

    /**
     * Whether to log the results to the console or not.
     *
     * @parameter expression="${logResults}" default-value="false"
     */
    protected boolean logResults;

    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

    /**
     * @parameter expression="${executedProject}"
     */
    private MavenProject executedProject;

    public ClirrDiffListener executeClirr()
        throws MojoExecutionException, MojoFailureException
    {
        return executeClirr( null );
    }

    protected ClirrDiffListener executeClirr( Severity minSeverity )
        throws MojoExecutionException, MojoFailureException
    {
        ClirrDiffListener listener = new ClirrDiffListener();

        ClassFilter classFilter = new ClirrClassFilter( includes, excludes );

        JavaType[] origClasses = resolvePreviousReleaseClasses( classFilter );

        JavaType[] currentClasses = resolveCurrentClasses( classFilter );

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

    private JavaType[] resolveCurrentClasses( ClassFilter classFilter )
        throws MojoExecutionException
    {
        try
        {
            ClassLoader currentDepCL = createClassLoader( project.getArtifacts(), null );
            return createClassSet( classesDirectory, currentDepCL, classFilter );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error creating classloader for current classes", e );
        }
    }

    private JavaType[] resolvePreviousReleaseClasses( ClassFilter classFilter )
        throws MojoFailureException, MojoExecutionException
    {
        Artifact previousArtifact = getComparisonArtifact();

        try
        {
            getLog().info( "Comparing to version: " + previousArtifact.getVersion() );

            // TODO: better way? Can't use previousArtifact as the originatingArtifact, it culls everything out
            //  perhaps resolve the artifact itself (not the pom artifact), then load th epom and get dependencies
            Artifact dummy = factory.createProjectArtifact( "dummy", "dummy", "1.0" );
            ArtifactResolutionResult result = resolver.resolveTransitively( Collections.singleton( previousArtifact ),
                                                                            dummy, localRepository,
                                                                            project.getRemoteArtifactRepositories(),
                                                                            metadataSource, null );

            ClassLoader origDepCL = createClassLoader( result.getArtifacts(), previousArtifact );
            File file = new File( localRepository.getBasedir(), localRepository.pathOf( previousArtifact ) );
            return BcelTypeArrayBuilder.createClassSet( new File[]{file}, origDepCL, classFilter );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Error resolving previous version: " + e.getMessage(), e );
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

    private Artifact getComparisonArtifact()
        throws MojoFailureException, MojoExecutionException
    {
        // Find the previous version JAR and resolve it, and it's dependencies
        VersionRange range;
        try
        {
            range = VersionRange.createFromVersionSpec( comparisonVersion );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new MojoFailureException( "Invalid comparision version: " + e.getMessage() );
        }

        Artifact previousArtifact;
        try
        {
            previousArtifact = factory.createDependencyArtifact( project.getGroupId(), project.getArtifactId(), range,
                                                                 project.getPackaging(), null, Artifact.SCOPE_COMPILE );

            if ( !previousArtifact.getVersionRange().isSelectedVersionKnown( previousArtifact ) )
            {
                getLog().debug( "Searching for versions in range: " + previousArtifact.getVersionRange() );
                List availableVersions = metadataSource.retrieveAvailableVersions( previousArtifact, localRepository,
                                                                                   project.getRemoteArtifactRepositories() );
                ArtifactVersion version = range.matchVersion( availableVersions );
                if ( version != null )
                {
                    previousArtifact.selectVersion( version.toString() );
                }
            }
        }
        catch ( OverConstrainedVersionException e1 )
        {
            throw new MojoFailureException( "Invalid comparision version: " + e1.getMessage() );
        }
        catch ( ArtifactMetadataRetrievalException e11 )
        {
            throw new MojoExecutionException( "Error determining previous version: " + e11.getMessage(), e11 );
        }
        catch ( ArtifactResolutionException e12 )
        {
            throw new MojoExecutionException( "Error resolving previous version: " + e12.getMessage(), e12 );
        }

        if ( previousArtifact.getVersion() == null )
        {
            getLog().info( "Unable to find a previous version of the project in the repository" );
        }

        return previousArtifact;
    }

    public static JavaType[] createClassSet( File classes, ClassLoader thirdPartyClasses, ClassFilter classFilter )
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
            if ( classFilter.isSelected( clazz ) )
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

    protected boolean canGenerate()
        throws MojoFailureException, MojoExecutionException
    {
        boolean sources = false;

        for ( Iterator i = executedProject.getCompileSourceRoots().iterator(); i.hasNext() && !sources; )
        {
            String root = (String) i.next();
            if ( new File( root ).exists() )
            {
                sources = true;
            }
        }

        if ( !sources )
        {
            return false;
        }
        else
        {
            Artifact previousArtifact = getComparisonArtifact();
            return previousArtifact.getVersion() != null;
        }
    }
}
