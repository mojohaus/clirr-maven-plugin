/*
 * Copyright 2012 The Apache Software Foundation.
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

package org.codehaus.mojo.clirr;

import net.sf.clirr.core.ApiDifference;
import net.sf.clirr.core.MessageTranslator;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * A specification of a ignored difference found by Clirr.
 *
 * @author Lukas Krejci
 */
public class Difference
{

    private static final MessageTranslator ARGS_EXTRACTOR = new MessageTranslator();

    static
    {
        ARGS_EXTRACTOR.setResourceName( Difference.class.getName() );
    }

    public static Difference[] parseXml( Reader xml )
        throws XmlPullParserException, IOException
    {
        XmlPullParser parser = new MXParser();
        parser.setInput( xml );

        ArrayList diffs = new ArrayList();

        int state = 0;
        int event;
        Difference current = null;
        while ( ( event = parser.next() ) != XmlPullParser.END_DOCUMENT )
        {
            switch ( event )
            {
                case XmlPullParser.START_TAG:
                    switch ( state )
                    {
                        case 0: //start document
                            state = 1;
                            break;
                        case 1: //expect next difference
                            if ( "difference".equals( parser.getName() ) )
                            {
                                current = new Difference();
                                state = 2;
                            }
                            break;
                        case 2: //reading difference
                            String name = parser.getName();
                            String value = parser.nextText();
                            if ( "className".equals( name ) )
                            {
                                current.className = value;
                            }
                            else if ( "differenceType".equals( name ) )
                            {
                                current.differenceType = Integer.parseInt( value );
                            }
                            else if ( "field".equals( name ) )
                            {
                                current.field = value;
                            }
                            else if ( "method".equals( name ) )
                            {
                                current.method = value;
                            }
                            else if ( "from".equals( name ) )
                            {
                                current.from = value;
                            }
                            else if ( "to".equals( name ) )
                            {
                                current.to = value;
                            }
                            else if ( "justification".equals( name ) )
                            {
                                current.justification = value;
                            }
                            break;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    switch ( state )
                    {
                        case 1:
                        case 2:
                            if ( "difference".equals( parser.getName() ) )
                            {
                                diffs.add( current );
                                state = 1;
                            }
                            break;
                    }
            }
        }

        Difference[] ret = new Difference[diffs.size()];

        return (Difference[]) diffs.toArray( ret );
    }

    /**
     * See http://clirr.sourceforge.net/clirr-core/exegesis.html for the different
     * codes of differences.
     *
     * @parameter
     * @required
     */
    private int differenceType;

    /**
     * The name of the class that contains the ignored difference. This can be a path expression.
     *
     * @parameter
     * @required
     */
    private String className;

    /**
     * The name of the field that should be ignored according to the difference type and optionally 'from' and 'to' conditions.
     * This is parameter is a regular expression.
     *
     * @parameter
     */
    private String field;

    private Pattern fieldPattern;

    /**
     * The name of the method that should be ignored according to the difference type and optionally 'from' and 'to' conditions.
     * This is parameter is a regular expression.
     *
     * @parameter
     */
    private String method;

    private Pattern methodPattern;

    /**
     * The original type of the field or method (if it is important for the difference type, otherwise can
     * be left out).
     * <p/>
     * The "type" of the method is its full signature, i.e.:<br/>
     * <code>
     * RETURN_TYPE METHOD_NAME(PARAMETERS)
     * </code>
     *
     * @parameter
     */
    private String from;

    /**
     * The "new" type of the field or method (if it is important for the difference type, otherwise can
     * be left out).
     *
     * @parameter
     */
    private String to;

    /**
     * The reason why ignoring this difference is deemed OK.
     *
     * @parameter
     * @required
     */
    private String justification;


    public int getDifferenceType()
    {
        return differenceType;
    }

    public void setDifferenceType( int differenceType )
    {
        this.differenceType = differenceType;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName( String className )
    {
        this.className = className;
    }

    public String getField()
    {
        return field;
    }

    public void setField( String field )
    {
        this.field = field;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod( String method )
    {
        this.method = method;
    }

    public String getFrom()
    {
        return from;
    }

    public void setFrom( String from )
    {
        this.from = from;
    }

    public String getTo()
    {
        return to;
    }

    public void setTo( String to )
    {
        this.to = to;
    }

    public String getJustification()
    {
        return justification;
    }

    public void setJustification( String justification )
    {
        this.justification = justification;
    }

    public boolean matches( ApiDifference apiDiff )
    {
        if ( apiDiff.getMessage().getId() != differenceType )
        {
            return false;
        }

        String affectedClassPath = apiDiff.getAffectedClass().replace( ".", File.separator );
        if ( !SelectorUtils.matchPath( className, affectedClassPath ) )
        {
            return false;
        }

        initPatterns();

        //The interpretation of "from" and "to" depends on the error
        switch ( differenceType )
        {
            case 6000: //added field
                return matches6000( apiDiff );
            case 6001: //removed field
                return matches6001( apiDiff );
            case 6002: //field value no longer a compile-time constant
                return matches6002( apiDiff );
            case 6003: //value of the compile-time constant changed on a field
                return matches6003( apiDiff );
            case 6004: //field type changed
                return matches6004( apiDiff );
            case 6005: //field now non-final
                return matches6005( apiDiff );
            case 6006: //field now final
                return matches6006( apiDiff );
            case 6007: //field now non-static
                return matches6007( apiDiff );
            case 6008: //field now static
                return matches6008( apiDiff );
            case 6009: //field more accessible
                return matches6009( apiDiff );
            case 6010: //field less accessible
                return matches6010( apiDiff );
            case 6011: //removed a constant field
                return matches6011( apiDiff );
            case 7000: //method now in superclass
                return matches7000( apiDiff );
            case 7001: //method now in interface
                return matches7001( apiDiff );
            case 7002: //method removed
                return matches7002( apiDiff );
            case 7003: //Method Overide Removed
                return matches7003( apiDiff );
            case 7004: //Method Argument Count Changed
                return matches7004( apiDiff );
            case 7005: //Method Argument Type changed
                return matches7005( apiDiff );
            case 7006: //Method Return Type changed
                return matches7006( apiDiff );
            case 7007: //Method has been Deprecated
                return matches7007( apiDiff );
            case 7008: //Method has been Undeprecated
                return matches7008( apiDiff );
            case 7009: //Method is now Less Accessible
                return matches7009( apiDiff );
            case 7010: //Method is now More Accessible
                return matches7010( apiDiff );
            case 7011: //Method Added
                return matches7011( apiDiff );
            case 7012: //Method Added to Interface
                return matches7012( apiDiff );
            case 7013: //Abstract Method Added to Class
                return matches7013( apiDiff );
            case 7014: //Method now final
                return matches7014( apiDiff );
            case 7015: //Method now non-final
                return matches7015( apiDiff );
            default:
                return false;
        }
    }

    /**
     * added field
     */
    private boolean matches6000( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * removed field
     */
    private boolean matches6001( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field value no longer a compile-time constant
     */
    private boolean matches6002( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * value of the compile-time constant changed on a field
     */
    private boolean matches6003( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field type changed
     */
    private boolean matches6004( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, true );

        if ( !fieldPattern.matcher( apiDiff.getAffectedField() ).matches() )
        {
            return false;
        }

        String[] args = getArgs( apiDiff );
        String diffFrom = args[0];
        String diffTo = args[1];

        return from.equals( diffFrom ) && to.equals( diffTo );
    }

    /**
     * field now non-final
     */
    private boolean matches6005( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field now final
     */
    private boolean matches6006( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field now non-static
     */
    private boolean matches6007( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field now static
     */
    private boolean matches6008( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field more accessible
     */
    private boolean matches6009( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * field less accessible
     */
    private boolean matches6010( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * removed a constant field
     */
    private boolean matches6011( ApiDifference apiDiff )
    {
        throwIfMissing( true, false, false );
        return fieldPattern.matcher( apiDiff.getAffectedField() ).matches();
    }

    /**
     * method now in superclass
     */
    private boolean matches7000( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * method now in interface
     */
    private boolean matches7001( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * method removed
     */
    private boolean matches7002( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method Overide Removed
     */
    private boolean matches7003( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method Argument Count Changed
     */
    private boolean matches7004( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method Argument Type changed
     */
    private boolean matches7005( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, true );

        if ( !methodPattern.matcher( getMethodName( apiDiff ) ).matches() )
        {
            return false;
        }

        String[] args = getArgs( apiDiff );

        //1-based
        int idx = Integer.parseInt( args[0] ) - 1;
        String diffNewType = args[1];

        //now find the old type of the parameter at given position
        //by parsing the signature of the method
        String diffOldType = extractParamTypeFromSignature( apiDiff.getAffectedMethod(), idx );

        String oldType = extractParamTypeFromSignature( from, idx );
        String newType = extractParamTypeFromSignature( to, idx );

        return diffOldType.equals( oldType ) && diffNewType.equals( newType );
    }

    /**
     * Method Return Type changed
     */
    private boolean matches7006( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, true );

        if ( !methodPattern.matcher( getMethodName( apiDiff ) ).matches() )
        {
            return false;
        }

        if ( "<init>".equals( method ) )
        {
            //well, it's kinda hard to tell here, right?
            //Constructors aren't supposed to change return types
            //or to have any.
            return false;
        }

        String origSig = apiDiff.getAffectedMethod();
        int afterVisibility = origSig.indexOf( ' ' );
        if ( afterVisibility < 0 )
        {
            return false;
        }

        int afterRetType = origSig.indexOf( ' ', afterVisibility + 1 );

        String origRetType = origSig.substring( afterVisibility + 1, afterRetType );

        String newRetType = getArgs( apiDiff )[0];

        return from.equals( origRetType ) && to.equals( newRetType );
    }

    /**
     * Method has been Deprecated
     */
    private boolean matches7007( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method has been Undeprecated
     */
    private boolean matches7008( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method is now Less Accessible
     */
    private boolean matches7009( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method is now More Accessible
     */
    private boolean matches7010( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method Added
     */
    private boolean matches7011( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method Added to Interface
     */
    private boolean matches7012( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Abstract Method Added to Class
     */
    private boolean matches7013( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method now final
     */
    private boolean matches7014( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    /**
     * Method now non-final
     */
    private boolean matches7015( ApiDifference apiDiff )
    {
        throwIfMissing( false, true, false );
        return methodPattern.matcher( getMethodName( apiDiff ) ).matches();
    }

    private String[] getArgs( ApiDifference apiDiff )
    {
        String args = apiDiff.getReport( ARGS_EXTRACTOR );
        return args.split( "&" );
    }

    private String getMethodName( ApiDifference apiDiff )
    {
        String methodSig = apiDiff.getAffectedMethod();
        //skip the visibility
        int spaceIdx = methodSig.indexOf( ' ' );

        //skip the return type
        if ( spaceIdx > -1 )
        {
            spaceIdx = methodSig.indexOf( ' ', spaceIdx + 1 );
        }

        int parenthesisIdx = methodSig.indexOf( '(' );

        if ( spaceIdx == -1 || spaceIdx > parenthesisIdx )
        {
            //constructor
            return "<init>";
        }
        else
        {
            //method
            return methodSig.substring( spaceIdx + 1, parenthesisIdx );
        }
    }

    private void throwIfMissing( boolean field, boolean method, boolean fromto )
    {
        if ( field && this.field == null )
        {
            throw new IllegalArgumentException( "Field must be specified for this difference type." );
        }
        else if ( method && this.method == null )
        {
            throw new IllegalArgumentException( "Method must be specified for this difference type." );
        }
        else if ( fromto && ( this.from == null || this.to == null ) )
        {
            throw new IllegalArgumentException( "From and to must be specified for this difference type." );
        }
    }

    private String extractParamTypeFromSignature( String signature, int idx )
    {
        int openParIdx = signature.indexOf( '(' );
        int closeParIdx = signature.indexOf( ')' );

        if ( openParIdx < 0 || closeParIdx < 0 )
        {
            throw new IllegalArgumentException(
                "Invalid method signature found in the API difference report: " + signature );
        }

        String paramsString = signature.substring( openParIdx + 1, closeParIdx );
        String[] params = paramsString.split( "\\s*,\\s*" );

        return params[idx];
    }

    private void initPatterns()
    {
        if ( field != null && fieldPattern == null )
        {
            fieldPattern = Pattern.compile( field );
        }

        if ( method != null && methodPattern == null )
        {
            methodPattern = Pattern.compile( method );
        }
    }
}
