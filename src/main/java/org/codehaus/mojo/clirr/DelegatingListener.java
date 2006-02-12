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
import net.sf.clirr.core.DiffListener;
import net.sf.clirr.core.Severity;

import java.util.Iterator;
import java.util.List;

/**
 * Delegates to a number of listeners, filtering by severity.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class DelegatingListener
    implements DiffListener
{
    private final List listeners;

    private final Severity minSeverity;

    public DelegatingListener( List listeners, Severity minSeverity )
    {
        this.listeners = listeners;

        this.minSeverity = minSeverity;
    }

    public void start()
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            DiffListener listener = (DiffListener) i.next();
            listener.start();
        }
    }

    public void reportDiff( ApiDifference apiDifference )
    {
        if ( minSeverity == null || minSeverity.compareTo( apiDifference.getMaximumSeverity() ) <= 0 )
        {
            for ( Iterator i = listeners.iterator(); i.hasNext(); )
            {
                DiffListener listener = (DiffListener) i.next();
                listener.reportDiff( apiDifference );
            }
        }
    }

    public void stop()
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            DiffListener listener = (DiffListener) i.next();
            listener.stop();
        }
    }
}
