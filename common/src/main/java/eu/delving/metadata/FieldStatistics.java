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

import java.io.Serializable;
import java.util.Set;

/**
 * Maintain a map of strings and counters
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 */

public class FieldStatistics implements Comparable<FieldStatistics>, Serializable {
    private static final int RANDOM_SAMPLE_SIZE = 300;
    private static final int HISTOGRAM_MAX_STORAGE_SIZE = 1024 * 64;
    private static final int HISTOGRAM_MAX_SIZE = 2400;

    private Path path;
    private int total;
    private ValueStats valueStats;

    public FieldStatistics(Path path) {
        this.path = path;
    }

    public void recordValue(String value) {
        if (valueStats == null) {
            valueStats = new ValueStats();
        }
        valueStats.recordValue(value);
    }

    public void recordOccurrence() {
        total++;
    }

    public Path getPath() {
        return path;
    }

    public int getTotal() {
        return total;
    }

    public Histogram getHistogram() {
        return valueStats != null ? valueStats.histogram : null;
    }

    public RandomSample getRandomSample() {
        return valueStats != null ? valueStats.randomSample : null;
    }

    public String getSummary() {
        if (valueStats == null) {
            if (total == 1) {
                return String.format("Element appears just once.");
            }
            else {
                return String.format("Element appears %d times.", total);
            }
        }
        else {
            return valueStats.getSummary();
        }
    }

    public boolean hasValues() {
        return valueStats != null;
    }

    public Set<String> getHistogramValues() {
        if (valueStats == null || valueStats.histogram == null || valueStats.histogram.isTrimmed()) return null;
        return valueStats.histogram.getValues();
    }

    public void finish() {
        if (valueStats != null) {
            valueStats.finish();
        }
    }

    public String toString() {
        return path + " (" + total + ")";
    }

    @Override
    public int compareTo(FieldStatistics fieldStatistics) {
        return path.compareTo(fieldStatistics.path);
    }

    private class ValueStats implements Serializable {
        RandomSample randomSample = new RandomSample(RANDOM_SAMPLE_SIZE);
        Histogram histogram = new Histogram(HISTOGRAM_MAX_STORAGE_SIZE, HISTOGRAM_MAX_SIZE);
        Uniqueness uniqueness = new Uniqueness();
        boolean uniqueValues;

        void recordValue(String value) {
            if (randomSample != null) {
                randomSample.recordValue(value);
            }
            if (histogram != null) {
                histogram.recordValue(value);
                if (histogram.isTooLarge()) {
                    histogram.getTrimmedCounters();
                }
                else if (histogram.isTooMuchData()) {
                    histogram = null;
                }
            }
            if (uniqueness != null) {
                if (uniqueness.isRepeated(value)) {
                    uniqueness = null;
                }
            }
        }

        public void finish() {
            if (uniqueness != null) {
                Set<String> repeated = uniqueness.getRepeated();
                if (repeated.isEmpty()) {
                    uniqueValues = total > 1;
                }
                uniqueness = null;
                if (total > 1) {
                    uniqueValues = true;
                    histogram = null;
                }
            }
            if (histogram != null) {
                histogram.getTrimmedCounters();
            }
        }

        public String getSummary() {
            if (uniqueValues) {
                return String.format("All %d values are completely unique", total);
            }
            else if (histogram != null) {
                if (histogram.isTrimmed()) {
                    return String.format("Histogram size %d exceeded, so histogram is incomplete.", histogram.getMaxSize());
                }
                else {
                    if (histogram.getSize() == 1) {
                        Histogram.Counter counter = histogram.getTrimmedCounters().iterator().next();
                        return String.format("The single value '%s' appears %d times.", counter.getValue(), counter.getCount());
                    }
                    else {
                        return String.format("There were %d different values, not all unique.", histogram.getSize());
                    }
                }
            }
            else {
                return String.format("Storage %dk exceeded, so histogram is discarded.", HISTOGRAM_MAX_STORAGE_SIZE / 1024);
            }
        }
    }
}
