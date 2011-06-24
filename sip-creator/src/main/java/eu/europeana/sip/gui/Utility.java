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

package eu.europeana.sip.gui;

import javax.swing.Spring;
import javax.swing.SpringLayout;
import java.awt.Component;
import java.awt.Container;

/**
 * Help with SpringLayout forms
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Utility {

    public static final String GROOVY_TOOL_TIP = "<html><table cellpadding=10><tr><td>" +
            "<h2>Operators</h2>" +
            "<ul>" +
            "<li><b>each</b>: the star operator runs the code in braces for every input entry<br>" +
            "<b><code>input * {}</code></li></b>" +
            "<li><b>concat</b>: the star-string-star operator concatenates with a delimiter<br>" +
            "<b><code>input * '; ' * {}</code></b></li>" +
            "<li><b>word</b>: the star-star operator takes only the first if there are multiple<br>" +
            "<b><code>input ** {}</code></b></li>" +
            "<li><b>split</b>: the  operator splits inputs on a regular expression<br>" +
            "<b><code>input % /;/ * {}</code></b></li>" +
            "</ul>" +
            "<h2>Notes</h2>" +
            "<ul>" +
            "<li>Inside the braces the '<b>it</b>' variable holds the contents</li>" +
            "<li>it can easily be substituted like <b>&quot;</b>http://somewhere.com/images/${<b>it</b>}.jpg<b>&quot;</li>" +
            "</ul>" +
            "<br><br>" +
            "</td></tr></table></html>";

    private static SpringLayout.Constraints getConstraintsForCell(int row, int col, Container parent, int cols) {
        SpringLayout layout = (SpringLayout) parent.getLayout();
        Component c = parent.getComponent(row * cols + col);
        return layout.getConstraints(c);
    }

    public static void makeCompactGrid(Container parent, int rows, int cols, int initialX, int initialY, int xPad, int yPad) {
        SpringLayout layout = (SpringLayout) parent.getLayout();
        //Align all cells in each column and make them the same width.
        Spring x = Spring.constant(initialX);
        for (int c = 0; c < cols; c++) {
            Spring width = Spring.constant(0);
            for (int r = 0; r < rows; r++) {
                width = Spring.max(width, getConstraintsForCell(r, c, parent, cols).getWidth());
            }
            for (int r = 0; r < rows; r++) {
                SpringLayout.Constraints constraints = getConstraintsForCell(r, c, parent, cols);
                constraints.setX(x);
                constraints.setWidth(width);
            }
            x = Spring.sum(x, Spring.sum(width, Spring.constant(xPad)));
        }

        //Align all cells in each row and make them the same height.
        Spring y = Spring.constant(initialY);
        for (int r = 0; r < rows; r++) {
            Spring height = Spring.constant(0);
            for (int c = 0; c < cols; c++) {
                height = Spring.max(height, getConstraintsForCell(r, c, parent, cols).getHeight());
            }
            for (int c = 0; c < cols; c++) {
                SpringLayout.Constraints constraints = getConstraintsForCell(r, c, parent, cols);
                constraints.setY(y);
                constraints.setHeight(height);
            }
            y = Spring.sum(y, Spring.sum(height, Spring.constant(yPad)));
        }

        //Set the parent's size.
        SpringLayout.Constraints pCons = layout.getConstraints(parent);
        pCons.setConstraint(SpringLayout.EAST, x);
    }

}
