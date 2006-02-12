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
import net.sf.clirr.core.DiffListenerAdapter;
import net.sf.clirr.core.MessageTranslator;
import net.sf.clirr.core.Severity;
import org.apache.maven.plugin.logging.Log;

/**
 * Log messages to the console as they are processed.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class LogDiffListener
    extends DiffListenerAdapter
{
    private final Log log;

    private final MessageTranslator messageTranslator;

    public LogDiffListener( Log log )
    {
        this.log = log;

        this.messageTranslator = new MessageTranslator();
    }

    public void reportDiff( ApiDifference apiDifference )
    {
        String message = apiDifference.getAffectedClass() + ": " + apiDifference.getReport( messageTranslator );

        Severity severity = apiDifference.getMaximumSeverity();
        if ( severity.equals( Severity.INFO ) )
        {
            log.info( message );
        }
        else if ( severity.equals( Severity.WARNING ) )
        {
            log.warn( message );
        }
        else if ( severity.equals( Severity.ERROR ) )
        {
            log.error( message );
        }
    }
}
