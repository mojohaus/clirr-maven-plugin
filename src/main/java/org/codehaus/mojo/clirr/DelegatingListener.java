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
import java.util.List;
import java.util.Map;

/**
 * Delegates to a number of listeners, filtering by severity.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class DelegatingListener
    implements MojoDiffListener
{
    private final List<DiffListener> listeners;

    private final Severity minSeverity;

    private final List<Difference> ignored;

    private Map<Difference, Map<Object, List<ApiDifference>>> deferredMatchesPerDifference = new HashMap<Difference, Map<Object, List<ApiDifference>>>();

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
        if ( ( minSeverity == null || minSeverity.compareTo( apiDifference.getMaximumSeverity() ) <= 0 ) )
        {
            Difference reasonToIgnoreDiff = getReasonToIgnoreDiff( apiDifference );
            if ( reasonToIgnoreDiff == null )
            {
                reportDiff_(apiDifference);
            }
            else
            {
                reportIgnoredDiff(apiDifference, reasonToIgnoreDiff);
            }
        }
    }

    private void reportDiff_(ApiDifference apiDifference)
    {
        for ( DiffListener listener : listeners )
        {
            listener.reportDiff( apiDifference );
        }
    }

    public void reportIgnoredDiff( ApiDifference ignoredDiff, Difference reason )
    {
        for ( DiffListener listener : listeners )
        {
            if ( listener instanceof MojoDiffListener )
            {
                MojoDiffListener l = (MojoDiffListener) listener;
                l.reportIgnoredDiff( ignoredDiff, reason );
            }
        }
    }

    public void stop()
    {
        //process the deferred matches now
        for ( Map.Entry<Difference, Map<Object, List<ApiDifference>>> perDifferenceEntry : deferredMatchesPerDifference.entrySet() )
        {
            Difference diff = perDifferenceEntry.getKey();
            Map<Object, List<ApiDifference>> diffsPerId = perDifferenceEntry.getValue();

            for ( List<ApiDifference> apiDiffs : diffsPerId.values() )
            {
                if ( !diff.resolveDefferedMatches( apiDiffs ) )
                {
                    for ( ApiDifference apiDiff : apiDiffs )
                    {
                        reportDiff_(apiDiff);
                    }
                }
                else
                {
                    for ( ApiDifference apiDiff : apiDiffs )
                    {
                        reportIgnoredDiff(apiDiff, diff);
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

    private Difference getReasonToIgnoreDiff( ApiDifference apiDiff )
    {
        if ( ignored == null )
        {
            return null;
        }

        boolean someDeferred = false;
        boolean matched = false;

        Difference reason = null;
        for ( Difference difference : ignored )
        {
            if ( difference == null )
            {
                continue;
            }
            Difference.Result res = difference.matches( apiDiff );

            switch ( res.getCode() )
            {
                case Difference.Result.MATCHED:
                    if ( reason == null )
                    {
                        reason = difference;
                    }
                    matched = true;
                    break;
                case Difference.Result.NOT_MATCHED:
                    break;
                case Difference.Result.DEFERRED_MATCH:
                    Map<Object, List<ApiDifference>> diffsPerDifferentiator =
                        deferredMatchesPerDifference.get( difference );
                    if ( diffsPerDifferentiator == null )
                    {
                        diffsPerDifferentiator = new HashMap<Object, List<ApiDifference>>();
                        deferredMatchesPerDifference.put( difference, diffsPerDifferentiator );
                    }

                    List<ApiDifference> diffs = diffsPerDifferentiator.get( res.getDifferentiator() );
                    if ( diffs == null )
                    {
                        diffs = new ArrayList<ApiDifference>();
                        diffsPerDifferentiator.put( res.getDifferentiator(), diffs );
                    }

                    diffs.add( apiDiff );
                    if ( reason == null )
                    {
                        reason = difference;
                    }
                    someDeferred = true;
                    break;
            }
        }

        if ( matched || someDeferred )
        {
          return reason;
        }
        return null;
    }
}
