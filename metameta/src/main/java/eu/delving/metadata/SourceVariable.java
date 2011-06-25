/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

/**
 * Hold a variable for later use
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceVariable implements Comparable<SourceVariable> {
    private AnalysisTree.Node node;
    private String variableName;
    private int mappingCount;

    public SourceVariable(AnalysisTree.Node node) {
        this.node = node;
        this.variableName = node.getVariableName();
    }

    public void checkIfMapped(String variableName) {
        if (this.variableName.equals(variableName)) {
            mappingCount++;
        }
    }

    public AnalysisTree.Node getNode() {
        return node;
    }

    public String getVariableName() {
        return variableName;
    }

    public String toString() {
        StringBuilder out = new StringBuilder(variableName);
        switch (mappingCount) {
            case 0:
                break;
            case 1:
                out.append(" (mapped once)");
                break;
            case 2:
                out.append(" (mapped twice)");
                break;
            default:
                out.append(" (mapped ").append(mappingCount).append(" times)");
                break;
        }
        return out.toString();
    }

    @Override
    public int compareTo(SourceVariable o) {
        if (mappingCount > o.mappingCount) {
            return 11;
        }
        else if (mappingCount < o.mappingCount) {
            return -1;
        }
        else {
            return node.compareTo(o.node);
        }
    }

    public boolean hasStatistics() {
        return node != null && node.getStatistics() != null;
    }

    public FieldStatistics getStatistics() {
        return node.getStatistics();
    }

    public static class Filter {
        private static final String PLAIN_ASCII =
                "AaEeIiOoUu"    // grave
                        + "AaEeIiOoUuYy"  // acute
                        + "AaEeIiOoUuYy"  // circumflex
                        + "AaOoNn"        // tilde
                        + "AaEeIiOoUuYy"  // umlaut
                        + "Aa"            // ring
                        + "Cc"            // cedilla
                        + "OoUu"          // double acute
                        + "_"             // dash
                        + "_"             // dot
                        + "_"             // colon
                ;

        private static final String UNICODE =
                "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"
                        + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"
                        + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"
                        + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"
                        + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"
                        + "\u00C5\u00E5"
                        + "\u00C7\u00E7"
                        + "\u0150\u0151\u0170\u0171"
                        + "-"
                        + "."
                        + ":";

        public static String tagToVariable(String s) {
            if (s == null) return null;
            StringBuilder sb = new StringBuilder();
            int n = s.length();
            for (int i = 0; i < n; i++) {
                char c = s.charAt(i);
                int pos = UNICODE.indexOf(c);
                if (pos > -1) {
                    sb.append(PLAIN_ASCII.charAt(pos));
                }
                else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

}
