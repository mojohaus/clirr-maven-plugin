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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Perform a violation check against the last checkstyle run to see if there are any violations.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @goal check
 * @phase verify
 * @todo write
 */
public class ClirrCheckMojo
    extends AbstractMojo
{
    /**
     * @parameter default-value="false"
     */
    private boolean failOnSrcWarning;

    /**
     * @parameter default-value="false"
     */
    private boolean failOnBinWarning;

    /**
     * @parameter default-value="true"
     */
    private boolean failOnSrcError;

    /**
     * @parameter default-value="true"
     */
    private boolean failOnBinError;

    /**
     * @parameter expression="${previousVersion}"
     */
    private String previousVersion;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        // TODO: execute
    }
}
