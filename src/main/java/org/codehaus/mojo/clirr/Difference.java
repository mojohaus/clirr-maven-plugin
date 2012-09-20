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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.clirr.core.ApiDifference;
import net.sf.clirr.core.MessageTranslator;

import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * A specification of a ignored difference found by Clirr.
 *
 * @author Lukas Krejci
 */
public class Difference {

    public static class Result {
        public static final int MATCHED = 0;
        public static final int NOT_MATCHED = 1;
        public static final int DEFERRED_MATCH = 2;
        
        public Result(int code, Object differentiator) {
            this.code = code;
            this.differentiator = differentiator;
        }
        
        public static Result notMatched() {
            return new Result(NOT_MATCHED, null);
        }
        
        public static Result matched() {            
            return new Result(MATCHED, null);
        }
        
        public static Result deferred(Object differentiator) {
            return new Result(DEFERRED_MATCH, differentiator);
        }
        
        private int code;
        private Object differentiator;
        
        public int getCode() {
            return code;
        }
        
        public Object getDifferentiator() {
            return differentiator;
        }
    }
    
    private static final MessageTranslator ARGS_EXTRACTOR = new MessageTranslator();
    static {
        ARGS_EXTRACTOR.setResourceName(Difference.class.getName());        
    }
    
    public static Difference[] parseXml(Reader xml) throws XmlPullParserException, IOException {
        XmlPullParser parser = new MXParser();
        parser.setInput(xml);
        
        ArrayList diffs = new ArrayList();
        
        int state = 0;
        int event;
        Difference current = null;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            switch(event) {
            case XmlPullParser.START_TAG:
                switch(state) {
                case 0: //start document
                    state = 1; 
                    break;
                case 1: //expect next difference
                    if ("difference".equals(parser.getName())) {
                        current = new Difference();
                        state = 2;
                    }
                    break;
                case 2: //reading difference
                    String name = parser.getName();
                    String value = parser.nextText();
                    if ("className".equals(name)) {
                        current.className = value;
                    } else if ("differenceType".equals(name)) {
                        current.differenceType = Integer.parseInt(value);
                    } else if ("field".equals(name)) {
                        current.field = value;
                    } else if ("method".equals(name)) {
                        current.method = value;
                    } else if ("from".equals(name)) {
                        current.from = value;
                    } else if ("to".equals(name)) {
                        current.to = value;
                    } else if ("justification".equals(name)) {
                        current.justification = value;
                    }
                    break;
                }
                break;
            case XmlPullParser.END_TAG:
                switch(state) {
                case 1: case 2:
                    if ("difference".equals(parser.getName())) {
                        diffs.add(current);
                        state = 1;
                    }
                    break;
                }
            }
        }
        
        Difference[] ret = new Difference[diffs.size()];
        
