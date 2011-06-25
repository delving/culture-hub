package eu.europeana.sip.gui;

import org.apache.log4j.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * GUI for the AutoCompletionImpl.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class AutoCompleteDialog extends JFrame {
    private static final Logger LOG = Logger.getLogger(AutoCompleteDialog.class);
    public static final String DEFAULT_PREFIX = "input.";
    private Listener listener;
    private JTextComponent parent;
    private JComboBox jComboBox = new JComboBox();
    private AutoComplete autoComplete = new AutoComplete();

    interface Listener {
        void itemSelected(Object selectedItem);
    }

    public AutoCompleteDialog(Listener listener, JTextComponent parent) {
        this.listener = listener;
        this.parent = parent;
        add(jComboBox);
        init();
    }

    private void init() {
        jComboBox.setEditable(true);
        jComboBox.getEditor().getEditorComponent().addKeyListener(
                new KeyAdapter() {

                    @Override
                    public void keyPressed(KeyEvent e) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_ESCAPE: {
                                setVisible(false);
                                autoComplete.cancelled();
                            }
                        }
                    }
                }
        );
        jComboBox.addItemListener(
                new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (ItemEvent.SELECTED == e.getStateChange()) {
                            listener.itemSelected(e.getItem());
                            setVisible(false);
                            autoComplete.cleared();
                        }
                    }
                }
        );
        setUndecorated(true);
    }

    public void updateLocation(Point caretLocation, Point editorLocation) {
        if (null == caretLocation) {
            return;
        }
        Point point = new Point(
                (int) caretLocation.getX() + (int) editorLocation.getX(),
                (int) caretLocation.getY() + (int) editorLocation.getY() + 16 // todo: get caret height
        );
        setLocation(point);
    }

    public void updateElements(KeyEvent event, List<String> availableElements) { // todo: delegate to autoComplete
        availableElements = autoComplete.complete(event, availableElements);
        if (null == availableElements) {
            setVisible(false);
            parent.requestFocus();
            return;
        }
        jComboBox.setModel(new DefaultComboBoxModel(availableElements.toArray()));
        setVisible(true);
        setSize(new Dimension(300, 20));
    }

    public void requestFocus(Point lastCaretPosition) {
        Point lastCaretPosition1 = lastCaretPosition;
    }

    public class AutoComplete implements AutoCompleteDialog.Listener {

        private static final int DEFAULT_OFFSET = 0;
        private static final String DEFAULT_PREFIX = "input.";
        private static final String VALIDATION_REGEX = "[a-zA-Z_0-9\\.]+";
        private final StringBuffer keyBuffer = new StringBuffer();

        private String prefix;
        private int offSet;

        public AutoComplete() {
            this.offSet = DEFAULT_OFFSET;
            this.prefix = DEFAULT_PREFIX;
        }

        public void itemSelected(Object selectedItem) {
            keyBuffer.setLength(0);
            LOG.debug("Item selected and keyBuffer emptied ; " + selectedItem);
        }

        private boolean validate(KeyEvent entered) {
            Pattern pattern = Pattern.compile(VALIDATION_REGEX);
            return pattern.matcher("" + entered.getKeyChar()).find();
        }

        public List<String> complete(String entered, List<String> originalElements) {
            if (!entered.endsWith(prefix)) { // todo: do this check in advance?
                return null;
            }
            if (null == originalElements) {
                LOG.error("originalElements is null");
                return null;
            }
            entered = entered.substring(entered.lastIndexOf(DEFAULT_PREFIX) + DEFAULT_PREFIX.length());
            List<String> remaining = new ArrayList<String>();
            for (String inList : originalElements) {
                if (inList.startsWith(entered)) {
                    remaining.add(inList);
                }
            }
            return remaining;
        }

        public List<String> complete(KeyEvent entered, List<String> originalElements) {
            if (validate(entered)) {
                keyBuffer.append(entered.getKeyChar());
            }
            return complete(keyBuffer.toString(), originalElements);
        }

        public void cleared() {
            keyBuffer.setLength(0);
        }

        public void cancelled() {
            keyBuffer.setLength(0);
        }
    }

}
