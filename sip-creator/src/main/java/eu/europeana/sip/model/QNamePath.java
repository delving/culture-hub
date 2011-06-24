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

package eu.europeana.sip.model;

import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;

/**
 * A path consisting of a stack of qnames
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 */

public class QNamePath implements Comparable<QNamePath>, Serializable {
    private static final long serialVersionUID = 4032911343731921611L;
    private Stack<QName> stack = new Stack<QName>();

    public QNamePath() {
    }

    public QNamePath(QNamePath path) {
        for (QName name : path.stack) {
            stack.push(name);
        }
    }

    public QNamePath(QNamePath path, int count) {
        for (QName name : path.stack) {
            if (count-- > 0) {
                stack.push(name);
            }
        }
    }

    public void push(QName name) {
        stack.push(name);
    }

    public void pop() {
        stack.pop();
    }

    public boolean equals(QNamePath path) {
        return compareTo(path) == 0;
    }

//    public boolean equals(QNamePath path, int level) {
//        QNamePath us = new QNamePath(this, level);
//        QNamePath them = new QNamePath(path, level);
//        return us.equals(them);
//    }

    @Override
    public int compareTo(QNamePath path) {
        Iterator<QName> walkUs = stack.iterator();
        Iterator<QName> walkThem = path.stack.iterator();
        while (true) {
            if (!walkUs.hasNext()) {
                if (!walkThem.hasNext()) {
                    return 0;
                }
                else {
                    return -1;
                }
            }
            else if (!walkThem.hasNext()) {
                return 1;
            }
            int cmp = compare(walkUs.next(), walkThem.next());
            if (cmp != 0) {
                return cmp;
            }
        }
    }

    public QName getQName(int level) {
        if (level < stack.size()) {
            return stack.get(level);
        }
        else {
            return null;
        }
    }

    public QName peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public int size() {
        return stack.size();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder(300);
        for (QName name : stack) {
            builder.append('/');
            if (!name.getPrefix().isEmpty()) {
                builder.append(name.getPrefix());
                builder.append(':');
                builder.append(name.getLocalPart());
            }
            else {
                builder.append(name.getLocalPart());
            }
        }
        return builder.toString();
    }

    public String getLastNodeString() {
        if (stack.isEmpty()) {
            return "Empty";
        }
        else {
            QName name = stack.peek();
            if (!name.getPrefix().isEmpty()) {
                return name.getPrefix()+":"+name.getLocalPart();
            }
            else {
                return name.getLocalPart();
            }
        }
    }

    private static int compare(QName a, QName b) {
        int cmp = compare(a.getPrefix(), b.getPrefix());
        if (cmp != 0) {
            return cmp;
        }
        return compare(a.getLocalPart(), b.getLocalPart());
    }

    private static int compare(String a, String b) {
        if (a != null) {
            if (b != null) {
                return a.compareTo(b);
            }
            else {
                return -1;  // a, not b
            }
        }
        else if (b != null) {
            return 1; // b, not a
        }
        else {
            return 0;
        }
    }

}
