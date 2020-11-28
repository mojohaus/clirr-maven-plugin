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

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.JavaClass;
import org.codehaus.plexus.util.SelectorUtils;

import net.sf.clirr.core.ClassFilter;

/**
 * Filter classes by pattern sets.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ClirrClassFilter
    implements ClassFilter
{
    private final String[] excludes;

    private final String[] includes;

    private final String[] excludeAnnotated;

    private boolean alwaysTrue;

    public ClirrClassFilter( String[] includes, String[] excludes )
    {
        this( includes, excludes, null );
    }

    public ClirrClassFilter( String[] includes, String[] excludes, String[] excludeAnnotated )
    {
        if ( excludes == null || excludes.length == 0 )
        {
            this.excludes = null;
        }
        else
        {
            this.excludes = (String[]) excludes.clone();
        }

        if ( includes == null || includes.length == 0 )
        {
            this.includes = new String[]{"**"};

            if ( excludes == null )
            {
                alwaysTrue = true;
            }
        }
        else
        {
            this.includes = (String[]) includes.clone();
        }

        if ( excludeAnnotated == null || excludeAnnotated.length == 0 )
        {
            this.excludeAnnotated = null;
        }
        else
        {
            this.excludeAnnotated = excludeAnnotated.clone();
        }
    }

    public boolean isSelected( JavaClass javaClass )
    {
        boolean result = false;
        if ( alwaysTrue )
        {
            result = true;
        }
        else
        {
            String path = javaClass.getClassName().replace( '.', '/' );
            for ( int i = 0; i < includes.length && !result; i++ )
            {
                result = SelectorUtils.matchPath( includes[i], path );
            }

            if ( excludes != null )
            {
                for ( int i = 0; i < excludes.length && result; i++ )
                {
                    result = !SelectorUtils.matchPath( excludes[i], path );
                }
            }

            if (excludeAnnotated != null && javaClass.getAnnotationEntries() != null)
            {
                for (AnnotationEntry annotationEntry : javaClass.getAnnotationEntries())
                {
                    final String annotationType = annotationEntry.getAnnotationType();
                    for ( String annotated: excludeAnnotated )
                    {
                        if (annotationType.equals("L" + annotated.replace( '.', '/' ) + ";"))
                        {
                            return false;
                        }
                    }
                }
            }

        }

        return result;
    }
}
