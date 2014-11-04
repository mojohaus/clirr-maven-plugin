package org.codehaus.mojo.clirr;

/*
 * Copyright 2014 ForgeRock AS
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

/**
 * DiffListener that can listen for ignored differences.
 */
public interface MojoDiffListener
    extends DiffListener
{

    void reportIgnoredDiff( ApiDifference ignoredDiff, Difference reason );

}