        return (Difference[]) diffs.toArray(ret);
    }
    
    /**
     * See http://clirr.sourceforge.net/clirr-core/exegesis.html for the different
     * codes of differences.
     * <p>
     * Different types of differences require different parameters set
     * (className is always required):
     * <ul>
     * <li><b>1000 (Increased visibility of a class)</b>: no other params but className
     * <li><b>1001 (Decreased visibility of a class)</b>: no other params but className
     * <li><b>2000 (Changed from class to interface)</b>: no other params but className
     * <li><b>2001 (Changed from interface to class)</b>: no other params but className
     * <li><b>3001 (Removed final modifier from class)</b>: no other params but className
     * <li><b>3002 (Added final modifier to effectively final class)</b>: no other params but className
     * <li><b>3003 (Added final modifier to class)</b>: no other params but className
     * <li><b>3004 (Removed abstract modifier from class)</b>: no other params but className
     * <li><b>3005 (Added abstract modifier to class)</b>: no other params but className 
     * <li><b>4000 (Added interface to the set of implemented interfaces)</b>: className, to (as a path expression)
     * <li><b>4001 (Removed interface from the set of implemented interfaces)</b>: className, to (as a path expression)
     * <li><b>5000 (Added class to the set of superclasses)</b>: className, to (as a path expression)
     * <li><b>5001 (Removed class from the set of superclasses)</b>: className, to (as a path expression)
     * <li><b>6000 (added field)</b>: className,  field
     * <li><b>6001 (removed field)</b>: className,  field
     * <li><b>6002 (field value no longer a compile-time constant)</b>: className,  field
     * <li><b>6003 (value of the compile-time constant changed on a field)</b>: className,  field
     * <li><b>6004 (field type changed)</b>: className,  field, from, to
     * <li><b>6005 (field now non-final)</b>: className,  field
     * <li><b>6006 (field now final)</b>: className,  field
     * <li><b>6007 (field now non-static)</b>: className,  field
     * <li><b>6008 (field now static)</b>: className,  field
     * <li><b>6009 (field more accessible)</b>: className,  field
     * <li><b>6010 (field less accessible)</b>: className,  field
     * <li><b>6011 (removed a constant field)</b>: className,  field
     * <li><b>7000 (method now in superclass)</b>: className,  method
     * <li><b>7001 (method now in interface)</b>: className,  method
     * <li><b>7002 (method removed)</b>: className,  method
     * <li><b>7003 (Method Overide Removed)</b>: className,  method
     * <li><b>7004 (Method Argument Count Changed)</b>: className,  method
     * <li><b>7005 (Method Argument Type changed)</b>: className,  method, to (to is a full new signature)
     * <li><b>7006 (Method Return Type changed)</b>: className,  method, to (to is just the return type)
     * <li><b>7007 (Method has been Deprecated)</b>: className,  method
     * <li><b>7008 (Method has been Undeprecated)</b>: className,  method
     * <li><b>7009 (Method is now Less Accessible)</b>: className,  method
     * <li><b>7010 (Method is now More Accessible)</b>: className,  method
     * <li><b>7011 (Method Added)</b>: className,  method
     * <li><b>7012 (Method Added to Interface)</b>: className,  method
     * <li><b>7013 (Abstract Method Added to Class)</b>: className,  method
     * <li><b>7014 (Method now final)</b>: className,  method
     * <li><b>7015 (Method now non-final)</b>: className,  method
     * <li><b>8000 (Class added)</b>: className
     * <li><b>8001 (Class removed)</b>: className
     * <li><b>10000 (Class format version increased)</b>: className, from, to (class format version numbers)
     * <li><b>10001 (Class format version decreased)</b>: className, from, to (class format version numbers)
     * </ul>
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
     * Note that this must not contain any type information and only match the field's name.
     * This parameter is an expression (technically a maven path expression). 
     * 
     * @parameter
     */
    private String field;
    
    /**
     * The signature of the method that should be ignored according to the {@link #differenceType difference type}.
     * This parameter is an expression (technically a maven path expression). 
     * 
     * @parameter
     */
    private String method;
    
    /**
     * The original type of the field (if it is important for the difference type, otherwise can
     * be left out). Note that this parameter is ignored when dealing with methods.
     * This parameter is an expression (technically a maven path expression). 
     * 
     * @parameter
     */
    private String from;
    
    /**
     * The "new" type of the field (or method return type) or "new" method signature (if it is important for the difference type, otherwise can
     * be left out).
     * This parameter is an expression (technically a maven path expression). 
     * 
     * @parameter
     */
    private String to;
    
    /**
     * The reason why ignoring this difference is deemed OK.
     * @parameter
     * @required
     */
    private String justification;

    
    public int getDifferenceType() {
        return differenceType;
    }

    public void setDifferenceType(int differenceType) {
        this.differenceType = differenceType;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public Result matches(ApiDifference apiDiff) {
        if (apiDiff.getMessage().getId() != differenceType) {
            return Result.notMatched();
        }

        String affectedClassPath = apiDiff.getAffectedClass().replace(".", File.separator);
        if (!SelectorUtils.matchPath(className, affectedClassPath)) {
            return Result.notMatched();
        }
        
        switch (differenceType) {
        case 1000: 
            return Result.matched();
        case 1001: 
            return Result.matched();
        case 2000: 
            return Result.matched();
        case 2001:             
            return Result.matched();
        case 3000: 
            return Result.matched();
        case 3001: 
            return Result.matched();
        case 3002:
            return Result.matched();
        case 3003: 
            return Result.matched();
        case 3004: 
            return Result.matched();
        case 3005: 
            return Result.matched();
        case 4000: 
            return matches4000(apiDiff) ? Result.matched() : Result.notMatched();
        case 4001: 
            return matches4001(apiDiff) ? Result.matched() : Result.notMatched();
        case 5000: 
            return matches5000(apiDiff) ? Result.matched() : Result.notMatched();
        case 5001: 
            return matches5001(apiDiff) ? Result.matched() : Result.notMatched();
        case 6000: //added field
            return matches6000(apiDiff) ? Result.matched() : Result.notMatched();
        case 6001: //removed field
            return matches6001(apiDiff) ? Result.matched() : Result.notMatched();
        case 6002: //field value no longer a compile-time constant
            return matches6002(apiDiff) ? Result.matched() : Result.notMatched();
        case 6003: //value of the compile-time constant changed on a field
            return matches6003(apiDiff) ? Result.matched() : Result.notMatched();
        case 6004: //field type changed
            return matches6004(apiDiff) ? Result.matched() : Result.notMatched();
        case 6005: //field now non-final
            return matches6005(apiDiff) ? Result.matched() : Result.notMatched();
        case 6006: //field now final
            return matches6006(apiDiff) ? Result.matched() : Result.notMatched();
        case 6007: //field now non-static
            return matches6007(apiDiff) ? Result.matched() : Result.notMatched();
        case 6008: //field now static
            return matches6008(apiDiff) ? Result.matched() : Result.notMatched();
        case 6009: //field more accessible
            return matches6009(apiDiff) ? Result.matched() : Result.notMatched();
        case 6010: //field less accessible
            return matches6010(apiDiff) ? Result.matched() : Result.notMatched();
        case 6011: //removed a constant field
            return matches6011(apiDiff) ? Result.matched() : Result.notMatched();
        case 7000: //method now in superclass
            return matches7000(apiDiff) ? Result.matched() : Result.notMatched();
        case 7001: //method now in interface
            return matches7001(apiDiff) ? Result.matched() : Result.notMatched();
        case 7002: //method removed
            return matches7002(apiDiff) ? Result.matched() : Result.notMatched();
        case 7003: //Method Overide Removed
            return matches7003(apiDiff) ? Result.matched() : Result.notMatched();
        case 7004: //Method Argument Count Changed
            return matches7004(apiDiff) ? Result.matched() : Result.notMatched();
        case 7005: //Method Argument Type changed
            return Result.deferred(getDifferentiatorFor7005(apiDiff));
        case 7006: //Method Return Type changed
            return matches7006(apiDiff) ? Result.matched() : Result.notMatched();
        case 7007: //Method has been Deprecated
            return matches7007(apiDiff) ? Result.matched() : Result.notMatched();
        case 7008: //Method has been Undeprecated
            return matches7008(apiDiff) ? Result.matched() : Result.notMatched();
        case 7009: //Method is now Less Accessible
            return matches7009(apiDiff) ? Result.matched() : Result.notMatched();
        case 7010: //Method is now More Accessible
            return matches7010(apiDiff) ? Result.matched() : Result.notMatched();
        case 7011: //Method Added
            return matches7011(apiDiff) ? Result.matched() : Result.notMatched();
        case 7012: //Method Added to Interface
            return matches7012(apiDiff) ? Result.matched() : Result.notMatched();
        case 7013: //Abstract Method Added to Class
            return matches7013(apiDiff) ? Result.matched() : Result.notMatched();
        case 7014: //Method now final
            return matches7014(apiDiff) ? Result.matched() : Result.notMatched();
        case 7015: //Method now non-final
            return matches7015(apiDiff) ? Result.matched() : Result.notMatched();
        case 8000: //Class added
            return Result.matched();
        case 8001: //Class removed
            return Result.matched();
        case 10000:
            return matches10000(apiDiff) ? Result.matched() : Result.notMatched();
        case 10001:
            return matches10001(apiDiff) ? Result.matched() : Result.notMatched();
        default:
            return Result.notMatched();
        }
    }

    public boolean resolveDefferedMatches(List defferedApiDifferences) {
        if (differenceType == 7005) {
            return matches7005(defferedApiDifferences);
        } else {
            return false;
        }
    }
    
    public String toString() {
        return new StringBuilder("Difference[differenceType=").append(differenceType).append(", className=").append(className).append(", field=").append(field).append(", method=").append(method).append(", from=").append(from).append(", to=").append(to).append("]").toString();
        
    }
    
    /**
     * Added interface to the set of implemented interfaces
     */
    private boolean matches4000(ApiDifference apiDiff) {
        throwIfMissing(false, false, false, true);
        
        String newIface = getArgs(apiDiff)[0];
        newIface = newIface.replace(".", File.separator);
        
        return SelectorUtils.matchPath(to, newIface);
    }

    /**
     * Removed interface from the set of implemented interfaces
     */
    private boolean matches4001(ApiDifference apiDiff) {
        throwIfMissing(false, false, false, true);
        
        String removedIface = getArgs(apiDiff)[0];
        removedIface = removedIface.replace(".", File.separator);
        
        return SelectorUtils.matchPath(to, removedIface);
    }

    /**
     * Added class to the set of superclasses
     */
    private boolean matches5000(ApiDifference apiDiff) {
        throwIfMissing(false, false, false, true);
        
        String newSuperclass = getArgs(apiDiff)[0];
        newSuperclass = newSuperclass.replace(".", File.separator);
        
        return SelectorUtils.matchPath(to, newSuperclass);
    }

    /**
     * Removed class from the set of superclasses
     */
    private boolean matches5001(ApiDifference apiDiff) {
        throwIfMissing(false, false, false, true);
        
        String removedSuperclass = getArgs(apiDiff)[0];
        removedSuperclass = removedSuperclass.replace(".", File.separator);
        
        return SelectorUtils.matchPath(to, removedSuperclass);
    }

    /**
     * added field
     */
    private boolean matches6000(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * removed field
     */
    private boolean matches6001(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field value no longer a compile-time constant
     */
    private boolean matches6002(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * value of the compile-time constant changed on a field
     */
    private boolean matches6003(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field type changed
     */
    private boolean matches6004(ApiDifference apiDiff) {
        throwIfMissing(true, false, true, true);
        
        if (!SelectorUtils.matchPath(field, apiDiff.getAffectedField())) {
            return false;
        }
        
        String[] args = getArgs(apiDiff);
        String diffFrom = args[0];
        String diffTo = args[1];
        
        return SelectorUtils.matchPath(from, diffFrom) && SelectorUtils.matchPath(to, diffTo);
    }

    /**
     * field now non-final
     */
    private boolean matches6005(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field now final
     */
    private boolean matches6006(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field now non-static
     */
    private boolean matches6007(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field now static
     */
    private boolean matches6008(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field more accessible
     */
    private boolean matches6009(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * field less accessible
     */
    private boolean matches6010(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * removed a constant field
     */
    private boolean matches6011(ApiDifference apiDiff) {
        throwIfMissing(true, false, false, false);
        return SelectorUtils.matchPath(field, apiDiff.getAffectedField());
    }

    /**
     * method now in superclass
     */
    private boolean matches7000(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * method now in interface
     */
    private boolean matches7001(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * method removed
     */
    private boolean matches7002(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method Overide Removed
     */
    private boolean matches7003(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method Argument Count Changed
     */
    private boolean matches7004(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    private Object getDifferentiatorFor7005(ApiDifference apiDiff) {
        return apiDiff.getAffectedClass() + apiDiff.getAffectedMethod();
    }
    
    /**
     * Method Argument Type changed
     */
    private boolean matches7005(List apiDiffs) {
        throwIfMissing(false, true, false, true);
        
        ApiDifference firstDiff = (ApiDifference) apiDiffs.get(0);
        
        String methodSig = removeVisibilityFromMethodSignature(firstDiff);
        
        if (!SelectorUtils.matchPath(method, methodSig)) {
            return false;
        }
        
        String newType = methodSig;
        for(Iterator i = apiDiffs.iterator(); i.hasNext();) {
            ApiDifference apiDiff = (ApiDifference) i.next();
            
            String[] args = getArgs(apiDiff);
            
            //1-based
            int idx = Integer.parseInt(args[0]) - 1;
            String diffNewType = args[1];
            
            //construct the new full type
            
            newType = replaceNthArgumentType(newType, idx, diffNewType);            
        }
        
        return SelectorUtils.matchPath(to, newType);
    }

    /**
     * Method Return Type changed
     */
    private boolean matches7006(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, true);

        String methodSig = removeVisibilityFromMethodSignature(apiDiff);
        if (!SelectorUtils.matchPath(method, methodSig)) {
            return false;
        }
        
        if (methodSig.startsWith(apiDiff.getAffectedClass())) {
            //well, it's kinda hard to tell here, right?
            //Constructors aren't supposed to change return types
            //or to have any.
            return false;
        }
        
        String newRetType = getArgs(apiDiff)[0];
        
        return SelectorUtils.matchPath(to, newRetType);
    }

    /**
     * Method has been Deprecated
     */
    private boolean matches7007(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method has been Undeprecated
     */
    private boolean matches7008(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method is now Less Accessible
     */
    private boolean matches7009(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method is now More Accessible
     */
    private boolean matches7010(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method Added
     */
    private boolean matches7011(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method Added to Interface
     */
    private boolean matches7012(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Abstract Method Added to Class
     */
    private boolean matches7013(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method now final
     */
    private boolean matches7014(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Method now non-final
     */
    private boolean matches7015(ApiDifference apiDiff) {
        throwIfMissing(false, true, false, false);
        return SelectorUtils.matchPath(method, removeVisibilityFromMethodSignature(apiDiff));
    }

    /**
     * Class format version increased
     */
    private boolean matches10000(ApiDifference apiDiff) {
        throwIfMissing(false, false, true, true);
        
        int fromVersion = 0;
        int toVersion = 0;
        try {
            fromVersion = Integer.parseInt(from);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse the \"from\" parameter as a number for " + this);
        }

        try {
            toVersion = Integer.parseInt(to);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse the \"to\" parameter as a number for " + this);
        }
        
        String[] args = getArgs(apiDiff);
        
        int reportedOld = Integer.parseInt(args[0]);
        int reportedNew = Integer.parseInt(args[1]);
        
        return fromVersion == reportedOld && toVersion == reportedNew;
    }
    
    /**
     * Class format version decreased
     */
    private boolean matches10001(ApiDifference apiDiff) {
        throwIfMissing(false, false, true, true);
        
        int fromVersion = 0;
        int toVersion = 0;
        try {
            fromVersion = Integer.parseInt(from);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse the \"from\" parameter as a number for " + this);
        }

        try {
            toVersion = Integer.parseInt(to);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse the \"to\" parameter as a number for " + this);
        }
        
        String[] args = getArgs(apiDiff);
        
        int reportedOld = Integer.parseInt(args[0]);
        int reportedNew = Integer.parseInt(args[1]);
        
        return fromVersion == reportedOld && toVersion == reportedNew;
    }
    
    private String[] getArgs(ApiDifference apiDiff) {
        String args = apiDiff.getReport(ARGS_EXTRACTOR);
        return args.split("&");
    }
    
    private void throwIfMissing(boolean field, boolean method, boolean from, boolean to) {
        boolean doThrow = (field && this.field == null) || (method && this.method == null) ||
            (from && this.from == null) || (to && this.to == null);
        
        if (doThrow) {            
            StringBuilder message = new StringBuilder("The following parameters are missing: ");
            if (field && this.field == null) {
                message.append("field, ");            
            }
            
            if (method && this.method == null) {
                message.append("method, ");            
            } 
            
            if (from && this.from == null) {
                message.append("from, ");            
            }
            
            if (to && this.to == null) {
                message.append("to, ");
            }
            
            message.replace(message.length() - 2, message.length(), "");
            
            message.append(" on ").append(this);
            
            throw new IllegalArgumentException(message.toString());
        }
    }
    
    private static String replaceNthArgumentType(String signature, int idx, String newType) {
        int openParIdx = signature.indexOf('(');
        int closeParIdx = signature.indexOf(')');
        
        if (openParIdx < 0 || closeParIdx < 0) {
            throw new IllegalArgumentException("Invalid method signature found in the API difference report: " + signature);
        }
        
        StringBuilder bld = new StringBuilder();
        bld.append(signature, 0, openParIdx).append('(');
        
        int commaIdx = openParIdx + 1;
        int paramIdx = 0;
        while(true) {
            int nextCommaIdx = signature.indexOf(',', commaIdx);
            
            if (nextCommaIdx < 0) {
                break;
            }
            
            String type = paramIdx == idx ? newType : signature.substring(commaIdx, nextCommaIdx);
            
            bld.append(type);
            bld.append(", ");
            
            commaIdx = nextCommaIdx + 1;
            paramIdx++;
            
        }
        
        if (paramIdx == idx) {
            bld.append(newType);
        } else {
            bld.append(signature, commaIdx + 1, closeParIdx);
        }
        
        bld.append(")");
        
        return bld.toString();
    }
    
    private String removeVisibilityFromMethodSignature(ApiDifference apiDiff) {
        String methodSig = apiDiff.getAffectedMethod();
        if (methodSig == null) {
            return null;
        }
        
        int spaceIdx = methodSig.indexOf(' ');
        if (spaceIdx < 0) {
            return methodSig;
        } else {
            return methodSig.substring(spaceIdx + 1);
        }
    }
}
