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

import java.util.LinkedList;
import java.util.List;
import java.util.Collections;

/**
 * Listen to the Clirr events.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ClirrDiffListener
    extends DiffListenerAdapter
{
    /** The list of differences that occurred. */
    private List apiDifferences = new LinkedList();

    public void reportDiff( ApiDifference apiDifference )
    {
        // TODO: count
        apiDifferences.add( apiDifference );
    }

    public List getApiDifferences()
    {
        return Collections.unmodifiableList( apiDifferences );
    }
}
