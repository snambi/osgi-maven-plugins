/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

package org.apache.tuscany.maven.bundle.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

/**
 * Parser for the service descriptors. The syntax of the service declaration is similar with the OSGi
 * headers with the following exceptions:
 * <ul>
 * <li>Tuscany uses , and ; as the separator for attibutes
 * <li>Tuscany 
 */
public class HeaderParser {

    private static final String PATH_SEPARATOR = ","; // OSGi style
    // private static final String PATH_SEPARATOR = "|";

    private static final String SEGMENT_SEPARATOR = ";"; // OSGi style
    // private static final String SEGMENT_SEPARATOR = ";,";

    private static final String ATTRIBUTE_SEPARATOR = "=";
    private static final String DIRECTIVE_SEPARATOR = ":=";

    private static final char QUOTE_CHAR = '"';
    private static final String QUOTE = "\"";

    // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2,
    //            path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
    public static List<HeaderClause> parse(String header) {

        if (header != null) {
            if (header.length() == 0) {
                throw new IllegalArgumentException("A header cannot be an empty string.");
            }

            String[] clauseStrings = parseDelimitedString(header, PATH_SEPARATOR);

            List<HeaderClause> completeList = new ArrayList<HeaderClause>();
            for (int i = 0; (clauseStrings != null) && (i < clauseStrings.length); i++) {
                completeList.add(parseClause(clauseStrings[i]));
            }

            return completeList;
        }

        return null;

    }

    // Like this: path; path; dir1:=dirval1; dir2:=dirval2; attr1=attrval1; attr2=attrval2
    private static HeaderClause parseClause(String clauseString) throws IllegalArgumentException {
        // Break string into semi-colon delimited pieces.
        String[] pieces = parseDelimitedString(clauseString, SEGMENT_SEPARATOR);

        // Count the number of different paths; paths
        // will not have an '=' in their string. This assumes
        // that paths come first, before directives and
        // attributes.
        int pathCount = 0;
        for (int pieceIdx = 0; pieceIdx < pieces.length; pieceIdx++) {
            if (pieces[pieceIdx].indexOf('=') >= 0) {
                break;
            }
            pathCount++;
        }

        // Create an array of paths.
        String[] paths = new String[pathCount];
        System.arraycopy(pieces, 0, paths, 0, pathCount);

        // Parse the directives/attributes.
        Map<String, String> dirsMap = new HashMap<String, String>();
        Map<String, String> attrsMap = new HashMap<String, String>();
        int idx = -1;
        String sep = null;
        for (int pieceIdx = pathCount; pieceIdx < pieces.length; pieceIdx++) {
            // Check if it is a directive.
            if ((idx = pieces[pieceIdx].indexOf(DIRECTIVE_SEPARATOR)) >= 0) {
                sep = DIRECTIVE_SEPARATOR;
            }
            // Check if it is an attribute.
            else if ((idx = pieces[pieceIdx].indexOf(ATTRIBUTE_SEPARATOR)) >= 0) {
                sep = ATTRIBUTE_SEPARATOR;
            }
            // It is an error.
            else {
                throw new IllegalArgumentException("Not a directive/attribute: " + clauseString);
            }

            String key = pieces[pieceIdx].substring(0, idx).trim();
            String value = pieces[pieceIdx].substring(idx + sep.length()).trim();

            // Remove quotes, if value is quoted.
            if (value.startsWith(QUOTE) && value.endsWith(QUOTE)) {
                value = value.substring(1, value.length() - 1);
            }

            // Save the directive/attribute in the appropriate array.
            if (sep.equals(DIRECTIVE_SEPARATOR)) {
                // Check for duplicates.
                if (dirsMap.get(key) != null) {
                    throw new IllegalArgumentException("Duplicate directive: " + key);
                }
                dirsMap.put(key, value);
            } else {
                // Check for duplicates.
                if (attrsMap.get(key) != null) {
                    throw new IllegalArgumentException("Duplicate attribute: " + key);
                }
                attrsMap.put(key, value);
            }
        }

        StringBuffer path = new StringBuffer();
        for (int i = 0; i < paths.length; i++) {
            path.append(paths[i]);
            if (i != paths.length - 1) {
                path.append(';');
            }
        }

        HeaderClause descriptor = new HeaderClause();
        descriptor.text = clauseString;
        descriptor.value = path.toString();
        descriptor.valueComponents = paths;
        descriptor.attributes = attrsMap;
        descriptor.directives = dirsMap;

        return descriptor;
    }

