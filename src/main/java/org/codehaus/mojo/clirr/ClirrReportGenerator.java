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
import net.sf.clirr.core.MessageTranslator;
import net.sf.clirr.core.Severity;

import org.apache.maven.doxia.sink.Sink;
import org.codehaus.plexus.i18n.I18N;

import java.util.*;
import java.util.Map.Entry;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generate the Clirr report.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ClirrReportGenerator
{

    /**
     * Clirr's difference types.
     */
    private static final int FIELD_TYPE_CHANGED = 6004;
    private static final int METHOD_ARGUMENT_TYPE_CHANGED = 7005;
    private static final int METHOD_RETURN_TYPE_CHANGED = 7006;

    private static final class JustificationComparator implements Comparator<Difference>
    {
        public int compare( Difference o1, Difference o2 )
        {
            return o1.getJustification().compareTo( o2.getJustification() );
        }
    }

    private static final class ApiChangeComparator implements Comparator<ApiChange>
    {
        public int compare(ApiChange c1, ApiChange c2)
        {
            int cmp = c1.getAffectedClass().compareTo(c2.getAffectedClass());
            if (cmp == 0)
            {
                cmp = c1.getFrom().compareTo(c2.getFrom());
                if (cmp == 0)
                {
                    return c1.getTo().compareTo(c2.getTo());
                }
            }
            return cmp;
        }
    }

    private static class ApiChange
    {
        private Difference difference;
        private List<ApiDifference> apiDifferences = new LinkedList<ApiDifference>();
        private String from;
        private String to;

        private String getAffectedClass()
        {
            return apiDifferences.get(0).getAffectedClass();
        }

        private String getFrom()
        {
          return from;
        }

        private String getTo()
        {
            return to;
        }

        private void computeFields()
        {
          ApiDifference apiDiff = apiDifferences.get(0);
          String methodSig = apiDiff.getAffectedMethod();

          // set default values that can be overwritten later
          if (apiDiff.getAffectedMethod() != null)
          {
              from = apiDiff.getAffectedMethod();
          }
          else if (apiDiff.getAffectedField() != null)
          {
              from = apiDiff.getAffectedField();
          } else {
              from = "";
          }
          to = difference.getTo();


          switch (difference.getDifferenceType())
          {
          case FIELD_TYPE_CHANGED: {
              String clirrReport = apiDiff.getReport(new MessageTranslator());
              Pattern p = Pattern.compile("Changed type of field ([^ ]+) from ([^ ]+) to ([^ ]+)");
              Matcher m = p.matcher(clirrReport);
              if (m.find())
              {
                  from = m.group(2) + ' ' + m.group(1);
                  to = m.group(3) + ' ' + m.group(1);
              }
              break;
              }

          case METHOD_ARGUMENT_TYPE_CHANGED: {
              to = Difference.getNewMethodSignature( methodSig, apiDifferences );
              break;
              }

          case METHOD_RETURN_TYPE_CHANGED: {
              String clirrReport = apiDiff.getReport(new MessageTranslator());
              Pattern p = Pattern.compile("Return type of method '[^']+' has been changed to (.+)");
              Matcher m = p.matcher(clirrReport);
              if (m.find())
              {
                  int openParIdx = methodSig.indexOf('(');
                  int afterReturnTypeIdx = methodSig.lastIndexOf(' ', openParIdx);
                  int beforeReturnTypeIdx = methodSig.lastIndexOf(' ', afterReturnTypeIdx - 1);
                  to = new StringBuilder()
                        .append(methodSig, 0, beforeReturnTypeIdx + 1)
                        .append(m.group(1))
                        .append(methodSig, afterReturnTypeIdx, methodSig.length())
                        .toString();
              }
              break;
              }

          }
        }

    }

    private final I18N i18n;

    private final ResourceBundle bundle;

    private final Sink sink;
    private boolean enableSeveritySummary;
    private final Locale locale;
    private Severity minSeverity;
    private String xrefLocation;
    private String currentVersion;
    private String comparisonVersion;

    public ClirrReportGenerator( Sink sink, I18N i18n, ResourceBundle bundle, Locale locale )
    {
        this.i18n = i18n;
        this.bundle = bundle;
        this.sink = sink;
        this.enableSeveritySummary = true;
        this.locale = locale;
    }

    public void generateReport( ClirrDiffListener listener )
    {
        doHeading();

        if ( enableSeveritySummary )
        {
            doSeveritySummary( listener );
        }

        doDetails( listener );
        doApiChanges( listener );

        sink.body_();
        sink.flush();
        sink.close();
    }

    private void doHeading()
    {
        sink.head();
        sink.title();

        String title = bundle.getString( "report.clirr.title" );
        sink.text( title );
        sink.title_();
        sink.head_();

        sink.body();

        sink.section1();
        sink.sectionTitle1();
        sink.text( title );
        sink.sectionTitle1_();

        sink.paragraph();
        sink.text( bundle.getString( "report.clirr.clirrlink" ) + " " );
        sink.link( "http://clirr.sourceforge.net/" );
        sink.text( "Clirr" );
        sink.link_();
        sink.text( "." );
        sink.paragraph_();

        sink.list();

        sink.listItem();
        sink.text( bundle.getString( "report.clirr.version.current" ) + " " );
        sink.text( getCurrentVersion() );
        sink.listItem_();

        if ( getComparisonVersion() != null )
        {
            sink.listItem();
            sink.text( bundle.getString( "report.clirr.version.comparison" ) + " " );
            sink.text( getComparisonVersion() );
            sink.listItem_();
        }

        sink.list_();

        sink.section1_();
    }

    private void iconInfo()
    {
        icon( "report.clirr.level.info", "images/icon_info_sml.gif" );
    }

    private void iconWarning()
    {
        icon( "report.clirr.level.warning" , "images/icon_warning_sml.gif" );
    }

    private void iconError()
    {
        icon( "report.clirr.level.error", "images/icon_error_sml.gif" );
    }

    private void icon(String altText, String image)
    {
        sink.figure();
        sink.figureCaption();
        sink.text( bundle.getString( altText ) );
        sink.figureCaption_();
        sink.figureGraphics( image );
        sink.figure_();
    }

    private void doSeveritySummary( ClirrDiffListener listener )
    {
        sink.section1();
        sink.sectionTitle1();
        sink.text( bundle.getString( "report.clirr.summary" ) );
        sink.sectionTitle1_();

        sink.table();

        sink.tableRow();

        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.severity" ) );
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.number" ) );
        sink.tableHeaderCell_();

        sink.tableRow_();

        severityReportTableRow( listener, Severity.ERROR,
            "report.clirr.level.error", "images/icon_error_sml.gif" );

        if ( minSeverity == null || minSeverity.compareTo( Severity.WARNING ) <= 0 )
        {
            severityReportTableRow( listener, Severity.WARNING,
                "report.clirr.level.warning", "images/icon_warning_sml.gif" );
        }

        if ( minSeverity == null || minSeverity.compareTo( Severity.INFO ) <= 0 )
        {
            severityReportTableRow( listener, Severity.INFO,
                "report.clirr.level.info", "images/icon_info_sml.gif" );
        }

        sink.table_();

        if ( minSeverity == null || minSeverity.compareTo( Severity.INFO ) > 0 )
        {
            sink.paragraph();
            sink.italic();
            sink.text( bundle.getString( "report.clirr.filtered" ) );
            sink.italic_();
            sink.paragraph_();
        }

        sink.section1_();
    }

    private void severityReportTableRow( ClirrDiffListener listener, Severity severity,
        String altText, String image )
    {
        sink.tableRow();
        sink.tableCell();
        icon( altText, image );
        sink.nonBreakingSpace();
        sink.text( bundle.getString( altText ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( String.valueOf( listener.getSeverityCount( severity ) ) );
        sink.tableCell_();
        sink.tableRow_();
    }

    private void doDetails( ClirrDiffListener listener )
    {
        sink.section1();
        sink.sectionTitle1();
        sink.text( bundle.getString( "report.clirr.api.incompatibilities" ) );
        sink.sectionTitle1_();

        List<ApiDifference> differences = listener.getApiDifferences();

        if ( !differences.isEmpty() )
        {
            doIncompatibilitiesTable( differences );
        }
        else
        {
            sink.paragraph();
            sink.text( bundle.getString( "report.clirr.noresults" ) );
            sink.paragraph_();
        }

        sink.section1_();
    }

    private void doIncompatibilitiesTable( List<ApiDifference> differences )
    {
        sink.table();
        sink.tableRow();
        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.severity" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.message" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.class" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( bundle.getString( "report.clirr.column.methodorfield" ) );
        sink.tableHeaderCell_();
        sink.tableRow_();

        MessageTranslator translator = new MessageTranslator();
        translator.setLocale( locale );

        for ( ApiDifference difference : differences )
        {
            // TODO: differentiate source and binary? The only difference seems to be MSG_CONSTANT_REMOVED at this point
            Severity maximumSeverity = difference.getMaximumSeverity();

            if ( minSeverity == null || minSeverity.compareTo( maximumSeverity ) <= 0 )
            {
                sink.tableRow();

                sink.tableCell();
                levelIcon( maximumSeverity );
                sink.tableCell_();

                sink.tableCell();
                sink.text( difference.getReport( translator ) );
                sink.tableCell_();

                sink.tableCell();
                if ( xrefLocation != null )
                {
                    String pathToClass = difference.getAffectedClass().replace( '.', '/' );
                    // MCLIRR-18 Special handling of links to inner classes:
                    // We link to the page for the containing class
                    final int innerClassIndex = pathToClass.lastIndexOf( '$' );
                    if ( innerClassIndex != -1 )
                    {
                        pathToClass = pathToClass.substring( 0, innerClassIndex );
                    }
                    sink.link( xrefLocation + "/" + pathToClass + ".html" );
                }
                sink.text( difference.getAffectedClass() );
                if ( xrefLocation != null )
                {
                    sink.link_();
                }
                sink.tableCell_();

                sink.tableCell();
                sink.text( difference.getAffectedMethod() != null ? difference.getAffectedMethod()
                    : difference.getAffectedField() );
                sink.tableCell_();

                sink.tableRow_();
            }
        }

        sink.table_();
    }

    private void levelIcon( Severity level )
    {
        if ( Severity.INFO.equals( level ) )
        {
            iconInfo();
        }
        else if ( Severity.WARNING.equals( level ) )
        {
            iconWarning();
        }
        else if ( Severity.ERROR.equals( level ) )
        {
            iconError();
        }
    }

    private void doApiChanges( ClirrDiffListener listener )
    {
        sink.section1();
        sink.sectionTitle1();
        sink.text( bundle.getString( "report.clirr.api.changes" ) );
        sink.sectionTitle1_();

        Map<Difference, List<ApiChange>> apiChangeReport = getApiChangeReport( listener );
        if ( apiChangeReport.isEmpty() )
        {
            sink.paragraph();
            sink.text( bundle.getString( "report.clirr.noresults" ) );
            sink.paragraph_();
        }
        else
        {
            doApiChangesTable( apiChangeReport );
        }

        sink.section1_();
    }

    private Map<Difference, List<ApiChange>> getApiChangeReport( ClirrDiffListener listener )
    {
        final Map<String, List<ApiChange>> tmp = new HashMap<String, List<ApiChange>>();
        for ( Entry<Difference, List<ApiDifference>> ignoredDiff
            : listener.getIgnoredApiDifferences().entrySet() )
        {
            for ( ApiDifference apiDiff : ignoredDiff.getValue() )
            {
                putApiChange( tmp, apiDiff, ignoredDiff.getKey() );
            }
        }
        for ( ApiDifference apiDiff : listener.getApiDifferences() )
        {
            putApiChange( tmp, apiDiff, null );
        }


        final Map<Difference, List<ApiChange>> results =
            new TreeMap<Difference, List<ApiChange>>( new JustificationComparator() );

        for ( List<ApiChange> changes : tmp.values() )
        {
            for ( ApiChange apiChange : changes )
            {
                List<ApiChange> changesForDifference = results.get( apiChange.difference );
                if ( changesForDifference == null )
                {
                    changesForDifference = new LinkedList<ApiChange>();
                    results.put( apiChange.difference, changesForDifference );
                }
                changesForDifference.add( apiChange );
            }
        }
        return results;
    }

    private void putApiChange( Map<String, List<ApiChange>> results,
            ApiDifference ignoredDiff, Difference reason )
    {
        String apiChangeKey = getKey( ignoredDiff );
        List<ApiChange> apiChanges = results.get( apiChangeKey );
        if ( apiChanges == null )
        {
            apiChanges = new LinkedList<ApiChange>();
            results.put(apiChangeKey, apiChanges);
        }

        if ( reason != null && reason.getDifferenceType() == METHOD_ARGUMENT_TYPE_CHANGED )
        {
            ApiChange apiChange7005 = find7005ApiChange( apiChanges );
            if ( apiChange7005 != null )
            {
                apiChange7005.apiDifferences.add(ignoredDiff);
                return;
            }
        }
        ApiChange change = new ApiChange();
        change.difference = reason != null ? reason : createNullObject();
        if (change.difference.getJustification() == null)
        {
          change.difference.setJustification( bundle.getString( "report.clirr.api.changes.unjustified" ) );
        }
        change.apiDifferences.add( ignoredDiff );
        apiChanges.add( change );
    }

    /**
     * Use a null object to avoid doing null checks everywhere.
     */
    private Difference createNullObject()
    {
        Difference difference = new Difference();
        difference.setClassName("");
        difference.setMethod("");
        difference.setField("");
        difference.setFrom("");
        difference.setTo("");
        difference.setJustification( bundle.getString( "report.clirr.api.changes.unjustified" ) );
        return difference;
    }

    private String getKey( ApiDifference apiDiff )
    {
        if ( apiDiff.getAffectedMethod() != null )
        {
            return apiDiff.getAffectedClass() + " " + apiDiff.getAffectedMethod();
        }
        return apiDiff.getAffectedClass() + " " + apiDiff.getAffectedField();
    }

    private ApiChange find7005ApiChange( List<ApiChange> apiChanges )
    {
        for ( ApiChange apiChange : apiChanges )
        {
            if ( apiChange.difference.getDifferenceType() == METHOD_ARGUMENT_TYPE_CHANGED )
            {
                return apiChange;
            }
        }
        return null;
    }

    private void doApiChangesTable( Map<Difference, List<ApiChange>> apiChangeReport )
    {
        if ( comparisonVersion != null )
        {
            String[] args = new String[]{comparisonVersion, currentVersion};
            String message = i18n.format(
                "clirr-report", locale, "report.clirr.api.changes.listing.comparisonversion", args );
            sink.text( message );
        }
        else
        {
            sink.text( bundle.getString( "report.clirr.api.changes.listing" ) );
        }

        sink.list();
        for ( Entry<Difference, List<ApiChange>> apiChanges
            : apiChangeReport.entrySet() )
        {
            sink.listItem();
            sink.text( apiChanges.getKey().getJustification() );
            sink.paragraph();

            sink.table();
            sink.tableRow();
            sink.tableHeaderCell();
            sink.text( bundle.getString( "report.clirr.api.changes.class" ) );
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text( bundle.getString( "report.clirr.api.changes.from" ) );
            sink.tableHeaderCell_();
            sink.tableHeaderCell();
            sink.text( bundle.getString( "report.clirr.api.changes.to" ) );
            sink.tableHeaderCell_();
            sink.tableRow_();

            for (ApiChange apiChange : apiChanges.getValue())
            {
                apiChange.computeFields();
            }
            Collections.sort(apiChanges.getValue(), new ApiChangeComparator());

            for (ApiChange apiChange : apiChanges.getValue())
            {
                sink.tableRow();
                sink.tableCell();
                sink.text( apiChange.getAffectedClass() );
                sink.tableCell_();
                sink.tableCell();
                sink.text( apiChange.getFrom() );
                sink.tableCell_();
                sink.tableCell();
                sink.text( apiChange.getTo() );
                sink.tableCell_();
                sink.tableRow_();
            }
            sink.table_();
            sink.paragraph_();
            sink.listItem_();
        }
        sink.list_();
    }

    public void setEnableSeveritySummary( boolean enableSeveritySummary )
    {
        this.enableSeveritySummary = enableSeveritySummary;
    }

    public void setMinSeverity( Severity minSeverity )
    {
        this.minSeverity = minSeverity;
    }

    public String getXrefLocation()
    {
        return xrefLocation;
    }

    public void setXrefLocation( String xrefLocation )
    {
        this.xrefLocation = xrefLocation;
    }

    public String getCurrentVersion()
    {
        return currentVersion;
    }

    public void setCurrentVersion( String currentVersion )
    {
        this.currentVersion = currentVersion;
    }

    public String getComparisonVersion()
    {
        return comparisonVersion;
    }

    public void setComparisonVersion( String comparisonVersion )
    {
        this.comparisonVersion = comparisonVersion;
    }
}
