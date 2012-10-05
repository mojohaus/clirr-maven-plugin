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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Delegates to a number of listeners, filtering by severity.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class DelegatingListener
    implements DiffListener
{
    private final List<DiffListener> listeners;

    private final Severity minSeverity;

    private final List<Difference> ignored;

    private Map deferredMatchesPerDifference = new HashMap();

    public DelegatingListener( List<DiffListener> listeners, Severity minSeverity, List<Difference> ignored )
    {
        this.listeners = listeners == null ? Collections.<DiffListener>emptyList() : listeners;

        this.minSeverity = minSeverity;

        this.ignored = ignored == null ? Collections.<Difference>emptyList() : ignored;
    }

    public void start()
    {
        deferredMatchesPerDifference.clear();

        for ( DiffListener listener : listeners )
        {
            listener.start();
        }
    }

    public void reportDiff( ApiDifference apiDifference )
    {
        if ( ( minSeverity == null || minSeverity.compareTo( apiDifference.getMaximumSeverity() ) <= 0 ) && !isIgnored(
            apiDifference ) )
        {
            for ( DiffListener listener : listeners )
            {
                listener.reportDiff( apiDifference );
            }
        }
    }

    public void stop()
    {
        //process the deferred matches now
        for ( Iterator perDifferenceIt = deferredMatchesPerDifference.entrySet().iterator();
              perDifferenceIt.hasNext(); )
        {
            Map.Entry perDifferenceEntry = (Map.Entry) perDifferenceIt.next();

            Difference diff = (Difference) perDifferenceEntry.getKey();
            Map diffsPerId = (Map) perDifferenceEntry.getValue();

            for ( Iterator perIdIt = diffsPerId.values().iterator(); perIdIt.hasNext(); )
            {
                List apiDiffs = (List) perIdIt.next();

                if ( !diff.resolveDefferedMatches( apiDiffs ) )
                {
                    for ( DiffListener listener : listeners )
                    {

                        for ( Iterator j = apiDiffs.iterator(); j.hasNext(); )
                        {
                            listener.reportDiff( (ApiDifference) j.next() );
                        }
                    }
                }
            }
        }

        //and stop the underlying listeners
        for ( DiffListener listener : listeners )
        {
            listener.stop();
        }
    }

    private boolean isIgnored( ApiDifference apiDiff )
    {
        if ( ignored == null )
        {
            return false;
        }

        boolean someDeferred = false;
        boolean matched = false;

        for ( Difference difference : ignored )
        {
            Difference.Result res = difference.matches( apiDiff );

            switch ( res.getCode() )
            {
                case Difference.Result.MATCHED:
                    matched = true;
                    break;
                case Difference.Result.NOT_MATCHED:
                    break;
                case Difference.Result.DEFERRED_MATCH:
                    Map<Object, List<ApiDifference>> diffsPerDifferentiator =
                        (Map) deferredMatchesPerDifference.get( ignored );
                    if ( diffsPerDifferentiator == null )
                    {
                        diffsPerDifferentiator = new HashMap<Object, List<ApiDifference>>();
                        deferredMatchesPerDifference.put( ignored, diffsPerDifferentiator );
                    }

                    List<ApiDifference> diffs = diffsPerDifferentiator.get( res.getDifferentiator() );
                    if ( diffs == null )
                    {
                        diffs = new ArrayList<ApiDifference>();
                        diffsPerDifferentiator.put( res.getDifferentiator(), diffs );
                    }

                    diffs.add( apiDiff );
                    someDeferred = true;
                    break;
            }
        }

        return matched || someDeferred;
    }
}
