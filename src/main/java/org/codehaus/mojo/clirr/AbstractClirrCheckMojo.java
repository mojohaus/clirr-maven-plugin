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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.i18n.I18N;

import java.util.Iterator;
import java.util.Locale;

/**
 * Check for compatibility with previous version.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class AbstractClirrCheckMojo
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
     * Whether to fail on info.
     *
     * @parameter expression="${failOnInfo}" default-value="false"
     */
    private boolean failOnInfo;

    /**
     * @component
     */
    private I18N i18n;

    protected void doExecute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !canGenerate() )
        {
            return;
        }
        Severity minSeverity = convertSeverity( this.minSeverity );
        
        
        ClirrDiffListener listener;
        try
        {
            listener = executeClirr(minSeverity);
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
        if ( failOnWarning && warningCount > 0 )
        {
            log( listener, Severity.WARNING );
            String message;
            if ( warningCount > 1 )
            {
                String[] args = new String[]{String.valueOf( warningCount )};
                message = i18n.format( "clirr-report", locale, "check.clirr.failure.warnings", args );
            }
            else
            {
                message = i18n.getString( "clirr-report", locale, "check.clirr.failure.warning" );
            }
            throw new MojoFailureException( message );
        }

        int infoCount = listener.getSeverityCount( Severity.INFO );
        if ( failOnInfo && infoCount > 0)
        {
            log( listener, Severity.INFO );
            String message;
            if ( infoCount > 1 )
            {
                String[] args = new String[]{String.valueOf( infoCount )};
                message = i18n.format( "clirr-report", locale, "check.clirr.failure.infos", args );
            }
            else
            {
                message = i18n.getString( "clirr-report", locale, "check.clirr.failure.info" );
            }
            throw new MojoFailureException( message );
        }

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

}
