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

import eu.europeana.sip.model.CompileModel;
import eu.europeana.sip.model.SipModel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Show the current parsed record, and allow for moving to next, and rewinding
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordPanel extends JPanel {
    private JButton firstButton = new JButton("First");
    private JButton nextButton = new JButton("Next");

    public RecordPanel(SipModel sipModel, CompileModel compileModel) {
        super(new BorderLayout());
        final JTabbedPane tabs = new JTabbedPane();
        final RecordSearchPanel rsp = new RecordSearchPanel(sipModel, new RecordSearchPanel.Listener() {
            @Override
            public void searchStarted(String description) {
                firstButton.setText(String.format("First: %s", description));
                nextButton.setText(String.format("Next: %s", description));
            }

            @Override
            public void searchFinished() {
                tabs.setSelectedIndex(0);
            }
        });
        tabs.addTab("Input Record", createRecordTab(compileModel));
        tabs.addTab("Search", rsp);
        add(tabs);
        setPreferredSize(new Dimension(240, 500));
        firstButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rsp.scan(false);
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rsp.scan(true);
            }
        });
    }

    private JPanel createRecordTab(CompileModel compileModel) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scroll(createRecordView(compileModel)), BorderLayout.CENTER);
        JPanel bp = new JPanel(new GridLayout(1, 0, 5, 5));
        bp.add(firstButton);
        bp.add(nextButton);
        p.add(bp, BorderLayout.SOUTH);
        return p;
    }

    private JEditorPane createRecordView(CompileModel compileModel) {
        final JEditorPane recordView = new JEditorPane();
        recordView.setContentType("text/html");
        recordView.setDocument(compileModel.getInputDocument());
        recordView.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        recordView.setCaretPosition(0);
                    }
                });
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });
        recordView.setEditable(false);
        return recordView;
    }

    private JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setPreferredSize(new Dimension(240, 300));
        return scroll;
    }
}