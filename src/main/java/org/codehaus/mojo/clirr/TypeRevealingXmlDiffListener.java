package org.codehaus.mojo.clirr;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import net.sf.clirr.core.ApiDifference;
import net.sf.clirr.core.DiffListener;
import net.sf.clirr.core.MessageTranslator;

/**
 * A copy of Clirr's XmlDiffListener (which is not subclassable nor extendable in some way)
 * that includes the type of the difference in the XML. This feature is useful for people working
 * with the ignored differences so that they can easily come up with the rules without consulting
 * the documentation for the correct difference type number.
 *
 * @author Lukas Krejci
 * @since 2.6
 */
public class TypeRevealingXmlDiffListener
    implements DiffListener
{
    private static final String DIFFREPORT = "diffreport";

    private static final String DIFFERENCE = "difference";

    private final MessageTranslator translator = new MessageTranslator();

    private final PrintStream out;

    public TypeRevealingXmlDiffListener( String outFile )
        throws FileNotFoundException
    {
        out = new PrintStream( new FileOutputStream( outFile ) );
    }

    public void reportDiff( ApiDifference difference )
    {
        out.print( "  <" + DIFFERENCE );
        out.print( " type=\"" + difference.getMessage().getId() + "\"" );
        out.print( " binseverity=\"" + difference.getBinaryCompatibilitySeverity() + "\"" );
        out.print( " srcseverity=\"" + difference.getSourceCompatibilitySeverity() + "\"" );
        out.print( " class=\"" + difference.getAffectedClass() + "\"" );
        if ( difference.getAffectedMethod() != null )
        {
            out.print( " method=\"" + difference.getAffectedMethod() + "\"" );
        }
        if ( difference.getAffectedField() != null )
        {
            out.print( " field=\"" + difference.getAffectedField() + "\"" );
        }
        out.print( ">" );
        out.print( difference.getReport( translator ) ); // TODO: XML escapes??
        out.println( "</" + DIFFERENCE + '>' );
    }

    public void start()
    {
        out.println( "<?xml version=\"1.0\"?>" );
        out.println( '<' + DIFFREPORT + '>' );
    }


    public void stop()
    {
        out.println( "</" + DIFFREPORT + '>' );
        out.close();
    }
}
