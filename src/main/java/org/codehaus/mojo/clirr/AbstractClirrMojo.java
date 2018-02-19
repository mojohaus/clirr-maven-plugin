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
import net.sf.clirr.core.DiffListener;
import net.sf.clirr.core.PlainDiffListener;
import net.sf.clirr.core.Severity;
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
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Base parameters for Clirr check and report.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @todo i18n exceptions, log messages
 * @requiresDependencyResolution compile
 */
public abstract class AbstractClirrMojo
    extends AbstractMojo
{
    /**
     * Flag to easily skip execution.
     *
     * @parameter property="clirr.skip" default-value="false"
     */
    protected boolean skip;

    /**
     * @parameter property="project"
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
    private ArtifactMetadataSource metadataSource;

    /**
     * @component
     */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * The classes of this project to compare the last release against.
     *
     * @parameter default-value="${project.build.outputDirectory}
     */
    protected File classesDirectory;

    /**
     * Version to compare the current code against.
     *
     * @parameter property="comparisonVersion" default-value="(,${project.version})"
     */
    protected String comparisonVersion;

    /**
     * List of artifacts to compare the current code against. This
     * overrides <code>comparisonVersion</code>, if present.
     * Each <code>comparisonArtifact</code> is made of a <code>groupId</code>, an <code>artifactId</code> and
     * a <code>version</code> number. Optionally it may have a <code>classifier</code>
     * (default null) and a <code>type</code> (default "jar").
     *
     * @parameter
     */
    protected ArtifactSpecification[] comparisonArtifacts;

    /**
     * Show only messages of this severity or higher. Valid values are
     * <code>info</code>, <code>warning</code> and <code>error</code>.
     *
     * @parameter property="minSeverity" default-value="warning"
     */
    protected String minSeverity;

    /**
     * A text output file to render to. If omitted, no output is rendered to a text file.
     *
     * @parameter property="textOutputFile"
     */
    protected File textOutputFile;

    /**
     * An XML file to render to. If omitted, no output is rendered to an XML file.
     *
     * @parameter property="xmlOutputFile"
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
     * A list of differences reported by Clirr that should be ignored when producing the final report.
     * Values specified here will be joined with the ones specified using the "ignoredDifferencesFile"
     * parameter.
     *
     * @parameter
     * @since 2.5
     */
    protected Difference[] ignored;

    /**
     * A path to the XML file containing the ignored differences definitions.
     * Values specified int the file will be joined with the ones specified using the "ignored"
     * parameter.
     * <br/>
     * File Format
     * <pre>
     * &lt;differences&gt;
     *   ...
     *   &lt;difference&gt;
     *     &lt;className&gt;com.thoughtworks.qdox.parser.impl.Parser&lt;/className&gt;
     *     &lt;field&gt;*&lt;/field&gt;
     *   &lt;/difference&gt;
     *   ...
     * &lt;/differences&gt;
     * </pre>
     *
     * @parameter property="clirr.ignoredDifferencesFile"
     * @since 2.5
     */
    protected File ignoredDifferencesFile;

    /**
     * Whether to log the results to the console or not.
     *
     * @parameter property="logResults" default-value="false"
     */
    protected boolean logResults;

    /**
     * @parameter property="clirr.skipArtifactTypeTest" default-value="false"
     */
    private boolean skipArtifactTypeTest;

    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().info( "Skipping execution" );
        }
        else
        {
            doExecute();
        }
    }

    protected abstract void doExecute()
        throws MojoExecutionException, MojoFailureException;

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

        List<DiffListener> listeners = new ArrayList<DiffListener>();

        listeners.add( listener );

        if ( xmlOutputFile != null )
        {
            try
            {
                listeners.add( new TypeRevealingXmlDiffListener( xmlOutputFile.getAbsolutePath() ) );
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

        checker.addDiffListener( new DelegatingListener( listeners, minSeverity, getAllIgnored() ) );

        reportDiffs( checker, origClasses, currentClasses );

        return listener;
    }

    protected List<Difference> getAllIgnored()
    {
        Difference[] ret = ignored;

        if ( ignoredDifferencesFile != null && ignoredDifferencesFile.exists() )
        {
            XmlStreamReader rdr = null;
            try
            {
                rdr = ReaderFactory.newXmlReader( ignoredDifferencesFile );

                Difference[] diffs = Difference.parseXml( rdr );

                int ignoredLength = ignored == null ? 0 : ignored.length;

                Difference[] tmp = new Difference[ignoredLength + diffs.length];

                if ( ignored != null )
                {
                    System.arraycopy( ignored, 0, tmp, 0, ignoredLength );
                }

                System.arraycopy( diffs, 0, tmp, ignoredLength, diffs.length );

                ret = tmp;
            }
            catch ( IOException e )
            {
                getLog().error( "Could not read the ignored differences file.", e );
            }
            catch ( XmlPullParserException e )
            {
                getLog().error( "Could not read the ignored differences file.", e );
            }
            finally
            {
                IOUtil.close( rdr );
            }
        }

        return ret == null ? Collections.<Difference>emptyList() : Arrays.asList( ret );
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
        final Set previousArtifacts;
        final Artifact firstPreviousArtifact;
        if ( comparisonArtifacts == null )
        {
            firstPreviousArtifact = getComparisonArtifact();
            comparisonVersion = firstPreviousArtifact.getVersion();
            getLog().info( "Comparing to version: " + comparisonVersion );
            previousArtifacts = Collections.singleton( firstPreviousArtifact );
        }
        else
        {
            previousArtifacts = resolveArtifacts( comparisonArtifacts );
            Artifact a = null;
            for ( Iterator iter = previousArtifacts.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                if ( a == null )
                {
                    a = artifact;
                }
                getLog().debug( "Comparing to " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                                    + artifact.getVersion() + ":" + artifact.getClassifier() + ":"
                                    + artifact.getType() );
            }
            firstPreviousArtifact = a;
        }

        try
        {
            for ( Iterator iter = previousArtifacts.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                resolver.resolve( artifact, project.getRemoteArtifactRepositories(), localRepository );
            }

            final List dependencies = getTransitiveDependencies( previousArtifacts );

            ClassLoader origDepCL = createClassLoader( dependencies, previousArtifacts );
            final Set files = new HashSet();
            for ( Iterator iter = previousArtifacts.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                // Clirr expects JAR files, so let's not pass other artifact files.
                // MCLIRR-39 Support for Maven Plugins, which are also JARs
                // MCLIRR-61: jenkins plugin so test if isAddedToClasspath
                if ( "jar".equals( artifact.getType() ) || "maven-plugin".equals( artifact.getType() )
                    || "bundle".equals( artifact.getType() ) || artifact.getArtifactHandler().isAddedToClasspath()
                    || skipArtifactTypeTest )
                {
                    files.add( new File( localRepository.getBasedir(), localRepository.pathOf( artifact ) ) );
                }
            }
            return BcelTypeArrayBuilder.createClassSet( (File[]) files.toArray( new File[files.size()] ), origDepCL,
                                                        classFilter );
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
            getLog().warn( "Impossible to find previous version" );
            return new JavaType[0];
            //throw new MojoExecutionException( "Error finding previous version: " + e.getMessage(), e );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error creating classloader for previous version's classes", e );
        }
    }

    protected List getTransitiveDependencies( final Set previousArtifacts )
        throws ProjectBuildingException, InvalidDependencyVersionException, ArtifactResolutionException,
        ArtifactNotFoundException
    {
        final List dependencies = new ArrayList();
        for ( Iterator iter = previousArtifacts.iterator(); iter.hasNext(); )
        {
            final Artifact a = (Artifact) iter.next();
            final Artifact pomArtifact =
                factory.createArtifact( a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getScope(), "pom" );
            final MavenProject pomProject =
                mavenProjectBuilder.buildFromRepository( pomArtifact, project.getRemoteArtifactRepositories(),
                                                         localRepository );
            final Set pomProjectArtifacts = pomProject.createArtifacts( factory, null, null );
            final ArtifactResolutionResult result =
                resolver.resolveTransitively( pomProjectArtifacts, pomArtifact, localRepository,
                                              project.getRemoteArtifactRepositories(), metadataSource, null );
            dependencies.addAll( result.getArtifacts() );
        }
        return dependencies;
    }
    
    private Artifact resolveArtifactFromVersionSpec(ArtifactSpecification artifactWithVersionSpec) throws MojoFailureException, MojoExecutionException {
        final String groupId = artifactWithVersionSpec.getGroupId();
        final String artifactId = artifactWithVersionSpec.getArtifactId();
        final String versionSpec = artifactWithVersionSpec.getVersion();
        final String artifactType = (artifactWithVersionSpec.getType()==null)?"jar":artifactWithVersionSpec.getType();
        final String artifactClassifier = artifactWithVersionSpec.getClassifier();
    
        checkMandatory(groupId, "groupId", artifactWithVersionSpec);
        checkMandatory(artifactId, "artifactId", artifactWithVersionSpec);
        checkMandatory(versionSpec, "version", artifactWithVersionSpec);
        checkMandatory(artifactType, "type", artifactWithVersionSpec);
        
        VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec( versionSpec );
        } catch ( InvalidVersionSpecificationException e ) {
            getLog().info(String.format("%s cannot be used as a Version specification", versionSpec));
            range = VersionRange.createFromVersion(versionSpec);
        }
        
        Artifact resolvedArtifact;
        try {
            resolvedArtifact = factory.createDependencyArtifact( groupId, artifactId, range, artifactType, artifactClassifier, Artifact.SCOPE_COMPILE);

            if (!resolvedArtifact.getVersionRange().isSelectedVersionKnown(resolvedArtifact)) {
                getLog().debug("Searching for versions in range: " + resolvedArtifact.getVersionRange());
                
                List availableVersions = metadataSource.retrieveAvailableVersions(resolvedArtifact, localRepository, project.getRemoteArtifactRepositories());
                filterSnapshots(availableVersions);

                ArtifactVersion version = range.matchVersion(availableVersions);
                if (version != null) {
                    resolvedArtifact.selectVersion(version.toString());
                }
            }
        }
        catch (OverConstrainedVersionException e1) {
            throw new MojoFailureException("Invalid comparison version: " + e1.getMessage());
        } catch (ArtifactMetadataRetrievalException e11) {
            throw new MojoExecutionException("Error determining previous version: " + e11.getMessage(), e11);
        }

        if (resolvedArtifact.getVersion() == null) {
            getLog().info( "Unable to find a good candidate version of " + artifactWithVersionSpec.toString() + " in the repository");
        }

        return resolvedArtifact;
    }
    
    private void checkMandatory(String item, String itemName, ArtifactSpecification artifact) throws MojoFailureException {
        if (item == null || item.trim().length() == 0) {
            throw new MojoFailureException("A " + itemName + " is required in artifact: " + artifact.toString());
        }
    }

    protected Set resolveArtifacts( ArtifactSpecification[] artifacts )
        throws MojoFailureException, MojoExecutionException
    {
        Set artifactSet = new HashSet();
        Artifact[] result = new Artifact[artifacts.length];
        for ( int i = 0; i < result.length; i++ )
        {
            artifactSet.add( resolveArtifactFromVersionSpec( artifacts[i] ) );
        }
        return artifactSet;
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
            throw new MojoFailureException( "Invalid comparison version: " + e.getMessage() );
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
                filterSnapshots( availableVersions );
                ArtifactVersion version = range.matchVersion( availableVersions );
                if ( version != null )
                {
                    previousArtifact.selectVersion( version.toString() );
                }
            }
        }
        catch ( OverConstrainedVersionException e1 )
        {
            throw new MojoFailureException( "Invalid comparison version: " + e1.getMessage() );
        }
        catch ( ArtifactMetadataRetrievalException e11 )
        {
            throw new MojoExecutionException( "Error determining previous version: " + e11.getMessage(), e11 );
        }

        if ( previousArtifact.getVersion() == null )
        {
            getLog().info( "Unable to find a previous version of the project in the repository" );
        }

        return previousArtifact;
    }

    private void filterSnapshots( List versions )
    {
        for ( Iterator versionIterator = versions.iterator(); versionIterator.hasNext(); )
        {
            ArtifactVersion version = (ArtifactVersion) versionIterator.next();
            if ( "SNAPSHOT".equals( version.getQualifier() ) )
            {
                versionIterator.remove();
            }
        }
    }

    public static JavaType[] createClassSet( File classes, ClassLoader thirdPartyClasses, ClassFilter classFilter )
        throws MalformedURLException
    {
        ClassLoader classLoader = new URLClassLoader( new URL[]{ classes.toURI().toURL() }, thirdPartyClasses );

        Repository repository = new ClassLoaderRepository( classLoader );

        List selected = new ArrayList();

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( classes );
        scanner.setIncludes( new String[]{ "**/*.class" } );
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

    /**
     * Create a ClassLoader, which includes the artifacts in <code>artifacts</code>,
     * but excludes the artifacts in <code>previousArtifacts</code>. The intention is,
     * that we let BCEL inspect the artifacts in the latter set, using a
     * {@link ClassLoader}, which contains the dependencies. However, the
     * {@link ClassLoader} must not contain the jar files, which are being inspected.
     *
     * @param artifacts         The artifacts, from which to build a {@link ClassLoader}.
     * @param previousArtifacts The artifacts being inspected, or null, if te
     *                          returned {@link ClassLoader} should contain all the elements of
     *                          <code>artifacts</code>.
     * @return A {@link ClassLoader} which may be used to inspect the classes in
     *         previousArtifacts.
     * @throws MalformedURLException Failed to convert a file to an URL.
     */
    protected static ClassLoader createClassLoader( Collection artifacts, Set previousArtifacts )
        throws MalformedURLException
    {
        URLClassLoader cl = null;
        if ( !artifacts.isEmpty() )
        {
            List urls = new ArrayList( artifacts.size() );
            for ( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if ( previousArtifacts == null || !previousArtifacts.contains( artifact ) )
                {
                    urls.add( artifact.getFile().toURI().toURL() );
                }
            }
            if ( !urls.isEmpty() )
            {
                cl = new URLClassLoader( (URL[]) urls.toArray( EMPTY_URL_ARRAY ) );
            }
        }
        return cl;
    }

    protected static Severity convertSeverity( String minSeverity )
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

    protected boolean canGenerate()
        throws MojoFailureException, MojoExecutionException
    {
        boolean classes = false;

        if ( classesDirectory.exists() )
        {
            classes = true;
        }
        else
        {
            getLog().debug( "Classes directory not found: " + classesDirectory );
        }

        if ( !classes )
        {
            getLog().info( "Not generating Clirr report as there are no classes generated by the project" );
            return false;
        }

        if ( comparisonArtifacts == null || comparisonArtifacts.length == 0 )
        {
            Artifact previousArtifact = getComparisonArtifact();
            if ( previousArtifact.getVersion() == null )
            {
                getLog().info(
                    "Not generating Clirr report as there is no previous version of the library to compare against" );
                return false;
            }
        }

        return true;
    }

    /**
     * Calls {@link Checker#reportDiffs(JavaType[], JavaType[])} and take care of BCEL errors.
     *
     * @param checker        not null
     * @param origClasses    not null
     * @param currentClasses not null
     * @see Checker#reportDiffs(JavaType[], JavaType[])
     */
    private void reportDiffs( Checker checker, JavaType[] origClasses, JavaType[] currentClasses )
    {
        try
        {
            checker.reportDiffs( origClasses, currentClasses );
        }
        catch ( CheckerException e )
        {
            getLog().error( e.getMessage() );

            // remove class with errors
            int matchingClasses = 0;
            int j = 0;
            for ( int i = 0; i < origClasses.length; i++ )
            {
                if ( !e.getMessage().endsWith( origClasses[i].getName() ) )
                {
                    matchingClasses++;
                }
            }
            JavaType[] origClasses2 = new JavaType[matchingClasses];
            for ( int i = 0; i < origClasses.length; i++ )
            {
                if ( !e.getMessage().endsWith( origClasses[i].getName() ) )
                {
                    origClasses2[j++] = origClasses[i];
                }
            }

            matchingClasses = 0;
            j = 0;
            for ( int i = 0; i < currentClasses.length; i++ )
            {
                if ( !e.getMessage().endsWith( currentClasses[i].getName() ) )
                {
                    matchingClasses++;
                }
            }
            JavaType[] currentClasses2 = new JavaType[matchingClasses];
            for ( int i = 0; i < currentClasses.length; i++ )
            {
                if ( !e.getMessage().endsWith( currentClasses[i].getName() ) )
                {
                    currentClasses2[j++] = currentClasses[i];
                }
            }

            reportDiffs( checker, origClasses2, currentClasses2 );
        }
    }
}
