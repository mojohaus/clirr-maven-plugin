package org.codehaus.mojo.clirr;

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
