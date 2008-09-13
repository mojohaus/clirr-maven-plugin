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

import org.apache.maven.plugin.MojoExecutionException;

/**
 * This exception is thrown, if no previous version of the current artifact was found.
 */
class MissingPreviousException
    extends MojoExecutionException
{
    private static final long serialVersionUID = -5160106292241626179L;

    MissingPreviousException( String pMessage, Throwable pCause )
    {
        super( pMessage, pCause );
    }
}
