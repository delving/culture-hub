/*
 * Copyright 2007 EDL FOUNDATION
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

package eu.europeana.sip.xml;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Counters {
    private enum Counter {

        CONVERTER_PROBLEM(true),
        NO_IS_SHOWN_AT_OR_BY(true),
        UNKNOWN_EUROPEANA_TYPE(true),
        DUPLICATE_URI_DISCARDED(true),
        MISSING_EUROPEANA_URI(true),
        MISSING_LANGUAGE(true),

        DISCARDED(false),

        WITHOUT_EUROPEANA_OBJECT(false),
        DISCARDED_WITHOUT_EUROPEANA_OBJECT(true),
        DUPLICATE_URI_ALLOWED(false),
        UNPARSEABLE_YEAR(false);

        private boolean discarded;

        private Counter(boolean discarded) {
            this.discarded = discarded;
        }

        public boolean isDiscarded() {
            return discarded;
        }
    }
    private int [] counts = new int[Counter.values().length];

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("Counters:\n\n");
        out.append("   Discarded:\n");
        for (Counter counter: Counter.values()) {
            if (counter == Counter.DISCARDED) continue;
            if (counter.isDiscarded() && counts[counter.ordinal()] > 0) {
                out.append("      ").append(counter.toString()).append(": ").append(counts[counter.ordinal()]).append("\n");
            }
        }
        out.append("   Total Discarded: ").append(counts[Counter.DISCARDED.ordinal()]).append("\n\n");
        out.append("   Warnings:\n");
        for (Counter counter: Counter.values()) {
            if (counter == Counter.DISCARDED) continue;
            if (!counter.isDiscarded() && counts[counter.ordinal()] > 0) {
                out.append("      ").append(counter.toString()).append(": ").append(counts[counter.ordinal()]).append("\n");
            }
        }
        return out.toString();
    }

    public void converterProblem() {
        increment(Counter.CONVERTER_PROBLEM);
    }

    public void withoutEuropeanaObject() {
        increment(Counter.WITHOUT_EUROPEANA_OBJECT);
    }

    public void discardedWithoutEuropeanaObject() {
        increment(Counter.DISCARDED_WITHOUT_EUROPEANA_OBJECT);
    }

    public void noIsShownValue() {
        increment(Counter.NO_IS_SHOWN_AT_OR_BY);
    }

    public void unknownEuropeanaType() {
        increment(Counter.UNKNOWN_EUROPEANA_TYPE);
    }

    public void duplicateUriDiscarded() {
        increment(Counter.DUPLICATE_URI_DISCARDED);
    }

    public void duplicateUriAllowed() {
        increment(Counter.DUPLICATE_URI_ALLOWED);
    }

    public void missingLanguage() {
        increment(Counter.MISSING_LANGUAGE);
    }

    public void missingEuropeanaUri() {
        increment(Counter.MISSING_EUROPEANA_URI);
    }

    public void unparseableYear() {
        increment(Counter.UNPARSEABLE_YEAR);
    }

    private void increment(Counter counter) {
        counts[counter.ordinal()]++;
        if (counter.isDiscarded()) {
            counts[Counter.DISCARDED.ordinal()]++;
        }
    }
}