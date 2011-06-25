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

package eu.europeana.sip.gui;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Histogram;
import eu.delving.metadata.RandomSample;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Show statistics in an html panel, with special tricks for separately threading the html generation
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FieldStatisticsPanel extends JPanel {
    private JLabel summaryLabel = new JLabel("Summary", JLabel.CENTER);
    private HistogramModel histogramModel = new HistogramModel();
    private RandomSampleModel randomSampleModel = new RandomSampleModel();

    public FieldStatisticsPanel() {
        super(new BorderLayout(5, 5));
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder("Statistics"),
                        BorderFactory.createEmptyBorder(0, 0, 8, 0)
                )
        );
        add(createTabs(), BorderLayout.CENTER);
        summaryLabel.setFont(new Font(summaryLabel.getFont().getFamily(), Font.BOLD, summaryLabel.getFont().getSize()));
        add(summaryLabel, BorderLayout.NORTH);
    }

    public void setStatistics(final FieldStatistics fieldStatistics) {
        if (fieldStatistics == null) {
            summaryLabel.setText("No statistics.");
            histogramModel.setHistogram(null);
            randomSampleModel.setRandomSample(null);
        }
        else {
            summaryLabel.setText(fieldStatistics.getSummary());
            histogramModel.setHistogram(fieldStatistics.getHistogram());
            randomSampleModel.setRandomSample(fieldStatistics.getRandomSample());
        }
    }

    private JComponent createTabs() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Random Sample", createRandomSamplePanel());
        tabbedPane.add("Histogram", createHistogramPanel());
        return tabbedPane;
    }

    private JComponent createRandomSamplePanel() {
        JList list = new JList(randomSampleModel);
        return scroll(list);
    }

    private JComponent createHistogramPanel() {
        JList list = new JList(histogramModel);
        return scroll(list);
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    private class HistogramModel extends AbstractListModel {

        private List<Histogram.Counter> list = new ArrayList<Histogram.Counter>();

        public void setHistogram(Histogram histogram) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (histogram != null) {
                list.addAll(histogram.getTrimmedCounters());
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            Histogram.Counter counter = list.get(i);
            return String.format("   %d (%s) : '%s'", counter.getCount(), counter.getPercentage(), counter.getValue());
        }
    }

    private class RandomSampleModel extends AbstractListModel {

        private List<String> list = new ArrayList<String>();

        public void setRandomSample(RandomSample randomSample) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (randomSample != null) {
                list.addAll(randomSample.getValues());
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Object getElementAt(int i) {
            return "   " + list.get(i);
        }
    }
}