    /**
     * Parses delimited string and returns an array containing the tokens. This
     * parser obeys quotes, so the delimiter character will be ignored if it is
     * inside of a quote. This method assumes that the quote character is not
     * included in the set of delimiter characters.
     * @param value the delimited string to parse.
     * @param delim the characters delimiting the tokens.
     * @return an array of string tokens or null if there were no tokens.
    **/
    private static String[] parseDelimitedString(String value, String delim) {
        if (value == null) {
            value = "";
        }

        List<String> list = new ArrayList<String>();

        int CHAR = 1;
        int DELIMITER = 2;
        int STARTQUOTE = 4;
        int ENDQUOTE = 8;

        StringBuffer sb = new StringBuffer();

        int expecting = (CHAR | DELIMITER | STARTQUOTE);

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            boolean isDelimiter = (delim.indexOf(c) >= 0);
            boolean isQuote = (c == QUOTE_CHAR);

            if (isDelimiter && ((expecting & DELIMITER) > 0)) {
                list.add(sb.toString().trim());
                sb.delete(0, sb.length());
                expecting = (CHAR | DELIMITER | STARTQUOTE);
            } else if (isQuote && ((expecting & STARTQUOTE) > 0)) {
                sb.append(c);
                expecting = CHAR | ENDQUOTE;
            } else if (isQuote && ((expecting & ENDQUOTE) > 0)) {
                sb.append(c);
                expecting = (CHAR | STARTQUOTE | DELIMITER);
            } else if ((expecting & CHAR) > 0) {
                sb.append(c);
            } else {
                throw new IllegalArgumentException("Invalid delimited string: " + value);
            }
        }

        if (sb.length() > 0) {
            list.add(sb.toString().trim());
        }

        return (String[])list.toArray(new String[list.size()]);
    }

    public static class HeaderClause {
        private String text;
        private String value;
        private String[] valueComponents;
        private Map<String, String> attributes;
        private Map<String, String> directives;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String[] getValueComponents() {
            return valueComponents;
        }

        public void setValueComponents(String[] valueComponents) {
            this.valueComponents = valueComponents;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public Map<String, String> getDirectives() {
            return directives;
        }

        public String toString() {
            String text = null;
            if (text == null) {
                StringBuffer buf = new StringBuffer();
                if (value == null) {
                    int start = buf.length();
                    for (int i = 0; i < valueComponents.length; i++) {
                        if (i != valueComponents.length - 1) {
                            buf.append(valueComponents[i]).append(';');
                        } else {
                            buf.append(valueComponents[i]);
                        }
                    }
                    int end = buf.length();
                    if (end > start) {
                        value = buf.substring(start, end);
                    }
                }
                buf.append(value);
                for (Map.Entry<String, String> e : attributes.entrySet()) {
                    buf.append(';').append(e.getKey()).append("=\"").append(e.getValue()).append("\"");
                }
                for (Map.Entry<String, String> e : directives.entrySet()) {
                    buf.append(';').append(e.getKey()).append(":=\"").append(e.getValue()).append("\"");
                }
                text = buf.toString();
            }
            return text;
        }

    }

    /**
     * Returns a QName object from a QName expressed as {ns}name
     * or ns#name.
     *
     * @param qname
     * @return
     */
    public static QName getQName(String qname) {
        if (qname == null) {
            return null;
        }
        qname = qname.trim();
        if (qname.startsWith("{")) {
            int h = qname.indexOf('}');
            if (h != -1) {
                return new QName(qname.substring(1, h), qname.substring(h + 1));
            }
        } else {
            int h = qname.indexOf('#');
            if (h != -1) {
                return new QName(qname.substring(0, h), qname.substring(h + 1));
            }
        }
        return new QName(qname);
    }

    public static String toHeader(List<HeaderClause> descriptors) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < descriptors.size(); i++) {
            HeaderClause descriptor = descriptors.get(i);
            buf.append(descriptor);
            if (i != descriptors.size() - 1) {
                buf.append(',');
            }
        }
        return buf.toString();
    }

    public static String merge(String... headers) {
        List<HeaderClause> merged = new ArrayList<HeaderClause>();
        for (String header : headers) {
            if (header == null || header.length() == 0) {
                continue;
            }
            List<HeaderClause> descriptors = parse(header);
            merged.addAll(descriptors);
        }
        Set<String> values = new HashSet<String>();
        for (Iterator<HeaderClause> i = merged.iterator(); i.hasNext();) {
            if (!values.add(i.next().getValue())) {
                i.remove();
            }
        }
        return toHeader(merged);
    }
}
