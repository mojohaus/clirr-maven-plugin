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
import net.sf.clirr.core.ClassSelector;
import net.sf.clirr.core.PlainDiffListener;
import net.sf.clirr.core.internal.bcel.BcelTypeArrayBuilder;
import net.sf.clirr.core.spi.JavaType;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.site.renderer.SiteRenderer;

import java.io.File;
import java.io.IOException;
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
 * @execute phase="package"
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
     * @parameter default-value="${executedProject}"
     * @required
     * @readonly
     */
    private MavenProject executedProject;

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
        // Find the previous version JAR and resolve it
        ClassLoader origDepCL = null;
        ClassLoader currentDepCL = null;
        Artifact previousArtifact = null;
        try
        {
            // TODO: allow this to be specified as "comparisonVersion"
            // TODO: VersionRange previousVersion = VersionRange.createFromVersionSpec( "(," + project.getVersion() + ")" );
            VersionRange previousVersion = VersionRange.createFromVersion( "2.0" );
            previousArtifact = factory.createDependencyArtifact( project.getGroupId(), project.getArtifactId(),
                                                                 previousVersion, project.getPackaging(), null,
                                                                 Artifact.SCOPE_COMPILE );

            getLog().info( "Previous version: " + previousArtifact.getVersion() );

            // TODO: better way?
            Artifact dummy = factory.createProjectArtifact( "dummy", "dummy", "1.0" );
            ArtifactResolutionResult result = resolver.resolveTransitively( Collections.singleton( previousArtifact ),
                                                                            dummy, localRepository,
                                                                            project.getRemoteArtifactRepositories(),
                                                                            metadataSource, null );

            origDepCL = createClassLoader( result.getArtifacts(), previousArtifact );
            currentDepCL = createClassLoader( project.getArtifacts(), null );
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

        // a selector that selects everything
        // TODO: filter classes?
        ClassSelector classSelector = new ClassSelector( ClassSelector.MODE_UNLESS );

        File file = new File( localRepository.getBasedir(), localRepository.pathOf( previousArtifact ) );
        JavaType[] origClasses = BcelTypeArrayBuilder.createClassSet( new File[]{file}, origDepCL, classSelector );

        // TODO: change so we don't need to use execute package, just compile
        JavaType[] currentClasses = BcelTypeArrayBuilder.createClassSet( new File[]{executedProject.getArtifact().getFile()},
                                                                         currentDepCL, classSelector );

        // Create a Clirr checker and execute
        Checker checker = new Checker();

        ClirrDiffListener listener = new ClirrDiffListener();

        checker.addDiffListener( listener );

        // TODO: remove
        try
        {
            checker.addDiffListener( new PlainDiffListener( "diffs.txt" ) );
        }
        catch ( IOException e )
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        checker.reportDiffs( origClasses, currentClasses );

        // TODO: take the listener and generate report
    }

    private static ClassLoader createClassLoader( Set artifacts, Artifact previousArtifact )
        throws MalformedURLException
    {
        List urls = new ArrayList( artifacts.size() - 1 );
        for ( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            if ( !artifact.equals( previousArtifact ) )
            {
                urls.add( artifact.getFile().toURL() );
            }
        }
        return new URLClassLoader( (URL[]) urls.toArray( EMPTY_URL_ARRAY ) );
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
